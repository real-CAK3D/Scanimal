package com.realcak3d.scanimal.ui

import android.graphics.Paint
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realcak3d.scanimal.core.Dsp
import com.realcak3d.scanimal.core.SignalInfo
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.SensorHub
import kotlinx.coroutines.delay
import kotlin.math.log10
import kotlin.math.max

private val seriesColors = listOf(Color(0xFF2EE6A6), Color(0xFFFFD54F), Color(0xFFFF6FA5), Color(0xFF4FC3F7), Color(0xFFB388FF), Color(0xFFFF8A65), Color(0xFF9CCC65), Color(0xFFF06292))

@Composable
fun SpectrumScreen(repo: GameRepository, hub: SensorHub) {
    var tab by rememberSaveable { mutableStateOf("wifi") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenTitle("Spectrum", "See the invisible: radio, sound and magnetism around you")
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("wifi" to "📶 Wi-Fi", "rssi" to "📈 Signals", "audio" to "🎤 Audio", "emf" to "🧲 EMF").forEach { (k, l) -> FilterChip(l, tab == k) { tab = k } }
        }
        Spacer(Modifier.height(8.dp))
        when (tab) {
            "wifi" -> WifiSpectrum(hub)
            "rssi" -> SignalHistory(hub)
            "audio" -> AudioScope(hub)
            else -> EmfScope(hub)
        }
    }
}

@Composable
private fun WifiSpectrum(hub: SensorHub) {
    val nets by hub.wifi.results.collectAsStateWithLifecycle()
    var band by rememberSaveable { mutableStateOf("2.4") }
    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("2.4", "5", "6").forEach { b -> FilterChip("$b GHz (${nets.count { bandOf(it) == b }})", band == b) { band = b } }
    }
    val shown = nets.filter { bandOf(it) == band }.sortedByDescending { it.rssi }.take(24)
    val (lo, hi) = when (band) { "2.4" -> 2400 to 2495; "5" -> 5150 to 5895; else -> 5925 to 7125 }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    Box(Modifier.padding(12.dp).fillMaxWidth().height(260.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF07130F))) {
        Canvas(Modifier.fillMaxSize().padding(start = 30.dp, end = 8.dp, top = 10.dp, bottom = 24.dp)) {
            fun x(mhz: Float) = (mhz - lo) / (hi - lo) * size.width
            fun y(dbm: Float) = (1f - (dbm + 100f) / 70f).coerceIn(0f, 1f) * size.height
            grid(this, paint, listOf(-30, -50, -70, -90)) { y(it.toFloat()) }
            // Channel labels
            paint.color = Color.White.copy(alpha = 0.5f).toArgb(); paint.textSize = 9.sp.toPx()
            val ticks = when (band) { "2.4" -> (1..13).toList(); "5" -> listOf(36, 52, 100, 116, 132, 149, 165); else -> listOf(1, 33, 65, 97, 129, 161, 193, 225) }
            for (ch in ticks) {
                val f = when (band) { "2.4" -> 2407 + ch * 5; "5" -> 5000 + ch * 5; else -> 5950 + ch * 5 }
                drawContext.canvas.nativeCanvas.drawText("$ch", x(f.toFloat()), size.height + 14.sp.toPx(), paint)
            }
            shown.forEachIndexed { i, n ->
                val c = seriesColors[i % seriesColors.size]
                val half = n.widthMhz / 2f
                val l = x(n.frequencyMhz - half); val r = x(n.frequencyMhz + half); val top = y(n.rssi.toFloat())
                val path = Path().apply {
                    moveTo(l, size.height)
                    cubicTo(l + (r - l) * 0.1f, top, r - (r - l) * 0.1f, top, r, size.height)
                }
                drawPath(path, c.copy(alpha = 0.18f))
                drawPath(path, c, style = Stroke(if (n.connected) 4f else 2f))
                paint.color = c.toArgb(); paint.textSize = 10.sp.toPx()
                drawContext.canvas.nativeCanvas.drawText(n.name.ifBlank { "hidden" }.take(14), (l + r) / 2, top - 4f, paint)
            }
        }
    }
    Panel(Modifier.padding(horizontal = 12.dp), title = "Channel crowding") {
        val byCh = shown.groupBy { Dsp.wifiChannel(it.frequencyMhz) }.toSortedMap()
        if (byCh.isEmpty()) Text("No networks on this band right now.", color = Pal.sub, fontSize = 13.sp)
        byCh.forEach { (ch, list) -> KeyValue("Channel $ch", "${list.size} network${if (list.size == 1) "" else "s"} · strongest ${list.maxOf { it.rssi }} dBm") }
        Text("Fewer networks on a channel means less interference. Wi-Fi scans are throttled by Android (about 4 every 2 minutes).", color = Pal.sub, fontSize = 11.sp)
    }
}

