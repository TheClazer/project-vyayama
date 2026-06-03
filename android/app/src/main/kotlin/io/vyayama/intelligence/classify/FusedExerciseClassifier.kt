package io.vyayama.intelligence.classify

import io.vyayama.api.ExerciseClassifier
import io.vyayama.api.ExerciseState
import io.vyayama.api.PoseWindow
import kotlin.math.max
import kotlin.math.min

/**
 * Honest fusion of the rule baseline and the optional learned head (bible §9.4).
 *
 * Rules ALWAYS run (the guaranteed baseline). If [learned] is present and confident (> [tau]) it
 * wins; if they agree the result is FUSED with boosted confidence. If [learned] is null/absent
 * (mock-mode and any build without the TFLite model), this is silently rules-only — its absence is
 * invisible to the rest of the app. The result always carries its honest `source`.
 */
class FusedExerciseClassifier(
    private val rules: ExerciseClassifier,
    private val learned: ExerciseClassifier? = null,
    private val tau: Float = 0.7f
) : ExerciseClassifier {

    override fun classify(window: PoseWindow): ExerciseState {
        val r = rules.classify(window)                  // source = RULES (or IDLE)
        val l = learned?.classify(window) ?: return r
        if (l.confidence <= tau) return r
        return if (l.type == r.type)
            ExerciseState(l.type, min(1f, max(l.confidence, r.confidence) + 0.05f), ExerciseState.Source.FUSED)
        else
            ExerciseState(l.type, l.confidence, ExerciseState.Source.LEARNED)
    }

    override fun reset() { rules.reset(); learned?.reset() }
}
