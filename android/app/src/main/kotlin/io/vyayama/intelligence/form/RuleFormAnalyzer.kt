package io.vyayama.intelligence.form

import io.vyayama.api.Angles
import io.vyayama.api.Cue
import io.vyayama.api.ExerciseType
import io.vyayama.api.FormAnalyzer
import io.vyayama.api.FormFeedback
import io.vyayama.api.Kp
import io.vyayama.api.NormalizedPose
import io.vyayama.api.RepPhase
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Per-rep biomechanical form rules → 0..100 score + actionable cues (bible §13).
 * Operates on the window of NormalizedPose frames spanning one rep. The single highest-severity cue
 * is the one the UI shows ("one cue at a time"). Sub-score weights/cutoffs are tunable constants.
 */
class RuleFormAnalyzer : FormAnalyzer {

    override fun reset() {}

    override fun analyze(exercise: ExerciseType, repWindow: List<NormalizedPose>, phase: RepPhase): FormFeedback {
        val frames = repWindow.filter { it.visible }
        if (frames.size < 3) return FormFeedback.EMPTY
        return when (exercise) {
            ExerciseType.SQUAT -> squat(frames)
            ExerciseType.PUSHUP -> pushup(frames)
            ExerciseType.LUNGE -> lunge(frames)
            ExerciseType.BICEP_CURL -> curl(frames)
            ExerciseType.JUMPING_JACK -> jack(frames)
            else -> FormFeedback.EMPTY
        }
    }

    // ---------------- per-exercise ----------------
    private fun squat(f: List<NormalizedPose>): FormFeedback {
        val minKnee = minOf(f) { avg(it.angle(Angles.KNEE_L), it.angle(Angles.KNEE_R)) }
        val depth = clamp01((165f - minKnee) / (165f - 95f))
        val sym = meanAbsDiff(f, Angles.KNEE_L, Angles.KNEE_R)
        val symScore = clamp01(1f - sym / 40f)
        val maxLean = maxOf(f) { it.angle(Angles.TORSO_LEAN) }
        val upright = clamp01(1f - max(0f, maxLean - 30f) / 45f)
        val tempo = tempoScore(f.size)

        val cues = mutableListOf<Cue>()
        if (depth < 0.75f) cues += Cue(9, "knee", "Go deeper — hips below knees")
        if (sym > 22f) cues += Cue(6, "knee", "Even out your weight")
        if (maxLean > 50f) cues += Cue(5, "torso", "Chest up — keep your back straight")
        return build(cues, depth = depth, sym = symScore, tempo = tempo,
            weights = floatArrayOf(0.45f, 0.20f, 0.20f, 0.15f),
            scores = floatArrayOf(depth, symScore, upright, tempo))
    }

    private fun pushup(f: List<NormalizedPose>): FormFeedback {
        val minElbow = minOf(f) { avg(it.angle(Angles.ELBOW_L), it.angle(Angles.ELBOW_R)) }
        val depth = clamp01((160f - minElbow) / (160f - 95f))
        // hip sag/pike: with hip-centered coords the body is in line when the shoulder–ankle
        // line passes near the origin (the hips). Larger perpendicular distance = sag or pike.
        val sag = maxOf(f) { hipDeviation(it) }
        val sagScore = clamp01(1f - sag / 0.35f)
        val sym = meanAbsDiff(f, Angles.ELBOW_L, Angles.ELBOW_R)
        val symScore = clamp01(1f - sym / 40f)
        val tempo = tempoScore(f.size)

        val cues = mutableListOf<Cue>()
        if (depth < 0.75f) cues += Cue(8, "elbow", "Lower your chest further")
        if (sagScore < 0.7f) cues += Cue(9, "hip", "Keep your hips in line — no sagging")
        if (sym > 22f) cues += Cue(5, "elbow", "Press evenly with both arms")
        return build(cues, depth = depth, sym = symScore, tempo = tempo,
            weights = floatArrayOf(0.35f, 0.35f, 0.15f, 0.15f),
            scores = floatArrayOf(depth, sagScore, symScore, tempo))
    }

    private fun lunge(f: List<NormalizedPose>): FormFeedback {
        val minFront = minOf(f) { minVal(it.angle(Angles.KNEE_L), it.angle(Angles.KNEE_R)) }
        val depth = clamp01((165f - minFront) / (165f - 95f))
        val maxLean = maxOf(f) { it.angle(Angles.TORSO_LEAN) }
        val upright = clamp01(1f - max(0f, maxLean - 25f) / 45f)
        val tempo = tempoScore(f.size)
        val cues = mutableListOf<Cue>()
        if (depth < 0.7f) cues += Cue(8, "knee", "Drop your back knee lower")
        if (maxLean > 45f) cues += Cue(6, "torso", "Stay upright")
        return build(cues, depth = depth, sym = upright, tempo = tempo,
            weights = floatArrayOf(0.5f, 0.3f, 0.2f),
            scores = floatArrayOf(depth, upright, tempo))
    }

