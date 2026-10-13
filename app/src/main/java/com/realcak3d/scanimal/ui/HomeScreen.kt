package com.realcak3d.scanimal.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.GameData
import com.realcak3d.scanimal.core.Sex
import com.realcak3d.scanimal.core.Stage
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.SensorHub

fun GameData.look(now: Long) = BuddyLook(
    hue = buddy.hue, stage = buddy.stage(now), pattern = buddy.pattern, sex = buddy.sex, accessory = buddy.accessory,
    asleep = buddy.asleep, sick = buddy.sick, poops = buddy.poops, dirty = buddy.stats.hygiene < 30,
    happiness = buddy.stats.happiness, weight = buddy.weight, tantrum = buddy.tantrum, seed = buddy.seed, dead = buddy.dead,
    species = buddy.species, outfit = buddy.outfit, eggProgress = buddy.eggProgress(now),
    babyProgress = (buddy.ageHours(now) / 24.0).toFloat().coerceIn(0f, 1f),
)

fun com.realcak3d.scanimal.core.Friend.look(now: Long) = BuddyLook(
    hue = hue, stage = when { level >= 25 -> Stage.ELDER; level >= 10 -> Stage.ADULT; level >= 5 -> Stage.TEEN; level >= 2 -> Stage.CHILD; else -> Stage.BABY },
    pattern = pattern, sex = sex, accessory = accessory, species = species, seed = id.hashCode().toLong(),
)

