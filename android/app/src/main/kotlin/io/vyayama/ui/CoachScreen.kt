package io.vyayama.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.vyayama.api.Backend
import io.vyayama.api.CoachState
import io.vyayama.api.ExerciseType
import io.vyayama.api.Kp
import io.vyayama.api.PoseFrame
import kotlin.math.min

@Composable
fun CoachScreen(state: CoachState, onForceBackend: (Backend) -> Unit) {
    Box(Modifier.fillMaxSize().background(Ground)) {
        SkeletonOverlay(state.pose, Modifier.fillMaxSize())
        TopHud(state, Modifier.align(Alignment.TopCenter))
        CueBanner(state, Modifier.align(Alignment.Center))
        LatencyPanel(state, onForceBackend, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SkeletonOverlay(pose: PoseFrame?, modifier: Modifier) {
    if (pose == null) return
    val accent = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val sw = pose.srcWidth.toFloat().coerceAtLeast(1f)
        val sh = pose.srcHeight.toFloat().coerceAtLeast(1f)
        val scale = min(size.width / sw, size.height / sh)
        val offX = (size.width - sw * scale) / 2f
        val offY = (size.height - sh * scale) / 2f
        fun pt(i: Int) = Offset(offX + pose.x(i) * scale, offY + pose.y(i) * scale)
        for (b in Kp.BONES) {
            if (pose.conf(b[0]) > 0.3f && pose.conf(b[1]) > 0.3f)
                drawLine(accent, pt(b[0]), pt(b[1]), strokeWidth = 7f)
        }
        for (i in 0 until Kp.COUNT) {
            if (pose.conf(i) > 0.3f) drawCircle(Color.White, radius = 9f, center = pt(i))
        }
    }
}

@Composable
private fun TopHud(state: CoachState, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            val label = if (!state.isExercising) "READY" else state.exercise.type.pretty()
            Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            val sub = if (!state.isExercising) "Start exercising" else "via ${state.exercise.source.name.lowercase()}"
            Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${state.rep?.index ?: 0}", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 56.sp, fontWeight = FontWeight.Black)
            Text("REPS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            state.feedback?.let {
                Spacer(Modifier.height(4.dp))
                Text("form ${it.perRepScore}", color = scoreColor(it.perRepScore), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CueBanner(state: CoachState, modifier: Modifier) {
    val cue = state.feedback?.topCue ?: return
    if (!state.isExercising) return
    Surface(modifier.padding(24.dp), color = Color(0xFFE08A3C), shape = RoundedCornerShape(14.dp)) {
        Text(cue.message, color = Color(0xFF1A1208), fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
    }
}

@Composable
private fun LatencyPanel(state: CoachState, onForceBackend: (Backend) -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            val p = state.perf
            val backendText = when (p.backend) {
                Backend.NPU -> "NPU · Hexagon HTP"
                Backend.GPU -> "GPU · Adreno"
                Backend.CPU -> "CPU"
                Backend.MOCK -> "MOCK (synthetic pose — no device)"
                Backend.AUTO -> "AUTO"
            }
            Text("ENGINE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(backendText, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("pose ${fmt(p.poseMsP50)} ms (p50) · ${fmt(p.poseMsP90)} (p90)   ·   ${fmt(p.fps)} fps",
                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (mode in listOf(Backend.AUTO, Backend.NPU, Backend.GPU, Backend.CPU)) {
                    Button(
                        onClick = { onForceBackend(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == p.backend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                        )
                    ) { Text(mode.name, fontSize = 12.sp) }
                }
            }
            Text("Form guidance, not medical advice.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun ExerciseType.pretty(): String = when (this) {
    ExerciseType.SQUAT -> "SQUAT"; ExerciseType.PUSHUP -> "PUSH-UP"; ExerciseType.LUNGE -> "LUNGE"
    ExerciseType.BICEP_CURL -> "BICEP CURL"; ExerciseType.JUMPING_JACK -> "JUMPING JACK"
    ExerciseType.UNKNOWN -> "…"; ExerciseType.NONE -> "READY"
}

private fun scoreColor(s: Int): Color = when {
    s >= 90 -> Color(0xFF2FD9B6); s >= 75 -> Color(0xFFE0C341); else -> Color(0xFFE0853B)
}

private fun fmt(v: Float): String = if (v <= 0f) "—" else String.format("%.1f", v)
