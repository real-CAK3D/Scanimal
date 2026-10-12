package com.realcak3d.scanimal.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Sex
import com.realcak3d.scanimal.core.Species
import com.realcak3d.scanimal.core.Stage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Everything a species/outfit painter needs about the current frame. */
class SpecCtx(
    val cx: Float, val cy: Float, val bw: Float, val bh: Float, val u: Float, val s: Float, val t: Float,
    val base: Color, val light: Color, val dark: Color, val hue: Float, val anim: Anim, val p: Float, val look: BuddyLook,
)

fun lerpColor(a: Color, b: Color, f: Float) = lerp(a, b, f)

fun skinTone(seed: Long): Color = listOf(
    Color(0xFFFFE0C4), Color(0xFFF1C27D), Color(0xFFE0AC69), Color(0xFFC68642), Color(0xFF8D5524), Color(0xFF5C3A1E),
)[((seed ushr 7) and 0xFFFF).toInt() % 6]

private fun hairColor(seed: Long): Color = listOf(
    Color(0xFF2B1B10), Color(0xFF4E342E), Color(0xFF8D6E63), Color(0xFFE6C068), Color(0xFFB5482A), Color(0xFF1A1A1A), Color(0xFF7E57C2),
)[((seed ushr 13) and 0xFFFF).toInt() % 7]

fun DrawScope.drawLeaf(at: Offset, length: Float, color: Color) {
    val dir = if (length < 0) -1f else 1f
    val l = kotlin.math.abs(length)
    val leaf = Path().apply {
        moveTo(at.x, at.y)
        quadraticTo(at.x + dir * l * 0.5f, at.y - l * 0.45f, at.x + dir * l, at.y)
        quadraticTo(at.x + dir * l * 0.5f, at.y + l * 0.3f, at.x, at.y)
        close()
    }
    drawPath(leaf, color)
    drawLine(lerp(color, Color.Black, 0.3f), at, Offset(at.x + dir * l * 0.85f, at.y - l * 0.03f), l * 0.04f)
}

private fun wagSpeed(c: SpecCtx) = if (c.anim == Anim.HAPPY || c.anim == Anim.LOVE || c.look.happiness > 70) 12f else 3f

