package com.realcak3d.scanimal.data

import android.content.Context
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.ChatBrain
import com.realcak3d.scanimal.core.Codec
import com.realcak3d.scanimal.core.Engine
import com.realcak3d.scanimal.core.GameData
import com.realcak3d.scanimal.core.GameEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** The single owner of game state. Every change goes through [act], which saves and publishes a fresh snapshot. */
class GameRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("scanimal_save", Context.MODE_PRIVATE)
    private val lock = Any()

    val data: GameData = load()
    val engine = Engine(data)
    val chat = ChatBrain(engine)

    private val _state = MutableStateFlow(snapshot())
    /** An immutable-by-convention deep copy for the UI. Never mutate it. */
    val state: StateFlow<GameData> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GameEvent> = _events.asSharedFlow()

    private val _speech = MutableStateFlow(Speech("", Anim.NONE, 0L))
    val speech: StateFlow<Speech> = _speech.asStateFlow()

    data class Speech(val text: String, val anim: Anim, val at: Long)

    private fun load(): GameData {
        val raw = prefs.getString(KEY, null)
        val g = raw?.let { runCatching { Codec.decode(it) }.getOrNull() } ?: GameData()
        if (g.buddy.eggLaidAt == 0L && !g.buddy.hatched) Engine(g).newEgg(System.currentTimeMillis())
        return g
    }

    private fun snapshot(): GameData = Codec.decode(Codec.encode(data))

    fun act(block: Engine.(now: Long) -> List<GameEvent>): List<GameEvent> {
        val events: List<GameEvent>
        synchronized(lock) {
            events = engine.block(System.currentTimeMillis())
            persist()
        }
        publish(events)
        return events
    }

    /** For chat: returns the reply text and publishes side effects. */
    fun talk(text: String): ChatBrain.Reply {
        val reply: ChatBrain.Reply
        synchronized(lock) {
            reply = chat.respond(text, System.currentTimeMillis())
            persist()
        }
        publish(reply.events.filter { it !is GameEvent.Say })
        if (reply.anim != Anim.NONE) _speech.value = Speech("", reply.anim, System.currentTimeMillis())
        return reply
    }

    fun say(text: String, anim: Anim = Anim.NONE) {
        _speech.value = Speech(text, anim, System.currentTimeMillis())
    }

    fun publish(events: List<GameEvent>) {
        _state.value = snapshot()
        for (e in events) {
            if (e is GameEvent.Say) _speech.value = Speech(e.text, e.anim, System.currentTimeMillis())
            _events.tryEmit(e)
        }
    }

    private fun persist() {
        prefs.edit().putString(KEY, Codec.encode(data)).apply()
    }

    /** Replace every field of the live save with [src], keeping Engine/Chat references valid. */
    private fun replaceWith(src: GameData) {
        data.buddy = src.buddy; data.coins = src.coins; data.ownerName = src.ownerName
        data.inventory.clear(); data.inventory.putAll(src.inventory)
        data.ownedCosmetics.clear(); data.ownedCosmetics.addAll(src.ownedCosmetics)
        data.achievements.clear(); data.achievements.putAll(src.achievements)
        data.counters.clear(); data.counters.putAll(src.counters)
        data.codeLastEaten.clear(); data.codeLastEaten.putAll(src.codeLastEaten)
        data.codeTimesEaten.clear(); data.codeTimesEaten.putAll(src.codeTimesEaten)
        data.seenWifi.clear(); data.seenWifi.addAll(src.seenWifi)
        data.seenBt.clear(); data.seenBt.addAll(src.seenBt)
        data.friendNames.clear(); data.friendNames.addAll(src.friendNames)
        data.constellations.clear(); data.constellations.addAll(src.constellations)
        data.memory.clear(); data.memory.putAll(src.memory)
        data.likes.clear(); data.likes.addAll(src.likes)
        data.journal.clear(); data.journal.addAll(src.journal)
        data.graveyard.clear(); data.graveyard.addAll(src.graveyard)
        data.daily.clear(); data.daily.putAll(src.daily)
        data.settings = src.settings
        data.lastFoodName = src.lastFoodName; data.favoriteFood = src.favoriteFood
        data.bestScores.clear(); data.bestScores.putAll(src.bestScores)
        data.created = src.created; data.myId = src.myId; data.owner = src.owner
        data.devices.clear(); data.devices.putAll(src.devices)
        data.friends.clear(); data.friends.addAll(src.friends)
        data.lastVersionCheck = src.lastVersionCheck
    }

    fun resetEverything() {
        synchronized(lock) {
            replaceWith(GameData())
            engine.newEgg(System.currentTimeMillis())
            persist()
        }
        publish(emptyList())
        say("*a fresh egg appears*")
    }

    /** Restores a backup made with Settings → Export save. */
    fun importSave(text: String): Boolean {
        val i = text.indexOf("SCANIMALSAVE1:")
        if (i < 0) return false
        val decoded = runCatching {
            Codec.decode(String(java.util.Base64.getDecoder().decode(text.substring(i + 14).trim().takeWhile { !it.isWhitespace() })))
        }.getOrNull() ?: return false
        synchronized(lock) {
            replaceWith(decoded)
            data.buddy.lastUpdate = System.currentTimeMillis()
            persist()
        }
        publish(emptyList())
        say("I'm back! Did you miss me? 💾", Anim.LOVE)
        return true
    }

    companion object {
        private const val KEY = "game"
        @Volatile private var instance: GameRepository? = null
        fun get(context: Context): GameRepository =
            instance ?: synchronized(this) { instance ?: GameRepository(context.applicationContext).also { instance = it } }
    }
}
