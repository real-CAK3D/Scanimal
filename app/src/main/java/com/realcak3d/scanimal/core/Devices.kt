package com.realcak3d.scanimal.core

/** Guesses what kind of thing a signal is from its name (and Bluetooth class when available). */
object DeviceClassifier {
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
        DeviceType.TRACKER to listOf("airtag", "tile", "smarttag", "chipolo", "pebblebee", "find my"),
        DeviceType.TV to listOf("tv", "bravia", "roku", "fire tv", "firetv", "chromecast", "android tv", "shield", "vizio", "hisense", "tcl", "[tv]", "webos", "apple tv"),
        DeviceType.CAR to listOf("car", "ford", "sync", "toyota", "honda", "chevy", "chevrolet", "uconnect", "tesla", "bmw", "audi", "mercedes",
            "mazda", "subaru", "hyundai", "kia", "nissan", "jeep", "ram ", "gmc", "volvo", "vw ", "volkswagen", "my car", "carplay", "android auto", "lexus"),
        DeviceType.KEYBOARD to listOf("keyboard", "keys-to-go", "mx keys", "k380", "k780", "magic keyboard", "keychron"),
        DeviceType.MOUSE to listOf("mouse", "mx master", "mx anywhere", "trackpad", "magic mouse"),
        DeviceType.GAMEPAD to listOf("controller", "xbox", "dualsense", "dualshock", "joy-con", "pro controller", "gamepad", "8bitdo", "switch"),
        DeviceType.LAPTOP to listOf("macbook", "laptop", "desktop", "-pc", "pc-", "thinkpad", "surface", "imac", "dell", "hp ", "lenovo", "asus", "acer", "chromebook"),
        DeviceType.TABLET to listOf("ipad", "tab ", "galaxy tab", "fire hd", "kindle", "tablet"),
        DeviceType.PHONE to listOf("iphone", "galaxy", "pixel", "oneplus", "moto", "xiaomi", "redmi", "oppo", "vivo", "realme", "nokia", "phone", "sm-"),
        DeviceType.LIGHT to listOf("hue", "light", "lamp", "bulb", "govee", "lifx", "nanoleaf", "wiz", "led"),
        DeviceType.PRINTER to listOf("printer", "deskjet", "officejet", "laserjet", "envy", "epson", "canon", "brother", "pixma"),
        DeviceType.CAMERA to listOf("camera", "gopro", "insta360", "dji", "ring", "wyze", "arlo", "blink", "eufy"),
        DeviceType.HEALTH to listOf("scale", "thermometer", "blood", "glucose", "omron", "withings", "dexcom", "libre", "toothbrush", "oral-b", "sonicare"),
        DeviceType.APPLIANCE to listOf("fridge", "washer", "dryer", "oven", "vacuum", "roomba", "dyson", "thermostat", "nest", "ecobee", "plug", "switchbot", "airfryer"),
    )

    private val hotspotHints = listOf("iphone", "galaxy", "androidap", "android", "pixel", "hotspot", "mobile", "moto", "oneplus", "redmi", "'s phone")
    private val routerHints = listOf("netgear", "linksys", "tp-link", "tplink", "asus", "xfinity", "spectrum", "att", "verizon", "fios", "eero",
        "orbi", "deco", "google wifi", "nest wifi", "starlink", "ubnt", "unifi", "mesh", "5g", "2.4", "home", "wifi", "wi-fi", "router", "cox", "optimum", "frontier")

    fun classify(sig: SignalInfo): DeviceType {
        val n = sig.name.lowercase()
        if (sig.kind == SignalKind.CELL) return DeviceType.CELL
        if (sig.kind == SignalKind.WIFI) {
            if (n.startsWith("scanimal")) return DeviceType.BUDDY
            if (hotspotHints.any { n.contains(it) }) return DeviceType.HOTSPOT
            if (n.contains("direct-") && (n.contains("tv") || n.contains("bravia") || n.contains("roku"))) return DeviceType.TV
            if (n.contains("direct-") && listOf("hp", "epson", "canon", "brother").any { n.contains(it) }) return DeviceType.PRINTER
            for ((type, keys) in rules) if (type in setOf(DeviceType.TV, DeviceType.PRINTER, DeviceType.CAR, DeviceType.CAMERA, DeviceType.APPLIANCE, DeviceType.LIGHT) && keys.any { n.contains(it) }) return type
            if (routerHints.any { n.contains(it) } || n.isNotBlank()) return DeviceType.ROUTER
            return DeviceType.ROUTER
        }
        for ((type, keys) in rules) if (keys.any { n.contains(it) }) return type
        // Bluetooth major device class fallback (values from android.bluetooth.BluetoothClass.Device.Major).
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
