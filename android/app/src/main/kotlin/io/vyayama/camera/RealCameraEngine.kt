package io.vyayama.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import io.vyayama.api.CameraEngine
import io.vyayama.api.CameraFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * CameraX frame source (bible §15, §20): YUV_420_888, KEEP_ONLY_LATEST, single analyzer thread,
 * zero per-frame allocation via two ping-pong RGB buffers (avoids tearing the buffer the consumer
 * is reading). DEVICE-PATH ONLY (mock mode uses MockCameraEngine). VIBE-with-verify on-device.
 */
class RealCameraEngine(private val context: Context) : CameraEngine {

    private val _bus = MutableSharedFlow<CameraFrame>(replay = 1, extraBufferCapacity = 2)
    override val frameBus: SharedFlow<CameraFrame> = _bus.asSharedFlow()

    private val analyzerExec = Executors.newSingleThreadExecutor()
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null

    private var bufA: ByteBuffer? = null
    private var bufB: ByteBuffer? = null
    private var useA = true

    override fun attachPreview(view: Any?) { previewView = view as? PreviewView }

    override fun start(owner: LifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val cp = future.get(); provider = cp

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
            analysis.setAnalyzer(analyzerExec) { image -> onFrame(image) }

            val preview = Preview.Builder().build()
            previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }

            cp.unbindAll()
            cp.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun onFrame(image: ImageProxy) {
        try {
            val size = image.width * image.height * 3
            if (bufA == null || bufA!!.capacity() != size) {
                bufA = ByteBuffer.allocateDirect(size)
                bufB = ByteBuffer.allocateDirect(size)
            }
            val target = if (useA) bufA!! else bufB!!
            useA = !useA
            YuvToRgb.convert(image, target)
            _bus.tryEmit(CameraFrame(target, image.width, image.height, image.imageInfo.timestamp))
        } finally {
            image.close()
        }
    }

    override fun stop() { provider?.unbindAll() }

    override fun close() {
        stop()
        analyzerExec.shutdown()
    }
}
