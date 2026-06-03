package io.vyayama.pose

import android.content.Context
import io.vyayama.api.Backend
import io.vyayama.api.CameraFrame
import io.vyayama.api.Kp
import io.vyayama.api.PerfSnapshot
import io.vyayama.api.PoseEngine
import io.vyayama.api.PoseFrame

/**
 * Kotlin facade over the C++/JNI/SNPE core (bible §10). DEVICE-GATED: real keypoints flow only when
 * the native lib + a `.dlc` are present; otherwise AppContainer falls back to MockPoseEngine.
 *
 * Honest backend reporting: [backend] returns the tier the native layer ACTUALLY reached
 * (DSP→GPU→CPU fallback happens in C++ via isRuntimeAvailable). Never claims NPU on a fallback.
 */
class SnpePoseEngine(private val context: Context) : PoseEngine {

    private val out = FloatArray(Kp.COUNT * 3 + 5)   // reused every frame — no per-frame JNI alloc
    private var backend = Backend.MOCK
    private var ready = false
    // The forked C++ resolves DLCs from the APK via AAssetManager; this marker is forwarded for logging.
    private val assetDir = "models"

    init { PoseJni.load() }

    override fun init(backend: Backend): Backend {
        check(PoseJni.available) { "native lib libvyayama_snpe.so not loaded" }
        val rt = when (backend) {
            Backend.CPU -> 'C'
            Backend.GPU -> 'G'
            Backend.NPU, Backend.AUTO -> 'D'
            else -> 'D'
        }
        val code = PoseJni.nativeInit(assetDir, rt)
        this.backend = codeToBackend(code)
        ready = true
        return this.backend
    }

    override suspend fun infer(frame: CameraFrame): PoseFrame {
        check(ready) { "init() not called / failed" }
        val rc = PoseJni.nativeInfer(frame.rgb, frame.width, frame.height, out)
        check(rc == 0) { "nativeInfer failed rc=$rc" }
        // out layout: [0..50] keypoints, [51]=backendCode, [52]=detMs, [53]=poseMs, [54]=srcW, [55]=srcH
        val kps = out.copyOfRange(0, Kp.COUNT * 3)        // immutable payload for the StateFlow snapshot
        backend = codeToBackend(out[51].toInt())          // HONEST: what the native layer used this frame
        return PoseFrame(
            keypoints = kps,
            srcWidth = out[54].toInt().takeIf { it > 0 } ?: frame.width,
            srcHeight = out[55].toInt().takeIf { it > 0 } ?: frame.height,
            backend = backend,
            detLatencyMs = out[52],
            poseLatencyMs = out[53],
            timestampNs = frame.timestampNs
        )
    }

    override fun backend(): Backend = backend
    override fun isReady(): Boolean = ready

    override fun forceBackend(b: Backend): Backend {
        // Re-init pinned to a backend for the live CPU-vs-NPU A/B (bible §10.4).
        ready = false
        return init(b)
    }

    override fun lastPerf(): PerfSnapshot =
        PerfSnapshot(backend, out[53], out[53], out[52] + out[53], 0f)

    override fun close() {
        if (PoseJni.available && ready) PoseJni.nativeClose()
        ready = false
    }

    private fun codeToBackend(code: Int): Backend = when (code) {
        0 -> Backend.CPU
        1 -> Backend.GPU
        2 -> Backend.NPU
        else -> Backend.MOCK
    }
}
