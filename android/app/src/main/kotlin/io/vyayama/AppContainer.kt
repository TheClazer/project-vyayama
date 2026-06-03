package io.vyayama

import android.content.Context
import android.util.Log
import io.vyayama.api.Backend
import io.vyayama.api.CameraEngine
import io.vyayama.api.PoseEngine
import io.vyayama.bench.RealBenchmark
import io.vyayama.camera.RealCameraEngine
import io.vyayama.coach.CoachOrchestrator
import io.vyayama.intelligence.classify.FusedExerciseClassifier
import io.vyayama.intelligence.classify.RuleExerciseClassifier
import io.vyayama.intelligence.feature.RealFeatureExtractor
import io.vyayama.intelligence.form.RuleFormAnalyzer
import io.vyayama.intelligence.reps.StateMachineRepCounter
import io.vyayama.mocks.MockCameraEngine
import io.vyayama.mocks.MockPoseEngine
import io.vyayama.pose.SnpePoseEngine

/**
 * Hand-rolled DI with per-interface mock-fallback (bible §7, §20 — no Hilt/Koin, fast hackathon builds).
 * The app ALWAYS boots to something demoable:
 *   - a `.dlc` asset present AND native SNPE loads → real on-device path
 *   - otherwise → MockPoseEngine + MockCameraEngine (full demo on a synthetic squatter, no kit)
 * The intelligence layer is ALWAYS real (it's pure Kotlin, device-free).
 */
class AppContainer(private val context: Context) {

    val benchmark = RealBenchmark()
    val features = RealFeatureExtractor()
    val classifier = FusedExerciseClassifier(RuleExerciseClassifier())   // learned head = null in mock build
    val reps = StateMachineRepCounter()
    val form = RuleFormAnalyzer()

    private val hasDlc: Boolean = runCatching {
        context.assets.list("")?.any { it.endsWith(".dlc") } == true
    }.getOrDefault(false)

    val pose: PoseEngine = if (hasDlc) {
        runCatching {
            SnpePoseEngine(context).also { check(it.init(Backend.AUTO) != Backend.MOCK) { "SNPE init fell through" } }
        }.getOrElse {
            Log.w(TAG, "SNPE unavailable (${it.message}); falling back to MockPoseEngine")
            MockPoseEngine()
        }
    } else {
        Log.i(TAG, "No .dlc asset found → MockPoseEngine (mock mode; full demo on synthetic pose)")
        MockPoseEngine()
    }

    val camera: CameraEngine =
        if (pose is MockPoseEngine) MockCameraEngine() else RealCameraEngine(context)

    fun orchestrator(): CoachOrchestrator =
        CoachOrchestrator(pose, camera, features, classifier, reps, form, benchmark)

    val backendLabel: String get() = pose.backend().name

    companion object { const val TAG = "Vyayama" }
}
