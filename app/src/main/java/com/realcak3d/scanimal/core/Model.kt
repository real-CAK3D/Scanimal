package com.realcak3d.scanimal.core

import kotlin.math.max
import kotlin.math.min

/** Everything here is plain Kotlin (no Android imports) so the game rules can be unit-tested on the JVM. */

enum class Sex(val label: String, val symbol: String, val they: String, val their: String) {
    MALE("Boy", "♂", "he", "his"),
    FEMALE("Girl", "♀", "she", "her"),
    MYSTERY("Mystery", "✶", "they", "their");
}

enum class Stage(val label: String, val minAgeHours: Double, val scale: Float) {
    EGG("Egg", 0.0, 0.8f),
    BABY("Baby", 0.0, 0.62f),
    CHILD("Child", 24.0, 0.74f),
    TEEN("Teen", 72.0, 0.86f),
    ADULT("Adult", 168.0, 1.0f),
    ELDER("Elder", 720.0, 0.96f);

    companion object {
        fun forAgeHours(hours: Double): Stage =
            entries.filter { it != EGG }.lastOrNull { hours >= it.minAgeHours } ?: BABY
    }
}

enum class Personality(val label: String, val blurb: String) {
    CURIOUS("Curious", "Loves new codes and new signals."),
    FOODIE("Foodie", "Lives for the next snack."),
    SOCIAL("Social", "Makes friends with every Bluetooth device."),
    LAZY("Sleepy", "Naps whenever possible."),
    SPORTY("Sporty", "Wants to go on walks."),
    NERDY("Nerdy", "Loves chatting, riddles and satellites."),
}

enum class Pattern { PLAIN, SPOTS, STRIPES, STARS }

enum class Species(val label: String, val emoji: String, val blurb: String, val defaultHue: Float) {
    BLOB("Blip", "🟢", "The original radar critter. Antenna included.", 160f),
    DINO("Dino", "🦖", "Tiny arms, big heart, spiky back.", 110f),
    DOG("Dog", "🐶", "Floppy ears and a waggy tail.", 30f),
    CAT("Cat", "🐱", "Pointy ears, whiskers, attitude.", 25f),
    CHAMELEON("Chameleon", "🦎", "Slowly shifts color. Curly tail.", 95f),
    PLANT("Sprout", "🌱", "Grows from sprout to flower to tree.", 120f),
    FISH("Fish", "🐟", "Fins, bubbles, and a love of floating.", 200f),
    BIRD("Bird", "🐦", "Beak, wings and a fancy crest.", 50f),
    HUMAN("Human", "🧑", "A tiny person with hair and a big smile.", 28f),
    ALIEN("Alien", "👽", "Two antennae and a third eye.", 140f),
    ROBOT("Robot", "🤖", "Bolts, screen face and a blinking light.", 210f),
}

