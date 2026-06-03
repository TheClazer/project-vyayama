package io.vyayama.api

import java.nio.ByteBuffer

/**
 * Vyāyāma domain types — the frozen contract every module is built against (bible Appendix A).
 *
 * Deliberately FREE of Android imports so the whole intelligence layer (FeatureExtractor →
 * Classifier → RepCounter → FormAnalyzer) is unit-testable on the plain JVM with recorded
 * keypoint fixtures — no device, no NPU, no camera.
 */

/** The compute backend actually reached (never claim NPU on a fallback — bible §20). */
enum class Backend { AUTO, NPU, GPU, CPU, MOCK }

/** The Core-5 exercises + the honest non-answers. */
enum class ExerciseType { NONE, SQUAT, PUSHUP, LUNGE, BICEP_CURL, JUMPING_JACK, UNKNOWN }

/** Rep finite-state-machine phase (bible §12). */
enum class RepPhase { TOP, DESCENDING, BOTTOM, ASCENDING }

/** COCO-17 keypoint indices (the order HRNet emits; bible §8.2). */
object Kp {
    const val NOSE = 0
    const val L_EYE = 1; const val R_EYE = 2
    const val L_EAR = 3; const val R_EAR = 4
    const val L_SHOULDER = 5; const val R_SHOULDER = 6
    const val L_ELBOW = 7; const val R_ELBOW = 8
    const val L_WRIST = 9; const val R_WRIST = 10
    const val L_HIP = 11; const val R_HIP = 12
    const val L_KNEE = 13; const val R_KNEE = 14
    const val L_ANKLE = 15; const val R_ANKLE = 16
    const val COUNT = 17

    /** Anatomically-connected joint pairs for skeleton rendering. */
    val BONES: Array<IntArray> = arrayOf(
        intArrayOf(5, 7), intArrayOf(7, 9), intArrayOf(6, 8), intArrayOf(8, 10),
        intArrayOf(5, 6), intArrayOf(11, 12), intArrayOf(5, 11), intArrayOf(6, 12),
        intArrayOf(11, 13), intArrayOf(13, 15), intArrayOf(12, 14), intArrayOf(14, 16),
        intArrayOf(0, 5), intArrayOf(0, 6)
    )
}

/** Indices into [NormalizedPose.jointAngles] (bible §8.3, §9.3 — 10 angles). */
object Angles {
    const val KNEE_L = 0; const val KNEE_R = 1
    const val HIP_L = 2; const val HIP_R = 3
    const val ELBOW_L = 4; const val ELBOW_R = 5
    const val SHOULDER_L = 6; const val SHOULDER_R = 7
    const val TORSO_LEAN = 8   // degrees from image vertical
    const val OPENNESS = 9     // jumping-jack composite, ~0 closed … ~1 open
    const val COUNT = 10
}

/**
 * One frame of pose output, exactly as it crosses THE SEAM (bible §8.1).
 * [keypoints] = 17 * (x, y, conf) = 51 floats, x/y in SOURCE-FRAME pixels.
 */
class PoseFrame(
    val keypoints: FloatArray,          // size 51
    val srcWidth: Int,
    val srcHeight: Int,
    val backend: Backend,               // HONEST — the tier actually used
    val detLatencyMs: Float,
    val poseLatencyMs: Float,
    val timestampNs: Long
) {
    init { require(keypoints.size == Kp.COUNT * 3) { "expected 51 keypoint floats, got ${keypoints.size}" } }
    fun x(i: Int): Float = keypoints[i * 3]
    fun y(i: Int): Float = keypoints[i * 3 + 1]
    fun conf(i: Int): Float = keypoints[i * 3 + 2]
}

/** Output of [io.vyayama.api.FeatureExtractor] — scale/translation/camera invariant (bible §8.3). */
class NormalizedPose(
    val jointAngles: FloatArray,        // size Angles.COUNT; NaN where confidence-gated out
    val keypointsNorm: FloatArray,      // size 34 (17 * x,y), hip-centered + torso-normalized
    val confidences: FloatArray,        // size 17, passed through for gating
    val visible: Boolean                // enough high-confidence keypoints to analyze at all
) {
    fun angle(i: Int): Float = jointAngles[i]
    fun hasAngle(i: Int): Boolean = !jointAngles[i].isNaN()
}

/** Which exercise + honest provenance of the call (bible §9.4). */
class ExerciseState(
    val type: ExerciseType,
    val confidence: Float,
    val source: Source
) {
    enum class Source { RULES, LEARNED, FUSED, NONE }
    companion object { val IDLE = ExerciseState(ExerciseType.NONE, 1f, Source.NONE) }
}

/** Emitted every frame by [RepCounter]; [completedNow] is true exactly on a rep completion. */
class RepEvent(
    val exercise: ExerciseType,
    val index: Int,                     // 1-based clean-rep count this set
    val phase: RepPhase,
    val completedNow: Boolean,
    val partial: Boolean,
    val formScore: Int                  // 0..100 for the just-completed rep (else last)
)

/** One coaching cue. Higher [severity] = higher priority (only the top one is shown). */
class Cue(val severity: Int, val jointRef: String, val message: String)

/** Per-rep form result (bible §13). */
class FormFeedback(
    val cues: List<Cue>,
    val perRepScore: Int,               // 0..100
    val symmetryPct: Float,
    val depthPct: Float,
    val tempoScore: Float
) {
    val topCue: Cue? get() = cues.maxByOrNull { it.severity }
    companion object { val EMPTY = FormFeedback(emptyList(), 100, 1f, 1f, 1f) }
}

/** Live performance snapshot for the CPU-vs-NPU panel (bible §10.4). */
class PerfSnapshot(
    val backend: Backend,
    val poseMsP50: Float,
    val poseMsP90: Float,
    val endToEndMs: Float,
    val fps: Float
) {
    companion object { val NONE = PerfSnapshot(Backend.MOCK, 0f, 0f, 0f, 0f) }
}

/** Result of a CPU↔NPU A/B sweep (bible §10.4). */
class AbResult(
    val npuMs: Float, val cpuMs: Float,
    val npuFps: Float, val cpuFps: Float,
    val speedup: Float, val backendReached: Backend
)

/** The single immutable snapshot the UI renders each frame (bible §6). */
class CoachState(
    val pose: PoseFrame?,
    val normalized: NormalizedPose?,
    val exercise: ExerciseState,
    val rep: RepEvent?,
    val feedback: FormFeedback?,
    val perf: PerfSnapshot,
    val isExercising: Boolean
) {
    companion object {
        val INITIAL = CoachState(
            pose = null, normalized = null, exercise = ExerciseState.IDLE,
            rep = null, feedback = null, perf = PerfSnapshot.NONE, isExercising = false
        )
    }
}

/** A camera frame produced by CameraEngine, consumed by PoseEngine. [rgb] is PRE-ALLOCATED + reused. */
class CameraFrame(
    val rgb: ByteBuffer,
    val width: Int,
    val height: Int,
    val timestampNs: Long
)

/** Fixed-capacity ring buffer of recent NormalizedPose, fed to the classifier (bible §9). */
class PoseWindow(val capacity: Int) {
    private val buf = ArrayDeque<NormalizedPose>(capacity)
    fun push(p: NormalizedPose) {
        if (buf.size == capacity) buf.removeFirst()
        buf.addLast(p)
    }
    val size: Int get() = buf.size
    fun frames(): List<NormalizedPose> = buf.toList()
    fun last(): NormalizedPose? = buf.lastOrNull()
    fun clear() = buf.clear()
}