@Composable
fun HomeScreen(repo: GameRepository, hub: SensorHub, go: (Screen) -> Unit) {
    val g by rememberGame(repo)
    val speech by repo.speech.collectAsStateWithLifecycle()
    val body by hub.body.readings.collectAsStateWithLifecycle()
    val wifi by hub.wifi.results.collectAsStateWithLifecycle()
    val bt by hub.bt.devices.collectAsStateWithLifecycle()
    val walk by com.realcak3d.scanimal.sensors.DistanceTracker.walk.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val b = g.buddy
    var eggTaps by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        // Header
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (b.hatched) "${b.name} ${b.sex.symbol}" else "Mystery Egg",
                    color = Pal.text, fontSize = 24.sp, fontWeight = FontWeight.Black,
                )
                Text(
                    if (b.hatched) "Lv ${b.level} · ${b.stage(now).label} · ${b.ageYears(now)} yrs · ${repo.engine.mood(now)}"
                    else "Generation ${b.generation} · waiting to hatch",
                    color = Pal.sub, fontSize = 13.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("❤️".repeat(b.lives.coerceIn(0, 5)) + "🖤".repeat((5 - b.lives).coerceIn(0, 5)), fontSize = 12.sp)
                Text("🪙 ${g.coins}", color = Pal.amber, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (b.hatched) {
            XpBar(b.xp, b.xpToNext(), Modifier.padding(horizontal = 16.dp))
        }
        UpdateBanner(go)
        if (b.hatched) EnvChips(repo)

        // Stage + speech
        Box(Modifier.fillMaxWidth().height(330.dp)) {
            RoomBackdrop(Modifier.fillMaxSize(), night = repo.engine.isNight(now), asleep = b.asleep, species = b.species, hatched = b.hatched, toy = g.toy, toyX = g.toyX)
            if (b.hatched && b.species == com.realcak3d.scanimal.core.Species.FISH) UnderwaterBubbles(Modifier.fillMaxSize())
            val antenna = ((wifi.size + bt.size) / 25f).coerceIn(0f, 1f)
            val physics = remember { BuddyPhysics() }
            val bodyNow by rememberUpdatedState(body)
            val physicsOn = g.settings.physics && b.hatched
            val asleepNow by rememberUpdatedState(b.asleep)
            var px by remember { mutableFloatStateOf(0f) }
            var py by remember { mutableFloatStateOf(0f) }
            var spin by remember { mutableFloatStateOf(0f) }
            var squash by remember { mutableFloatStateOf(0f) }
            var wanderX by remember { mutableFloatStateOf(0f) }
            var facing by remember { mutableFloatStateOf(1f) }
            var strolling by remember { mutableStateOf(false) }
            val toyNow by rememberUpdatedState(g.toy)
            val toyXNow by rememberUpdatedState(g.toyX)
            LaunchedEffect(physicsOn) {
                physics.onBump = { f -> hub.haptics.buzz((20 + f * 40).toLong(), (60 + f * 150).toInt().coerceAtMost(255)); repo.act { n -> onBump(f, n) } }
                var last = 0L; var target = 0f; var nextWander = 0L; var lastToy = 0L
                while (true) {
                    withFrameMillis { t ->
                        val dt = if (last == 0L) 16L else t - last; last = t
                        physics.step(dt, bodyNow.tiltX, bodyNow.linX, bodyNow.linY, physicsOn, asleepNow)
                        px = physics.x; py = physics.y; spin = physics.spin; squash = physics.squash
                        // Wandering: stroll to a target, pause, pick a new one; drift toward the toy sometimes.
                        val still = kotlin.math.abs(physics.x) < 0.15f && physics.y < 0.05f
                        if (asleepNow || !still) { strolling = false /* hold position */ }
                        else {
                            if (t >= nextWander) {
                                val tx = toyXNow
                                target = if (toyNow != null && kotlin.random.Random.nextInt(3) == 0) ((tx - 0.5f) * 1.7f).coerceIn(-0.85f, 0.85f)
                                         else kotlin.random.Random.nextFloat() * 1.7f - 0.85f
                                nextWander = t + 2500L + kotlin.random.Random.nextLong(5000L)
                            }
                            val d = target - wanderX
                            strolling = kotlin.math.abs(d) > 0.02f
                            if (kotlin.math.abs(d) > 0.01f) { facing = if (d > 0) 1f else -1f; wanderX += d.coerceIn(-1f, 1f) * (dt / 1000f) * 0.6f }
                            // Reached the toy? poke it (throttled; engine throttles too).
                            val toyPos = (toyXNow - 0.5f) * 1.7f
                            if (toyNow != null && kotlin.math.abs(wanderX - toyPos) < 0.1f && t - lastToy > 20000L) { lastToy = t; repo.act { n -> toyInteract(n) } }
                        }
                    }
                }
            }
            BuddyView(
                look = g.look(now), anim = speech.anim, animAt = speech.at,
                underwater = b.species == com.realcak3d.scanimal.core.Species.FISH,
                walking = strolling,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    translationX = (px + wanderX) * size.width * 0.30f
                    translationY = -py * size.height * 0.45f
                    rotationZ = spin.coerceIn(-720f, 720f) % 360f
                    scaleX = (1f + squash * 0.18f) * facing
                    scaleY = 1f - squash * 0.18f
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.8f)
                },
                lookAt = Offset(-body.tiltX / 6f, body.tiltY / 9f - 0.3f),
                antennaGlow = antenna,
                onTap = {
                    if (!b.hatched) {
                        eggTaps++
                        hub.haptics.buzz(20, 60 + (b.eggProgress(System.currentTimeMillis()) * 180).toInt())
                        val ev = repo.act { n -> warmEgg(n, 10_000L) }
                        if (ev.isEmpty()) repo.say(listOf("*wobble*", "*tap tap*", "*the egg feels warm*", "*something moves inside*", "*crk*").random(), Anim.SHOCK)
                    } else {
                        hub.haptics.buzz(15, 60)
                        repo.act { n -> pet(n) }
                    }
                },
            )
            val nearby by hub.link.nearby.collectAsStateWithLifecycle()
            if (g.settings.showVisitors && b.hatched) {
                VisitorLayer(repo, hub, g.friends.filter { it.onScreen }, nearby.map { it.card.id }.toSet(), go, hostSpecies = b.species, modifier = Modifier.fillMaxSize())
            }
            if (speech.text.isNotBlank()) {
                AnimatedContent(
                    targetState = speech.text, label = "speech",
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) { text -> SpeechBubble(text) }
            }
            Text(
                listOfNotNull(
                    if (wifi.isNotEmpty()) "📶 ${wifi.size}" else null,
                    if (bt.isNotEmpty()) "🔵 ${bt.size}" else null,
                    if (body.charging) "⚡" else null,
                    if (body.stepsSession > 0) "👟 ${body.stepsSession}" else null,
                ).joinToString("   "),
                color = Pal.sub, fontSize = 12.sp, fontFamily = Mono,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }

        when {
            b.dead -> GameOverPanel(repo, g)
            !b.hatched -> IncubationPanel(repo, g, go)
            else -> {
                if (walk.active) WalkCard(repo, walk)
                ActionRow(repo, hub, g, go, walk.active)
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.padding(horizontal = 12.dp), title = "Vitals") {
                    val s = b.stats
                    StatBar("Fullness", "🍔", s.fullness, Pal.amber)
                    StatBar("Happiness", "😊", s.happiness, Pal.pink)
                    StatBar("Energy", "⚡", s.energy, Pal.sky)
                    StatBar("Hygiene", "🫧", s.hygiene, Pal.mint)
                    StatBar("Health", "❤️", s.health, Pal.red)
                }
                Spacer(Modifier.height(10.dp))
                Panel(Modifier.padding(horizontal = 12.dp), title = "Traits") {
                    val s = b.stats
                    StatBar("Fitness", "💪", s.fitness, Pal.violet)
                    StatBar("Smarts", "🧠", s.smarts, Pal.sky)
                    StatBar("Social", "🤝", s.social, Pal.pink)
                    StatBar("Discipline", "🎖️", s.discipline, Pal.amber)
                    Spacer(Modifier.height(4.dp))
                    Text("${b.personality.label}: ${b.personality.blurb}", color = Pal.sub, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                val claimable = g.quests.count { com.realcak3d.scanimal.core.Quests.done(it, g) && !it.claimed }
                Panel(Modifier.padding(horizontal = 12.dp), title = "Today's quests") {
                    if (g.quests.isEmpty()) Text("Fresh quests load as you play.", color = Pal.sub, fontSize = 13.sp)
                    g.quests.forEach { q ->
                        val prog = com.realcak3d.scanimal.core.Quests.progress(q, g)
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${if (q.claimed) "✅" else if (prog >= q.target) "🎁" else "•"} ${q.title}", color = Pal.text, fontSize = 12.sp)
                            Text("$prog/${q.target}", color = Pal.sub, fontSize = 12.sp, fontFamily = Mono)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallBtn(if (claimable > 0) "🎁 Claim ($claimable)" else "🎯 Quests", if (claimable > 0) Pal.mint else Pal.panel2) { go(Screen.QUESTS) }
                        SmallBtn("📔 Album", Pal.panel2) { go(Screen.ALBUM) }
                        SmallBtn("🖥️ HUD", Pal.panel2) { go(Screen.HUD) }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateBanner(go: (Screen) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val st by com.realcak3d.scanimal.work.Updater.state.collectAsStateWithLifecycle()
    val (text, action) = when (val u = st) {
        is com.realcak3d.scanimal.work.Updater.State.Ready -> "⬆️ Scanimal ${u.release.version} is ready. Tap to install!" to { com.realcak3d.scanimal.work.Updater.install(ctx, u.file) }
        is com.realcak3d.scanimal.work.Updater.State.Available -> "⬆️ Update ${u.release.version} available" to { go(Screen.SETTINGS) }
        else -> return
    }
    Text(text, color = Pal.bg, fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Pal.amber).clickable { action() }.padding(10.dp))
}

@Composable
fun XpBar(xp: Int, need: Int, modifier: Modifier = Modifier) {
    val f = (xp.toFloat() / need).coerceIn(0f, 1f)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("XP", color = Pal.amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Pal.panel2)) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Pal.amber))
        }
        Text(" $xp/$need", color = Pal.sub, fontSize = 11.sp, fontFamily = Mono)
    }
}