enum class DeviceType(val label: String, val icon: String, val effect: String) {
    EARBUDS("Earbuds", "🎧", "music: +happiness"),
    HEADPHONES("Headphones", "🎧", "music: +happiness"),
    SPEAKER("Speaker", "🔊", "dance party: +happiness, +fitness"),
    WATCH("Smartwatch", "⌚", "workout buddy: +fitness"),
    FITNESS("Fitness band", "💪", "workout buddy: +fitness"),
    PHONE("Phone", "📱", "new friend: +social"),
    TABLET("Tablet", "📲", "cartoons: +happiness"),
    LAPTOP("Computer", "💻", "homework: +smarts"),
    TV("TV", "📺", "binge watching: +happiness, -energy"),
    CAR("Car", "🚗", "road trip: +XP"),
    KEYBOARD("Keyboard", "⌨️", "typing lessons: +smarts"),
    MOUSE("Mouse", "🖱️", "chasing the cursor: +fitness"),
    GAMEPAD("Game controller", "🎮", "gaming: +happiness"),
    TRACKER("Item tracker", "🏷️", "hide and seek: +XP"),
    LIGHT("Smart light", "💡", "sunbathing: +energy"),
    ROUTER("Router", "📡", "packet snack: +energy"),
    HOTSPOT("Phone hotspot", "📶", "borrowed Wi-Fi: +energy, +social"),
    PRINTER("Printer", "🖨️", "paper snack: +fullness"),
    CAMERA("Camera", "📷", "photo shoot: +happiness"),
    HEALTH("Health device", "🩺", "check-up: +health"),
    APPLIANCE("Smart appliance", "🏠", "smart home tour: +smarts"),
    BUDDY("Scanimal", "🐾", "playdate: +social, +happiness"),
    CELL("Cell tower", "🗼", "tower spotting: +XP"),
    SURVEILLANCE("Surveillance cam", "📷", "spotted a watcher: +XP, +smarts"),
    ALPR("License-plate reader", "🚘", "spotted an ALPR: +XP, +smarts"),
    MESH("Mesh node", "🕸️", "off-grid friend: +social, +XP"),
    LORA("LoRa node", "📡", "long-range radio: +XP, +smarts"),
    THREAD("Thread/Matter", "🧵", "smart-home mesh: +smarts"),
    UNKNOWN("Mystery device", "❔", "curiosity: +XP"),
}

/** Everything the radar has ever spotted, with how often it was seen and connected. */
class DeviceRecord(
    val id: String,
    var name: String,
    val kind: SignalKind,
    var type: DeviceType,
    val firstSeen: Long,
    var lastSeen: Long,
    var timesSeen: Int = 1,
    var timesConnected: Int = 0,
    var bestRssi: Int = -127,
    var lastConnected: Long = 0L,
    var vendor: String = "",
    var notable: Boolean = false,
)

data class FriendMsg(val fromMe: Boolean, val text: String, val time: Long)

/** Another player's buddy: imported from a link code or met nearby over Bluetooth. */
class Friend(
    val id: String,
    var name: String,
    var owner: String,
    var species: Species,
    var sex: Sex,
    var hue: Float,
    var pattern: Pattern,
    var level: Int,
    var accessory: String?,
    var addedAt: Long,
    var lastNearby: Long = 0L,
    var onScreen: Boolean = true,
    var friendship: Int = 0,
    val messages: MutableList<FriendMsg> = mutableListOf(),
    var viaLink: Boolean = true,
)

data class OwnerProfile(
    var name: String = "",
    var birthday: String = "",      // "MM-DD"
    var favoriteColor: String = "",
    var favoriteFood: String = "",
    var hobby: String = "",
    var pet: String = "",
    var done: Boolean = false,
)

class Stats(
    var fullness: Double = 70.0,
    var happiness: Double = 70.0,
    var energy: Double = 85.0,
    var hygiene: Double = 85.0,
    var health: Double = 100.0,
    var fitness: Double = 20.0,
    var smarts: Double = 10.0,
    var social: Double = 15.0,
    var discipline: Double = 25.0,
) {
    fun clampAll() {
        fullness = clamp(fullness); happiness = clamp(happiness); energy = clamp(energy)
        hygiene = clamp(hygiene); health = clamp(health); fitness = clamp(fitness)
        smarts = clamp(smarts); social = clamp(social); discipline = clamp(discipline)
    }

    fun copy() = Stats(fullness, happiness, energy, hygiene, health, fitness, smarts, social, discipline)

    companion object {
        fun clamp(v: Double) = min(100.0, max(0.0, v))
    }
}

