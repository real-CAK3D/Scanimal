package com.realcak3d.scanimal.core

import org.json.JSONArray
import org.json.JSONObject

/** Save-file format. Unknown/missing fields fall back to defaults so old saves keep loading. */
object Codec {
    const val VERSION = 1

    private fun obj(map: Map<*, *>): JSONObject {
        val o = JSONObject()
        map.entries.sortedBy { it.key.toString() }.forEach { o.put(it.key.toString(), it.value) }
        return o
    }

    fun encode(g: GameData): String {
        val b = g.buddy
        val s = b.stats
        val o = JSONObject()
        o.put("v", VERSION)
        o.put("buddy", JSONObject().apply {
            put("name", b.name); put("sex", b.sex.name); put("seed", b.seed); put("eggLaidAt", b.eggLaidAt)
            put("bornAt", b.bornAt); put("level", b.level); put("xp", b.xp); put("lives", b.lives); put("weight", b.weight)
            put("hue", b.hue.toDouble()); put("pattern", b.pattern.name); put("personality", b.personality.name)
            put("accessory", b.accessory ?: ""); put("asleep", b.asleep); put("sick", b.sick); put("poops", b.poops)
            put("tantrum", b.tantrum); put("generation", b.generation); put("lastUpdate", b.lastUpdate)
            put("sleptAt", b.sleptAt); put("hoursNeglected", b.hoursNeglected); put("dead", b.dead)
            put("species", b.species.name); put("hatchReadyAt", b.hatchReadyAt); put("outfit", b.outfit ?: "")
            put("stats", JSONObject().apply {
                put("fullness", s.fullness); put("happiness", s.happiness); put("energy", s.energy); put("hygiene", s.hygiene)
                put("health", s.health); put("fitness", s.fitness); put("smarts", s.smarts); put("social", s.social)
                put("discipline", s.discipline)
            })
        })
        o.put("coins", g.coins)
        o.put("ownerName", g.ownerName)
        o.put("inventory", obj(g.inventory))
        o.put("cosmetics", JSONArray(g.ownedCosmetics.toList()))
        o.put("achievements", obj(g.achievements))
        o.put("counters", obj(g.counters))
        o.put("codeLastEaten", obj(g.codeLastEaten))
        o.put("codeTimesEaten", obj(g.codeTimesEaten))
        o.put("seenWifi", JSONArray(g.seenWifi.toList()))
        o.put("seenBt", JSONArray(g.seenBt.toList()))
        o.put("friendNames", JSONArray(g.friendNames))
        o.put("constellations", JSONArray(g.constellations.toList()))
        o.put("memory", obj(g.memory))
        o.put("likes", JSONArray(g.likes))
        o.put("journal", JSONArray().apply { g.journal.forEach { put(JSONObject().put("t", it.time).put("i", it.icon).put("x", it.text)) } })
        o.put("graveyard", JSONArray().apply {
            g.graveyard.forEach {
                put(JSONObject().put("name", it.name).put("sex", it.sex.name).put("gen", it.generation).put("level", it.level)
                    .put("age", it.ageYears).put("diedAt", it.diedAt).put("cause", it.cause))
            }
        })
        o.put("daily", obj(g.daily))
        o.put("settings", JSONObject().apply {
            put("notifications", g.settings.notifications); put("vibration", g.settings.vibration); put("sounds", g.settings.sounds)
            put("online", g.settings.onlineProductLookup); put("autoSleep", g.settings.autoSleepAtNight)
            put("background", g.settings.backgroundMode); put("discoverable", g.settings.discoverable); put("autoUpdate", g.settings.autoUpdate)
            put("metric", g.settings.metric); put("theme", g.settings.theme); put("physics", g.settings.physics)
            put("showVisitors", g.settings.showVisitors); put("chatter", g.settings.ambientChatter); put("mapTrail", g.settings.mapTrail); put("surv", g.settings.surveillanceAlerts); put("accentHue", g.settings.accentHue); put("bigText", g.settings.bigText)
        })
        o.put("lastFoodName", g.lastFoodName)
        o.put("favoriteFood", g.favoriteFood)
        o.put("bestScores", obj(g.bestScores))
        o.put("created", g.created)
        o.put("myId", g.myId)
        o.put("lastVersionCheck", g.lastVersionCheck)
        o.put("owner", JSONObject().apply {
            val w = g.owner
            put("name", w.name); put("birthday", w.birthday); put("color", w.favoriteColor); put("food", w.favoriteFood)
            put("hobby", w.hobby); put("pet", w.pet); put("done", w.done)
        })
        o.put("devices", JSONArray().apply {
            g.devices.values.forEach { d ->
                put(JSONObject().put("id", d.id).put("n", d.name).put("k", d.kind.name).put("t", d.type.name).put("f", d.firstSeen)
                    .put("l", d.lastSeen).put("s", d.timesSeen).put("c", d.timesConnected).put("r", d.bestRssi).put("lc", d.lastConnected).put("v", d.vendor).put("nb", d.notable))
            }
        })
        o.put("friends", JSONArray().apply { g.friends.forEach { put(friendJson(it, withPrivate = true)) } })
        o.put("stickers", JSONArray(g.stickers.toList()))
        o.put("questDay", g.questDay)
        o.put("weather", g.weather.name); o.put("biome", g.biome.name); o.put("motion", g.motion.name)
        o.put("bootAt", g.bootAt); o.put("pressureBaseline", g.pressureBaseline.toDouble())
        o.put("quests", JSONArray().apply {
            g.quests.forEach { put(JSONObject().put("id", it.id).put("t", it.title).put("k", it.counterKey).put("b", it.baseline).put("tg", it.target).put("r", it.reward).put("c", it.claimed)) }
        })
        o.put("discoveries", JSONArray().apply {
            g.discoveries.takeLast(2000).forEach { put(JSONObject().put("la", it.lat).put("lo", it.lon).put("k", it.kind).put("n", it.label).put("t", it.time)) }
        })
        return o.toString()
    }

