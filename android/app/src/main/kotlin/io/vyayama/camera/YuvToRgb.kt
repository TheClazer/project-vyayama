package io.vyayama.camera

import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * YUV_420_888 → RGB888 conversion into a CALLER-OWNED buffer (no per-frame allocation).
 * Handles row/pixel strides per the Android API. VIBE-with-verify (bible §19) — confirm colors
 * on-device; CameraX YUV plane strides vary by device.
 */
object YuvToRgb {

    /** Writes width*height*3 bytes (R,G,B) into [out]. [out] must be a direct buffer of that size. */
    fun convert(image: ImageProxy, out: ByteBuffer) {
        val w = image.width
        val h = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRow = yPlane.rowStride
        val uvRow = uPlane.rowStride
        val uvPix = uPlane.pixelStride

        out.clear()
        var idx = 0
        for (j in 0 until h) {
            val yr = j * yRow
            val uvr = (j shr 1) * uvRow
            for (i in 0 until w) {
                val y = (yBuf.get(yr + i).toInt() and 0xFF)
                val uvCol = (i shr 1) * uvPix
                val u = (uBuf.get(uvr + uvCol).toInt() and 0xFF) - 128
                val v = (vBuf.get(uvr + uvCol).toInt() and 0xFF) - 128
                // BT.601 full-range-ish
                var r = y + ((91881 * v) shr 16)
                var g = y - ((22554 * u + 46802 * v) shr 16)
                var b = y + ((116130 * u) shr 16)
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255
                out.put(idx, r.toByte()); out.put(idx + 1, g.toByte()); out.put(idx + 2, b.toByte())
                idx += 3
            }
        }
        out.rewind()
    }
}
