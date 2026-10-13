package com.realcak3d.scanimal.sensors

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Codec
import com.realcak3d.scanimal.core.FoodFactory
import com.realcak3d.scanimal.core.Friend
import com.realcak3d.scanimal.core.Pattern
import com.realcak3d.scanimal.core.Species
import com.realcak3d.scanimal.core.GameEvent
import com.realcak3d.scanimal.core.ScanInput
import com.realcak3d.scanimal.core.Sex
import com.realcak3d.scanimal.data.GameRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Owns every sensor and routes their events into the game. Lives as long as the activity. */
class SensorHub(ctx: Context, val repo: GameRepository) {
    val wifi = WifiRadar(ctx)
    val bt = BluetoothRadar(ctx)
    val gnss = GnssWatcher(ctx)
    val body = BodySensors(ctx)
    val sound = SoundMeter(ctx)
    val haptics = Haptics(ctx)
    val cells = CellRadar(ctx)
    val link = BuddyLink(ctx, repo)
    val torch = Torch(ctx)

    private var lastAlt = 0L
    private var lastSpeed = 0f
    private var lastLoc: Location? = null
    private var stepWindow = ArrayDeque<Long>()   // recent step event times, for cadence
    private val mappedIds = HashSet<String>()
    private val handler = Handler(Looper.getMainLooper())

    init {
        gnss.onSatellites = { sats -> repo.act { now -> onSatellites(sats, now) } }
        gnss.onLocation = { loc ->
            DistanceTracker.feed(repo, loc)
            lastLoc = loc
            if (loc.hasSpeed()) lastSpeed = loc.speed
            maybeRevisitChime(loc)
            if (loc.hasAltitude() && System.currentTimeMillis() - lastAlt > 60_000) { lastAlt = System.currentTimeMillis(); repo.act { now -> onAltitude(loc.altitude, now) } }
        }
        body.onShake = { haptics.buzz(40, 160); repo.act { now -> onShake(now) } }
        body.onSteps = { n ->
            DistanceTracker.addSteps(n)
            val t = System.currentTimeMillis(); repeat(n) { stepWindow.addLast(t) }
            while (stepWindow.isNotEmpty() && t - stepWindow.first() > 30_000) stepWindow.removeFirst()
            repo.act { now -> onSteps(n, now) }
        }
        body.onDark = { repo.act { now -> onDark(now) } }
        body.onPeekaboo = { repo.act { now -> onPeekaboo(now) } }
        body.onCharging = { repo.act { now -> onCharging(now) } }
        body.onMagnet = { m -> repo.act { now -> onMagnet(m, now) } }
        body.onAltitude = { alt -> repo.act { now -> onAltitude(alt, now) } }
        sound.onLoud = { repo.act { now -> onLoudSound(now) } }
        cells.onBatch = { list -> repo.act { now -> onCells(list, now) } }
        bt.onBuddyAdvert = { data, rssi, addr -> link.onAdvert(data, rssi, addr) }
        body.onSignificantMotion = { repo.act { now -> onSignificantMotion(now) } }
        wifi.onBatch = { list -> mapNew(list.map { "WIFI:${it.id}" to it.name }); repo.act { now -> onWifiScan(list, now) } }
        bt.onBatch = { list -> mapNew(list.map { "BT:${it.id}" to it.name }); repo.act { now -> onBluetooth(list, now) } }
    }

    private fun cadenceSpm(): Float {
        val t = System.currentTimeMillis()
        while (stepWindow.isNotEmpty() && t - stepWindow.first() > 30_000) stepWindow.removeFirst()
        if (stepWindow.size < 2) return 0f
        val span = (t - stepWindow.first()).coerceAtLeast(1).toFloat() / 60_000f
        return stepWindow.size / span
    }

    private fun pocketed(): Boolean {
        val r = body.readings.value
        return (r.hasLight && r.lux in 0f..6f) && (!r.hasProximity || r.proximityNear) && r.accel < 2f
    }

