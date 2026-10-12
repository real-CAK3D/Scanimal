package com.realcak3d.scanimal.core

import kotlin.random.Random

/** Daily missions. A fresh set rolls each calendar day; progress is measured against a baseline snapshot. */
object Quests {
    private data class Template(val id: String, val title: String, val key: String, val amount: Long, val reward: Int)

    private val pool = listOf(
        Template("scan", "Feed %d codes", C.SCANS, 5, 20),
        Template("unique", "Discover %d new codes", C.UNIQUE_CODES, 3, 25),
        Template("walk", "Walk %d meters", C.DISTANCE_M, 500, 25),
        Template("wifi", "Sniff %d Wi-Fi networks", C.WIFI, 8, 20),
        Template("bt", "Meet %d Bluetooth devices", C.BT, 5, 20),
        Template("pet", "Pet your buddy %d times", C.PETS, 10, 15),
        Template("chat", "Chat %d times", C.CHATS, 8, 15),
        Template("game", "Play %d mini-games", C.GAMES, 2, 20),
        Template("clean", "Tidy up %d times", C.CLEANS, 2, 15),
        Template("sat", "Go stargazing %d time(s)", C.STARGAZES, 1, 25),
        Template("connect", "Get %d connection bonus(es)", C.CONNECTIONS, 1, 25),
        Template("devtype", "Find a new kind of device (%d)", C.DEVICE_TYPES, 1, 20),
        Template("sticker", "Earn %d new sticker(s)", C.STICKERS, 1, 25),
        Template("dance", "Have %d dance part(ies)", C.DANCES, 1, 15),
        Template("feed_rare", "Eat %d rare+ food(s)", C.RARE, 1, 30),
    )

    fun rollFor(dayIndex: Long, g: GameData): List<Quest> {
        val r = Random(dayIndex * 2654435761L)
        return pool.shuffled(r).take(3).map { t ->
            val baseline = g.count(t.key)
            Quest(t.id, t.title.format(t.amount), t.key, baseline, t.amount, t.reward)
        }
    }

    fun progress(q: Quest, g: GameData): Long = (g.count(q.counterKey) - q.baseline).coerceIn(0, q.target)
    fun done(q: Quest, g: GameData): Boolean = progress(q, g) >= q.target
}
