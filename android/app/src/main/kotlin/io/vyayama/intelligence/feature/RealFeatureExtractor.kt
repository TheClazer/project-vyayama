package io.vyayama.intelligence.feature

import io.vyayama.api.Angles
import io.vyayama.api.FeatureExtractor
import io.vyayama.api.Kp
import io.vyayama.api.NormalizedPose
import io.vyayama.api.PoseFrame
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Pure geometry helpers (no platform deps). */
object Geom {
    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(ax - bx, ay - by)

    /** Angle (degrees) at vertex B for points A-B-C; NaN if a limb has zero length. */
    fun angleDeg(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float {
        val v1x = ax - bx; val v1y = ay - by
        val v2x = cx - bx; val v2y = cy - by
        val m1 = hypot(v1x, v1y); val m2 = hypot(v2x, v2y)
        if (m1 < 1e-6f || m2 < 1e-6f) return Float.NaN
        var cosv = (v1x * v2x + v1y * v2y) / (m1 * m2)
        if (cosv > 1f) cosv = 1f
        if (cosv < -1f) cosv = -1f
        return Math.toDegrees(acos(cosv.toDouble())).toFloat()
    }

    fun clamp01(v: Float): Float = max(0f, min(1f, v))
}

/** Config for normalization (tunable = test vectors, bible §8.3). */
data class FeatureConfig(
    val tauKp: Float = 0.3f,        // keypoint confidence gate
    val emaAlpha: Float = 0.3f      // angle smoothing factor (0 = off)
)

/**
 * PoseFrame → NormalizedPose: confidence-gate → hip-center → torso-normalize → joint angles →
 * EMA smoothing. Camera/scale/translation invariant. Stateful (holds EMA); construct one per session.
 * Bible §8.3.
 */
class RealFeatureExtractor(private val cfg: FeatureConfig = FeatureConfig()) : FeatureExtractor {

    private val ema = FloatArray(Angles.COUNT) { Float.NaN }

