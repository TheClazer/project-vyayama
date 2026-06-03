package io.vyayama.coach

import androidx.lifecycle.LifecycleOwner
import io.vyayama.api.Backend
import io.vyayama.api.Benchmark
import io.vyayama.api.CameraEngine
import io.vyayama.api.CoachState
import io.vyayama.api.ExerciseClassifier
import io.vyayama.api.ExerciseType
import io.vyayama.api.FeatureExtractor
import io.vyayama.api.FormAnalyzer
import io.vyayama.api.FormFeedback
import io.vyayama.api.NormalizedPose
import io.vyayama.api.PoseWindow
import io.vyayama.api.RepCounter
import io.vyayama.api.RepEvent
import io.vyayama.api.RepPhase
import io.vyayama.api.PoseEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The single place the pipeline is wired (bible §6, §20): camera frame → pose → features → classify
 * → reps → form → CoachState. Runs the whole chain on the collector (analyzer) coroutine and
 * publishes an immutable [state] the UI renders; the UI never blocks this loop.
 */
class CoachOrchestrator(
    private val pose: PoseEngine,
    private val camera: CameraEngine,
    private val features: FeatureExtractor,
    private val classifier: ExerciseClassifier,
    private val reps: RepCounter,
    private val form: FormAnalyzer,
    private val benchmark: Benchmark
) {
    private val _state = MutableStateFlow(CoachState.INITIAL)
    val state: StateFlow<CoachState> = _state.asStateFlow()

    private val window = PoseWindow(30)
    private val repFrames = ArrayList<NormalizedPose>(64)
    private var prevPhase = RepPhase.TOP
    private var lastFeedback: FormFeedback? = null
    @Volatile var sessionReps: Int = 0; private set
    @Volatile var scoreSum: Int = 0; private set   // for session-average form

    fun start(owner: LifecycleOwner, scope: CoroutineScope) {
        pose.init(Backend.AUTO)
        camera.start(owner)
        scope.launch {
            camera.frameBus.collectLatest { frame ->
                val t0 = System.nanoTime()
                val pf = pose.infer(frame)
                val np = features.normalize(pf)
                window.push(np)
                val ex = classifier.classify(window)
                val rep = reps.update(ex.type, np, pf.timestampNs)

                // accumulate the in-flight rep's frames; analyze form on completion
                if (prevPhase == RepPhase.TOP && rep.phase == RepPhase.DESCENDING) repFrames.clear()
                if (rep.phase != RepPhase.TOP && np.visible) repFrames.add(np)
                if (rep.completedNow) {
                    lastFeedback = form.analyze(ex.type, repFrames.toList(), rep.phase)
                    sessionReps++; scoreSum += lastFeedback?.perRepScore ?: 0
                    repFrames.clear()
                }
                if (rep.phase == RepPhase.TOP && !rep.completedNow) repFrames.clear()
                prevPhase = rep.phase

                val totalMs = (System.nanoTime() - t0) / 1_000_000f
                benchmark.onInference(pf.backend, pf.detLatencyMs, pf.poseLatencyMs, totalMs)

                val repOut = RepEvent(
                    rep.exercise, rep.index, rep.phase, rep.completedNow, rep.partial,
                    formScore = lastFeedback?.perRepScore ?: 0
                )
                _state.value = CoachState(
                    pose = pf, normalized = np, exercise = ex, rep = repOut,
                    feedback = lastFeedback, perf = benchmark.snapshot(),
                    isExercising = ex.type != ExerciseType.NONE
                )
            }
        }
    }

    /** Live CPU↔NPU switch for the A/B (bible §10.4). Returns the backend actually reached. */
    fun forceBackend(b: Backend): Backend = pose.forceBackend(b)

    fun sessionAverageScore(): Int = if (sessionReps > 0) scoreSum / sessionReps else 0

    fun stop() { camera.stop(); pose.close() }
}