class Buddy(
    var name: String = "",
    var sex: Sex = Sex.MYSTERY,
    var seed: Long = 0L,
    var eggLaidAt: Long = 0L,
    var bornAt: Long = 0L,
    var level: Int = 1,
    var xp: Int = 0,
    var lives: Int = 3,
    var weight: Double = 12.0,
    var hue: Float = 160f,
    var pattern: Pattern = Pattern.PLAIN,
    var personality: Personality = Personality.CURIOUS,
    var accessory: String? = null,
    var asleep: Boolean = false,
    var sick: Boolean = false,
    var poops: Int = 0,
    var tantrum: Boolean = false,
    var generation: Int = 1,
    var stats: Stats = Stats(),
    var lastUpdate: Long = 0L,
    var sleptAt: Long = 0L,
    var hoursNeglected: Double = 0.0,
    var dead: Boolean = false,
    var species: Species = Species.BLOB,
    var hatchReadyAt: Long = 0L,
    var outfit: String? = null,
) {
    val hatched get() = bornAt > 0L

    /** 0..1 incubation progress for the egg. */
    fun eggProgress(now: Long): Float {
        if (hatched) return 1f
        if (hatchReadyAt <= eggLaidAt) return 0f
        return ((now - eggLaidAt).toFloat() / (hatchReadyAt - eggLaidAt)).coerceIn(0f, 1f)
    }

    fun ageHours(now: Long): Double = if (!hatched) 0.0 else (now - bornAt) / 3_600_000.0

    /** One real day = one buddy year, tamagotchi-style. */
    fun ageYears(now: Long): Int = (ageHours(now) / 24.0).toInt()

    fun stage(now: Long): Stage = if (!hatched) Stage.EGG else Stage.forAgeHours(ageHours(now))

    fun xpToNext(): Int = 60 + level * 30 + (level * level) / 2

    fun pronoun() = sex.they
}

data class LogEntry(val time: Long, val icon: String, val text: String)

data class Memorial(
    val name: String, val sex: Sex, val generation: Int, val level: Int,
    val ageYears: Int, val diedAt: Long, val cause: String,
)

data class Settings(
    var notifications: Boolean = true,
    var vibration: Boolean = true,
    var sounds: Boolean = true,
    var onlineProductLookup: Boolean = true,
    var autoSleepAtNight: Boolean = true,
    var backgroundMode: Boolean = false,
    var discoverable: Boolean = true,
    var autoUpdate: Boolean = true,
    var metric: Boolean = true,
    var theme: String = "radar",
    var physics: Boolean = true,
    var showVisitors: Boolean = true,
    var ambientChatter: Boolean = true,
    var mapTrail: Boolean = true,
    var surveillanceAlerts: Boolean = true,
    var accentHue: Int = -1,       // -1 = use theme default; else 0..359 custom accent
    var bigText: Boolean = false,
    var spriteArt: Boolean = true,
    var matureLanguage: Boolean = true,
    var revisitChime: Boolean = true,
)

/** A signal seen by the radar (Wi-Fi network or Bluetooth device). */
data class SignalInfo(
    val id: String,           // BSSID or MAC
    val name: String,         // SSID or device name (may be blank)
    val rssi: Int,
    val kind: SignalKind,
    val frequencyMhz: Int = 0,
    val secured: Boolean = true,
    val classHint: Int = -1,      // Bluetooth major device class, if known
    val connected: Boolean = false,
    val widthMhz: Int = 20,       // Wi-Fi channel width, for the spectrum view
    val services: List<String> = emptyList(),  // advertised BLE service UUIDs (lowercase)
    val mfgId: Int = -1,          // BLE manufacturer id (company identifier)
)

enum class SignalKind { WIFI, BLUETOOTH, BLE, CELL }

data class SatelliteInfo(
    val svid: Int,
    val constellation: String,
    val cn0: Float,
    val azimuth: Float,
    val elevation: Float,
    val usedInFix: Boolean,
)

