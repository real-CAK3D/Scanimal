package com.realcak3d.scanimal.core

/** A collectible sticker for the album. Earned the first time you encounter the thing it represents. */
data class Sticker(val id: String, val name: String, val emoji: String, val category: String, val hint: String)

object Stickers {
    val all: List<Sticker> = buildList {
        FoodFamily.entries.forEach { add(Sticker("food:${it.name}", it.label, it.emoji, "Foods", "Eat a ${it.label.lowercase()}")) }
        Rarity.entries.forEach { add(Sticker("rarity:${it.name}", "${it.label} food", "★", "Foods", "Eat a ${it.label.lowercase()} food")) }
        DeviceType.entries.forEach { add(Sticker("dev:${it.name}", it.label, it.icon, "Devices", "Find a ${it.label.lowercase()}")) }
        listOf("GPS" to "🇺🇸", "GLONASS" to "🇷🇺", "Galileo" to "🇪🇺", "BeiDou" to "🇨🇳", "QZSS" to "🇯🇵", "SBAS" to "📡", "NavIC" to "🇮🇳")
            .forEach { (c, e) -> add(Sticker("sat:$c", "$c satellite", e, "Sky", "See a $c satellite")) }
        Weather.entries.filter { it != Weather.UNKNOWN }.forEach { add(Sticker("wx:${it.name}", it.label, it.icon, "Weather", "Experience ${it.label.lowercase()}")) }
        Biome.entries.forEach { add(Sticker("biome:${it.name}", it.label, it.icon, "Places", "Visit the ${it.label.lowercase()}")) }
        Motion.entries.forEach { add(Sticker("motion:${it.name}", it.label, it.icon, "Travel", "Travel by ${it.label.lowercase()}")) }
        Species.entries.forEach { add(Sticker("sp:${it.name}", it.label, it.emoji, "Species", "Raise a ${it.label.lowercase()}")) }
        add(Sticker("special:firefly", "Firefly night", "🪰", "Special", "Light the torch as a firefly"))
        add(Sticker("special:storm", "Storm chaser", "🌩️", "Special", "Be out when a storm rolls in"))
        add(Sticker("special:mountain", "Mountaineer", "⛰️", "Special", "Climb above 1500 m"))
        add(Sticker("special:nightowl", "Night owl", "🦉", "Special", "Play after midnight"))
        add(Sticker("special:earlybird", "Early bird", "🐓", "Special", "Play before 6 am"))
    }
    val byId = all.associateBy { it.id }
    val categories = all.map { it.category }.distinct()
}