    private var lastRevisitChime = 0L
    private val revisitChimedAt = HashMap<String, Long>()

    /** While walking or driving, a distinct chirp when we pass within ~40 m of a place we already pinned. */
    private fun maybeRevisitChime(loc: android.location.Location) {
        if (!repo.data.settings.revisitChime) return
        val moving = lastSpeed >= 0.7f || DistanceTracker.walk.value.active   // walking (~2.5 km/h) or faster
        if (!moving) return
        val now = System.currentTimeMillis()
        if (now - lastRevisitChime < 8_000L) return                           // don't chime constantly
        val d = repo.engine.nearestPinnedRevisit(loc.latitude, loc.longitude, 40.0, 120_000L, now) ?: return
        val key = "%.5f,%.5f".format(d.lat, d.lon)
        if (now - (revisitChimedAt[key] ?: 0L) < 120_000L) return             // once per spot per 2 min
        revisitChimedAt[key] = now; lastRevisitChime = now
        haptics.sfx("revisit"); haptics.buzz(25, 120)
        val b = repo.data.buddy
        if (b.hatched && !b.asleep) repo.say(listOf(
            "*ping* We've been to ${d.label} before!",
            "Hey, I pinned ${d.label} around here earlier.",
            "Back near ${d.label} — I remember this spot.",
        ).random())
    }

    /** Pin newly seen signals on the on-device map (only when the trail is enabled and we have a fix). */
    private fun mapNew(items: List<Pair<String, String>>) {
        if (!repo.data.settings.mapTrail) return
        val loc = lastLoc ?: return
        if (System.currentTimeMillis() - loc.time > 60_000) return
        for ((id, name) in items) {
            if (mappedIds.add(id)) repo.engine.mapDiscovery(loc.latitude, loc.longitude, id.substringBefore(':').lowercase(), name.ifBlank { id }, loc.time)
        }
    }

    private val envLoop = object : Runnable {
        override fun run() {
            val r = body.readings.value
            val devices = wifi.results.value.size + bt.devices.value.size + cells.cells.value.size
            repo.act { now -> onEnvironment(devices, r.pressureHpa.takeIf { r.hasBarometer } ?: 0f, lastSpeed, cadenceSpm(), pocketed(), now) }
            if (!r.ambientTempC.isNaN()) repo.act { now -> onAmbientTemp(r.ambientTempC, now) }
            handler.postDelayed(this, 30_000)
        }
    }

    fun fireflyTap() { torch.toggle(); if (torch.on) repo.act { now -> onTorchFirefly(now) } }

    fun applySettings() {
        val s = repo.data.settings
        haptics.vibrationOn = s.vibration
        haptics.soundOn = s.sounds
    }

    /** Foreground: the buddy is always sniffing around. */
    fun startAll() {
        applySettings()
        body.start(); wifi.start(); bt.start(); gnss.start(); cells.start(); link.start()
        handler.postDelayed(envLoop, 8_000)
    }

    fun stopAll() {
        handler.removeCallbacks(envLoop); torch.off()
        body.stop(); wifi.stop(); bt.stop(); gnss.stop(); sound.stop(); cells.stop(); link.stop()
    }

    /** Re-try anything that was blocked on a permission. */
    fun refreshPermissions() {
        body.restart(); wifi.stop(); wifi.start(); bt.stop(); bt.start(); gnss.stop(); gnss.start(); cells.stop(); cells.start(); link.stop(); link.start()
    }

    fun react(events: List<GameEvent>) {
        for (e in events) when (e) {
            is GameEvent.Unlocked -> { haptics.pattern(longArrayOf(0, 40, 60, 40, 60, 90)); haptics.sfx("achieve") }
            is GameEvent.LevelUp -> { haptics.pattern(longArrayOf(0, 80, 50, 80, 50, 160)); haptics.sfx("level") }
            is GameEvent.Ate -> { haptics.buzz(25, 90); haptics.sfx("eat") }
            is GameEvent.Died -> { haptics.pattern(longArrayOf(0, 300, 100, 300)); haptics.sfx("sad") }
            is GameEvent.Say -> if (e.anim == Anim.HATCH) { haptics.pattern(longArrayOf(0, 60, 40, 60, 40, 120, 60, 200)); haptics.sfx("hatch") }
                else if (e.anim == Anim.SPARKLE && e.text.contains("sticker", true)) haptics.sfx("sticker")
            else -> {}
        }
    }
}