class GameData(
    var buddy: Buddy = Buddy(),
    var coins: Int = 25,
    var ownerName: String = "",
    val inventory: MutableMap<String, Int> = mutableMapOf("kibble" to 3, "soap" to 1),
    val ownedCosmetics: MutableSet<String> = mutableSetOf(),
    val achievements: MutableMap<String, Long> = mutableMapOf(),
    val counters: MutableMap<String, Long> = mutableMapOf(),
    val codeLastEaten: MutableMap<String, Long> = mutableMapOf(),
    val codeTimesEaten: MutableMap<String, Int> = mutableMapOf(),
    val lastScanByCat: MutableMap<String, Long> = mutableMapOf(),   // per-category min-gap timers (barcode/QR/NFC/... don't block each other)

    val seenWifi: MutableSet<String> = mutableSetOf(),
    val seenBt: MutableSet<String> = mutableSetOf(),
    val friendNames: MutableList<String> = mutableListOf(),
    val constellations: MutableSet<String> = mutableSetOf(),
    val memory: MutableMap<String, String> = mutableMapOf(),
    val likes: MutableList<String> = mutableListOf(),
    val journal: MutableList<LogEntry> = mutableListOf(),
    val graveyard: MutableList<Memorial> = mutableListOf(),
    val daily: MutableMap<String, Long> = mutableMapOf(),
    var settings: Settings = Settings(),
    var lastFoodName: String = "",
    var favoriteFood: String = "",
    var bestScores: MutableMap<String, Int> = mutableMapOf(),
    var created: Long = 0L,
    var toy: String? = null,
    var toyX: Float = 0.5f,
    var myId: String = "",
    var owner: OwnerProfile = OwnerProfile(),
    val devices: MutableMap<String, DeviceRecord> = mutableMapOf(),
    val friends: MutableList<Friend> = mutableListOf(),
    var lastVersionCheck: Long = 0L,
    val stickers: MutableSet<String> = mutableSetOf(),
    val discoveries: MutableList<Discovery> = mutableListOf(),
    val quests: MutableList<Quest> = mutableListOf(),
    var questDay: Long = -1L,
    var weather: Weather = Weather.UNKNOWN,
    var biome: Biome = Biome.OUTDOORS,
    var motion: Motion = Motion.STILL,
    var bootAt: Long = 0L,
    var pressureBaseline: Float = 0f,
) {
    fun count(key: String): Long = counters[key] ?: 0L
    fun bump(key: String, by: Long = 1) { counters[key] = count(key) + by }
    fun setMax(key: String, value: Long) { if (value > count(key)) counters[key] = value }

    fun log(now: Long, icon: String, text: String) {
        journal.add(0, LogEntry(now, icon, text))
        while (journal.size > 150) journal.removeAt(journal.size - 1)
    }

    fun has(item: String) = (inventory[item] ?: 0) > 0
    fun addItem(item: String, n: Int = 1) { inventory[item] = (inventory[item] ?: 0) + n }
    fun takeItem(item: String): Boolean {
        val n = inventory[item] ?: 0
        if (n <= 0) return false
        if (n == 1) inventory.remove(item) else inventory[item] = n - 1
        return true
    }
}

