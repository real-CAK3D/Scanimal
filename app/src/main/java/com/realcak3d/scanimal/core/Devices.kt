package com.realcak3d.scanimal.core

/** Classifies a signal into a device type, guesses a vendor, and flags notable finds. */
object DeviceClassifier {
    // Meshtastic BLE service UUID (companion-app service on LoRa nodes).
    const val MESHTASTIC_SERVICE = "6ba1b218-15a8-461f-9fa8-5dcae273eafd"
    // Common LoRa / mesh dev-board brand tokens (seen over the board's BLE/Wi-Fi radio).
    // These are LoRa radio nodes (detected via their companion BLE/Wi-Fi radio, not the LoRa RF itself).
    private val loraBoards = listOf("meshtastic", "heltec", "t-beam", "tbeam", "lilygo", "ttgo", "lora", "rak4", "rak wisblock", "wisblock", "disaster radio")
    // Generic (non-LoRa) mesh / off-grid messaging stacks.
    private val meshTokens = listOf("meshcore", "reticulum", "bridgefy", "briar", "gotenna", "bluetooth mesh", "ble mesh")
    // Surveillance camera / ALPR tokens. Heuristic — flagged as "possible".
    private val alprTokens = listOf("flock", "flocksafety", "vigilant", "elsag", "alpr", "rekor", "plate reader", "lpr-", "neology", "perceptics", "genetec")
    private val camTokens = listOf("axis-", "hikvision", "dahua", "reolink", "amcrest", "lorex", "ubnt-cam", "unifi protect", "verkada", "avigilon", "flir", "bosch cam", "camera", "nvr", "ipcam", "cam-")
    private val threadTokens = listOf("thread", "matter", "nanoleaf", "eero", "homepod", "border router", "openthread")

    private val rules: List<Pair<DeviceType, List<String>>> = listOf(
        DeviceType.BUDDY to listOf("scanimal"),
        DeviceType.EARBUDS to listOf("airpods", "buds", "earbud", "skullcandy", "indy", "sesh", "dime", "jabra elite", "beats fit",
            "powerbeats", "studio buds", "soundcore liberty", "liberty", "freebuds", "pixel buds", "galaxy buds", "wf-", "tws", "earfun",
            "jlab", "raycon", "nothing ear", "ear (", "oneplus buds", "redmi buds", "tozo", "aukey ep"),
        DeviceType.HEADPHONES to listOf("headphone", "headset", "wh-", "bose qc", "quietcomfort", "bose 700", "beats solo", "beats studio",
            "crusher", "hesh", "momentum", "airpods max", "steelseries arctis", "hyperx", "corsair", "logitech g", "maxwell"),
        DeviceType.SPEAKER to listOf("speaker", "jbl", "flip", "charge", "boombox", "ue boom", "wonderboom", "soundlink", "sonos", "echo",
            "homepod", "megaboom", "marshall", "soundcore", "bose home", "soundbar", "nest audio", "nest mini", "party"),
        DeviceType.WATCH to listOf("watch", "galaxy watch", "pixel watch", "apple watch", "garmin", "fenix", "forerunner", "amazfit",
            "venu", "vivoactive", "suunto", "coros", "ticwatch", "versa", "sense"),
        DeviceType.FITNESS to listOf("fitbit", "charge 5", "charge 6", "mi band", "smart band", "whoop", "oura", "polar", "inspire", "wahoo", "hrm"),
        DeviceType.TRACKER to listOf("airtag", "tile", "smarttag", "chipolo", "pebblebee", "find my", "moto tag"),
        DeviceType.TV to listOf(" tv", "bravia", "roku", "fire tv", "firetv", "chromecast", "android tv", "shield", "vizio", "hisense", "tcl", "[tv]", "webos", "apple tv"),
        DeviceType.CAR to listOf("my car", "ford", "sync", "toyota", "honda", "chevy", "chevrolet", "uconnect", "tesla", "bmw", "audi", "mercedes",
            "mazda", "subaru", "hyundai", "kia", "nissan", "jeep", "ram ", "gmc", "volvo", "vw ", "volkswagen", "carplay", "android auto", "lexus"),
        DeviceType.KEYBOARD to listOf("keyboard", "keys-to-go", "mx keys", "k380", "k780", "magic keyboard", "keychron"),
        DeviceType.MOUSE to listOf("mouse", "mx master", "mx anywhere", "trackpad", "magic mouse"),
        DeviceType.GAMEPAD to listOf("controller", "xbox", "dualsense", "dualshock", "joy-con", "pro controller", "gamepad", "8bitdo", "switch"),
        DeviceType.LAPTOP to listOf("macbook", "laptop", "desktop", "-pc", "pc-", "thinkpad", "surface", "imac", "dell", "hp ", "lenovo", "asus", "acer", "chromebook"),
        DeviceType.TABLET to listOf("ipad", "tab ", "galaxy tab", "fire hd", "kindle", "tablet"),
        DeviceType.PHONE to listOf("iphone", "galaxy", "pixel", "oneplus", "moto ", "xiaomi", "redmi", "oppo", "vivo", "realme", "nokia", "phone", "sm-"),
        DeviceType.LIGHT to listOf("hue", "light", "lamp", "bulb", "govee", "lifx", "nanoleaf", "wiz", "led"),
        DeviceType.PRINTER to listOf("printer", "deskjet", "officejet", "laserjet", "envy", "epson", "canon", "brother", "pixma"),
        DeviceType.CAMERA to listOf("gopro", "insta360", "dji", "ring", "wyze", "arlo", "blink", "eufy", "webcam"),
        DeviceType.HEALTH to listOf("scale", "thermometer", "blood", "glucose", "omron", "withings", "dexcom", "libre", "toothbrush", "oral-b", "sonicare"),
        DeviceType.APPLIANCE to listOf("fridge", "washer", "dryer", "oven", "vacuum", "roomba", "dyson", "thermostat", "nest", "ecobee", "plug", "switchbot", "airfryer"),
    )

