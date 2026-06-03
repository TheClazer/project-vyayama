package io.vyayama.pose

import java.nio.ByteBuffer

/**
 * THE SEAM — the only JNI surface (bible §8.1). The forked VisionSolution4 C++
 * (cpp/posedetectionYoloNAS.cpp) is adapted so its JNI function names match these:
 *   Java_io_vyayama_pose_PoseJni_nativeInit / _nativeInfer / _nativeClose
 *
 * `nativeInfer` fills a PRE-ALLOCATED FloatArray(56) — 51 keypoint floats (x,y,conf) + 5 metadata
 * [backendCode, detMs, poseMs, srcW, srcH] — so there is NO per-frame JNI object allocation.
 *
 * DEVICE-GATED: the native library `libvyayama_snpe.so` and the SNPE skel libs are bundled when
 * forking on-device (see docs/device-runbook.md). In the mock-mode build the library is absent and
 * [load] returns false, so AppContainer never instantiates SnpePoseEngine.
 */
object PoseJni {
    @Volatile var available: Boolean = false
        private set

    fun load(): Boolean = try {
        System.loadLibrary("vyayama_snpe")
        available = true
        true
    } catch (t: Throwable) {
        available = false
        false
    }

    /** runtime: 'C' CPU / 'G' GPU / 'D' DSP-HTP. Returns the backend code actually reached (0/1/2). */
    external fun nativeInit(assetDir: String, runtime: Char): Int

    /** Fills out[56] = 51 keypoints + [backendCode, detMs, poseMs, srcW, srcH]. Returns 0 on success. */
    external fun nativeInfer(rgb: ByteBuffer, w: Int, h: Int, out: FloatArray): Int

    external fun nativeClose()
}