    private fun curl(f: List<NormalizedPose>): FormFeedback {
        val elbowRange = range(f) { minVal(it.angle(Angles.ELBOW_L), it.angle(Angles.ELBOW_R)) }
        val rom = clamp01(elbowRange / 100f)
        // shoulder should stay fixed (no swinging) → low variance of shoulder angle
        val drift = variance(f) { avg(it.angle(Angles.SHOULDER_L), it.angle(Angles.SHOULDER_R)) }
        val driftScore = clamp01(1f - drift / 400f)   // 400 ≈ (20°)² std
        val tempo = tempoScore(f.size)
        val cues = mutableListOf<Cue>()
        if (rom < 0.7f) cues += Cue(7, "elbow", "Full range — extend and squeeze")
        if (driftScore < 0.7f) cues += Cue(9, "shoulder", "Stop swinging — isolate the bicep")
        return build(cues, depth = rom, sym = driftScore, tempo = tempo,
            weights = floatArrayOf(0.45f, 0.4f, 0.15f),
            scores = floatArrayOf(rom, driftScore, tempo))
    }

    private fun jack(f: List<NormalizedPose>): FormFeedback {
        val maxOpen = maxOf(f) { it.angle(Angles.OPENNESS) }
        val extension = clamp01(maxOpen / 0.9f)
        val tempo = tempoScore(f.size)
        val cues = mutableListOf<Cue>()
        if (extension < 0.7f) cues += Cue(7, "limbs", "Full extension — arms overhead, feet wide")
        return build(cues, depth = extension, sym = extension, tempo = tempo,
            weights = floatArrayOf(0.7f, 0.3f),
            scores = floatArrayOf(extension, tempo))
    }

    // ---------------- scoring + helpers ----------------
    private fun build(cues: List<Cue>, depth: Float, sym: Float, tempo: Float,
                      weights: FloatArray, scores: FloatArray): FormFeedback {
        var s = 0f
        for (i in weights.indices) s += weights[i] * scores[i]
        val score = (clamp01(s) * 100f).roundToInt()
        return FormFeedback(cues.sortedByDescending { it.severity }, score, sym, depth, tempo)
    }

    private fun tempoScore(frames: Int): Float {
        // ideal rep ~ 20..60 frames (≈0.66–2 s @30 fps); penalize too fast / too slow
        return when {
            frames < 12 -> 0.4f
            frames in 12..18 -> 0.75f
            frames in 19..70 -> 1.0f
            frames in 71..100 -> 0.8f
            else -> 0.6f
        }
    }

    private fun avg(a: Float, b: Float): Float =
        when { !a.isNaN() && !b.isNaN() -> (a + b) * 0.5f; !a.isNaN() -> a; !b.isNaN() -> b; else -> Float.NaN }

    private fun minVal(a: Float, b: Float): Float =
        when { !a.isNaN() && !b.isNaN() -> min(a, b); !a.isNaN() -> a; !b.isNaN() -> b; else -> Float.NaN }

    private inline fun minOf(f: List<NormalizedPose>, sel: (NormalizedPose) -> Float): Float {
        var m = Float.MAX_VALUE; var any = false
        for (p in f) { val v = sel(p); if (!v.isNaN()) { m = min(m, v); any = true } }
        return if (any) m else Float.NaN
    }

    private inline fun maxOf(f: List<NormalizedPose>, sel: (NormalizedPose) -> Float): Float {
        var m = -Float.MAX_VALUE; var any = false
        for (p in f) { val v = sel(p); if (!v.isNaN()) { m = max(m, v); any = true } }
        return if (any) m else 0f
    }

    private inline fun range(f: List<NormalizedPose>, sel: (NormalizedPose) -> Float): Float {
        var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE; var any = false
        for (p in f) { val v = sel(p); if (!v.isNaN()) { lo = min(lo, v); hi = max(hi, v); any = true } }
        return if (any) hi - lo else 0f
    }

    private inline fun variance(f: List<NormalizedPose>, sel: (NormalizedPose) -> Float): Float {
        var sum = 0f; var n = 0
        val vals = ArrayList<Float>(f.size)
        for (p in f) { val v = sel(p); if (!v.isNaN()) { vals.add(v); sum += v; n++ } }
        if (n < 2) return 0f
        val mean = sum / n; var acc = 0f
        for (v in vals) acc += (v - mean) * (v - mean)
        return acc / n
    }

    private fun meanAbsDiff(f: List<NormalizedPose>, ia: Int, ib: Int): Float {
        var sum = 0f; var n = 0
        for (p in f) { val a = p.angle(ia); val b = p.angle(ib); if (!a.isNaN() && !b.isNaN()) { sum += abs(a - b); n++ } }
        return if (n > 0) sum / n else 0f
    }

    /** Perpendicular distance from the hips (origin in normalized coords) to the shoulder–ankle line. */
    private fun hipDeviation(p: NormalizedPose): Float {
        val n = p.keypointsNorm
        fun mid(i: Int, j: Int, c: Int) = (n[i * 2 + c] + n[j * 2 + c]) * 0.5f
        val sx = mid(Kp.L_SHOULDER, Kp.R_SHOULDER, 0); val sy = mid(Kp.L_SHOULDER, Kp.R_SHOULDER, 1)
        val ax = mid(Kp.L_ANKLE, Kp.R_ANKLE, 0); val ay = mid(Kp.L_ANKLE, Kp.R_ANKLE, 1)
        if (sx.isNaN() || sy.isNaN() || ax.isNaN() || ay.isNaN()) return 0f
        val len = hypot(ax - sx, ay - sy)
        if (len < 1e-4f) return 0f
        // distance from origin (0,0) to line through (sx,sy)-(ax,ay)
        return abs((ax - sx) * (sy) - (sx) * (ay - sy)) / len
    }

    private fun clamp01(v: Float): Float = max(0f, min(1f, v))
}
