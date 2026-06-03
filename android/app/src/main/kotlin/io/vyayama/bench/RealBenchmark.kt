package io.vyayama.bench

import io.vyayama.api.AbResult
import io.vyayama.api.Backend
import io.vyayama.api.Benchmark
import io.vyayama.api.PerfSnapshot

/**
 * Honest latency aggregator (bible §10.3): discards warm-up frames, keeps a steady-state window,
 * reports MEDIAN (p50) and p90 — not mean (robust to GC/scheduler spikes) — plus an EMA'd FPS.
 * Reports the backend ACTUALLY reached. Thread-confined to the analyzer thread.
 *
 * The live CPU↔NPU A/B sweep (force NPU → measure → force CPU → measure → speedup) is orchestrated
 * by the UI calling PoseEngine.forceBackend + reading snapshot() under each backend, then
 * [recordAb]. This keeps the measurement honest and same-input.
 */
class RealBenchmark(
    private val warmup: Int = 10,
    private val window: Int = 100
) : Benchmark {

    private val poseMs = ArrayDeque<Float>()
    private var count = 0
    private var backend = Backend.MOCK
    private var emaTotal = Float.NaN
    private var emaFps = Float.NaN
    private var ab: AbResult? = null

    override fun onInference(backend: Backend, detMs: Float, poseMs: Float, totalMs: Float) {
        this.backend = backend
        count++
        if (count <= warmup) return                       // exclude warm-up from reported numbers
        val total = detMs + poseMs
        if (this.poseMs.size == window) this.poseMs.removeFirst()
        this.poseMs.addLast(total)
        emaTotal = ema(emaTotal, totalMs, 0.2f)
        if (totalMs > 0.01f) emaFps = ema(emaFps, 1000f / totalMs, 0.2f)
    }

    override fun snapshot(): PerfSnapshot {
        if (poseMs.isEmpty()) return PerfSnapshot(backend, 0f, 0f, if (emaFps.isNaN()) 0f else emaFps, 0f)
        val sorted = poseMs.sorted()
        return PerfSnapshot(
            backend = backend,
            poseMsP50 = percentile(sorted, 50f),
            poseMsP90 = percentile(sorted, 90f),
            endToEndMs = if (emaTotal.isNaN()) 0f else emaTotal,
            fps = if (emaFps.isNaN()) 0f else emaFps
        )
    }

    /** Called by the UI after sweeping both backends (bible §10.4). */
    fun recordAb(npuMs: Float, cpuMs: Float, npuFps: Float, cpuFps: Float, reached: Backend) {
        val speedup = if (npuMs > 0.01f) cpuMs / npuMs else 0f
        ab = AbResult(npuMs, cpuMs, npuFps, cpuFps, speedup, reached)
    }

    override fun startAB() { /* reset handled by the UI sweep; snapshot() drives each leg */ }
    override fun abResult(): AbResult? = ab

    override fun reset() {
        poseMs.clear(); count = 0; emaTotal = Float.NaN; emaFps = Float.NaN; ab = null
    }

    private fun ema(prev: Float, x: Float, a: Float) = if (prev.isNaN()) x else a * x + (1 - a) * prev

    private fun percentile(sorted: List<Float>, p: Float): Float {
        if (sorted.isEmpty()) return 0f
        val idx = ((p / 100f) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }
}
