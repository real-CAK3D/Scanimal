package com.realcak3d.scanimal.core

/**
 * Tiny on-device MAC-prefix (OUI) → vendor table, plus BLE manufacturer-id lookups. Not exhaustive —
 * just enough to label common gear and flag notable kinds (surveillance, mesh/LoRa dev boards).
 * OUIs are the first 3 bytes of a MAC, uppercase, no separators.
 */
object Vendors {
    // Common consumer + infrastructure vendors.
    private val oui = mapOf(
        "FCFBFB" to "Cisco", "00000C" to "Cisco", "001A2B" to "Cisco", "F09FC2" to "Ubiquiti", "24A43C" to "Ubiquiti",
        "B4FBE3" to "Ubiquiti", "802AA8" to "Ubiquiti", "D021F9" to "Ubiquiti", "68D79A" to "Espressif", "240AC4" to "Espressif",
        "3C71BF" to "Espressif", "A4CF12" to "Espressif", "7C9EBD" to "Espressif", "8CAAB5" to "Espressif", "C8C9A3" to "Espressif",
        "DCA632" to "Raspberry Pi", "B827EB" to "Raspberry Pi", "E45F01" to "Raspberry Pi", "D83ADD" to "Raspberry Pi",
        "F0182B" to "Apple", "3C0754" to "Apple", "A85C2C" to "Apple", "ACBC32" to "Apple", "DC2B2A" to "Apple", "F4F15A" to "Apple",
        "001632" to "Samsung", "5CF938" to "Samsung", "8425DB" to "Samsung", "E8508B" to "Samsung",
        "D0037F" to "TP-Link", "3C52A1" to "TP-Link", "50C7BF" to "TP-Link", "001018" to "Broadcom",
        "00904C" to "Epson", "B0702D" to "Amazon", "FCA667" to "Amazon", "44650D" to "Amazon", "68B6B3" to "Amazon",
        "001788" to "Philips Hue", "ECB5FA" to "Philips Hue", "D073D5" to "LIFX", "B0F893" to "Govee",
        "D8A01D" to "Espressif", "40F520" to "Google", "F4F5D8" to "Google", "1CF29A" to "Google", "54604D" to "Google",
        "0017880" to "Philips", "001124" to "Fujitsu", "0CB2B7" to "Realtek",
    )

    // BLE SIG company identifiers (manufacturer id in advertisements).
    private val mfg = mapOf(
        0x004C to "Apple", 0x0006 to "Microsoft", 0x00E0 to "Google", 0x0075 to "Samsung", 0x0157 to "Huawei",
        0x038F to "Xiaomi", 0x0499 to "Ruuvi", 0x0059 to "Nordic", 0x05A7 to "Sonos", 0x001D to "Qualcomm",
        0x0171 to "Amazon", 0x0087 to "Garmin", 0x00D2 to "Fitbit", 0x022B to "Tile", 0x0A98 to "Meshtastic",
    )

    // Vendors whose gear is worth flagging as "notable".
    private val meshVendors = setOf("Espressif", "Raspberry Pi", "Nordic")

    fun forMac(mac: String?): String {
        val m = mac?.replace(":", "")?.replace("-", "")?.uppercase() ?: return ""
        if (m.length < 6) return ""
        return oui[m.substring(0, 6)] ?: ""
    }

    fun forMfg(id: Int): String = mfg[id] ?: ""

    fun isMeshVendor(v: String) = v in meshVendors
}