    override fun normalize(pose: PoseFrame): NormalizedPose {
        val tau = cfg.tauKp
        fun gated(i: Int) = pose.conf(i) >= tau

        val confidences = FloatArray(Kp.COUNT) { pose.conf(it) }
        val angles = FloatArray(Angles.COUNT) { Float.NaN }
        val norm = FloatArray(Kp.COUNT * 2) { Float.NaN }

        // Need both hips + both shoulders to do anything meaningful.
        val hipsOk = gated(Kp.L_HIP) && gated(Kp.R_HIP)
        val shouldersOk = gated(Kp.L_SHOULDER) && gated(Kp.R_SHOULDER)
        val visible = hipsOk && shouldersOk
        if (!visible) {
            // freeze EMA; emit all-NaN angles so downstream skips this frame.
            return NormalizedPose(angles.copyOf(), norm, confidences, visible = false)
        }

        val hipCx = (pose.x(Kp.L_HIP) + pose.x(Kp.R_HIP)) * 0.5f
        val hipCy = (pose.y(Kp.L_HIP) + pose.y(Kp.R_HIP)) * 0.5f
        val shCx = (pose.x(Kp.L_SHOULDER) + pose.x(Kp.R_SHOULDER)) * 0.5f
        val shCy = (pose.y(Kp.L_SHOULDER) + pose.y(Kp.R_SHOULDER)) * 0.5f
        var torsoLen = Geom.dist(hipCx, hipCy, shCx, shCy)
        if (torsoLen < 1e-3f) torsoLen = 1f

        // hip-centered, torso-normalized coords (NaN where gated out)
        for (i in 0 until Kp.COUNT) {
            if (gated(i)) {
                norm[i * 2] = (pose.x(i) - hipCx) / torsoLen
                norm[i * 2 + 1] = (pose.y(i) - hipCy) / torsoLen
            }
        }

        // ---- raw joint angles (only if their 3 keypoints are gated) ----
        fun ang(a: Int, b: Int, c: Int): Float =
            if (gated(a) && gated(b) && gated(c))
                Geom.angleDeg(pose.x(a), pose.y(a), pose.x(b), pose.y(b), pose.x(c), pose.y(c))
            else Float.NaN

        val raw = FloatArray(Angles.COUNT) { Float.NaN }
        raw[Angles.KNEE_L] = ang(Kp.L_HIP, Kp.L_KNEE, Kp.L_ANKLE)
        raw[Angles.KNEE_R] = ang(Kp.R_HIP, Kp.R_KNEE, Kp.R_ANKLE)
        raw[Angles.HIP_L] = ang(Kp.L_SHOULDER, Kp.L_HIP, Kp.L_KNEE)
        raw[Angles.HIP_R] = ang(Kp.R_SHOULDER, Kp.R_HIP, Kp.R_KNEE)
        raw[Angles.ELBOW_L] = ang(Kp.L_SHOULDER, Kp.L_ELBOW, Kp.L_WRIST)
        raw[Angles.ELBOW_R] = ang(Kp.R_SHOULDER, Kp.R_ELBOW, Kp.R_WRIST)
        raw[Angles.SHOULDER_L] = ang(Kp.L_HIP, Kp.L_SHOULDER, Kp.L_ELBOW)
        raw[Angles.SHOULDER_R] = ang(Kp.R_HIP, Kp.R_SHOULDER, Kp.R_ELBOW)

        // torso lean from image vertical: angle between (shoulderC→hipC) and straight-down (0,1).
        // upright standing ≈ 0°, horizontal (push-up) ≈ 90°.
        raw[Angles.TORSO_LEAN] = Geom.angleDeg(shCx, shCy + 1f, shCx, shCy, hipCx, hipCy)

        // openness composite for jumping jack (bible §8.3)
        raw[Angles.OPENNESS] = openness(pose, ::gated, hipCx, hipCy, shCx, shCy, torsoLen)

        // ---- EMA smoothing (skip NaN; hold last on gap) ----
        val a = cfg.emaAlpha
        for (i in 0 until Angles.COUNT) {
            val r = raw[i]
            if (r.isNaN()) {
                angles[i] = Float.NaN        // downstream skips; ema state preserved
            } else if (a <= 0f || ema[i].isNaN()) {
                ema[i] = r; angles[i] = r
            } else {
                ema[i] = a * r + (1f - a) * ema[i]
                angles[i] = ema[i]
            }
        }

        return NormalizedPose(angles, norm, confidences, visible = true)
    }

    private fun openness(
        pose: PoseFrame, gated: (Int) -> Boolean,
        hipCx: Float, hipCy: Float, shCx: Float, shCy: Float, torsoLen: Float
    ): Float {
        // arm raise: wrists above shoulders (image y grows downward)
        val wristsOk = gated(Kp.L_WRIST) && gated(Kp.R_WRIST)
        val anklesOk = gated(Kp.L_ANKLE) && gated(Kp.R_ANKLE)
        if (!wristsOk && !anklesOk) return Float.NaN
        var armRaise = 0f
        if (wristsOk) {
            val wcy = (pose.y(Kp.L_WRIST) + pose.y(Kp.R_WRIST)) * 0.5f
            armRaise = Geom.clamp01((shCy - wcy) / torsoLen)
        }
        var legSpread = 0f
        if (anklesOk) {
            val ankleSpread = Geom.dist(pose.x(Kp.L_ANKLE), pose.y(Kp.L_ANKLE), pose.x(Kp.R_ANKLE), pose.y(Kp.R_ANKLE))
            val shoulderWidth = Geom.dist(pose.x(Kp.L_SHOULDER), pose.y(Kp.L_SHOULDER), pose.x(Kp.R_SHOULDER), pose.y(Kp.R_SHOULDER))
            val sw = if (shoulderWidth < 1e-3f) torsoLen else shoulderWidth
            legSpread = Geom.clamp01((ankleSpread / sw - 1f) / 1f)
        }
        return 0.5f * armRaise + 0.5f * legSpread
    }
}
