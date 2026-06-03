package io.vyayama.intelligence.reps

import io.vyayama.api.Angles
import io.vyayama.api.ExerciseType
import io.vyayama.api.NormalizedPose
import io.vyayama.api.RepCounter
import io.vyayama.api.RepEvent
import io.vyayama.api.RepPhase
import kotlin.math.max
import kotlin.math.min

/**
 * Per-exercise primary signal + the two rest/peak anchor values. Progress
 *   p = (value - top) / (bottom - top)
 * maps rest→0 and peak-effort→1 REGARDLESS of direction, so the same FSM handles angle-based reps
 * (squat/push-up/lunge/curl: top>bottom) and the openness-based jumping jack (top<bottom).
 * Anchor values are tunable constants (= test vectors, bible §12).
 */
data class ExerciseRepConfig(
    val top: Float,
    val bottom: Float,
    val primary: (NormalizedPose) -> Float
) {
    fun progress(p: NormalizedPose): Float {
        val v = primary(p)
        if (v.isNaN()) return Float.NaN
        return (v - top) / (bottom - top)
    }
}

private fun avgValid(a: Float, b: Float): Float =
    when {
        !a.isNaN() && !b.isNaN() -> (a + b) * 0.5f
        !a.isNaN() -> a
        !b.isNaN() -> b
        else -> Float.NaN
    }

private fun minValid(a: Float, b: Float): Float =
    when {
        !a.isNaN() && !b.isNaN() -> min(a, b)
        !a.isNaN() -> a
        !b.isNaN() -> b
        else -> Float.NaN
    }

/** Default Core-5 configs. Tune on recorded data via tools/threshold_tuner. */
object RepConfigs {
    val DEFAULT: Map<ExerciseType, ExerciseRepConfig> = mapOf(
        ExerciseType.SQUAT to ExerciseRepConfig(165f, 95f) { avgValid(it.angle(Angles.KNEE_L), it.angle(Angles.KNEE_R)) },
        ExerciseType.PUSHUP to ExerciseRepConfig(160f, 95f) { avgValid(it.angle(Angles.ELBOW_L), it.angle(Angles.ELBOW_R)) },
        ExerciseType.LUNGE to ExerciseRepConfig(165f, 95f) { minValid(it.angle(Angles.KNEE_L), it.angle(Angles.KNEE_R)) },
        ExerciseType.BICEP_CURL to ExerciseRepConfig(155f, 50f) { minValid(it.angle(Angles.ELBOW_L), it.angle(Angles.ELBOW_R)) },
        ExerciseType.JUMPING_JACK to ExerciseRepConfig(0.15f, 0.85f) { it.angle(Angles.OPENNESS) },
    )
}

/**
 * Rep FSM with hysteresis, min-rep-duration, confidence-freeze, and partial-rep flagging (bible §12).
 * A rep counts on a full TOP→…→BOTTOM→…→TOP cycle.
 */
class StateMachineRepCounter(
    private val configs: Map<ExerciseType, ExerciseRepConfig> = RepConfigs.DEFAULT,
    private val topEnter: Float = 0.15f,
    private val bottomEnter: Float = 0.85f,
    private val partialMin: Float = 0.40f,   // a "real attempt" that didn't reach bottom
    private val minRepMs: Long = 300L
) : RepCounter {

    private var current: ExerciseType = ExerciseType.NONE
    private var phase: RepPhase = RepPhase.TOP
    private var reps = 0
    private var repStartNs = 0L
    private var maxP = 0f

    override fun count(): Int = reps

    override fun reset() {
        phase = RepPhase.TOP; reps = 0; repStartNs = 0L; maxP = 0f
    }

    override fun update(exercise: ExerciseType, pose: NormalizedPose, tsNs: Long): RepEvent {
        if (exercise != current) { current = exercise; reset() }
        if (exercise == ExerciseType.NONE) return event(false, false)

        val cfg = configs[exercise] ?: return event(false, false)
        val p = cfg.progress(pose)
        if (p.isNaN()) return event(false, false)   // confidence-freeze: don't advance on missing data

        var completed = false
        var partial = false

        when (phase) {
            RepPhase.TOP -> if (p > topEnter) { phase = RepPhase.DESCENDING; repStartNs = tsNs; maxP = p }
            RepPhase.DESCENDING -> {
                maxP = max(maxP, p)
                if (p >= bottomEnter) phase = RepPhase.BOTTOM
                else if (p <= topEnter) { if (maxP >= partialMin) partial = true; phase = RepPhase.TOP }
            }
            RepPhase.BOTTOM -> { maxP = max(maxP, p); if (p < bottomEnter) phase = RepPhase.ASCENDING }
            RepPhase.ASCENDING -> {
                if (p >= bottomEnter) phase = RepPhase.BOTTOM
                else if (p <= topEnter) {
                    val durMs = (tsNs - repStartNs) / 1_000_000L
                    if (durMs >= minRepMs) { reps++; completed = true } // else: too fast → noise, drop
                    phase = RepPhase.TOP
                }
            }
        }
        return event(completed, partial)
    }

    private fun event(completed: Boolean, partial: Boolean) =
        RepEvent(current, reps, phase, completedNow = completed, partial = partial, formScore = 0)

    /** Exposed for FormAnalyzer/UI: peak progress of the in-flight rep (0..1+, depth proxy). */
    fun lastMaxProgress(): Float = maxP
}
