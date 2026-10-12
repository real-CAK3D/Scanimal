package com.realcak3d.scanimal.core

enum class StickerTier(val label: String, val argb: Long) {
    COMMON("Common", 0xFFB0BEC5), UNCOMMON("Uncommon", 0xFF66BB6A), RARE("Rare", 0xFF42A5F5),
    EPIC("Epic", 0xFFAB47BC), LEGENDARY("Legendary", 0xFFFFCA28);
}

/** A collectible sticker. [set] groups stickers into album pages; completing a set grants a trophy. */
data class Sticker(val id: String, val name: String, val emoji: String, val set: String, val tier: StickerTier, val hint: String)

/** A completable album set with a reward for 100%. */
data class StickerSet(val name: String, val trophyId: String, val trophyEmoji: String, val reward: Int)

object Stickers {
    val all: List<Sticker> = buildList {
        FoodFamily.entries.forEach { add(Sticker("food:${it.name}", it.label, it.emoji, "Foods", StickerTier.COMMON, "Eat a ${it.label.lowercase()}")) }
        Rarity.entries.forEach { add(Sticker("rarity:${it.name}", "${it.label} meal", "★", "Rarities", tierForRarity(it), "Eat a ${it.label.lowercase()} food")) }
        DeviceType.entries.forEach { add(Sticker("dev:${it.name}", it.label, it.icon, "Devices", tierForDevice(it), "Find a ${it.label.lowercase()}")) }
        listOf("GPS" to "🇺🇸", "GLONASS" to "🇷🇺", "Galileo" to "🇪🇺", "BeiDou" to "🇨🇳", "QZSS" to "🇯🇵", "SBAS" to "📡", "NavIC" to "🇮🇳")
            .forEach { (c, e) -> add(Sticker("sat:$c", "$c satellite", e, "Sky", StickerTier.RARE, "See a $c satellite")) }
        Weather.entries.filter { it != Weather.UNKNOWN }.forEach { add(Sticker("wx:${it.name}", it.label, it.icon, "Weather", StickerTier.UNCOMMON, "Experience ${it.label.lowercase()}")) }
        Biome.entries.forEach { add(Sticker("biome:${it.name}", it.label, it.icon, "Places", StickerTier.UNCOMMON, "Visit the ${it.label.lowercase()}")) }
        Motion.entries.forEach { add(Sticker("motion:${it.name}", it.label, it.icon, "Travel", StickerTier.COMMON, "Travel by ${it.label.lowercase()}")) }
        Species.entries.forEach { add(Sticker("sp:${it.name}", it.label, it.emoji, "Species", StickerTier.EPIC, "Raise a ${it.label.lowercase()}")) }
        // Level milestones
        listOf(5, 10, 25, 50, 99).forEach { lv -> add(Sticker("lvl:$lv", "Level $lv", "🎚️", "Milestones", if (lv >= 50) StickerTier.LEGENDARY else StickerTier.RARE, "Reach level $lv")) }
        // Distance milestones (meters)
        listOf(1_000L to "1 km", 10_000L to "10 km", 42_195L to "Marathon", 100_000L to "100 km").forEach { (m, lbl) ->
            add(Sticker("dist:$m", lbl, "🥾", "Milestones", StickerTier.RARE, "Travel $lbl"))
        }
        // Specials
        add(Sticker("special:firefly", "Firefly night", "🪰", "Specials", StickerTier.RARE, "Light the torch as a firefly"))
        add(Sticker("special:storm", "Storm chaser", "🌩️", "Specials", StickerTier.EPIC, "Be out when a storm rolls in"))
        add(Sticker("special:mountain", "Mountaineer", "⛰️", "Specials", StickerTier.EPIC, "Climb above 1500 m"))
        add(Sticker("special:nightowl", "Night owl", "🦉", "Specials", StickerTier.UNCOMMON, "Play after midnight"))
        add(Sticker("special:earlybird", "Early bird", "🐓", "Specials", StickerTier.UNCOMMON, "Play before 6 am"))
        add(Sticker("special:surveillance", "Watched", "📷", "Specials", StickerTier.RARE, "Spot a surveillance camera"))
        add(Sticker("special:lora", "Long range", "📡", "Specials", StickerTier.EPIC, "Find a LoRa node"))
        add(Sticker("special:firstfriend", "First friend", "🤝", "Specials", StickerTier.RARE, "Make a buddy friend"))
        add(Sticker("special:elder", "Wise elder", "🧓", "Specials", StickerTier.LEGENDARY, "Raise a buddy to Elder age"))
    }
    val byId = all.associateBy { it.id }
    val sets = all.map { it.set }.distinct()

    /** Sets that grant a trophy when fully collected (excludes the Trophies set itself). */
    val completable: List<StickerSet> = listOf(
        StickerSet("Foods", "trophy:Foods", "🍱", 60),
        StickerSet("Devices", "trophy:Devices", "🗄️", 150),
        StickerSet("Sky", "trophy:Sky", "🌌", 80),
        StickerSet("Weather", "trophy:Weather", "🌦️", 50),
        StickerSet("Places", "trophy:Places", "🗺️", 60),
        StickerSet("Travel", "trophy:Travel", "🧳", 50),
        StickerSet("Species", "trophy:Species", "🧬", 200),
        StickerSet("Specials", "trophy:Specials", "🎀", 120),
        StickerSet("Milestones", "trophy:Milestones", "🏆", 150),
    )
    private val trophyIds = completable.map { it.trophyId }.toSet()

    /** Stickers that count toward completing [set] (trophies don't count). */
    fun setMembers(set: String) = all.filter { it.set == set && it.id !in trophyIds }

    private fun tierForRarity(r: Rarity) = when (r) {
        Rarity.COMMON -> StickerTier.COMMON; Rarity.UNCOMMON -> StickerTier.UNCOMMON
        Rarity.RARE -> StickerTier.RARE; Rarity.EPIC -> StickerTier.EPIC; Rarity.LEGENDARY -> StickerTier.LEGENDARY
    }
    private fun tierForDevice(t: DeviceType) = when (t) {
        DeviceType.SURVEILLANCE, DeviceType.ALPR, DeviceType.LORA -> StickerTier.EPIC
        DeviceType.MESH, DeviceType.THREAD, DeviceType.CELL -> StickerTier.RARE
        else -> StickerTier.COMMON
    }
}
