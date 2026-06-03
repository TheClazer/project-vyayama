package io.vyayama.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.vyayama.AppContainer
import io.vyayama.coach.CoachOrchestrator
import io.vyayama.mocks.MockPoseEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Single-activity host. Builds the DI container + orchestrator, requests CAMERA on the device path
 * (mock mode needs no camera), and renders CoachScreen from the orchestrator's StateFlow.
 */
class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private lateinit var orchestrator: CoachOrchestrator
    private var started = false

    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startCoach() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = AppContainer(applicationContext)
        orchestrator = container.orchestrator()

        setContent {
            VyayamaTheme {
                val state by orchestrator.state.collectAsStateWithLifecycle()
                CoachScreen(state) { mode ->
                    lifecycleScope.launch(Dispatchers.Default) { orchestrator.forceBackend(mode) }
                }
            }
        }

        val needsCamera = container.pose !is MockPoseEngine
        if (needsCamera && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permission.launch(Manifest.permission.CAMERA)
        } else {
            startCoach()
        }
    }

    private fun startCoach() {
        if (started) return
        started = true
        orchestrator.start(this, lifecycleScope)
    }

    override fun onDestroy() {
        super.onDestroy()
        orchestrator.stop()
    }
}