    fun decode(json: String): GameData {
        val o = JSONObject(json)
        val g = GameData()
        o.optJSONObject("buddy")?.let { j ->
            val st = j.optJSONObject("stats") ?: JSONObject()
            g.buddy = Buddy(
                name = j.optString("name"), sex = enumOr(j.optString("sex"), Sex.MYSTERY), seed = j.optLong("seed"),
                eggLaidAt = j.optLong("eggLaidAt"), bornAt = j.optLong("bornAt"), level = j.optInt("level", 1),
                xp = j.optInt("xp"), lives = j.optInt("lives", 3), weight = j.optDouble("weight", 12.0),
                hue = j.optDouble("hue", 160.0).toFloat(), pattern = enumOr(j.optString("pattern"), Pattern.PLAIN),
                personality = enumOr(j.optString("personality"), Personality.CURIOUS),
                accessory = j.optString("accessory").ifBlank { null }, asleep = j.optBoolean("asleep"),
                sick = j.optBoolean("sick"), poops = j.optInt("poops"), tantrum = j.optBoolean("tantrum"),
                generation = j.optInt("generation", 1), lastUpdate = j.optLong("lastUpdate"), sleptAt = j.optLong("sleptAt"),
                hoursNeglected = j.optDouble("hoursNeglected", 0.0), dead = j.optBoolean("dead"),
                species = enumOr(j.optString("species"), Species.BLOB), hatchReadyAt = j.optLong("hatchReadyAt"),
                outfit = j.optString("outfit").ifBlank { null },
                stats = Stats(
                    st.optDouble("fullness", 70.0), st.optDouble("happiness", 70.0), st.optDouble("energy", 85.0),
                    st.optDouble("hygiene", 85.0), st.optDouble("health", 100.0), st.optDouble("fitness", 20.0),
                    st.optDouble("smarts", 10.0), st.optDouble("social", 15.0), st.optDouble("discipline", 25.0),
                ),
            )
        }
        g.coins = o.optInt("coins", 25)
        g.ownerName = o.optString("ownerName")
        o.optJSONObject("inventory")?.let { g.inventory.clear(); it.keys().forEach { k -> g.inventory[k] = it.optInt(k) } }
        o.optJSONArray("cosmetics")?.let { a -> for (i in 0 until a.length()) g.ownedCosmetics.add(a.getString(i)) }
        o.optJSONObject("achievements")?.let { it.keys().forEach { k -> g.achievements[k] = it.optLong(k) } }
        o.optJSONObject("counters")?.let { it.keys().forEach { k -> g.counters[k] = it.optLong(k) } }
        o.optJSONObject("codeLastEaten")?.let { it.keys().forEach { k -> g.codeLastEaten[k] = it.optLong(k) } }
        o.optJSONObject("codeTimesEaten")?.let { it.keys().forEach { k -> g.codeTimesEaten[k] = it.optInt(k) } }
        o.optJSONArray("seenWifi")?.let { a -> for (i in 0 until a.length()) g.seenWifi.add(a.getString(i)) }
        o.optJSONArray("seenBt")?.let { a -> for (i in 0 until a.length()) g.seenBt.add(a.getString(i)) }
        o.optJSONArray("friendNames")?.let { a -> for (i in 0 until a.length()) g.friendNames.add(a.getString(i)) }
        o.optJSONArray("constellations")?.let { a -> for (i in 0 until a.length()) g.constellations.add(a.getString(i)) }
        o.optJSONObject("memory")?.let { it.keys().forEach { k -> g.memory[k] = it.optString(k) } }
        o.optJSONArray("likes")?.let { a -> for (i in 0 until a.length()) g.likes.add(a.getString(i)) }
        o.optJSONArray("journal")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { g.journal.add(LogEntry(it.optLong("t"), it.optString("i"), it.optString("x"))) }
        }
        o.optJSONArray("graveyard")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let {
                g.graveyard.add(Memorial(it.optString("name"), enumOr(it.optString("sex"), Sex.MYSTERY), it.optInt("gen", 1),
                    it.optInt("level", 1), it.optInt("age"), it.optLong("diedAt"), it.optString("cause")))
            }
        }
        o.optJSONObject("daily")?.let { it.keys().forEach { k -> g.daily[k] = it.optLong(k) } }
        o.optJSONObject("settings")?.let {
            g.settings = Settings(it.optBoolean("notifications", true), it.optBoolean("vibration", true), it.optBoolean("sounds", true),
                it.optBoolean("online", true), it.optBoolean("autoSleep", true), it.optBoolean("background", false),
                it.optBoolean("discoverable", true), it.optBoolean("autoUpdate", true), it.optBoolean("metric", true),
                it.optString("theme", "radar"), it.optBoolean("physics", true), it.optBoolean("showVisitors", true),
                it.optBoolean("chatter", true), it.optBoolean("mapTrail", true), it.optBoolean("surv", true), it.optInt("accentHue", -1), it.optBoolean("bigText", false))
        }
        g.lastFoodName = o.optString("lastFoodName")
        g.favoriteFood = o.optString("favoriteFood")
        o.optJSONObject("bestScores")?.let { it.keys().forEach { k -> g.bestScores[k] = it.optInt(k) } }
        g.created = o.optLong("created")
        g.myId = o.optString("myId")
        g.lastVersionCheck = o.optLong("lastVersionCheck")
        o.optJSONArray("stickers")?.let { a -> for (i in 0 until a.length()) g.stickers.add(a.getString(i)) }
        g.questDay = o.optLong("questDay", -1L)
        g.weather = enumOr(o.optString("weather"), Weather.UNKNOWN)
        g.biome = enumOr(o.optString("biome"), Biome.OUTDOORS)
        g.motion = enumOr(o.optString("motion"), Motion.STILL)
        g.bootAt = o.optLong("bootAt")
        g.pressureBaseline = o.optDouble("pressureBaseline", 0.0).toFloat()
        o.optJSONArray("quests")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { q ->
                g.quests.add(Quest(q.optString("id"), q.optString("t"), q.optString("k"), q.optLong("b"), q.optLong("tg", 1), q.optInt("r"), q.optBoolean("c")))
            }
        }
        o.optJSONArray("discoveries")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { d ->
                g.discoveries.add(Discovery(d.optDouble("la"), d.optDouble("lo"), d.optString("k"), d.optString("n"), d.optLong("t")))
            }
        }
        o.optJSONObject("owner")?.let {
            g.owner = OwnerProfile(it.optString("name"), it.optString("birthday"), it.optString("color"), it.optString("food"),
                it.optString("hobby"), it.optString("pet"), it.optBoolean("done"))
        }
        o.optJSONArray("devices")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { d ->
                val kind = enumOr(d.optString("k"), SignalKind.BLE)
                val rec = DeviceRecord(d.optString("id"), d.optString("n"), kind, enumOr(d.optString("t"), DeviceType.UNKNOWN),
                    d.optLong("f"), d.optLong("l"), d.optInt("s", 1), d.optInt("c"), d.optInt("r", -127), d.optLong("lc"), d.optString("v"), d.optBoolean("nb"))
                g.devices["${kind.name}:${rec.id}"] = rec
            }
        }
        o.optJSONArray("friends")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let { f -> friendFrom(f)?.let { g.friends.add(it) } } }
        return g
    }

    // ---------------------------------------------------------------- friend link codes

    const val LINK_PREFIX = "SCANIMAL1:"
    const val POST_PREFIX = "SCANIMSG1:"

    fun friendJson(f: Friend, withPrivate: Boolean): JSONObject = JSONObject().apply {
        put("id", f.id); put("n", f.name); put("o", f.owner); put("sp", f.species.name); put("sx", f.sex.name)
        put("h", f.hue.toDouble()); put("p", f.pattern.name); put("lv", f.level); put("a", f.accessory ?: "")
        if (withPrivate) {
            put("added", f.addedAt); put("near", f.lastNearby); put("screen", f.onScreen); put("fs", f.friendship); put("link", f.viaLink)
            put("msgs", JSONArray().apply { f.messages.forEach { m -> put(JSONObject().put("me", m.fromMe).put("x", m.text).put("t", m.time)) } })
        }
    }

    fun friendFrom(j: JSONObject): Friend? {
        val id = j.optString("id").ifBlank { return null }
        val f = Friend(
            id = id, name = j.optString("n").take(16).ifBlank { "Friend" }, owner = j.optString("o").take(24),
            species = enumOr(j.optString("sp"), Species.BLOB), sex = enumOr(j.optString("sx"), Sex.MYSTERY),
            hue = j.optDouble("h", 160.0).toFloat(), pattern = enumOr(j.optString("p"), Pattern.PLAIN),
            level = j.optInt("lv", 1).coerceIn(1, 99), accessory = j.optString("a").ifBlank { null }?.takeIf { it in Items.byId },
            addedAt = j.optLong("added"), lastNearby = j.optLong("near"), onScreen = j.optBoolean("screen", true),
            friendship = j.optInt("fs"), viaLink = j.optBoolean("link", true),
        )
        j.optJSONArray("msgs")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.let { m -> f.messages.add(FriendMsg(m.optBoolean("me"), m.optString("x"), m.optLong("t"))) }
        }
        return f
    }

    private fun b64(s: String) = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun unb64(s: String) = String(java.util.Base64.getUrlDecoder().decode(s.trim()), Charsets.UTF_8)

    fun linkCode(card: Friend): String = LINK_PREFIX + b64(friendJson(card, withPrivate = false).toString())

    fun parseLink(code: String): Friend? = runCatching {
        val i = code.indexOf(LINK_PREFIX)
        if (i < 0) return null
        friendFrom(JSONObject(unb64(code.substring(i + LINK_PREFIX.length).takeWhile { !it.isWhitespace() })))
    }.getOrNull()

    /** A "postcard": a message for a friend that can be sent through any app (SMS, email, chat) and imported. */
    fun postcard(fromId: String, fromName: String, toId: String, text: String, time: Long): String =
        POST_PREFIX + b64(JSONObject().put("f", fromId).put("n", fromName).put("to", toId).put("x", text.take(300)).put("t", time).toString())

    data class Postcard(val fromId: String, val fromName: String, val toId: String, val text: String, val time: Long)

    fun parsePostcard(code: String): Postcard? = runCatching {
        val i = code.indexOf(POST_PREFIX)
        if (i < 0) return null
        val j = JSONObject(unb64(code.substring(i + POST_PREFIX.length).takeWhile { !it.isWhitespace() }))
        Postcard(j.getString("f"), j.optString("n"), j.optString("to"), j.optString("x"), j.optLong("t"))
    }.getOrNull()

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)
}
