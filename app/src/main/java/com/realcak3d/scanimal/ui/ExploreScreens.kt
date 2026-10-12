package com.realcak3d.scanimal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Biome
import com.realcak3d.scanimal.core.C
import com.realcak3d.scanimal.core.Env
import com.realcak3d.scanimal.core.GameEvent
import com.realcak3d.scanimal.core.Quests
import com.realcak3d.scanimal.core.Stickers
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.SensorHub
import com.realcak3d.scanimal.work.Updater
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

// ------------------------------------------------------------------ Quests

@Composable
fun QuestsScreen(repo: GameRepository) {
    val g by rememberGame(repo)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Quests", "Fresh missions every day · ${g.count(C.QUESTS_DONE)} completed all-time") }
        if (g.quests.isEmpty()) item { Text("New quests appear once your buddy hatches. Come back tomorrow for more!", color = Pal.sub, modifier = Modifier.padding(8.dp)) }
        listItems(g.quests, key = { it.id }) { q ->
            val prog = Quests.progress(q, g)
            val done = prog >= q.target
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.panel).border(1.dp, if (done) Pal.mint else Pal.line, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (q.claimed) "✅" else if (done) "🎁" else "🎯", fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(q.title, color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("🪙 ${q.reward}", color = Pal.amber, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Pal.panel2)) {
                    Box(Modifier.fillMaxWidth((prog.toFloat() / q.target).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (done) Pal.mint else Pal.amber))
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("$prog / ${q.target}", color = Pal.sub, fontSize = 12.sp, fontFamily = Mono)
                    if (q.claimed) Text("Claimed", color = Pal.sub, fontSize = 12.sp)
                    else if (done) SmallBtn("Claim reward", Pal.mint) { repo.act { now -> claimQuest(q.id, now) } }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ------------------------------------------------------------------ Album

@Composable
fun AlbumScreen(repo: GameRepository) {
    val g by rememberGame(repo)
    var cat by remember { mutableStateOf(Stickers.sets.first()) }
    val members = Stickers.all.filter { it.set == cat }
    val got = members.count { it.id in g.stickers }
    val setDef = Stickers.completable.firstOrNull { it.name == cat }
    val setComplete = setDef != null && setDef.trophyId in g.stickers
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Album", "${g.stickers.size} / ${Stickers.all.size} collected · ${g.stickers.count { it.startsWith("trophy:") }} trophies")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Stickers.sets.forEach { c ->
                val mm = Stickers.all.filter { it.set == c }
                val gg = mm.count { it.id in g.stickers }
                val done = gg == mm.size
                FilterChip("${if (done) "✓ " else ""}$c $gg/${mm.size}", cat == c) { cat = c }
            }
        }
        if (setDef != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Pal.panel2)) {
                    Box(Modifier.fillMaxWidth((got.toFloat() / members.size).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (setComplete) Pal.amber else Pal.mint))
                }
                Text("  ${setDef.trophyEmoji}${if (setComplete) " ✓" else " +${setDef.reward}🪙"}", color = if (setComplete) Pal.amber else Pal.sub, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            gridItems(members, key = { it.id }) { st ->
                val owned = st.id in g.stickers
                val tierColor = Color(st.tier.argb)
                Column(
                    Modifier.aspectRatio(0.82f).clip(RoundedCornerShape(14.dp))
                        .background(if (owned) tierColor.copy(alpha = 0.12f) else Pal.panel2.copy(alpha = 0.4f))
                        .border(if (owned) 2.dp else 1.dp, if (owned) tierColor.copy(alpha = 0.7f) else Pal.line, RoundedCornerShape(14.dp)).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(if (owned) st.emoji else "❔", fontSize = 30.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(if (owned) st.name else "???", color = if (owned) Pal.text else Pal.sub, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    if (owned) Text(st.tier.label, color = tierColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    else Text(st.hint, color = Pal.sub.copy(alpha = 0.7f), fontSize = 8.sp, maxLines = 2)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Map (tilted 3D)

@Composable
fun MapScreen(repo: GameRepository) {
    val g by rememberGame(repo)
    val pts = g.discoveries
    if (pts.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Explore map", "No discoveries pinned yet")
            Panel(Modifier.padding(12.dp)) {
                Text("With \"Map trail\" on in Settings and location allowed, every new network, device and tower you find drops a pin on this 3D map. It never leaves your phone.", color = Pal.sub, fontSize = 13.sp)
            }
        }
        return
    }
    // Project to local meters around the centroid.
    val lat0 = pts.sumOf { it.lat } / pts.size
    val lon0 = pts.sumOf { it.lon } / pts.size
    data class P(val x: Float, val y: Float, val kind: String)
    val mapped = pts.takeLast(1500).map {
        P(((it.lon - lon0) * 111320.0 * kotlin.math.cos(Math.toRadians(lat0))).toFloat(),
          ((it.lat - lat0) * 110540.0).toFloat(), it.kind)
    }
    val radius = max(1f, mapped.maxOf { kotlin.math.hypot(it.x, it.y) })

    var yaw by remember { mutableStateOf(0.5f) }
    var pitch by remember { mutableStateOf(0.95f) }   // radians, ~55°
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Explore map", "${pts.size} discoveries · drag to rotate, pinch to zoom")
        Box(
            Modifier.weight(1f).fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF05100A))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gz, rot ->
                        zoom = (zoom * gz).coerceIn(0.4f, 6f)
                        yaw += rot * (PI.toFloat() / 180f)
                        // Vertical drag tilts, horizontal drag spins (so it works one-finger too).
                        pitch = (pitch - pan.y * 0.004f).coerceIn(0.2f, 1.45f)
                        yaw += pan.x * 0.005f
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2; val cy = size.height * 0.52f
                val focal = 2.3f
                val camDist = 2.6f
                val worldScale = 1.1f / radius
                val cosY = kotlin.math.cos(yaw); val sinY = kotlin.math.sin(yaw)
                val cosP = kotlin.math.cos(pitch); val sinP = kotlin.math.sin(pitch)
                // Projects a world point (wx, wy ground metres, wz up-units) to screen + depth + scale.
                fun project(wx: Float, wy: Float, wz: Float): FloatArray {
                    val x = (wx * worldScale) - panX; val y = (wy * worldScale) - panY; val z = wz
                    val x2 = x * cosY - y * sinY; val y2 = x * sinY + y * cosY
                    val yP = y2 * cosP - z * sinP; val zP = y2 * sinP + z * cosP
                    val depth = yP + camDist
                    if (depth < 0.4f) return floatArrayOf(0f, 0f, -1f, 0f)
                    val k = focal / depth * zoom
                    return floatArrayOf(cx + x2 * k * (size.minDimension / 3f), cy - zP * k * (size.minDimension / 3f), depth, k)
                }
                // Ground grid
                val gl = 1.3f
                var gi = -6
                while (gi <= 6) {
                    val f = gi / 6f * gl / worldScale
                    val a0 = project(f, -gl / worldScale, 0f); val a1 = project(f, gl / worldScale, 0f)
                    if (a0[2] > 0 && a1[2] > 0) drawLine(Pal.mint.copy(alpha = 0.10f), Offset(a0[0], a0[1]), Offset(a1[0], a1[1]), 1f)
                    val b0 = project(-gl / worldScale, f, 0f); val b1 = project(gl / worldScale, f, 0f)
                    if (b0[2] > 0 && b1[2] > 0) drawLine(Pal.mint.copy(alpha = 0.10f), Offset(b0[0], b0[1]), Offset(b1[0], b1[1]), 1f)
                    gi++
                }
                // Pins as pillars, far-to-near for correct overlap.
                val pinH = 0.22f
                mapped.map { it to project(it.x, it.y, 0f) }.filter { it.second[2] > 0 }
                    .sortedByDescending { it.second[2] }
                    .forEach { (pt, base) ->
                        val top = project(pt.x, pt.y, pinH)
                        if (top[2] <= 0) return@forEach
                        val c = when (pt.kind) { "wifi" -> Pal.mint; "bt", "ble" -> Pal.sky; "cell" -> Pal.violet; else -> Pal.amber }
                        drawLine(c.copy(alpha = 0.55f), Offset(base[0], base[1]), Offset(top[0], top[1]), (1.5f * base[3]).coerceIn(1f, 5f))
                        drawCircle(Color.Black.copy(alpha = 0.3f), (2.5f * base[3]).coerceIn(1.5f, 7f), Offset(base[0], base[1]))
                        drawCircle(c, (2.8f * top[3]).coerceIn(1.5f, 8f), Offset(top[0], top[1]))
                    }
                // Compass
                val nrx = kotlin.math.sin(-yaw); val nry = -kotlin.math.cos(-yaw)
                drawLine(Pal.red, Offset(size.width - 26f, 30f), Offset(size.width - 26f + nrx * 16f, 30f + nry * 16f), 3f)
                drawCircle(Pal.line, 18f, Offset(size.width - 26f, 30f), style = Stroke(1.5f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Tilt", color = Pal.sub, fontSize = 12.sp)
            androidx.compose.material3.Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.2f..1.45f, modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Pal.mint, activeTrackColor = Pal.mint))
            SmallBtn("Reset", Pal.panel2) { yaw = 0.5f; pitch = 0.95f; zoom = 1f; panX = 0f; panY = 0f }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("📶 ${pts.count { it.kind == "wifi" }}", color = Pal.mint, fontSize = 12.sp)
            Text("🔵 ${pts.count { it.kind == "bt" || it.kind == "ble" }}", color = Pal.sky, fontSize = 12.sp)
            Text("🗼 ${pts.count { it.kind == "cell" }}", color = Pal.violet, fontSize = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ------------------------------------------------------------------ HUD (retro hunt status)

@Composable
fun HudScreen(repo: GameRepository, hub: SensorHub) {
    val g by rememberGame(repo)
    val speech by repo.speech.collectAsStateWithLifecycle()
    val wifi by hub.wifi.results.collectAsStateWithLifecycle()
    val bt by hub.bt.devices.collectAsStateWithLifecycle()
    val cells by hub.cells.cells.collectAsStateWithLifecycle()
    val sats by hub.gnss.satellites.collectAsStateWithLifecycle()
    val nearby by hub.link.nearby.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    val boot = if (g.bootAt > 0) g.bootAt else g.created
    val up = ((now - boot).coerceAtLeast(0)) / 1000
    val green = Color(0xFF8BE36A)
    Column(Modifier.fillMaxSize().background(Color(0xFF02150C)).verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text("SCANIMAL HUD v${Updater.currentVersion}", color = green, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text("────────────────────────────", color = green.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().height(170.dp)) {
            BuddyView(g.look(now), speech.anim, speech.at, Modifier.fillMaxSize(), onTap = { repo.act { n -> pet(n) } })
        }
        val face = when {
            !g.buddy.hatched -> "(egg)"
            g.buddy.asleep -> "(-_-) zzz"
            g.buddy.sick -> "(x_x)"
            g.buddy.stats.happiness > 70 -> "(^_^)"
            g.buddy.stats.happiness < 30 -> "(;_;)"
            else -> "(o_o)"
        }
        Text(face, color = green, fontFamily = FontFamily.Monospace, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        if (speech.text.isNotBlank()) Text("> ${speech.text}", color = green, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        val lines = listOf(
            "NAME  ${g.buddy.name.ifBlank { "----" }}  LV${g.buddy.level}  GEN${g.buddy.generation}",
            "UP    ${up / 3600}h ${(up % 3600) / 60}m   AGE ${g.buddy.ageYears(now)}y",
            "MOOD  ${repo.engine.mood(now).uppercase()}",
            "ENV   ${g.weather.icon}${g.weather.label}  ${g.biome.icon}${g.biome.label}  ${g.motion.icon}${g.motion.label}",
            "WIFI  ${wifi.size} now / ${g.count(C.WIFI)} seen",
            "BLE   ${bt.size} now / ${g.count(C.BT)} seen",
            "CELL  ${cells.size} now / ${g.count(C.CELLS)} seen",
            "SATS  ${sats.size} now / ${g.count(C.SATS_MAX)} max",
            "DEV   ${g.devices.size} logged / ${g.devices.values.map { it.type }.toSet().size} kinds",
            "WATCH 📷${g.count(C.SURVEILLANCE)} 🚘${g.count(C.ALPR)} 🕸️${g.count(C.MESH)} 📡${g.count(C.LORA)}",
            "CONN  ${g.count(C.CONNECTIONS)}  LOCK ${g.count(C.RADAR_LOCKS)}",
            "SCANS ${g.count(C.SCANS)} / uniq ${g.count(C.UNIQUE_CODES)}",
            "DIST  ${repo.engine.fmtDist(g.count(C.DISTANCE_M))}  STEP ${g.count(C.STEPS)}",
            "FRND  ${g.friends.size}  NEAR ${nearby.size}  MSG ${g.count(C.MESSAGES)}",
            "STICK ${g.stickers.size}/${Stickers.all.size}  QUEST ${g.count(C.QUESTS_DONE)}",
            "COIN  ${g.coins}  XP ${g.buddy.xp}/${g.buddy.xpToNext()}",
            "HP ${g.buddy.stats.health.toInt()} FOOD ${g.buddy.stats.fullness.toInt()} NRG ${g.buddy.stats.energy.toInt()} HAP ${g.buddy.stats.happiness.toInt()}",
        )
        lines.forEach { Text(it, color = green, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        Text("────────────────────────────", color = green.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Text("passive recon only · no attacks · all on-device", color = green.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace, fontSize = 10.sp)
    }
}

// ------------------------------------------------------------------ Nerd panel

@Composable
fun NerdScreen(repo: GameRepository, hub: SensorHub) {
    val r by hub.body.readings.collectAsStateWithLifecycle()
    val loc by hub.gnss.location.collectAsStateWithLifecycle()
    val g by rememberGame(repo)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenTitle("Nerd panel", "Raw sensor feed and power-user tips")
        Panel(Modifier.padding(12.dp), title = "Live sensors") {
            KeyValue("Heading", "${r.heading.toInt()}° ${compassName(r.heading)}")
            KeyValue("Pitch/Roll", "${r.pitch.toInt()}° / ${r.roll.toInt()}°")
            KeyValue("Accel (linear)", "%.2f m/s²".format(r.accel))
            KeyValue("Light", if (r.hasLight) "${r.lux.toInt()} lux" else "n/a")
            KeyValue("Proximity", if (r.hasProximity) (if (r.proximityNear) "near" else "far") else "n/a")
            KeyValue("Pressure", if (r.hasBarometer) "%.2f hPa".format(r.pressureHpa) else "n/a")
            KeyValue("Altitude (baro)", if (r.altitudeM.isNaN()) "n/a" else "%.0f m · ${Env.altitudeZone(r.altitudeM.toDouble())}".format(r.altitudeM))
            KeyValue("Magnetic", "%.1f µT".format(r.magneticUT))
            KeyValue("Temp", if (r.hasThermometer) "%.1f °C".format(r.ambientTempC) else "n/a (most phones lack this)")
            KeyValue("Humidity", if (r.hasHygrometer) "%.0f %%".format(r.humidity) else "n/a")
            KeyValue("Steps (session)", "${r.stepsSession}")
            KeyValue("Battery", if (r.batteryPct >= 0) "${r.batteryPct}%${if (r.charging) " ⚡" else ""}" else "n/a")
        }
        Panel(Modifier.padding(horizontal = 12.dp), title = "Fix / position") {
            val l = loc
            if (l == null) KeyValue("GPS", "no fix yet") else {
                KeyValue("Lat/Lon", "%.5f, %.5f".format(l.latitude, l.longitude))
                KeyValue("Accuracy", "±%.0f m".format(l.accuracy))
                KeyValue("Speed", if (l.hasSpeed()) "%.1f m/s".format(l.speed) else "n/a")
            }
            KeyValue("Weather guess", "${g.weather.icon} ${g.weather.label}")
            KeyValue("Biome", "${g.biome.icon} ${g.biome.label}")
            KeyValue("Motion", "${g.motion.icon} ${g.motion.label}")
        }
        Panel(Modifier.padding(12.dp), title = "Developer-options tips (passive)") {
            Text(
                "These stock Android toggles make the radars richer. They don't root your phone or change what Scanimal does — it only ever listens:\n\n" +
                    "• Developer options → Wi-Fi scan throttling OFF: the Wi-Fi radar and spectrum refresh many times faster.\n" +
                    "• Developer options → Stay awake while charging: leave the HUD up on a dock.\n" +
                    "• Location → Mode: High accuracy (Wi-Fi + Bluetooth scanning ON): more satellites and steadier bearings.\n" +
                    "• Keep Bluetooth and Wi-Fi radios ON even when 'not connected' so scans keep working.\n\n" +
                    "Spotting gear: Scanimal flags likely surveillance cameras, license-plate readers, mesh and LoRa (Meshtastic) devices from their public radio fingerprints — names, Bluetooth service IDs and MAC vendors. It's a guess, never a confirmation, and it only ever listens. A phone can't receive LoRa RF, so LoRa nodes are spotted by their Bluetooth.",
                color = Pal.sub, fontSize = 12.sp, lineHeight = 18.sp,
            )
        }
        Panel(Modifier.padding(horizontal = 12.dp), title = "Build") {
            KeyValue("Version", Updater.currentVersion)
            KeyValue("Devices logged", "${g.devices.size}")
            KeyValue("Discoveries pinned", "${g.discoveries.size}")
            KeyValue("Save size (approx)", "${com.realcak3d.scanimal.core.Codec.encode(g).length / 1024} KB")
        }
        Spacer(Modifier.height(8.dp))
        Text("Everything here is read from Android's public sensor APIs. Scanimal never transmits, injects, or attacks anything.", color = Pal.sub.copy(alpha = 0.7f), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/** Compact environment chips for the home header. */
@Composable
fun EnvChips(repo: GameRepository) {
    val g by rememberGame(repo)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (g.weather != com.realcak3d.scanimal.core.Weather.UNKNOWN) Chip("${g.weather.icon} ${g.weather.label}", Pal.sky)
        Chip("${g.biome.icon} ${g.biome.label}", Pal.mint)
        if (g.motion != com.realcak3d.scanimal.core.Motion.STILL) Chip("${g.motion.icon} ${g.motion.label}", Pal.violet)
    }
}

// ------------------------------------------------------------------ Notable finds (surveillance / mesh / LoRa)

@Composable
fun NotableScreen(repo: GameRepository) {
    val g by rememberGame(repo)
    val df = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }
    val notable = g.devices.values.filter { it.notable }.sortedByDescending { it.lastSeen }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            ScreenTitle("Notable finds", "Surveillance, readers, mesh & LoRa the buddy has spotted")
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    NotableStat("📷", g.count(C.SURVEILLANCE)); NotableStat("🚘", g.count(C.ALPR))
                    NotableStat("🕸️", g.count(C.MESH)); NotableStat("📡", g.count(C.LORA))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "All passive: the buddy only listens to radios that broadcast in the open. These are best guesses from device names, Bluetooth service IDs and MAC vendors — not confirmations.\n\n" +
                        "LoRa note: your phone has no LoRa radio, so a 'LoRa node' is spotted by its Bluetooth, not its long-range signal.",
                    color = Pal.sub, fontSize = 12.sp, lineHeight = 17.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        if (notable.isEmpty()) item { Text("Nothing notable yet. Keep exploring with the app open — cameras and mesh nodes turn up in public and around smart homes.", color = Pal.sub, modifier = Modifier.padding(8.dp)) }
        listItems(notable, key = { "${it.kind}:${it.id}" }) { d ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.panel).border(1.dp, Pal.amber.copy(alpha = 0.5f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.type.icon, fontSize = 26.sp, modifier = Modifier.padding(end = 10.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name.ifBlank { d.vendor.ifBlank { "(unnamed)" } }, color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("${d.type.label}${if (d.vendor.isNotBlank()) " · ${d.vendor}" else ""} · ${kindLabel(d.kind)}", color = Pal.amber, fontSize = 12.sp)
                    Text("${d.id} · seen ${d.timesSeen}× · last ${df.format(java.util.Date(d.lastSeen))}", color = Pal.sub, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun NotableStat(icon: String, n: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Text("$n", color = Pal.text, fontWeight = FontWeight.Bold, fontFamily = Mono)
    }
}