    private val hotspotHints = listOf("iphone", "galaxy", "androidap", "android", "pixel", "hotspot", "mobile", "moto", "oneplus", "redmi", "'s phone")
    private val routerHints = listOf("netgear", "linksys", "tp-link", "tplink", "asus", "xfinity", "spectrum", "att", "verizon", "fios", "eero",
        "orbi", "deco", "google wifi", "nest wifi", "starlink", "ubnt", "unifi", "mesh", "5g", "2.4", "home", "wifi", "wi-fi", "router", "cox", "optimum", "frontier")

    data class Result(val type: DeviceType, val vendor: String, val notable: Boolean)

    fun describe(sig: SignalInfo): Result {
        val t = classify(sig)
        val vendor = Vendors.forMac(sig.id).ifBlank { Vendors.forMfg(sig.mfgId) }
        val notable = t in setOf(DeviceType.SURVEILLANCE, DeviceType.ALPR, DeviceType.MESH, DeviceType.LORA)
        return Result(t, vendor, notable)
    }

    fun classify(sig: SignalInfo): DeviceType {
        val n = sig.name.lowercase()
        val services = sig.services.map { it.lowercase() }

        // Strong signals first: Meshtastic service UUID or brand = LoRa mesh node.
        if (services.any { it.contains(MESHTASTIC_SERVICE) || it.startsWith("6ba1b218") }) return DeviceType.LORA
        if (Vendors.forMfg(sig.mfgId) == "Meshtastic") return DeviceType.LORA
        if (loraBoards.any { n.contains(it) }) return DeviceType.LORA
        if (meshTokens.any { n.contains(it) }) return DeviceType.MESH
        if (alprTokens.any { n.contains(it) }) return DeviceType.ALPR
        if (camTokens.any { n.contains(it) }) return DeviceType.SURVEILLANCE
        if (threadTokens.any { n.contains(it) }) return DeviceType.THREAD

        if (sig.kind == SignalKind.CELL) return DeviceType.CELL
        if (sig.kind == SignalKind.WIFI) {
            if (n.startsWith("scanimal")) return DeviceType.BUDDY
            if (hotspotHints.any { n.contains(it) }) return DeviceType.HOTSPOT
            if (n.contains("direct-") && (n.contains("tv") || n.contains("bravia") || n.contains("roku"))) return DeviceType.TV
            if (n.contains("direct-") && listOf("hp", "epson", "canon", "brother").any { n.contains(it) }) return DeviceType.PRINTER
            for ((type, keys) in rules) if (type in setOf(DeviceType.TV, DeviceType.PRINTER, DeviceType.CAR, DeviceType.CAMERA, DeviceType.APPLIANCE, DeviceType.LIGHT) && keys.any { n.contains(it) }) return type
            return DeviceType.ROUTER
        }
        for ((type, keys) in rules) if (keys.any { n.contains(it) }) return type
        return when (sig.classHint) {
            0x0400 -> DeviceType.HEADPHONES
            0x0100 -> DeviceType.LAPTOP
            0x0200 -> DeviceType.PHONE
            0x0500 -> DeviceType.KEYBOARD
            0x0700 -> DeviceType.WATCH
            0x0900 -> DeviceType.HEALTH
            0x0600 -> DeviceType.CAMERA
            0x0800 -> DeviceType.GAMEPAD
            0x0300 -> DeviceType.ROUTER
            else -> DeviceType.UNKNOWN
        }
    }
}