private fun bandOf(n: SignalInfo) = when { n.frequencyMhz < 3000 -> "2.4"; n.frequencyMhz < 5925 -> "5"; else -> "6" }

@Composable
private fun SignalHistory(hub: SensorHub) {
    val bt by hub.bt.devices.collectAsStateWithLifecycle()
    val wifi by hub.wifi.results.collectAsStateWithLifecycle()
    val btNow by rememberUpdatedState(bt)
    val wifiNow by rememberUpdatedState(wifi)
    val history = remember { mutableStateMapOf<String, List<Int>>() }
    val names = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) {
        while (true) {
            val top = (btNow.take(5) + wifiNow.take(3))
            for (s in top) {
                names[s.id] = s.name.ifBlank { s.id.takeLast(8) }
                history[s.id] = ((history[s.id] ?: emptyList()) + s.rssi).takeLast(90)
            }
            // Fade out signals we lost.
            history.keys.filter { k -> top.none { it.id == k } }.forEach { k -> history[k] = ((history[k] ?: emptyList()) + -100).takeLast(90) }
            history.keys.filter { k -> history[k]!!.takeLast(20).all { it <= -100 } }.forEach { history.remove(it); names.remove(it) }
            delay(1000)
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT } }
    Box(Modifier.padding(12.dp).fillMaxWidth().height(240.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF07130F))) {
        Canvas(Modifier.fillMaxSize().padding(start = 30.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            fun y(dbm: Float) = (1f - (dbm + 100f) / 70f).coerceIn(0f, 1f) * size.height
            grid(this, paint, listOf(-30, -50, -70, -90)) { y(it.toFloat()) }
            history.entries.toList().forEachIndexed { i, (_, pts) ->
                if (pts.size < 2) return@forEachIndexed
                val c = seriesColors[i % seriesColors.size]
                val path = Path()
                pts.forEachIndexed { k, v ->
                    val px = size.width * (k + 90 - pts.size) / 89f
                    if (k == 0) path.moveTo(px, y(v.toFloat())) else path.lineTo(px, y(v.toFloat()))
                }
                drawPath(path, c, style = Stroke(3f))
            }
        }
    }
    Column(Modifier.padding(horizontal = 16.dp)) {
        history.keys.toList().forEachIndexed { i, k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(seriesColors[i % seriesColors.size]))
                Text("  ${names[k] ?: k}  ${history[k]?.lastOrNull() ?: ""} dBm", color = Pal.text, fontSize = 12.sp)
            }
        }
        Text("Live signal strength of the closest Bluetooth devices and Wi-Fi networks over the last 90 seconds. Walk around and watch them rise and fall.", color = Pal.sub, fontSize = 11.sp)
    }
}

