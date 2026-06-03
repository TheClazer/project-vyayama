package io.vyayama

import io.vyayama.api.Angles
import io.vyayama.api.Backend
import io.vyayama.api.ExerciseType
import io.vyayama.api.Kp
import io.vyayama.api.NormalizedPose
import io.vyayama.api.PoseFrame
import io.vyayama.api.PoseWindow
import io.vyayama.intelligence.classify.RuleExerciseClassifier
import io.vyayama.intelligence.feature.RealFeatureExtractor
import io.vyayama.intelligence.reps.StateMachineRepCounter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * JVM unit tests for the device-free intelligence layer (bible §7). Mirrors the proven Python
 * harness in tools/threshold_tuner/verify_core.py. Run: ./gradlew :app:testDebugUnitTest
 */
class IntelligenceTest {

    // ---- helpers ----
    private fun frameWithKnee(angle: Float, tsKp: Boolean = true): PoseFrame {
        // build a 17-kpt frame whose left+right knee angle ≈ `angle`, hips/shoulders gated.
        val kp = FloatArray(Kp.COUNT * 3)
        for (i in 0 until Kp.COUNT) kp[i * 3 + 2] = 1f   // all confident
        fun set(i: Int, x: Float, y: Float) { kp[i * 3] = x; kp[i * 3 + 1] = y }
        // upright torso
        set(Kp.L_SHOULDER, 90f, 100f); set(Kp.R_SHOULDER, 110f, 100f)
        set(Kp.L_HIP, 90f, 200f); set(Kp.R_HIP, 110f, 200f)
        // knee angle controlled by ankle position relative to a vertical thigh
        // thigh: hip(90,200)->knee(90,300) is straight down; ankle rotated by `angle` from the thigh
        val rad = Math.toRadians(angle.toDouble())
        // knee at (x,300); ankle placed so angle(hip,knee,ankle) = angle
        val len = 100f
        set(Kp.L_KNEE, 90f, 300f); set(Kp.R_KNEE, 110f, 300f)
        // knee->hip vector points up (0,-1). knee->ankle at `angle` from that.
        val axL = 90f + (len * Math.sin(rad)).toFloat(); val ayL = 300f + (len * cos(rad)).toFloat()
        set(Kp.L_ANKLE, axL, ayL)
        set(Kp.R_ANKLE, 110f + (len * Math.sin(rad)).toFloat(), 300f + (len * cos(rad)).toFloat())
        return PoseFrame(kp, 640, 480, Backend.MOCK, 0f, 0f, 0L)
    }

    private fun np(kneeL: Float = Float.NaN, kneeR: Float = Float.NaN,
                   elbowL: Float = Float.NaN, elbowR: Float = Float.NaN,
                   torso: Float = Float.NaN, openness: Float = Float.NaN): NormalizedPose {
        val a = FloatArray(Angles.COUNT) { Float.NaN }
        a[Angles.KNEE_L] = kneeL; a[Angles.KNEE_R] = kneeR
        a[Angles.ELBOW_L] = elbowL; a[Angles.ELBOW_R] = elbowR
        a[Angles.TORSO_LEAN] = torso; a[Angles.OPENNESS] = openness
        return NormalizedPose(a, FloatArray(34) { 0f }, FloatArray(17) { 1f }, visible = true)
    }

    private fun npKnee(angle: Float) = np(kneeL = angle, kneeR = angle, torso = 10f)

    private fun squatSignal(reps: Int, fpr: Int = 45): List<Float> {
        val out = ArrayList<Float>()
        repeat(reps) { for (f in 0 until fpr) out.add(130f + 40f * cos(2 * Math.PI * f / fpr).toFloat()) }
        out.add(170f); return out
    }

    // ---- FeatureExtractor: angle correctness ----
    @Test fun featureExtractor_rightAngleKnee() {
        val fx = RealFeatureExtractor()
        val n = fx.normalize(frameWithKnee(90f))
        assertTrue(n.visible)
        assertEquals(90f, n.angle(Angles.KNEE_L), 1.5f)
    }

    // ---- RepCounter: the proven FSM behaviors ----
    @Test fun repCounter_fiveSquats() {
        val rc = StateMachineRepCounter()
        var i = 0
        squatSignal(5).forEach { rc.update(ExerciseType.SQUAT, npKnee(it), (i++) * 33_333_333L) }
        assertEquals(5, rc.count())
    }

    @Test fun repCounter_partialNotCounted() {
        val rc = StateMachineRepCounter()
        val partial = listOf(170f, 150f, 130f, 120f, 130f, 150f, 170f, 170f)
        var i = 0
        partial.forEach { rc.update(ExerciseType.SQUAT, npKnee(it), (i++) * 33_333_333L) }
        assertEquals(0, rc.count())
    }

    @Test fun repCounter_tooFastRejected() {
        val rc = StateMachineRepCounter()
        rc.update(ExerciseType.SQUAT, npKnee(170f), 0L)
        rc.update(ExerciseType.SQUAT, npKnee(90f), 33_000_000L)
        rc.update(ExerciseType.SQUAT, npKnee(170f), 66_000_000L)
        assertEquals(0, rc.count())
    }

    @Test fun repCounter_jitterNoCount() {
        val rc = StateMachineRepCounter()
        val jitter = listOf(168f, 162f, 169f, 161f, 170f, 163f)
        var i = 0
        repeat(10) { jitter.forEach { rc.update(ExerciseType.SQUAT, npKnee(it), (i++) * 33_333_333L) } }
        assertEquals(0, rc.count())
    }

    @Test fun repCounter_jumpingJackInvertedDirection() {
        val rc = StateMachineRepCounter()
        var i = 0
        repeat(4) {
            for (x in 0 until 45) {
                val o = 0.5f - 0.5f * cos(2 * Math.PI * x / 45).toFloat()  // 0→1→0
                rc.update(ExerciseType.JUMPING_JACK, np(openness = o), (i++) * 33_333_333L)
            }
        }
        rc.update(ExerciseType.JUMPING_JACK, np(openness = 0f), (i++) * 33_333_333L)
        assertEquals(4, rc.count())
    }

    // ---- Classifier: squat vs push-up discrimination ----
    @Test fun classifier_recognizesSquat() {
        val c = RuleExerciseClassifier()
        val w = PoseWindow(30)
        var result = ExerciseType.NONE
        repeat(40) { i ->
            val knee = if (i % 2 == 0) 160f else 100f      // symmetric oscillation, upright torso
            w.push(np(kneeL = knee, kneeR = knee, torso = 10f, openness = 0f))
            result = c.classify(w).type
        }
        assertEquals(ExerciseType.SQUAT, result)
    }

    @Test fun classifier_recognizesPushup() {
        val c = RuleExerciseClassifier()
        val w = PoseWindow(30)
        var result = ExerciseType.NONE
        repeat(40) { i ->
            val elbow = if (i % 2 == 0) 160f else 90f      // elbow oscillation, horizontal torso
            w.push(np(elbowL = elbow, elbowR = elbow, kneeL = 175f, kneeR = 175f, torso = 80f, openness = 0f))
            result = c.classify(w).type
        }
        assertEquals(ExerciseType.PUSHUP, result)
    }

    @Test fun classifier_idleWhenStill() {
        val c = RuleExerciseClassifier()
        val w = PoseWindow(30)
        var result = ExerciseType.SQUAT
        repeat(30) {
            w.push(np(kneeL = 175f, kneeR = 175f, torso = 5f, openness = 0f))   // standing still
            result = c.classify(w).type
        }
        assertEquals(ExerciseType.NONE, result)
    }
}
