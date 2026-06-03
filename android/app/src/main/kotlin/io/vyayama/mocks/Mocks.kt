package io.vyayama.mocks

import androidx.lifecycle.LifecycleOwner
import io.vyayama.api.Backend
import io.vyayama.api.Benchmark
import io.vyayama.api.CameraEngine
import io.vyayama.api.CameraFrame
import io.vyayama.api.Cue
import io.vyayama.api.ExerciseClassifier
import io.vyayama.api.ExerciseState
import io.vyayama.api.ExerciseType
import io.vyayama.api.FormAnalyzer
import io.vyayama.api.FormFeedback
import io.vyayama.api.NormalizedPose
import io.vyayama.api.PerfSnapshot
import io.vyayama.api.PoseWindow
import io.vyayama.api.RepCounter
import io.vyayama.api.RepEvent
import io.vyayama.api.RepPhase
import io.vyayama.api.AbResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/** Emits a dummy frame at ~30 fps to DRIVE the pipeline in mock mode (MockPoseEngine ignores content). */
class MockCameraEngine : CameraEngine {
    private val _bus = MutableSharedFlow<CameraFrame>(replay = 1, extraBufferCapacity = 4)
    override val frameBus: SharedFlow<CameraFrame> = _bus.asSharedFlow()
    private var scope: CoroutineScope? = null
    private val dummy = CameraFrame(ByteBuffer.allocate(1), 1, 1, 0L)

    override fun start(owner: LifecycleOwner) {
        val s = CoroutineScope(Dispatchers.Default); scope = s
        s.launch { while (isActive) { _bus.emit(dummy); delay(33L) } }
    }
    override fun attachPreview(view: Any?) {}
    override fun stop() { scope?.cancel(); scope = null }
    override fun close() = stop()
}

/** Fixed-type classifier for pure-UI dev. */
class MockExerciseClassifier(private val type: ExerciseType = ExerciseType.SQUAT) : ExerciseClassifier {
    override fun classify(window: PoseWindow): ExerciseState = ExerciseState(type, 1f, ExerciseState.Source.RULES)
    override fun reset() {}
}

/** Timer-style rep increments for UI dev. */
class MockRepCounter(private val everyN: Int = 45) : RepCounter {
    private var reps = 0; private var t = 0
    override fun update(exercise: ExerciseType, pose: NormalizedPose, tsNs: Long): RepEvent {
        t++; val done = t % everyN == 0; if (done) reps++
        return RepEvent(exercise, reps, RepPhase.TOP, completedNow = done, partial = false, formScore = 90)
    }
    override fun count(): Int = reps
    override fun reset() { reps = 0; t = 0 }
}

/** Canned form feedback for UI polish. */
class MockFormAnalyzer : FormAnalyzer {
    private var i = 0
    override fun analyze(exercise: ExerciseType, repWindow: List<NormalizedPose>, phase: RepPhase): FormFeedback {
        val score = 80 + (i++ % 20)
        val cue = if (score < 90) listOf(Cue(7, "knee", "Go a little deeper")) else emptyList()
        return FormFeedback(cue, score, 0.95f, score / 100f, 1f)
    }
    override fun reset() { i = 0 }
}

/** Clearly-labelled fake metrics (backend = MOCK ⇒ honest). For pure-UI dev only. */
class MockBenchmark : Benchmark {
    override fun onInference(backend: Backend, detMs: Float, poseMs: Float, totalMs: Float) {}
    override fun snapshot(): PerfSnapshot = PerfSnapshot(Backend.MOCK, 0f, 0f, 0f, 30f)
    override fun startAB() {}
    override fun abResult(): AbResult? = null
    override fun reset() {}
}