/** Parts behind the body: tails, back spikes, ears that sit behind, capes, tree canopy. */
fun DrawScope.drawSpeciesBack(c: SpecCtx) {
    val (cx, cy, bw, bh) = listOf(c.cx, c.cy, c.bw, c.bh)
    val u = c.u * c.s
    when (c.look.species) {
        Species.DINO -> {
            // Tail
            val sw = sin(c.t * 2f) * 3 * u
            val tail = Path().apply {
                moveTo(cx - bw * 0.35f, cy + bh * 0.05f)
                quadraticTo(cx - bw * 0.85f, cy + bh * 0.2f + sw, cx - bw * 1.0f, cy + bh * 0.42f + sw)
                quadraticTo(cx - bw * 0.7f, cy + bh * 0.38f, cx - bw * 0.3f, cy + bh * 0.35f)
                close()
            }
            drawPath(tail, c.base)
            // Back spikes along the top-right curve
            for (i in 0..4) {
                val a = (-150f + i * 28f) * PI.toFloat() / 180f
                val px = cx + cos(a) * bw * 0.47f; val py = cy - bh * 0.05f + sin(a) * bh * 0.5f
                val tip = Offset(cx + cos(a) * bw * 0.66f, cy - bh * 0.05f + sin(a) * bh * 0.68f)
                val sp = Path().apply {
                    moveTo(px + cos(a + 1.4f) * 3 * u, py + sin(a + 1.4f) * 3 * u)
                    lineTo(tip.x, tip.y)
                    lineTo(px + cos(a - 1.4f) * 3 * u, py + sin(a - 1.4f) * 3 * u)
                    close()
                }
                drawPath(sp, Pal.amber)
            }
        }
        Species.DOG -> {
            val wag = sin(c.t * wagSpeed(c)) * 25f
            rotate(wag, Offset(cx + bw * 0.42f, cy + bh * 0.25f)) {
                val tail = Path().apply {
                    moveTo(cx + bw * 0.42f, cy + bh * 0.25f)
                    quadraticTo(cx + bw * 0.7f, cy + bh * 0.15f, cx + bw * 0.68f, cy - bh * 0.15f)
                }
                drawPath(tail, c.dark, style = Stroke(3.2f * u, cap = StrokeCap.Round))
            }
        }
        Species.CAT -> {
            val sway = sin(c.t * 1.6f) * 6 * u
            val tail = Path().apply {
                moveTo(cx + bw * 0.4f, cy + bh * 0.35f)
                cubicTo(cx + bw * 0.85f, cy + bh * 0.35f, cx + bw * 0.6f + sway, cy - bh * 0.05f, cx + bw * 0.85f + sway, cy - bh * 0.25f)
            }
            drawPath(tail, c.dark, style = Stroke(2.8f * u, cap = StrokeCap.Round))
        }
        Species.CHAMELEON -> {
            // Curled tail
            val path = Path()
            val center = Offset(cx + bw * 0.62f, cy + bh * 0.3f)
            for (i in 0..40) {
                val f = i / 40f
                val a = f * 4.2f * PI.toFloat() + c.t * 0.3f
                val r = (1 - f) * bw * 0.22f + 1.5f * u
                val pt = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
                if (i == 0) path.moveTo(cx + bw * 0.38f, cy + bh * 0.3f) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, c.dark, style = Stroke(2.6f * u, cap = StrokeCap.Round))
        }
        Species.FISH -> {
            val wag = sin(c.t * 6f) * 18f
            rotate(wag, Offset(cx - bw * 0.5f, cy)) {
                val fin = Path().apply {
                    moveTo(cx - bw * 0.48f, cy)
                    lineTo(cx - bw * 0.85f, cy - bh * 0.32f)
                    quadraticTo(cx - bw * 0.72f, cy, cx - bw * 0.85f, cy + bh * 0.32f)
                    close()
                }
                drawPath(fin, c.dark)
            }
            val dorsal = Path().apply {
                moveTo(cx - bw * 0.2f, cy - bh * 0.38f)
                quadraticTo(cx, cy - bh * 0.75f, cx + bw * 0.22f, cy - bh * 0.38f)
                close()
            }
            drawPath(dorsal, c.dark)
            // Bubbles
            for (k in 0..2) {
                val ph = (c.t * 0.4f + k / 3f) % 1f
                drawCircle(Pal.sky.copy(alpha = 0.6f * (1 - ph)), (1.5f + k) * u, Offset(cx + bw * 0.45f + sin(ph * 9f + k) * 3 * u, cy - bh * 0.3f - ph * 30 * u), style = Stroke(0.8f * u))
            }
        }
        Species.BIRD -> {
            for (k in -1..1) {
                val tf = Path().apply {
                    moveTo(cx - bw * 0.35f, cy + bh * 0.25f)
                    lineTo(cx - bw * 0.72f, cy + bh * (0.15f + k * 0.12f))
                    lineTo(cx - bw * 0.68f, cy + bh * (0.25f + k * 0.12f))
                    close()
                }
                drawPath(tf, c.dark)
            }
        }
        Species.PLANT -> {
            if (c.look.stage == Stage.ELDER) {
                // Tree canopy behind
                val g1 = Color(0xFF2E7D32); val g2 = Color(0xFF43A047)
                drawCircle(g1, bw * 0.45f, Offset(cx - bw * 0.3f, cy - bh * 0.75f))
                drawCircle(g2, bw * 0.5f, Offset(cx + bw * 0.25f, cy - bh * 0.85f))
                drawCircle(g2, bw * 0.42f, Offset(cx, cy - bh * 1.05f))
                drawRect(Color(0xFF6D4C41), Offset(cx - bw * 0.08f, cy - bh * 0.7f), Size(bw * 0.16f, bh * 0.3f))
            }
        }
        Species.HUMAN -> {
            if (c.look.sex == Sex.FEMALE) {
                val hc = hairColor(c.look.seed)
                drawRoundRect(hc, Offset(cx - bw * 0.52f, cy - bh * 0.45f), Size(bw * 1.04f, bh * 0.85f), androidx.compose.ui.geometry.CornerRadius(bw * 0.3f))
            }
        }
        else -> {}
    }
}

