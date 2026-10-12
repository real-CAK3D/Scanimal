package com.realcak3d.scanimal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class ThemeColors(
    val id: String, val label: String, val dark: Boolean,
    val bg: Color, val panel: Color, val panel2: Color, val line: Color, val mint: Color, val mintDim: Color,
    val amber: Color, val pink: Color, val sky: Color, val violet: Color, val red: Color, val text: Color, val sub: Color,
    val roomTop: Color, val roomBottom: Color, val floor: Color,
)

object Themes {
    val all = listOf(
        ThemeColors("radar", "Radar", true, Color(0xFF0B1220), Color(0xFF131D30), Color(0xFF1A2740), Color(0xFF26385A), Color(0xFF2EE6A6), Color(0xFF1B8F69),
            Color(0xFFFFD54F), Color(0xFFFF6FA5), Color(0xFF4FC3F7), Color(0xFFB388FF), Color(0xFFFF5370), Color(0xFFE6F1FF), Color(0xFF8DA2C0),
            Color(0xFF12233D), Color(0xFF1B3354), Color(0xFF0E1A2E)),
        ThemeColors("sunset", "Sunset", true, Color(0xFF1E1028), Color(0xFF2B1838), Color(0xFF3A2148), Color(0xFF55305F), Color(0xFFFF9E57), Color(0xFFB8673A),
            Color(0xFFFFD166), Color(0xFFFF5E8A), Color(0xFF7FDBFF), Color(0xFFC792EA), Color(0xFFFF4D6D), Color(0xFFFFF1E6), Color(0xFFC9A9C9),
            Color(0xFF4A1E4F), Color(0xFFB4485A), Color(0xFF2B1235)),
        ThemeColors("ocean", "Ocean", true, Color(0xFF051C2C), Color(0xFF0A2A40), Color(0xFF0F3753), Color(0xFF1B4C6E), Color(0xFF3DDCFF), Color(0xFF1E8FAA),
            Color(0xFFFFE08A), Color(0xFFFF8FB1), Color(0xFF7CF5C8), Color(0xFF9FA8FF), Color(0xFFFF6B6B), Color(0xFFE3F6FF), Color(0xFF86AFC8),
            Color(0xFF0B3A5C), Color(0xFF0E5A7E), Color(0xFF082B42)),
        ThemeColors("forest", "Forest", true, Color(0xFF0E1A12), Color(0xFF16261B), Color(0xFF1E3324), Color(0xFF2C4A33), Color(0xFF8BE36A), Color(0xFF4F8F3C),
            Color(0xFFF2C14E), Color(0xFFF28FAD), Color(0xFF6CC5E8), Color(0xFFC3A6FF), Color(0xFFE85D5D), Color(0xFFEAF5E4), Color(0xFF9CB59A),
            Color(0xFF1F3B24), Color(0xFF2E5532), Color(0xFF16291A)),
        ThemeColors("lcd", "Retro LCD", false, Color(0xFFC4CFA1), Color(0xFFB5C291), Color(0xFFA6B482), Color(0xFF8B9B6A), Color(0xFF2E3B1F), Color(0xFF4A5A33),
            Color(0xFF5E5A1E), Color(0xFF7A3B3B), Color(0xFF2E4A4A), Color(0xFF4A3B5E), Color(0xFF8B2E2E), Color(0xFF1F2A14), Color(0xFF4A5A33),
            Color(0xFFB5C291), Color(0xFFA6B482), Color(0xFF97A673)),
        ThemeColors("candy", "Candy", false, Color(0xFFFFF0F6), Color(0xFFFFFFFF), Color(0xFFFFE3EF), Color(0xFFF5C2D7), Color(0xFFE0569B), Color(0xFFB83E7C),
            Color(0xFFF2A007), Color(0xFFFF5FA2), Color(0xFF3FA7D6), Color(0xFF8A5CF5), Color(0xFFE5383B), Color(0xFF3B1F2B), Color(0xFF8C6577),
            Color(0xFFFFD6E8), Color(0xFFFFF0F6), Color(0xFFF7C5DA)),
        ThemeColors("daylight", "Daylight", false, Color(0xFFF3F6FA), Color(0xFFFFFFFF), Color(0xFFE8EEF5), Color(0xFFD0DAE6), Color(0xFF0F9D74), Color(0xFF0B7556),
            Color(0xFFE6A100), Color(0xFFE5508C), Color(0xFF1E88E5), Color(0xFF7E57C2), Color(0xFFE53935), Color(0xFF14202E), Color(0xFF5D6E82),
            Color(0xFFBFE3FF), Color(0xFFE6F4FF), Color(0xFFCFE3C8)),
        ThemeColors("midnight", "Midnight", true, Color(0xFF000000), Color(0xFF0D0D0F), Color(0xFF17171C), Color(0xFF26262E), Color(0xFFB388FF), Color(0xFF7552C7),
            Color(0xFFFFD54F), Color(0xFFFF6FA5), Color(0xFF4FC3F7), Color(0xFF2EE6A6), Color(0xFFFF5370), Color(0xFFF2F2F7), Color(0xFF8E8E9A),
            Color(0xFF05050A), Color(0xFF10101A), Color(0xFF08080D)),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all[0]
}

/** Current palette. Backed by Compose state so switching themes recolors everything live. */
object Pal {
    var theme by mutableStateOf(Themes.all[0])
    /** -1 = use the theme's own accent; 0..359 = a custom accent hue applied app-wide. */
    var accent by mutableStateOf(-1)
    var bigText by mutableStateOf(false)
    private fun acc(fallback: Color, v: Float = 0.9f) = if (accent in 0..359) hsv(accent.toFloat(), 0.72f, v) else fallback
    val bg get() = theme.bg
    val panel get() = theme.panel
    val panel2 get() = theme.panel2
    val line get() = theme.line
    val mint get() = acc(theme.mint)
    val mintDim get() = acc(theme.mintDim, 0.55f)
    val amber get() = theme.amber
    val pink get() = theme.pink
    val sky get() = theme.sky
    val violet get() = theme.violet
    val red get() = theme.red
    val text get() = theme.text
    val sub get() = theme.sub
}

@Composable
fun ScanimalTheme(content: @Composable () -> Unit) {
    val t = Pal.theme
    val base = if (t.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = t.mint, onPrimary = t.bg, secondary = t.amber, onSecondary = t.bg, tertiary = t.pink,
        background = t.bg, onBackground = t.text, surface = t.panel, onSurface = t.text,
        surfaceVariant = t.panel2, onSurfaceVariant = t.sub, outline = t.line, error = t.red,
        surfaceContainer = t.panel, surfaceContainerHigh = t.panel2, surfaceContainerHighest = t.panel2,
        surfaceContainerLow = t.panel, surfaceContainerLowest = t.bg,
    )
    if (Pal.bigText) {
        val d = androidx.compose.ui.platform.LocalDensity.current
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, (d.fontScale * 1.18f).coerceAtMost(1.5f))
        ) { MaterialTheme(colorScheme = scheme, content = content) }
    } else MaterialTheme(colorScheme = scheme, content = content)
}