@Composable
private fun AudioScope(hub: SensorHub) {
    DisposableEffect(Unit) { hub.sound.acquire(); onDispose { hub.sound.release() } }
    val wave by hub.sound.wave.collectAsStateWithLifecycle()
    val db by hub.sound.db.collectAsStateWithLifecycle()
    if (!hub.sound.canRun()) {
        Text("Microphone permission is needed. More → Settings → Grant permissions.", color = Pal.sub, modifier = Modifier.padding(16.dp))
        return
    }
    Text("Oscilloscope · ${db.toInt()} dB", color = Pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
    Box(Modifier.padding(12.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF07130F))) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            drawLine(Pal.mint.copy(alpha = 0.2f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
            if (wave.isNotEmpty()) {
                // Trigger on a rising zero crossing so the trace stands still.
                var start = 0
                for (i in 1 until wave.size / 2) if (wave[i - 1] < 0 && wave[i] >= 0) { start = i; break }
                val n = wave.size / 2
                val path = Path()
                for (k in 0 until n) {
                    val v = wave[(start + k).coerceAtMost(wave.size - 1)]
                    val px = size.width * k / (n - 1); val py = size.height / 2 - v * size.height / 2 * 3f
                    if (k == 0) path.moveTo(px, py.coerceIn(0f, size.height)) else path.lineTo(px, py.coerceIn(0f, size.height))
                }
                drawPath(path, Pal.mint, style = Stroke(2.5f))
            }
        }
    }
    val spec = remember(wave) { if (wave.size >= 64) Dsp.spectrum(wave) else FloatArray(0) }
    val peakBin = spec.indices.drop(2).maxByOrNull { spec[it] } ?: 0
    val peakHz = if (spec.isNotEmpty()) peakBin * hub.sound.sampleRate / (spec.size * 2) else 0
    Text("Spectrum · loudest ≈ $peakHz Hz ${noteName(peakHz)}", color = Pal.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
    Box(Modifier.padding(12.dp).fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF07130F))) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            if (spec.isEmpty()) return@Canvas
            val bars = 64
            // Log-spaced bars from ~40 Hz to Nyquist.
            for (b in 0 until bars) {
                val f0 = 40.0 * Math.pow(hub.sound.sampleRate / 2 / 40.0, b / bars.toDouble())
                val f1 = 40.0 * Math.pow(hub.sound.sampleRate / 2 / 40.0, (b + 1) / bars.toDouble())
                val i0 = (f0 / (hub.sound.sampleRate / 2.0) * spec.size).toInt().coerceIn(0, spec.size - 1)
                val i1 = (f1 / (hub.sound.sampleRate / 2.0) * spec.size).toInt().coerceIn(i0 + 1, spec.size)
                var m = 0f
                for (i in i0 until i1) m = max(m, spec[i])
                val h = ((m - 20f) / 70f).coerceIn(0f, 1f) * size.height
                val w = size.width / bars
                drawRect(hsv(160f - b * 2.2f, 0.7f, 0.95f), Offset(b * w + 1f, size.height - h), androidx.compose.ui.geometry.Size(w - 2f, h))
            }
        }
    }
    Text("Whistle, hum or play music. Loud sounds make your buddy dance!", color = Pal.sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
}

private fun noteName(hz: Int): String {
    if (hz < 30) return ""
    val n = Math.round(12 * (log10(hz / 440.0) / log10(2.0))).toInt() + 57
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return "(${names[((n % 12) + 12) % 12]}${n / 12})"
}

@Composable
private fun EmfScope(hub: SensorHub) {
    val r by hub.body.readings.collectAsStateWithLifecycle()
    val rNow by rememberUpdatedState(r)
    val pts = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) { pts.add(rNow.magneticUT); if (pts.size > 200) pts.removeAt(0); delay(50) }
    }
    Text("Magnetic field: ${r.magneticUT.toInt()} µT ${if (r.magneticUT > 200) "🧲 STRONG!" else if (r.magneticUT > 80) "⚡ elevated" else "(Earth is ~25-65 µT)"}", color = Pal.text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp))
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT } }
    Box(Modifier.padding(12.dp).fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF07130F))) {
        Canvas(Modifier.fillMaxSize().padding(start = 30.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
            val maxV = max(150f, pts.maxOrNull() ?: 0f)
            fun y(v: Float) = (1f - v / maxV).coerceIn(0f, 1f) * size.height
            paint.color = Color.White.copy(alpha = 0.4f).toArgb(); paint.textSize = 9.sp.toPx()
            for (v in listOf(25f, 65f, maxV / 2, maxV)) {
                drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y(v)), Offset(size.width, y(v)), 1f)
                drawContext.canvas.nativeCanvas.drawText("${v.toInt()}", -4f, y(v) + 3f, paint)
            }
            if (pts.size > 1) {
                val path = Path()
                pts.forEachIndexed { i, v -> val px = size.width * i / 199f; if (i == 0) path.moveTo(px, y(v)) else path.lineTo(px, y(v)) }
                drawPath(path, Pal.violet, style = Stroke(2.5f))
            }
        }
    }
    Text("Move the phone near speakers, chargers, laptops or fridge magnets to see spikes. This is the phone's compass sensor.", color = Pal.sub, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
}

private fun grid(d: DrawScope, paint: Paint, levels: List<Int>, y: (Int) -> Float) {
    paint.color = Color.White.copy(alpha = 0.4f).toArgb(); paint.textSize = 9f * d.density * d.fontScale
    for (l in levels) {
        d.drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y(l)), Offset(d.size.width, y(l)), 1f)
        d.drawContext.canvas.nativeCanvas.drawText("$l", -4f, y(l) + 3f, paint)
    }
}