/** Ears, snouts, whiskers, beaks, hair, extra eyes: drawn on top of the face. */
fun DrawScope.drawSpeciesFront(c: SpecCtx, eyeY: Float, eyeDX: Float, eyeR: Float, mouthY: Float) {
    val cx = c.cx; val cy = c.cy; val bw = c.bw; val bh = c.bh
    val u = c.u * c.s
    val top = cy - bh / 2
    val ink = Color(0xFF0B1220)
    when (c.look.species) {
        Species.DINO -> {
            drawCircle(ink.copy(alpha = 0.6f), 0.8f * u, Offset(cx - 2 * u, mouthY - 3.5f * u))
            drawCircle(ink.copy(alpha = 0.6f), 0.8f * u, Offset(cx + 2 * u, mouthY - 3.5f * u))
        }
        Species.DOG -> {
            for (side in listOf(-1, 1)) {
                val flop = sin(c.t * 2f + side) * 6f
                rotate(side * (15f + flop), Offset(cx + side * bw * 0.3f, top + bh * 0.1f)) {
                    drawOval(c.dark, Offset(cx + side * bw * 0.3f - bw * 0.1f, top + bh * 0.05f), Size(bw * 0.2f, bh * 0.42f))
                }
            }
            drawOval(ink, Offset(cx - 3 * u, mouthY - 5 * u), Size(6 * u, 4 * u))
            drawCircle(Color.White.copy(alpha = 0.6f), 0.8f * u, Offset(cx - 1 * u, mouthY - 4.2f * u))
            if (c.anim == Anim.HAPPY || c.look.happiness > 80) drawOval(Pal.pink, Offset(cx - 2 * u, mouthY + 2 * u), Size(4 * u, 5 * u))
        }
        Species.CAT -> {
            for (side in listOf(-1, 1)) {
                val ear = Path().apply {
                    moveTo(cx + side * bw * 0.12f, top + bh * 0.06f)
                    lineTo(cx + side * bw * 0.36f, top - bh * 0.22f)
                    lineTo(cx + side * bw * 0.44f, top + bh * 0.16f)
                    close()
                }
                drawPath(ear, c.base)
                val inner = Path().apply {
                    moveTo(cx + side * bw * 0.2f, top + bh * 0.08f)
                    lineTo(cx + side * bw * 0.34f, top - bh * 0.1f)
                    lineTo(cx + side * bw * 0.38f, top + bh * 0.13f)
                    close()
                }
                drawPath(inner, Pal.pink.copy(alpha = 0.7f))
                for (k in -1..1) drawLine(ink.copy(alpha = 0.55f), Offset(cx + side * bw * 0.14f, mouthY - 1.5f * u + k * 1.6f * u),
                    Offset(cx + side * bw * 0.42f, mouthY - 3 * u + k * 3.2f * u), 0.6f * u)
            }
            val nose = Path().apply { moveTo(cx - 1.6f * u, mouthY - 3.5f * u); lineTo(cx + 1.6f * u, mouthY - 3.5f * u); lineTo(cx, mouthY - 1.8f * u); close() }
            drawPath(nose, Pal.pink)
        }
        Species.CHAMELEON -> {
            // Casque ridge on top
            val ridge = Path().apply {
                moveTo(cx - bw * 0.3f, top + bh * 0.08f)
                for (i in 0..6) {
                    val x = cx - bw * 0.3f + i * bw * 0.1f
                    quadraticTo(x + bw * 0.05f, top - bh * 0.08f, x + bw * 0.1f, top + bh * 0.06f)
                }
            }
            drawPath(ridge, c.dark, style = Stroke(1.6f * u, cap = StrokeCap.Round))
            // Eye turrets
            for (side in listOf(-1, 1)) drawCircle(c.base, eyeR * 1.35f, Offset(cx + side * eyeDX, eyeY), style = Stroke(1.6f * u))
        }
        Species.PLANT -> {
            // Pot
            val pot = Path().apply {
                moveTo(cx - bw * 0.48f, cy + bh * 0.25f); lineTo(cx + bw * 0.48f, cy + bh * 0.25f)
                lineTo(cx + bw * 0.36f, cy + bh * 0.62f); lineTo(cx - bw * 0.36f, cy + bh * 0.62f); close()
            }
            drawPath(pot, Color(0xFFC0673E))
            drawRect(Color(0xFFD8834F), Offset(cx - bw * 0.52f, cy + bh * 0.22f), Size(bw * 1.04f, bh * 0.08f))
            // Head growth by stage
            val leafC = Color(0xFF4CAF50)
            when (c.look.stage) {
                Stage.BABY, Stage.CHILD -> {
                    drawLine(Color(0xFF388E3C), Offset(cx, top + 2 * u), Offset(cx, top - 6 * u), 1.2f * u)
                    drawLeaf(Offset(cx, top - 5 * u), -8 * u, leafC); drawLeaf(Offset(cx, top - 6 * u), 8 * u, leafC)
                }
                Stage.TEEN -> {
                    drawLine(Color(0xFF388E3C), Offset(cx, top + 2 * u), Offset(cx, top - 10 * u), 1.4f * u)
                    for (k in 0..2) { drawLeaf(Offset(cx, top - 3 * u - k * 3.5f * u), -(9 - k * 2) * u, leafC); drawLeaf(Offset(cx, top - 4.5f * u - k * 3.5f * u), (9 - k * 2) * u, leafC) }
                }
                Stage.ADULT -> {
                    drawLine(Color(0xFF388E3C), Offset(cx, top + 2 * u), Offset(cx, top - 10 * u), 1.4f * u)
                    val fc = Offset(cx, top - 14 * u)
                    val petal = hsv(c.hue + 160f, 0.6f, 1f)
                    for (k in 0 until 8) {
                        val a = k / 8f * 2 * PI.toFloat() + c.t * 0.2f
                        drawCircle(petal, 3.2f * u, Offset(fc.x + cos(a) * 4.5f * u, fc.y + sin(a) * 4.5f * u))
                    }
                    drawCircle(Pal.amber, 3 * u, fc)
                }
                else -> {}
            }
        }
        Species.BIRD -> {
            val beak = Path().apply {
                moveTo(cx - 3.2f * u, mouthY - 2 * u); lineTo(cx + 3.2f * u, mouthY - 2 * u); lineTo(cx, mouthY + (if (c.anim == Anim.EAT) 4f else 3f) * u); close()
            }
            drawPath(beak, Color(0xFFFFA726))
            for (k in -1..1) {
                val crest = Path().apply { moveTo(cx + k * 2 * u, top + 1 * u); quadraticTo(cx + k * 5 * u, top - 6 * u, cx + k * 3 * u - 2 * u, top - 9 * u) }
                drawPath(crest, c.dark, style = Stroke(1.6f * u, cap = StrokeCap.Round))
            }
        }
        Species.HUMAN -> {
            val hc = hairColor(c.look.seed)
            when (c.look.sex) {
                Sex.MALE -> {
                    val hair = Path().apply {
                        moveTo(cx - bw * 0.44f, top + bh * 0.2f)
                        quadraticTo(cx - bw * 0.45f, top - bh * 0.06f, cx, top - bh * 0.04f)
                        quadraticTo(cx + bw * 0.45f, top - bh * 0.06f, cx + bw * 0.44f, top + bh * 0.2f)
                        lineTo(cx + bw * 0.25f, top + bh * 0.1f); lineTo(cx + bw * 0.05f, top + bh * 0.16f)
                        lineTo(cx - bw * 0.15f, top + bh * 0.08f); lineTo(cx - bw * 0.3f, top + bh * 0.16f); close()
                    }
                    drawPath(hair, hc)
                }
                Sex.FEMALE -> {
                    val bangs = Path().apply {
                        moveTo(cx - bw * 0.46f, top + bh * 0.25f)
                        quadraticTo(cx - bw * 0.45f, top - bh * 0.06f, cx, top - bh * 0.04f)
                        quadraticTo(cx + bw * 0.45f, top - bh * 0.06f, cx + bw * 0.46f, top + bh * 0.25f)
                        quadraticTo(cx + bw * 0.1f, top + bh * 0.05f, cx - bw * 0.46f, top + bh * 0.25f); close()
                    }
                    drawPath(bangs, hc)
                }
                Sex.MYSTERY -> {
                    drawCircle(hc, bw * 0.16f, Offset(cx, top - bh * 0.02f))
                    drawArc(hc, 180f, 180f, true, Offset(cx - bw * 0.45f, top - bh * 0.05f), Size(bw * 0.9f, bh * 0.45f))
                }
            }
            // Ears
            for (side in listOf(-1, 1)) drawCircle(c.base, bw * 0.06f, Offset(cx + side * bw * 0.5f, eyeY + bh * 0.04f))
        }
        Species.ALIEN -> {
            val e = Offset(cx, eyeY - eyeR * 1.9f)
            drawOval(Color.White, Offset(e.x - eyeR * 0.6f, e.y - eyeR * 0.7f), Size(eyeR * 1.2f, eyeR * 1.4f))
            drawCircle(ink, eyeR * 0.42f, e)
            drawCircle(Color.White, eyeR * 0.14f, e + Offset(-eyeR * 0.15f, -eyeR * 0.2f))
        }
        Species.ROBOT -> {
            for (side in listOf(-1, 1)) {
                drawCircle(Color(0xFF90A4AE), 2.4f * u, Offset(cx + side * bw * 0.5f, eyeY + 2 * u))
                drawLine(Color(0xFF546E7A), Offset(cx + side * bw * 0.5f - 1.4f * u, eyeY + 2 * u), Offset(cx + side * bw * 0.5f + 1.4f * u, eyeY + 2 * u), 0.7f * u)
            }
            // Chest light
            val on = sin(c.t * 4f) > 0
            drawCircle(if (on) Pal.red else Pal.red.copy(alpha = 0.3f), 1.8f * u, Offset(cx + bw * 0.2f, cy + bh * 0.28f))
            drawRoundRect(Color(0xFF263238).copy(alpha = 0.6f), Offset(cx - bw * 0.15f, cy + bh * 0.22f), Size(bw * 0.25f, bh * 0.12f), androidx.compose.ui.geometry.CornerRadius(2 * u))
        }
        else -> {}
    }
}