val Mono = FontFamily.Monospace

@Composable
fun Panel(modifier: Modifier = Modifier, title: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Pal.panel)
            .border(1.dp, Pal.line, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        if (title != null) {
            Text(title.uppercase(), color = Pal.sub, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun StatBar(label: String, icon: String, value: Double, color: Color, modifier: Modifier = Modifier) {
    val v = (value / 100.0).toFloat().coerceIn(0f, 1f)
    val c = if (v < 0.2f) Pal.red else color
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 15.sp, modifier = Modifier.width(24.dp))
        Text(label, color = Pal.sub, fontSize = 12.sp, modifier = Modifier.width(74.dp))
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Pal.panel2)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(v)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(c)
            )
        }
        Text("${value.toInt()}", color = Pal.text, fontSize = 12.sp, fontFamily = Mono, modifier = Modifier.width(34.dp).padding(start = 6.dp))
    }
}

@Composable
fun Chip(text: String, color: Color = Pal.mint, modifier: Modifier = Modifier) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
fun KeyValue(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = Pal.sub, fontSize = 13.sp)
        Text(v, color = Pal.text, fontSize = 13.sp, fontFamily = Mono)
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = Pal.text, fontSize = 24.sp, fontWeight = FontWeight.Black)
        if (subtitle != null) Text(subtitle, color = Pal.sub, fontSize = 13.sp)
    }
}

fun hsv(h: Float, s: Float, v: Float, a: Float = 1f): Color = Color.hsv(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f), a)