@Composable
fun SpeechBubble(text: String) {
    Text(
        text,
        color = Color(0xFF14202E),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF4FFF9))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
fun RoomBackdrop(modifier: Modifier, night: Boolean, asleep: Boolean, species: com.realcak3d.scanimal.core.Species = com.realcak3d.scanimal.core.Species.BLOB, hatched: Boolean = true, toy: String? = null, toyX: Float = 0.5f) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bg = if (Pal.spritesOn) androidx.compose.runtime.remember(species) { SpriteStore.background(ctx, species) } else null
    androidx.compose.foundation.Canvas(modifier) {
        val th = Pal.theme
        val top = if (night || asleep) Color(0xFF0A0F1F) else th.roomTop
        val bottom = if (night || asleep) Color(0xFF111A30) else th.roomBottom
        val floorY = size.height * 0.8f
        if (bg != null) {
            // Photographic home scene, center-cropped to fill, with a night dimmer.
            val scale = maxOf(size.width / bg.width, size.height / bg.height)
            val dw = (bg.width * scale).toInt(); val dh = (bg.height * scale).toInt()
            val dx = ((size.width - dw) / 2f).toInt(); val dy = ((size.height - dh) / 2f).toInt()
            drawImage(bg, dstOffset = androidx.compose.ui.unit.IntOffset(dx, dy),
                dstSize = androidx.compose.ui.unit.IntSize(dw.coerceAtLeast(1), dh.coerceAtLeast(1)))
            if (night || asleep) drawRect(Color(0xFF060B1A).copy(alpha = 0.45f))
            if (toy != null) drawToy(toy, toyX * size.width, floorY - 3f)
            return@Canvas
        }
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(top, bottom)))
        drawRect(if (night || asleep) Color(0xFF0E1A2E) else th.floor, Offset(0f, floorY), androidx.compose.ui.geometry.Size(size.width, size.height - floorY))
        for (i in 0..10) {
            val x = size.width * i / 10f
            drawLine(Pal.mint.copy(alpha = 0.10f), Offset(size.width / 2 + (x - size.width / 2) * 0.4f, floorY), Offset(x, size.height), 1.5f)
        }
        for (j in 1..3) {
            val y = floorY + (size.height - floorY) * (j / 3.5f)
            drawLine(Pal.mint.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), 1.5f)
        }
        if (night || asleep) {
            val r = kotlin.random.Random(7)
            repeat(40) { drawCircle(Color.White.copy(alpha = 0.2f + r.nextFloat() * 0.5f), 1f + r.nextFloat() * 1.6f, Offset(r.nextFloat() * size.width, r.nextFloat() * floorY * 0.8f)) }
            drawCircle(Color(0xFFFFF3C4), 18f, Offset(size.width * 0.85f, size.height * 0.15f))
            drawCircle(top, 16f, Offset(size.width * 0.85f + 8f, size.height * 0.15f - 4f))
        }
        if (hatched) drawScenery(species, floorY)
        if (toy != null) drawToy(toy, toyX * size.width, floorY - 3f)
    }
}