/** Outfits that go behind the body (capes). */
fun DrawScope.drawOutfitBack(c: SpecCtx) {
    if (c.look.outfit != "cape") return
    val flutter = sin(c.t * 3f) * 4 * c.u
    val cape = Path().apply {
        moveTo(c.cx - c.bw * 0.35f, c.cy - c.bh * 0.05f)
        lineTo(c.cx + c.bw * 0.35f, c.cy - c.bh * 0.05f)
        lineTo(c.cx + c.bw * 0.55f + flutter, c.cy + c.bh * 0.55f)
        lineTo(c.cx - c.bw * 0.55f - flutter, c.cy + c.bh * 0.55f)
        close()
    }
    drawPath(cape, Color(0xFFD32F2F))
}

/** Outfits painted onto the body (already clipped to the body shape). */
fun DrawScope.drawOutfitFront(c: SpecCtx) {
    val cx = c.cx; val cy = c.cy; val bw = c.bw; val bh = c.bh; val u = c.u * c.s
    val neck = cy + bh * 0.24f
    when (c.look.outfit) {
        "scarf" -> {
            drawRect(Color(0xFFE53935), Offset(cx - bw, neck - 2.5f * u), Size(bw * 2, 5 * u))
            drawRect(Color(0xFFC62828), Offset(cx + bw * 0.12f, neck), Size(4 * u, 12 * u))
            for (i in -6..6) drawLine(Color.White.copy(alpha = 0.6f), Offset(cx + i * 4 * u, neck - 2.5f * u), Offset(cx + i * 4 * u, neck + 2.5f * u), 0.8f * u)
        }
        "bowtie" -> {
            val l = Path().apply { moveTo(cx, neck); lineTo(cx - 5 * u, neck - 3 * u); lineTo(cx - 5 * u, neck + 3 * u); close() }
            val r = Path().apply { moveTo(cx, neck); lineTo(cx + 5 * u, neck - 3 * u); lineTo(cx + 5 * u, neck + 3 * u); close() }
            drawPath(l, Color(0xFF1E88E5)); drawPath(r, Color(0xFF1E88E5)); drawCircle(Color(0xFF1565C0), 1.4f * u, Offset(cx, neck))
        }
        "sweater" -> {
            drawRect(Color(0xFF8E24AA), Offset(cx - bw, neck - 1 * u), Size(bw * 2, bh))
            for (row in 0..3) for (i in -6..6) drawCircle(Color.White.copy(alpha = 0.35f), 0.7f * u, Offset(cx + i * 4 * u + row % 2 * 2 * u, neck + 4 * u + row * 4 * u))
        }
        "tutu" -> {
            val waist = cy + bh * 0.3f
            for (i in -8..8) {
                drawCircle(Color(0xFFF8BBD0), 3 * u, Offset(cx + i * 3 * u, waist + (i % 2) * 1.5f * u))
            }
        }
        "spacesuit" -> {
            drawRect(Color(0xFFECEFF1), Offset(cx - bw, neck - 3 * u), Size(bw * 2, bh))
            drawRect(Color(0xFF90A4AE), Offset(cx - bw, cy + bh * 0.33f), Size(bw * 2, 3 * u))
            drawRoundRect(Color(0xFF1E88E5), Offset(cx - 4 * u, neck + 2 * u), Size(8 * u, 5 * u), androidx.compose.ui.geometry.CornerRadius(1.2f * u))
        }
        "ninja" -> {
            drawRect(Color(0xFF212121), Offset(cx - bw, cy - bh), Size(bw * 2, bh * 0.82f))
            drawRect(Color(0xFF212121), Offset(cx - bw, cy - bh * 0.02f), Size(bw * 2, bh))
            drawRect(Color(0xFFD32F2F), Offset(cx - bw, cy - bh * 0.3f), Size(bw * 2, 2 * u))
        }
        "raincoat" -> {
            drawRect(Color(0xFFFFD600), Offset(cx - bw, neck - 2 * u), Size(bw * 2, bh))
            for (k in 0..2) drawCircle(Color(0xFF6D4C41), 1 * u, Offset(cx, neck + 4 * u + k * 4 * u))
        }
        "tuxedo" -> {
            drawRect(Color(0xFF212121), Offset(cx - bw, neck - 2 * u), Size(bw * 2, bh))
            val v = Path().apply { moveTo(cx - bw * 0.16f, neck - 2 * u); lineTo(cx + bw * 0.16f, neck - 2 * u); lineTo(cx, neck + bh * 0.22f); close() }
            drawPath(v, Color.White)
            val l = Path().apply { moveTo(cx, neck); lineTo(cx - 4 * u, neck - 2.5f * u); lineTo(cx - 4 * u, neck + 2.5f * u); close() }
            val r = Path().apply { moveTo(cx, neck); lineTo(cx + 4 * u, neck - 2.5f * u); lineTo(cx + 4 * u, neck + 2.5f * u); close() }
            drawPath(l, Color.Black); drawPath(r, Color.Black)
        }
        else -> {}
    }
    // Humans always wear a shirt in their buddy color.
    if (c.look.species == Species.HUMAN && c.look.outfit == null) {
        drawRect(hsv(c.hue, 0.6f, 0.8f), Offset(cx - bw, neck - 1 * u), Size(bw * 2, bh))
    }
}

