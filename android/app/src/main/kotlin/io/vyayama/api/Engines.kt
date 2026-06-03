package io.vyayama.api

import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.SharedFlow

/**
 * The module interfaces (bible §7, Appendix A). Each has a real impl + a mock; the app boots and
 * demos with ANY subset mocked. Wired by CoachOrchestrator → StateFlow<CoachState> → Compose UI.
 */

/** The ONLY module that touches native code. `MockPoseEngine` replays recorded CSVs — the keystone mock. */
interface PoseEngine {
    /** Initialize; returns the backend ACTUALLY reached (may differ from requested — honest). */
    fun init(backend: Backend = Backend.AUTO): Backend
    /** One camera frame → 17 keypoints. */
    suspend fun infer(frame: CameraFrame): PoseFrame
    fun backend(): Backend
    fun isReady(): Boolean
    /** Re-init pinned to a backend (for the live CPU-vs-NPU A/B). Returns the tier reached. */
    fun forceBackend(b: Backend): Backend
    fun lastPerf(): PerfSnapshot
    fun close()
}

/** Frame source (CameraX YUV, zero per-frame alloc — bible §15, §20). */
interface CameraEngine {
    fun start(owner: LifecycleOwner)
    fun attachPreview(view: Any?)
    fun stop()
    val frameBus: SharedFlow<CameraFrame>
    fun close()
}

/** Pure function: PoseFrame → scale/translation/camera-invariant features (bible §8.3). */
interface FeatureExtractor {
    fun normalize(pose: PoseFrame): NormalizedPose
}

/** Which exercise + the is-exercising gate (bible §9). */
interface ExerciseClassifier {
    fun classify(window: PoseWindow): ExerciseState
    fun reset()
}

/** Per-exercise rep state machine with hysteresis (bible §12). */
interface RepCounter {
    fun update(exercise: ExerciseType, pose: NormalizedPose, tsNs: Long): RepEvent
    fun count(): Int
    fun reset()
}

/** Per-rep biomechanical form rules → cues + 0..100 score (bible §13). */
interface FormAnalyzer {
    fun analyze(exercise: ExerciseType, repWindow: List<NormalizedPose>, phase: RepPhase): FormFeedback
    fun reset()
}

/** Honest CPU-vs-NPU measurement service (bible §10.3). */
interface Benchmark {
    fun onInference(backend: Backend, detMs: Float, poseMs: Float, totalMs: Float)
    fun snapshot(): PerfSnapshot
    fun startAB()
    fun abResult(): AbResult?
    fun reset()
}