/** Species-appropriate props sitting in the room, behind the buddy. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawScenery(species: com.realcak3d.scanimal.core.Species, floorY: Float) {
    val w = size.width; val u = w / 100f
    when (species) {
        com.realcak3d.scanimal.core.Species.DINO -> {
            // A twig nest on the ground to the left.
            val nx = w * 0.16f
            drawOval(Color(0xFF6D4C41), Offset(nx - 13 * u, floorY - 5 * u), androidx.compose.ui.geometry.Size(26 * u, 9 * u))
            drawArc(Color(0xFF8D6E63), 200f, 140f, false, Offset(nx - 13 * u, floorY - 10 * u), androidx.compose.ui.geometry.Size(26 * u, 12 * u), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            for (e in listOf(-4f, 0f, 4f)) drawOval(Color(0xFFEFE3C0), Offset(nx + e * u - 2.2f * u, floorY - 6 * u), androidx.compose.ui.geometry.Size(4.4f * u, 3.4f * u))
        }
        com.realcak3d.scanimal.core.Species.BIRD -> {
            // A tree with a perch branch on the right.
            val tx = w * 0.84f
            drawRect(Color(0xFF5D4037), Offset(tx - 2 * u, floorY - 44 * u), androidx.compose.ui.geometry.Size(4 * u, 44 * u))
            drawLine(Color(0xFF6D4C41), Offset(tx, floorY - 30 * u), Offset(tx - 20 * u, floorY - 26 * u), 3f)
            for (c in listOf(Triple(-18f, -40f, 12f), Triple(2f, -46f, 14f), Triple(10f, -34f, 11f)))
                drawCircle(Color(0xFF2E7D32), c.third * u, Offset(tx + c.first * u, floorY + c.second * u))
        }
        com.realcak3d.scanimal.core.Species.CHAMELEON -> {
            // A climbing branch arching across.
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.1f, floorY); cubicTo(w * 0.3f, floorY - 30 * u, w * 0.7f, floorY - 44 * u, w * 0.92f, floorY - 20 * u)
            }
            drawPath(p, Color(0xFF6D4C41), style = androidx.compose.ui.graphics.drawscope.Stroke(5f))
            for (lx in listOf(0.4f, 0.6f, 0.78f)) drawCircle(Color(0xFF43A047), 7 * u, Offset(w * lx, floorY - (30 + lx * 10) * u))
        }
        com.realcak3d.scanimal.core.Species.PLANT -> {
            drawCircle(Color(0xFFFFF59D).copy(alpha = 0.5f), 16 * u, Offset(w * 0.85f, floorY - 54 * u))
            for (fx in listOf(0.15f, 0.9f)) { drawRect(Color(0xFF388E3C), Offset(w * fx - 1 * u, floorY - 12 * u), androidx.compose.ui.geometry.Size(2 * u, 12 * u)); drawCircle(Color(0xFFEC407A), 4 * u, Offset(w * fx, floorY - 13 * u)) }
        }
        com.realcak3d.scanimal.core.Species.FISH -> {
            // Watery floor + seaweed + bubbles.
            drawRect(Color(0xFF1565C0).copy(alpha = 0.18f), Offset(0f, floorY - 6 * u), androidx.compose.ui.geometry.Size(w, 6 * u))
            for (sx in listOf(0.12f, 0.88f)) { val p = androidx.compose.ui.graphics.Path().apply { moveTo(w * sx, floorY); cubicTo(w * sx + 6 * u, floorY - 16 * u, w * sx - 6 * u, floorY - 26 * u, w * sx + 3 * u, floorY - 40 * u) }; drawPath(p, Color(0xFF2E7D32), style = androidx.compose.ui.graphics.drawscope.Stroke(3f)) }
        }
        com.realcak3d.scanimal.core.Species.ROBOT -> {
            // A charging dock pad.
            drawRect(Color(0xFF37474F), Offset(w * 0.1f, floorY - 4 * u), androidx.compose.ui.geometry.Size(22 * u, 4 * u))
            drawCircle(Pal.sky.copy(alpha = 0.6f), 3 * u, Offset(w * 0.21f, floorY - 6 * u))
        }
        com.realcak3d.scanimal.core.Species.ALIEN -> {
            for (sx in listOf(0.12f, 0.9f)) drawCircle(Pal.violet.copy(alpha = 0.5f), 3 * u, Offset(w * sx, floorY - 18 * u))
            drawOval(Pal.violet.copy(alpha = 0.18f), Offset(w * 0.5f - 24 * u, floorY - 4 * u), androidx.compose.ui.geometry.Size(48 * u, 7 * u))
        }
        com.realcak3d.scanimal.core.Species.CAT -> { drawRoundRect(Color(0xFF8D6E63), Offset(w * 0.12f, floorY - 14 * u), androidx.compose.ui.geometry.Size(16 * u, 14 * u), androidx.compose.ui.geometry.CornerRadius(3 * u)); drawCircle(Color(0xFF3E2723), 5 * u, Offset(w * 0.2f, floorY - 7 * u)) }
        com.realcak3d.scanimal.core.Species.DOG -> { drawOval(Color(0xFFB71C1C), Offset(w * 0.13f, floorY - 5 * u), androidx.compose.ui.geometry.Size(16 * u, 5 * u)) ; drawCircle(Color(0xFFD32F2F), 3.5f * u, Offset(w * 0.21f, floorY - 8 * u)) }
        else -> {}
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawToy(toy: String, x: Float, floorY: Float) {
    val u = size.width / 100f
    val c = when (toy) { "ball" -> Color(0xFFEF5350); "bone" -> Color(0xFFECEFF1); "mouse" -> Color(0xFF9E9E9E); "yarn" -> Color(0xFFBA68C8); "stick" -> Color(0xFF8D6E63); "disc" -> Color(0xFF42A5F5); else -> Color(0xFF80DEEA) }
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(x - 5 * u, floorY - 1.5f * u), androidx.compose.ui.geometry.Size(10 * u, 3 * u))
    when (toy) {
        "disc" -> drawOval(c, Offset(x - 6 * u, floorY - 4 * u), androidx.compose.ui.geometry.Size(12 * u, 4 * u))
        "stick" -> drawLine(c, Offset(x - 7 * u, floorY - 3 * u), Offset(x + 7 * u, floorY - 5 * u), 2.5f * u, androidx.compose.ui.graphics.StrokeCap.Round)
        "bone" -> { drawLine(c, Offset(x - 5 * u, floorY - 4 * u), Offset(x + 5 * u, floorY - 4 * u), 3 * u, androidx.compose.ui.graphics.StrokeCap.Round); drawCircle(c, 2 * u, Offset(x - 5 * u, floorY - 5 * u)); drawCircle(c, 2 * u, Offset(x + 5 * u, floorY - 5 * u)) }
        "cube" -> drawRoundRect(c, Offset(x - 4 * u, floorY - 8 * u), androidx.compose.ui.geometry.Size(8 * u, 8 * u), androidx.compose.ui.geometry.CornerRadius(1.5f * u))
        else -> { drawCircle(c, 5 * u, Offset(x, floorY - 5 * u)); drawCircle(Color.White.copy(alpha = 0.5f), 1.6f * u, Offset(x - 1.6f * u, floorY - 6.6f * u)) }
    }
}

private data class Act(val icon: String, val label: String, val onClick: () -> Unit)

@Composable
private fun ActionRow(repo: GameRepository, hub: SensorHub, g: GameData, go: (Screen) -> Unit, walking: Boolean) {
    val b = g.buddy
    val darkRoom = hub.body.readings.value.lux in 0f..5f
    val actions = buildList {
        add(Act("🍔", "Feed") { go(Screen.SCAN) })
        add(Act("🥣", "Kibble ${g.inventory["kibble"] ?: 0}") { repo.act { n -> useItem("kibble", n) } })
        add(Act("🦮", if (walking) "End walk" else "Walk") {
            if (walking) com.realcak3d.scanimal.sensors.DistanceTracker.stopWalk(repo) else com.realcak3d.scanimal.sensors.DistanceTracker.startWalk(repo)
        })
        add(Act("🎮", "Play") { go(Screen.GAMES) })
        if (g.toy != null) {
            add(Act(repo.engine.toyNames[g.toy] ?: "🧸", "Pick up") { repo.act { n -> removeToy(n) } })
        } else {
            add(Act("🧸", "Toss toy") {
                val type = repo.engine.toyNames.keys.random()
                repo.act { n -> placeToy(type, 0.2f + kotlin.random.Random.nextFloat() * 0.6f, n) }
            })
        }
        add(Act("🧽", if (b.poops > 0) "Clean ${b.poops}💩" else "Bathe") { repo.act { n -> clean(n) } })
        add(Act(if (b.asleep) "☀️" else "🌙", if (b.asleep) "Wake" else "Sleep") { repo.act { n -> toggleSleep(n, darkRoom) } })
        if (b.sick) add(Act("💊", "Medicine") { repo.act { n -> medicine(n) } })
        if (b.tantrum) add(Act("☝️", "Scold") { repo.act { n -> scold(n) } })
        add(Act("👏", "Praise") { repo.act { n -> praise(n) } })
        add(Act("💬", "Talk") { go(Screen.CHAT) })
        add(Act("📡", "Radar") { go(Screen.RADAR) })
    }
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (icon, label, onClick) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Pal.panel)
                            .border(1.dp, Pal.line, RoundedCornerShape(14.dp))
                            .clickable { hub.haptics.buzz(10, 50); onClick() }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(icon, fontSize = 22.sp)
                        Text(label, color = Pal.text, fontSize = 11.sp, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WalkCard(repo: GameRepository, walk: com.realcak3d.scanimal.sensors.WalkSession) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }
    val secs = ((System.currentTimeMillis() - walk.startedAt) / 1000).toInt() + tick * 0
    val pace = if (walk.meters > 20) (secs / 60.0) / (walk.meters / 1000.0) else 0.0
    Panel(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), title = "🦮 Walk in progress") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            WalkStat("Distance", repo.engine.fmtDist(walk.meters.toLong()))
            WalkStat("Time", "%d:%02d".format(secs / 60, secs % 60))
            WalkStat("Steps", "${walk.steps}")
            WalkStat("Pace", if (pace > 0) "%.1f min/km".format(pace) else "—")
        }
        Text("Distance comes from GPS, so walking outside works best. Turn on Background mode in Settings to keep counting with the screen off.", color = Pal.sub, fontSize = 11.sp)
    }
}

@Composable
private fun WalkStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Pal.mint, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = Mono)
        Text(label, color = Pal.sub, fontSize = 11.sp)
    }
}

@Composable
private fun IncubationPanel(repo: GameRepository, g: GameData, go: (Screen) -> Unit) {
    val now = System.currentTimeMillis()
    val prog = g.buddy.eggProgress(now)
    val left = repo.engine.eggMinutesLeft(now)
    Panel(Modifier.padding(horizontal = 12.dp), title = "Incubating ${g.buddy.name} the ${g.buddy.species.label.lowercase()}") {
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Pal.panel2)) {
            Box(Modifier.fillMaxWidth(prog).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Pal.amber))
        }
        Spacer(Modifier.height(6.dp))
        Text(if (left <= 0) "Any second now…" else "About $left min to go (${(prog * 100).toInt()}%)", color = Pal.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text("🔥 Tap the egg to warm it (−10 s per tap)\n📷 Scan anything to feed it energy (−2 min)\n🛰️ Signals nearby help too!", color = Pal.sub, fontSize = 13.sp, lineHeight = 19.sp)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { go(Screen.SCAN) }, colors = ButtonDefaults.buttonColors(containerColor = Pal.amber, contentColor = Pal.bg), modifier = Modifier.fillMaxWidth()) {
            Text("📷  Scan to warm the egg", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun GameOverPanel(repo: GameRepository, g: GameData) {
    val m = g.graveyard.firstOrNull()
    Panel(Modifier.padding(horizontal = 12.dp), title = "Rest in pixels") {
        Text("🪦 ${m?.name ?: g.buddy.name}", color = Pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (m != null) Text("Level ${m.level} · ${m.ageYears} years · gen ${m.generation} · ${m.cause}", color = Pal.sub, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("All lives are used up. Your achievements, coins and items carry over to the next generation.", color = Pal.sub, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { repo.act { n -> startNextGeneration(n); emptyList() }; repo.say("*a new egg appears*") },
            colors = ButtonDefaults.buttonColors(containerColor = Pal.mint, contentColor = Pal.bg), modifier = Modifier.fillMaxWidth(),
        ) { Text("🥚  Start the next generation", fontWeight = FontWeight.Bold) }
    }
}

/** Little helper used by several screens. */
@Composable
fun BigButton(text: String, color: Color = Pal.mint, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Pal.bg, fontWeight = FontWeight.Bold) }
}

val Stage.emoji get() = when (this) { Stage.EGG -> "🥚"; Stage.BABY -> "🍼"; Stage.CHILD -> "🧒"; Stage.TEEN -> "🛹"; Stage.ADULT -> "🧑"; Stage.ELDER -> "🧓" }
