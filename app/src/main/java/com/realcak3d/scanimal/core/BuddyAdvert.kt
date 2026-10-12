package com.realcak3d.scanimal.core

/**
 * Compact Bluetooth LE advertisement so discoverable Scanimals can spot each other.
 * Fits a legacy advert: flags (3) + service-data header (4) + 24 payload bytes = 31.
 *   [0] magic 0x5C  [1] version  [2..9] buddy id (8 ASCII)  [10] species  [11] sex  [12] hue
 *   [13] pattern  [14] level  [15] accessory index (255 = none)  [16..23] name (UTF-8, ≤ 8 bytes)
 */
object BuddyAdvert {
    const val MAGIC: Byte = 0x5C
    const val VERSION: Byte = 1
    private val cosmetics = Items.all.filter { it.kind == ItemKind.COSMETIC }.map { it.id }

    fun encode(card: Friend): ByteArray {
        val out = ByteArray(16)
        out[0] = MAGIC; out[1] = VERSION
        val id = card.id.padEnd(8, '0').take(8)
        for (i in 0 until 8) out[2 + i] = id[i].code.toByte()
        out[10] = card.species.ordinal.toByte()
        out[11] = card.sex.ordinal.toByte()
        out[12] = (((card.hue % 360f + 360f) % 360f) / 360f * 255f).toInt().toByte()
        out[13] = card.pattern.ordinal.toByte()
        out[14] = card.level.coerceIn(1, 99).toByte()
        out[15] = (cosmetics.indexOf(card.accessory).takeIf { it >= 0 } ?: 255).toByte()
        return out + truncateUtf8(card.name, 8)
    }

    fun decode(data: ByteArray): Friend? {
        if (data.size < 16 || data[0] != MAGIC || data[1] != VERSION) return null
        val id = String(data, 2, 8, Charsets.US_ASCII)
        if (!id.all { it.isLetterOrDigit() }) return null
        val sp = Species.entries.getOrNull(data[10].toInt()) ?: Species.BLOB
        val sex = Sex.entries.getOrNull(data[11].toInt()) ?: Sex.MYSTERY
        val hue = (data[12].toInt() and 0xFF) / 255f * 360f
        val pattern = Pattern.entries.getOrNull(data[13].toInt()) ?: Pattern.PLAIN
        val level = (data[14].toInt() and 0xFF).coerceIn(1, 99)
        val acc = cosmetics.getOrNull(data[15].toInt() and 0xFF)
        val name = if (data.size > 16) String(data, 16, data.size - 16, Charsets.UTF_8).trim { it <= ' ' || it == '�' } else "Buddy"
        return Friend(id, name.ifBlank { "Buddy" }, "", sp, sex, hue, pattern, level, acc, 0L, viaLink = false)
    }

    private fun truncateUtf8(s: String, max: Int): ByteArray {
        var out = ByteArray(0)
        for (ch in s) {
            val b = ch.toString().toByteArray(Charsets.UTF_8)
            if (out.size + b.size > max) break
            out += b
        }
        return out
    }
}
