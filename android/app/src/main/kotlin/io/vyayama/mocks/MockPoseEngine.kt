package io.vyayama.mocks

import io.vyayama.api.Backend
import io.vyayama.api.CameraFrame
import io.vyayama.api.Kp
import io.vyayama.api.PerfSnapshot
import io.vyayama.api.PoseEngine
import io.vyayama.api.PoseFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The keystone mock (bible §7): synthesizes a person doing squats so the WHOLE app — pipeline +
 * UI + reps + cues — runs and animates with no device, no NPU, no camera. Knee angle follows a
 * squat cycle (170°→90°→170°); the rest of the skeleton tracks plausibly so the overlay reads as a
 * squat and the classifier recognizes SQUAT.
 *
 * (When recorded keypoint CSVs exist, swap the synthesis for CSV replay — same interface.)
 */
class MockPoseEngine(private val periodSec: Float = 1.6f) : PoseEngine {

    private var n = 0
    private var ready = false

    override fun init(backend: Backend): Backend { ready = true; return Backend.MOCK }
    override fun backend(): Backend = Backend.MOCK
    override fun isReady(): Boolean = ready
    override fun forceBackend(b: Backend): Backend = Backend.MOCK
    override fun lastPerf(): PerfSnapshot = PerfSnapshot(Backend.MOCK, 0f, 0f, 0f, 30f)
    override fun close() { ready = false }

    override suspend fun infer(frame: CameraFrame): PoseFrame {
        val t = n / 30f
        val phase = 2.0 * PI * t / periodSec
        val depth = (0.5 - 0.5 * cos(phase)).toFloat()        // 0 (stand) → 1 (deep) → 0
        val theta = 170f - 80f * depth                        // knee angle
        // Every 3rd rep, lean the torso forward (bad form) so the "Chest up" coaching cue actually
        // fires in the mock demo — otherwise the synthetic athlete is flawless and no cue is ever shown.
        val framesPerRep = (periodSec * 30f).toInt()
        val badForm = (n / framesPerRep) % 3 == 2
        val leanPx = if (badForm) 155f * depth else 0f
        val kp = squatFigure(theta, leanPx)
        val ts = n.toLong() * 33_333_333L                     // synthetic 30 fps clock
        n++
        return PoseFrame(kp, 640, 480, Backend.MOCK, detLatencyMs = 0f, poseLatencyMs = 0f, timestampNs = ts)
    }

    /** Build 17 COCO keypoints for a squat at the given knee angle. Feet narrow (openness low).
     *  [leanPx] shifts the upper body forward to simulate a torso lean (bad form) without touching
     *  the legs, so rep counting (knee-driven) is unaffected while TORSO_LEAN rises. */
    private fun squatFigure(kneeAngle: Float, leanPx: Float = 0f): FloatArray {
        val kp = FloatArray(Kp.COUNT * 3)
        for (i in 0 until Kp.COUNT) kp[i * 3 + 2] = 1f
        fun set(i: Int, x: Float, y: Float) { kp[i * 3] = x; kp[i * 3 + 1] = y }

        val thigh = 100f
        val alpha = Math.toRadians((180f - kneeAngle).toDouble())   // thigh angle from vertical
        val dx = (thigh * sin(alpha)).toFloat()
        val dy = (thigh * cos(alpha)).toFloat()

        val legXL = 300f; val legXR = 340f
        val ankleY = 430f; val kneeY = 330f
        set(Kp.L_ANKLE, legXL, ankleY); set(Kp.R_ANKLE, legXR, ankleY)
        set(Kp.L_KNEE, legXL, kneeY); set(Kp.R_KNEE, legXR, kneeY)
        val hipY = kneeY - dy
        val hipXL = legXL + dx; val hipXR = legXR + dx
        set(Kp.L_HIP, hipXL, hipY); set(Kp.R_HIP, hipXR, hipY)

        val shY = hipY - 110f
        set(Kp.L_SHOULDER, hipXL + leanPx, shY); set(Kp.R_SHOULDER, hipXR + leanPx, shY)
        val midX = (hipXL + hipXR) / 2f + leanPx
        set(Kp.NOSE, midX, shY - 45f)
        set(Kp.L_EYE, midX - 8f, shY - 50f); set(Kp.R_EYE, midX + 8f, shY - 50f)
        set(Kp.L_EAR, midX - 15f, shY - 45f); set(Kp.R_EAR, midX + 15f, shY - 45f)
        // arms track the torso (wrists below shoulders → openness stays low → not a jumping jack)
        set(Kp.L_ELBOW, hipXL + leanPx, shY + 55f); set(Kp.R_ELBOW, hipXR + leanPx, shY + 55f)
        set(Kp.L_WRIST, hipXL + leanPx, shY + 105f); set(Kp.R_WRIST, hipXR + leanPx, shY + 105f)
        return kp
    }
}