/** Counter keys, kept in one place so achievements and stats screens agree. */
object C {
    const val SCANS = "scans"
    const val UNIQUE_CODES = "unique_codes"
    const val QR = "qr_scans"
    const val PRODUCT = "product_scans"
    const val INDUSTRIAL = "industrial_scans"
    const val EXOTIC = "exotic_scans"
    const val NFC = "nfc_scans"
    const val LEGENDARY = "legendary_foods"
    const val EPIC = "epic_foods"
    const val RARE = "rare_foods"
    const val WIFI = "wifi_seen"
    const val WIFI_OPEN = "wifi_open"
    const val WIFI_24 = "wifi_24"
    const val WIFI_5G = "wifi_5g"
    const val WIFI_6G = "wifi_6g"
    const val BT = "bt_seen"
    const val BT_CLOSE = "bt_hugs"
    const val SATS_MAX = "sats_max"
    const val STARGAZES = "stargazes"
    const val STEPS = "steps"
    const val SHAKES = "shakes"
    const val PETS = "pets"
    const val CHATS = "chats"
    const val JOKES = "jokes"
    const val RIDDLES_SOLVED = "riddles_solved"
    const val GAMES = "games"
    const val GAME_WINS = "game_wins"
    const val CLEANS = "cleans"
    const val MEDS = "meds"
    const val SLEEPS = "sleeps"
    const val DEATHS = "deaths"
    const val PEEKABOO = "peekaboo"
    const val CHARGES = "charges"
    const val DANCES = "dances"
    const val PURCHASES = "purchases"
    const val COINS_EARNED = "coins_earned"
    const val RADAR_LOCKS = "radar_locks"
    const val ALTITUDE_MAX = "altitude_max"
    const val DARK_NAPS = "dark_naps"
    const val MAGNETS = "magnets"
    const val FEEDS = "feeds"
    const val DISTANCE_M = "distance_m"
    const val WALKS = "walks"
    const val CONNECTIONS = "connections"
    const val WIFI_CONNECTED_HOURS = "wifi_connected_hours"
    const val CELLS = "cells_seen"
    const val DEVICE_TYPES = "device_types"
    const val FRIENDS = "friends"
    const val NEARBY_MEETS = "nearby_meets"
    const val MESSAGES = "messages_sent"
    const val PLAYDATES = "playdates"
    const val BUMPS = "wall_bumps"
    const val HATCH_WARMS = "hatch_warms"
    const val QUESTS_DONE = "quests_done"
    const val STICKERS = "stickers"
    const val STORMS = "storms_weathered"
    const val BIOMES = "biomes_visited"
    const val RUN_METERS = "run_meters"
    const val RIDE_METERS = "ride_meters"
    const val POCKET_HOURS = "pocket_hours"
    const val FIREFLIES = "fireflies"
    const val SIG_MOTION = "significant_motions"
    const val HOT = "hot_readings"
    const val COLD = "cold_readings"
    const val SUNNY = "sunny_moments"
    const val MOONLIGHT = "moonlight_moments"
    const val DISCOVERIES_MAPPED = "discoveries_mapped"
    const val DEV_SNIFFS = "dev_sniffs"
    const val TOY_PLAYS = "toy_plays"
    const val TOY_ANNOYED = "toy_annoyed"
    const val WANDERS = "wanders"
    const val SURVEILLANCE = "surveillance_seen"
    const val ALPR = "alpr_seen"
    const val MESH = "mesh_seen"
    const val LORA = "lora_seen"
    const val NOTABLE = "notable_finds"
}

/** Weather the buddy infers from the barometer trend (no internet needed). */
enum class Weather(val label: String, val icon: String) {
    UNKNOWN("Unknown", "❓"), CLEAR("Clear", "☀️"), FAIR("Fair", "🌤️"), CLOUDY("Clouds rolling in", "☁️"),
    STORMY("Storm coming", "⛈️"), CLEARING("Clearing up", "🌈");
}

/** Where you physically are, guessed from how many devices are around. */
enum class Biome(val label: String, val icon: String, val blurb: String) {
    WILDERNESS("Wilderness", "🏕️", "Hardly any signals out here. Peaceful!"),
    OUTDOORS("Outdoors", "🌳", "A few signals drifting by."),
    SUBURB("Neighborhood", "🏘️", "Cozy, a handful of networks around."),
    CITY("City", "🏙️", "So many signals! The buddy is overstimulated."),
    HOTSPOT("Signal jungle", "🌐", "A wall of networks. Signal heaven!");
}

/** How the phone (and buddy) is moving. */
enum class Motion(val label: String, val icon: String) {
    STILL("Resting", "🧘"), POCKET("In your pocket", "👖"), WALKING("Walking", "🚶"),
    RUNNING("Running", "🏃"), CYCLING("Cycling", "🚲"), VEHICLE("Riding", "🚗");
}

data class Discovery(val lat: Double, val lon: Double, val kind: String, val label: String, val time: Long)

data class Quest(val id: String, val title: String, val counterKey: String, val baseline: Long, val target: Long, val reward: Int, var claimed: Boolean = false)