/** Turns a scan into food, hatching the egg first if needed. */
object Feeder {
    suspend fun lookupProduct(code: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=product_name,brands,nutriscore_grade")
            val c = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500; readTimeout = 3500
                setRequestProperty("User-Agent", "Scanimal/1.0 (Android hobby app)")
            }
            c.inputStream.bufferedReader().use { r ->
                val o = JSONObject(r.readText())
                if (o.optInt("status") != 1) return@runCatching null to null
                val p = o.optJSONObject("product") ?: return@runCatching null to null
                val name = p.optString("product_name").ifBlank { null }
                val brand = p.optString("brands").split(",").firstOrNull()?.trim()?.ifBlank { null }
                val full = when {
                    name == null -> null
                    brand != null && !name.contains(brand, ignoreCase = true) -> "$brand $name"
                    else -> name
                }
                full to p.optString("nutriscore_grade").ifBlank { null }
            }
        }.getOrDefault(null to null)
    }

    suspend fun prepare(repo: GameRepository, raw: String, format: String): ScanInput {
        val isProduct = format in setOf("EAN_13", "EAN_8", "UPC_A", "UPC_E")
        if (isProduct && repo.data.settings.onlineProductLookup) {
            val (name, score) = lookupProduct(raw)
            return ScanInput(raw, format, name, score)
        }
        return ScanInput(raw, format)
    }

    fun feed(repo: GameRepository, input: ScanInput): List<GameEvent> {
        val food = FoodFactory.fromScan(input)
        val events = repo.act { now ->
            if (!g.buddy.hatched) {
                val warm = warmEgg(now, 120_000L)
                if (!g.buddy.hatched) return@act listOf(GameEvent.Say("*the egg soaks up the energy of ${food.name}* (−2 min)", Anim.SPARKLE))
                warm + feed(food, now)
            } else feed(food, now)
        }
        // Pin the scan on the map at our exact spot when it actually counted (an Ate event came back).
        if (events.any { it is GameEvent.Ate }) mapScan(food)
        return events
    }

    /** Pin a counted scan on the on-device map at the current GPS fix. */
    private fun mapScan(food: com.realcak3d.scanimal.core.Food) {
        if (!repo.data.settings.mapTrail) return
        val loc = lastLoc ?: return
        if (System.currentTimeMillis() - loc.time > 60_000) return
        repo.engine.mapDiscovery(loc.latitude, loc.longitude, "scan", food.name, loc.time)
    }

    /** Friend link codes and message postcards are handled instead of eaten. Returns null for normal codes. */
    fun special(repo: GameRepository, raw: String): List<GameEvent>? {
        Codec.parseLink(raw)?.let { card -> return repo.act { now -> addFriend(card, now, viaLink = true).ifEmpty { listOf(GameEvent.Say("${card.name} is already our friend! Updated their card.", Anim.HAPPY)) } } }
        Codec.parsePostcard(raw)?.let { pc ->
            return repo.act { now ->
                if (pc.toId.isNotBlank() && pc.toId != g.myId) return@act listOf(GameEvent.Say("That postcard is for someone else!", Anim.SAD))
                if (g.friends.none { it.id == pc.fromId }) {
                    addFriend(Friend(pc.fromId, pc.fromName.ifBlank { "Pen pal" }, "", Species.BLOB, Sex.MYSTERY, 200f, Pattern.PLAIN, 1, null, now), now, true)
                }
                addMessage(pc.fromId, pc.text, false, now)
            }
        }
        return null
    }
}
