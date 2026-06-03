package io.vyayama.intelligence.classify

import io.vyayama.api.Angles
import io.vyayama.api.ExerciseClassifier
import io.vyayama.api.ExerciseType
import io.vyayama.api.NormalizedPose
import io.vyayama.api.PoseWindow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Tunable thresholds (= test vectors, bible §9). */
data class ClassifyConfig(
    val minFrames: Int = 10,
    val activeAmp: Float = 25f,     // "deg-equivalent" amplitude to be considered exercising
    val enterFrames: Int = 8,       // consecutive active frames to enter EXERCISING
    val exitFrames: Int = 14,       // consecutive idle frames to return to IDLE
    val switchFrames: Int = 8,      // a candidate must win this many frames to become the reported type
    val kneeAmpMin: Float = 30f,
    val elbowAmpMin: Float = 25f,
    val opennessAmpMin: Float = 0.40f,
    val symAsym: Float = 25f,       // |kneeL-kneeR| boundary between squat (sym) and lunge (asym)
    val horizTorso: Float = 50f     // torso-lean degrees above which the body is "horizontal" (push-up)
)

/**
 * Rule-based exercise recognition + the is-exercising gate (bible §9.1, §9.2). Stateful (holds the
 * idle/active hysteresis + classification hysteresis); construct one per session. Emits UNKNOWN
 * rather than guessing — honesty over a wrong label.
 */
class RuleExerciseClassifier(private val cfg: ClassifyConfig = ClassifyConfig()) : ExerciseClassifier {

    private var exercising = false
    private var activeStreak = 0
    private var idleStreak = 0
    private var reported = ExerciseType.NONE
    private var candidate = ExerciseType.NONE
    private var candidateStreak = 0

    override fun reset() {
        exercising = false; activeStreak = 0; idleStreak = 0
        reported = ExerciseType.NONE; candidate = ExerciseType.NONE; candidateStreak = 0
    }

    override fun classify(window: PoseWindow): ExerciseState {
        val frames = window.frames().filter { it.visible }
        if (frames.size < cfg.minFrames) return ExerciseState.IDLE

        val knee = series(frames) { avg(it.angle(Angles.KNEE_L), it.angle(Angles.KNEE_R)) }
        val elbow = series(frames) { avg(it.angle(Angles.ELBOW_L), it.angle(Angles.ELBOW_R)) }
        val openness = series(frames) { it.angle(Angles.OPENNESS) }
        val torso = series(frames) { it.angle(Angles.TORSO_LEAN) }

        val kneeAmp = amp(knee)
        val elbowAmp = amp(elbow)
        val opennessAmp = amp(openness)
        val activity = maxOf(kneeAmp, elbowAmp, opennessAmp * 140f)

        // ---- is-exercising hysteresis ----
        if (activity > cfg.activeAmp) { activeStreak++; idleStreak = 0 } else { idleStreak++; activeStreak = 0 }
        if (!exercising && activeStreak >= cfg.enterFrames) exercising = true
        if (exercising && idleStreak >= cfg.exitFrames) { exercising = false; reported = ExerciseType.NONE }
        if (!exercising) return ExerciseState.IDLE

        // ---- signature classification ----
        val avgTorso = mean(torso)
        val kneeSym = meanAbsDiff(frames) { it.angle(Angles.KNEE_L) to it.angle(Angles.KNEE_R) }
        val cand = when {
            opennessAmp > cfg.opennessAmpMin -> ExerciseType.JUMPING_JACK
            avgTorso > cfg.horizTorso && elbowAmp > cfg.elbowAmpMin -> ExerciseType.PUSHUP
            kneeAmp > cfg.kneeAmpMin && kneeSym < cfg.symAsym -> ExerciseType.SQUAT
            kneeAmp > cfg.kneeAmpMin && kneeSym >= cfg.symAsym -> ExerciseType.LUNGE
            elbowAmp > cfg.elbowAmpMin && kneeAmp < 20f && avgTorso < 35f -> ExerciseType.BICEP_CURL
            else -> ExerciseType.UNKNOWN
        }

        // ---- classification hysteresis ----
        if (cand == candidate) candidateStreak++ else { candidate = cand; candidateStreak = 1 }
        if (candidateStreak >= cfg.switchFrames && cand != ExerciseType.UNKNOWN) reported = cand
        if (reported == ExerciseType.NONE) return ExerciseState.IDLE

        val conf = min(1f, 0.2f + 0.8f * (candidateStreak.toFloat() / cfg.switchFrames))
        return ExerciseState(reported, conf, ExerciseState.Source.RULES)
    }

    // ---- helpers ----
    private inline fun series(frames: List<NormalizedPose>, sel: (NormalizedPose) -> Float): FloatArray =
        FloatArray(frames.size) { sel(frames[it]) }

    private fun avg(a: Float, b: Float): Float =
        when { !a.isNaN() && !b.isNaN() -> (a + b) * 0.5f; !a.isNaN() -> a; !b.isNaN() -> b; else -> Float.NaN }

    private fun amp(s: FloatArray): Float {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var any = false
        for (v in s) if (!v.isNaN()) { lo = min(lo, v); hi = max(hi, v); any = true }
        return if (any) hi - lo else 0f
    }

    private fun mean(s: FloatArray): Float {
        var sum = 0f; var n = 0
        for (v in s) if (!v.isNaN()) { sum += v; n++ }
        return if (n > 0) sum / n else Float.NaN
    }

    private inline fun meanAbsDiff(frames: List<NormalizedPose>, sel: (NormalizedPose) -> Pair<Float, Float>): Float {
        var sum = 0f; var n = 0
        for (f in frames) { val (a, b) = sel(f); if (!a.isNaN() && !b.isNaN()) { sum += abs(a - b); n++ } }
        return if (n > 0) sum / n else 0f
    }
}
