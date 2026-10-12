package com.realcak3d.scanimal.core

import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Visual hints for the UI's animations. */
enum class Anim { NONE, EAT, HAPPY, LOVE, SAD, SICK, SLEEP, WAKE, DIZZY, DANCE, LEVEL_UP, CLEAN, SPARKLE, SHOCK, HATCH, DIE, ANGRY }

sealed class GameEvent {
    data class Say(val text: String, val anim: Anim = Anim.NONE) : GameEvent()
    data class Unlocked(val achievement: Achievement) : GameEvent()
    data class LevelUp(val level: Int) : GameEvent()
    data class Ate(val food: Food, val multiplier: Double) : GameEvent()
    data class Died(val cause: String, val livesLeft: Int) : GameEvent()
    object GameOver : GameEvent()
    data class Coins(val amount: Int) : GameEvent()
}

/**
 * All game rules. Mutates [g] in place and returns events for the UI.
 * Times are epoch millis, passed in so tests can control the clock.
 */
class Engine(val g: GameData, private val rnd: Random = Random.Default) {

    private val b get() = g.buddy
    private val s get() = g.buddy.stats

    fun ctx(now: Long, food: String = "snack", thing: String = "thing", n: Int = 0) = Phrases.Ctx(
        name = b.name.ifBlank { "Buddy" }, owner = g.ownerName, stage = b.stage(now),
        food = food, thing = thing, n = n, happiness = s.happiness, mature = g.settings.matureLanguage,
    )

    fun say(topic: Topic, now: Long, anim: Anim = Anim.NONE, food: String = "snack", thing: String = "thing", n: Int = 0) =
        GameEvent.Say(Phrases.say(topic, ctx(now, food, thing, n), rnd), anim)

    // ---------------------------------------------------------------- lifecycle

    fun newEgg(now: Long, keepGeneration: Boolean = false) {
        val gen = if (keepGeneration) b.generation + 1 else 1
        val seed = rnd.nextLong()
        val sr = Random(seed)
        g.buddy = Buddy(
            seed = seed, eggLaidAt = now, lastUpdate = now, generation = gen,
            hue = listOf(150f, 175f, 200f, 220f, 265f, 300f, 330f, 20f, 45f).random(sr),
            pattern = Pattern.entries.random(sr),
            personality = Personality.entries.random(sr),
        )
        if (g.myId.isBlank()) g.myId = (1..8).map { "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ"[rnd.nextInt(34)] }.joinToString("")
    }

    /** Name, sex and species are chosen; the egg now incubates for [incubationMs] (warming speeds it up). */
    fun layEgg(name: String, sex: Sex, species: Species, now: Long, incubationMs: Long = DEFAULT_INCUBATION): List<GameEvent> {
        if (b.hatched) return emptyList()
        b.name = name.trim().take(16).ifBlank { "Blip" }
        b.sex = sex
        b.species = species
        val sr = Random(b.seed)
        b.hue = (species.defaultHue + sr.nextInt(-25, 26) + 360f) % 360f
        b.eggLaidAt = now
        b.hatchReadyAt = now + incubationMs
        g.log(now, "🥚", "${b.name}'s ${species.label.lowercase()} egg is incubating.")
        return listOf(GameEvent.Say("*the egg wiggles at the sound of \"${b.name}\"*", Anim.SHOCK))
    }

    /** Taps, scans and body heat shave time off incubation. Returns hatch events if it's ready. */
    fun warmEgg(now: Long, ms: Long): List<GameEvent> {
        if (b.hatched || b.hatchReadyAt == 0L) return emptyList()
        g.bump(C.HATCH_WARMS)
        b.hatchReadyAt = max(now, b.hatchReadyAt - ms)
        return if (now >= b.hatchReadyAt) hatch(b.name, b.sex, now) else emptyList()
    }

    fun eggMinutesLeft(now: Long): Int = if (b.hatchReadyAt == 0L) -1 else ((b.hatchReadyAt - now).coerceAtLeast(0) / 60_000L).toInt()

    fun hatch(name: String, sex: Sex, now: Long): List<GameEvent> {
        if (b.eggLaidAt == 0L) newEgg(now)
        b.name = name.trim().take(16).ifBlank { "Blip" }
        b.sex = sex
        b.bornAt = now
        b.hatchReadyAt = min(b.hatchReadyAt, now)
        b.lastUpdate = now
        b.stats = Stats()
        if (g.created == 0L) g.created = now
        g.log(now, "🐣", "${b.name} hatched! Generation ${b.generation}.")
        val ev = mutableListOf<GameEvent>(say(Topic.HATCH, now, Anim.HATCH))
        award("sp:${b.species.name}", now, ev)
        if (g.bootAt == 0L) g.bootAt = now
        rollQuestsIfNeeded(now)
        ev += checkAchievements(now)
        return ev
    }

    // ---------------------------------------------------------------- time

    /** Advance the simulation to [now]. Safe to call often; does nothing for tiny gaps. */
    fun tick(now: Long, offline: Boolean = false): List<GameEvent> {
        if (!b.hatched && !b.dead && b.hatchReadyAt > 0 && now >= b.hatchReadyAt) {
            b.lastUpdate = now
            return hatch(b.name, b.sex, now)
        }
        if (!b.hatched || b.dead) { b.lastUpdate = now; return emptyList() }
        val events = mutableListOf<GameEvent>()
        var last = b.lastUpdate.takeIf { it > 0 } ?: now
        if (now - last > 30L * 86_400_000L) last = now - 30L * 86_400_000L // ignore absurd gaps
        val hourMs = 3_600_000.0
        val startAge = b.ageYears(last)
        val longAbsence = (now - last) > 48 * 3_600_000L
        while (now - last >= 1_000) {
            val stepMs = min(now - last, 10 * 60_000L)
            val h = stepMs / hourMs
            step(h, last + stepMs, events, offline && !longAbsence)
            last += stepMs
            if (b.dead) break
        }
        b.lastUpdate = now
        val newAge = b.ageYears(now)
        if (newAge > startAge && !b.dead) {
            events += say(Topic.BIRTHDAY, now, Anim.SPARKLE, n = newAge)
            g.log(now, "🎂", "${b.name} turned $newAge!")
            grantCoins(10 * newAge, events)
        }
        if (isOwnerBirthday(now) && onceEvery("owner_bday_" + Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR), now, 1)) {
            events += GameEvent.Say("🎉 HAPPY BIRTHDAY, ${g.owner.name.ifBlank { "friend" }}!!! I made you a cake out of pixels! 🎂", Anim.LOVE)
            s.happiness += 20; s.clampAll()
            grantCoins(50, events)
        }
        events += milestoneStickers(now)
        events += checkAchievements(now)
        return events
    }

    fun isOwnerBirthday(now: Long): Boolean {
        val bd = g.owner.birthday
        if (bd.length != 5) return false
        val c = Calendar.getInstance().apply { timeInMillis = now }
        return bd == "%02d-%02d".format(c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun step(h: Double, at: Long, events: MutableList<GameEvent>, protectOffline: Boolean) {
        val p = b.personality
        val stageMult = when (b.stage(at)) { Stage.BABY -> 1.25; Stage.ELDER -> 0.85; else -> 1.0 }
        val night = isNight(at)
        if (b.asleep) {
            s.energy += 16 * h
            s.fullness -= 2.5 * h * stageMult
            s.hygiene -= 1.0 * h
            if (s.energy >= 100 && !(night && g.settings.autoSleepAtNight)) {
                b.asleep = false
                events += say(Topic.WAKE, at, Anim.WAKE)
                g.log(at, "☀️", "${b.name} woke up.")
            }
        } else {
            s.fullness -= (if (p == Personality.FOODIE) 7.0 else 5.5) * h * stageMult
            s.energy -= (if (p == Personality.LAZY) 6.0 else 4.5) * h * (if (night) 1.4 else 1.0)
            s.hygiene -= 3.0 * h
            s.happiness -= (if (p == Personality.SOCIAL) 4.5 else 3.5) * h
            s.social -= 1.5 * h
            if (s.energy <= 3) {
                b.asleep = true; b.sleptAt = at
                events += say(Topic.GOODNIGHT, at, Anim.SLEEP)
                g.log(at, "😴", "${b.name} passed out from exhaustion.")
            }
        }
        // Poop roughly every 4-5 hours awake, more when well fed.
        if (!b.asleep && s.fullness > 20 && rnd.nextDouble() < 0.22 * h * (s.fullness / 70.0)) {
            b.poops = min(4, b.poops + 1)
            s.hygiene -= 8
            events += say(Topic.POOPED, at)
        }
        s.hygiene -= b.poops * 1.5 * h
        // Sickness
        val risk = (if (s.hygiene < 25) 0.10 else 0.0) + (if (b.poops >= 3) 0.10 else 0.0) +
            (if (s.fullness < 10) 0.08 else 0.0) + (if (b.weight > 40) 0.05 else 0.0)
        if (!b.sick && risk > 0 && rnd.nextDouble() < risk * h) {
            b.sick = true
            events += say(Topic.SICK, at, Anim.SICK)
            g.log(at, "🤒", "${b.name} got sick.")
        }
        // Health
        var dmg = 0.0
        if (s.fullness <= 0.5) dmg += 6
        if (s.hygiene <= 5) dmg += 2
        if (b.sick) dmg += 3
        if (s.happiness <= 2) dmg += 1
        if (dmg > 0) {
            s.health -= dmg * h
            b.hoursNeglected += h
        } else {
            s.health += 2 * h
            b.hoursNeglected = max(0.0, b.hoursNeglected - h)
        }
        // Tantrums: sometimes a buddy just wants attention. Scolding builds discipline.
        if (!b.tantrum && !b.asleep && rnd.nextDouble() < 0.05 * h * (1 - s.discipline / 120.0)) {
            b.tantrum = true
            events += say(Topic.TANTRUM, at, Anim.ANGRY)
        }
        s.clampAll()
        if (protectOffline && s.health < 10) s.health = 10.0
        if (s.health <= 0) die(at, causeOfDeath(), events)
    }

    private fun causeOfDeath() = when {
        s.fullness <= 1 -> "starvation"
        b.sick -> "illness"
        s.hygiene <= 5 -> "a very messy room"
        else -> "a broken heart"
    }

    private fun die(now: Long, cause: String, events: MutableList<GameEvent>) {
        g.bump(C.DEATHS)
        b.lives -= 1
        if (b.lives > 0) {
            g.log(now, "💔", "${b.name} fainted from $cause and used an extra life. ${b.lives} left.")
            events += GameEvent.Died(cause, b.lives)
            b.stats = Stats(fullness = 50.0, happiness = 40.0, energy = 60.0, hygiene = 70.0, health = 60.0,
                fitness = s.fitness, smarts = s.smarts, social = s.social, discipline = s.discipline)
            b.sick = false; b.poops = 0; b.asleep = false; b.tantrum = false
            events += say(Topic.REVIVED, now, Anim.SPARKLE)
        } else {
            b.dead = true
            g.graveyard.add(0, Memorial(b.name, b.sex, b.generation, b.level, b.ageYears(now), now, cause))
            g.log(now, "🪦", "${b.name} has gone to the great server in the sky ($cause).")
            events += GameEvent.Died(cause, 0)
            events += GameEvent.GameOver
        }
    }

    fun startNextGeneration(now: Long) {
        newEgg(now, keepGeneration = true)
        g.log(now, "🥚", "A new egg appeared. Generation ${b.generation}.")
    }

    // ---------------------------------------------------------------- care actions

    fun feed(food: Food, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (!b.hatched) return ev
        if (b.asleep) { ev += say(Topic.ASLEEP_TAP, now, Anim.SLEEP); return ev }
        val lastEaten = g.codeLastEaten[food.key]
        val todayCount = if (lastEaten != null && now - lastEaten < 20 * 3_600_000L) (g.codeTimesEaten[food.key] ?: 0) else 0
        if (food.family != FoodFamily.PANTRY && todayCount >= 2) { ev += say(Topic.REFUSE_FOOD, now, Anim.ANGRY, food = food.name); return ev }
        if (s.fullness >= 96) { ev += say(Topic.FULL, now); return ev }

        val firstEver = food.family != FoodFamily.PANTRY && food.key !in g.codeLastEaten
        val mult = if (food.family == FoodFamily.PANTRY) 1.0 else when (todayCount) { 0 -> 1.0; else -> 0.35 }
        val wasOverfull = s.fullness > 85
        s.fullness += food.fullness * mult
        s.happiness += food.happiness * mult
        s.energy += food.energy * mult
        s.health += food.health * (if (food.health < 0) 1.0 else mult)
        s.hygiene += food.hygiene
        b.weight = max(5.0, b.weight + food.weight)
        if (wasOverfull) { b.weight += 0.6; if (rnd.nextInt(4) == 0) { b.sick = true; ev += say(Topic.SICK, now, Anim.SICK) } }
        if (food.trait == FoodTrait.SPOILED && rnd.nextInt(2) == 0) b.sick = true
        s.clampAll()

        g.codeLastEaten[food.key] = now
        g.codeTimesEaten[food.key] = todayCount + 1
        g.lastFoodName = food.name
        g.bump(C.FEEDS)
        if (food.family != FoodFamily.PANTRY) {
            g.bump(C.SCANS)
            if (firstEver) g.bump(C.UNIQUE_CODES)
            when (food.family) {
                FoodFamily.GROCERY -> g.bump(C.PRODUCT)
                FoodFamily.INDUSTRIAL -> g.bump(C.INDUSTRIAL)
                FoodFamily.EXOTIC -> g.bump(C.EXOTIC)
                FoodFamily.ELECTRIC -> g.bump(C.NFC)
                FoodFamily.DATA, FoodFamily.WEB, FoodFamily.SIGNAL, FoodFamily.SOCIAL, FoodFamily.MAP -> g.bump(C.QR)
                else -> {}
            }
            when (food.rarity) {
                Rarity.LEGENDARY -> g.bump(C.LEGENDARY)
                Rarity.EPIC -> g.bump(C.EPIC)
                Rarity.RARE -> g.bump(C.RARE)
                else -> {}
            }
            if (food.rarity.stars >= 3) g.favoriteFood = food.name
        }
        if (food.family != FoodFamily.PANTRY) {
            award("food:${food.family.name}", now, ev)
            award("rarity:${food.rarity.name}", now, ev)
        }
        ev += GameEvent.Ate(food, mult)
        val topic = when {
            food.trait == FoodTrait.SPOILED -> Topic.ATE_SPOILED
            food.rarity == Rarity.LEGENDARY -> Topic.ATE_LEGENDARY
            food.rarity.stars >= 3 -> Topic.ATE_RARE
            todayCount > 0 -> Topic.ATE_REPEAT
            food.family == FoodFamily.ELECTRIC -> Topic.NFC
            food.trait == FoodTrait.SPICY -> Topic.ATE_SPICY
            food.trait == FoodTrait.JUNK -> Topic.ATE_JUNK
            food.trait == FoodTrait.HEALTHY -> Topic.ATE_HEALTHY
            else -> Topic.ATE
        }
        ev += say(topic, now, if (food.trait == FoodTrait.SPOILED) Anim.SICK else Anim.EAT, food = food.name)
        val xp = (food.xp * mult * (if (firstEver) 1.5 else 1.0) * (if (b.personality == Personality.FOODIE) 1.2 else 1.0)).toInt()
        ev += gainXp(xp, now)
        if (firstEver && food.coins > 0) grantCoins(food.coins, ev)
        g.log(now, food.family.emoji, "Ate ${food.name} (${food.rarity.label}${if (food.trait != FoodTrait.NONE) ", ${food.trait.label}" else ""}).")
        ev += checkAchievements(now)
        return ev
    }

    fun pet(now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (!b.hatched) return listOf(GameEvent.Say("*the egg wobbles happily*", Anim.HAPPY))
        if (b.asleep) return listOf(say(Topic.ASLEEP_TAP, now, Anim.SLEEP))
        g.bump(C.PETS)
        val recent = g.daily["pet_window"] ?: 0L
        val streak = if (now - recent < 60_000) (g.daily["pet_streak"] ?: 0L) + 1 else 1L
        g.daily["pet_window"] = now; g.daily["pet_streak"] = streak
        if (streak > 12) {
            s.happiness -= 1
            ev += say(Topic.OVERPETTED, now, Anim.DIZZY)
        } else {
            s.happiness += 3; s.social += 1.5
            if (b.tantrum && streak > 4) b.tantrum = false
            ev += say(Topic.PETTED, now, Anim.LOVE)
            if (streak % 4 == 1L) ev += gainXp(1, now)
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun clean(now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (!b.hatched) return ev
        val hadPoop = b.poops > 0
        b.poops = 0
        if (g.takeItem("soap")) {
            s.hygiene = 100.0; s.happiness += 4
        } else {
            s.hygiene += if (hadPoop) 25.0 else 12.0
        }
        g.bump(C.CLEANS)
        s.clampAll()
        ev += say(Topic.CLEANED, now, Anim.CLEAN)
        ev += gainXp(2, now)
        g.log(now, "🧽", "Cleaned up ${b.name}.")
        ev += checkAchievements(now)
        return ev
    }

    fun medicine(now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (!g.takeItem("medicine")) return listOf(GameEvent.Say("We're out of medicine! The shop has some.", Anim.SAD))
        b.sick = false
        s.health = min(100.0, s.health + 35); s.happiness -= 4
        g.bump(C.MEDS)
        s.clampAll()
        ev += say(Topic.CURED, now, Anim.SPARKLE)
        g.log(now, "💊", "Gave ${b.name} medicine.")
        ev += checkAchievements(now)
        return ev
    }

    fun toggleSleep(now: Long, darkRoom: Boolean = false): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (!b.hatched) return ev
        if (b.asleep) {
            b.asleep = false
            if (s.energy < 50) s.happiness -= 6
            ev += say(Topic.WAKE, now, Anim.WAKE)
        } else {
            if (s.energy > 85 && !isNight(now)) {
                return listOf(GameEvent.Say("I'm not sleepy! Let's play!", Anim.ANGRY))
            }
            b.asleep = true; b.sleptAt = now
            g.bump(C.SLEEPS)
            if (darkRoom) { g.bump(C.DARK_NAPS); s.happiness += 4 }
            ev += say(Topic.GOODNIGHT, now, Anim.SLEEP)
            g.log(now, "🌙", "Tucked ${b.name} into bed.")
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun scold(now: Long): List<GameEvent> {
        if (!b.hatched) return emptyList()
        return if (b.tantrum) {
            b.tantrum = false; s.discipline += 12; s.happiness -= 3; s.clampAll()
            listOf(say(Topic.SCOLDED, now, Anim.SAD))
        } else {
            s.happiness -= 8; s.clampAll()
            listOf(GameEvent.Say("Hey! I didn't do anything! ${Phrases.sadEmotes.random(rnd)}", Anim.SAD))
        }
    }

    fun praise(now: Long): List<GameEvent> {
        if (!b.hatched) return emptyList()
        if (b.tantrum) { s.discipline -= 4; s.clampAll(); return listOf(GameEvent.Say("Hehe, I can get away with anything!", Anim.HAPPY)) }
        s.happiness += 5; s.discipline += 2; s.clampAll()
        return listOf(say(Topic.PRAISED, now, Anim.HAPPY))
    }

    // ---------------------------------------------------------------- shop & items

    fun buy(itemId: String, now: Long): List<GameEvent> {
        val item = Items.byId[itemId] ?: return emptyList()
        if (b.level < item.minLevel) return listOf(GameEvent.Say("That unlocks at level ${item.minLevel}!", Anim.NONE))
        if (item.kind != ItemKind.CONSUMABLE && itemId in g.ownedCosmetics) return listOf(GameEvent.Say("We already own that!", Anim.NONE))
        if (g.coins < item.price) return listOf(say(Topic.BROKE, now, Anim.SAD))
        g.coins -= item.price
        g.bump(C.PURCHASES)
        if (item.kind == ItemKind.CONSUMABLE) g.addItem(itemId) else g.ownedCosmetics.add(itemId)
        g.log(now, item.icon, "Bought ${item.name} for ${item.price} coins.")
        return listOf(say(Topic.BOUGHT, now, Anim.HAPPY, thing = item.name)) + checkAchievements(now)
    }

    fun useItem(itemId: String, now: Long): List<GameEvent> {
        val item = Items.byId[itemId] ?: return emptyList()
        if (item.kind == ItemKind.OUTFIT) {
            b.outfit = if (b.outfit == itemId) null else itemId
            return listOf(GameEvent.Say(if (b.outfit == null) "Outfit off!" else "Fashion show! ${Phrases.emotes.random(rnd)}", Anim.SPARKLE))
        }
        if (item.kind == ItemKind.COSMETIC) {
            b.accessory = if (b.accessory == itemId) null else itemId
            return listOf(GameEvent.Say(if (b.accessory == null) "Back to my natural look!" else "How do I look? ${Phrases.emotes.random(rnd)}", Anim.SPARKLE))
        }
        if (item.kind == ItemKind.PAINT) {
            val hue = Items.paintHue[itemId] ?: return emptyList()
            b.hue = hue
            return listOf(GameEvent.Say("Ooh, a fresh coat of paint!", Anim.SPARKLE))
        }
        if (!b.hatched) return listOf(GameEvent.Say("Wait until I hatch!", Anim.NONE))
        if (itemId == "kibble") {
            if (!g.has("kibble")) return emptyList()
            val ev = feed(FoodFactory.kibble(), now)
            if (ev.any { it is GameEvent.Ate }) g.takeItem("kibble")
            return ev
        }
        if (itemId == "medicine") return medicine(now)
        if (itemId == "soap") { return if (g.has("soap")) clean(now) else emptyList() }
        if (!g.takeItem(itemId)) return emptyList()
        val ev = mutableListOf<GameEvent>()
        when (itemId) {
            "vitamin" -> { s.health += 15; if (rnd.nextInt(3) == 0) b.sick = false; ev += GameEvent.Say("Vitamins! I feel sturdier.", Anim.SPARKLE) }
            "soda" -> { s.energy += 30; b.asleep = false; ev += GameEvent.Say("BZZZT! Energy!!", Anim.SHOCK) }
            "cake" -> { s.happiness += 30; s.fullness += 20; b.weight += 1.5; ev += GameEvent.Say("CAKE! Best day ever!", Anim.LOVE) }
            "ball" -> { s.happiness += 20; s.fitness += 6; s.energy -= 8; b.weight -= 0.4; ev += GameEvent.Say("Fetch! Again! Again!", Anim.HAPPY) }
            "book" -> { s.smarts += 10; s.happiness += 10; ev += GameEvent.Say(Phrases.randomStory(b.name, rnd), Anim.HAPPY) }
            "xp_chip" -> { ev += GameEvent.Say("*installs XP chip* Whoa!", Anim.SPARKLE); ev += gainXp(75, now) }
            "one_up" -> {
                if (b.lives >= 5) { g.addItem("one_up"); return listOf(GameEvent.Say("I already have the max 5 lives!", Anim.NONE)) }
                b.lives += 1; ev += GameEvent.Say("An extra life! I feel invincible! ❤️", Anim.SPARKLE)
            }
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    // ---------------------------------------------------------------- play

    /** [score] is game-specific; [won] decides the reaction. */
    fun gameFinished(game: String, score: Int, won: Boolean, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        g.bump(C.GAMES)
        if (score > (g.bestScores[game] ?: 0)) g.bestScores[game] = score
        if (b.hatched && !b.asleep) {
            s.happiness += if (won) 14 else 7
            s.energy -= 7; s.fitness += 2; s.smarts += 1; s.fullness -= 3
            b.weight = max(5.0, b.weight - 0.2)
        }
        if (won) g.bump(C.GAME_WINS)
        val coins = (score / 4).coerceIn(1, 40) + if (won) 5 else 0
        grantCoins(coins, ev)
        ev += say(if (won) Topic.GAME_WIN else Topic.GAME_LOSE, now, if (won) Anim.HAPPY else Anim.SAD)
        ev += gainXp(if (won) 12 else 5, now)
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun chatted(now: Long, xpBonus: Int = 0): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        g.bump(C.CHATS)
        if (b.hatched) {
            s.social += 1.2; s.smarts += 0.4; s.happiness += 0.8
            s.clampAll()
            if (g.count(C.CHATS) % 3 == 0L) ev += gainXp((2 + xpBonus) * (if (b.accessory == "glasses") 2 else 1), now)
        }
        ev += checkAchievements(now)
        return ev
    }

    // ---------------------------------------------------------------- sensors & signals

    fun onWifiScan(results: List<SignalInfo>, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (results.isEmpty()) {
            if (onceEvery("wifi_none", now, 30 * 60_000L)) ev += say(Topic.WIFI_NONE, now)
            return ev
        }
        ev += recordDevices(results, now)
        var fresh = 0
        for (r in results) {
            if (g.seenWifi.add(r.id)) {
                fresh++
                g.bump(C.WIFI)
                if (!r.secured) g.bump(C.WIFI_OPEN)
                if (r.frequencyMhz in 4900..5899) g.bump(C.WIFI_5G)
                if (r.frequencyMhz >= 5925) g.bump(C.WIFI_6G)
            }
        }
        if (fresh > 0 && b.hatched) {
            s.happiness += min(8.0, fresh * 1.0)
            if (b.personality == Personality.CURIOUS) s.happiness += 2
            ev += gainXp(min(30, fresh * 2), now)
            ev += say(Topic.WIFI_FOUND, now, Anim.SPARKLE, n = results.size)
            if (results.any { !it.secured } && rnd.nextInt(3) == 0) ev += say(Topic.WIFI_OPEN, now)
            g.log(now, "📶", "Sniffed $fresh new Wi-Fi network${if (fresh == 1) "" else "s"} (${results.size} in range).")
        }
        // Strongest network = a little energy snack, once per hour.
        val strongest = results.maxByOrNull { it.rssi }
        if (strongest != null && strongest.rssi > -55 && b.hatched && !b.asleep && onceEvery("wifi_snack", now, 3_600_000L)) {
            s.energy += 6; s.fullness += 3
            ev += GameEvent.Say("*slurps packets from ${strongest.name.ifBlank { "a hidden network" }}* Tasty!", Anim.EAT)
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun onBluetooth(devices: List<SignalInfo>, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        ev += recordDevices(devices, now)
        val freshNamed = mutableListOf<String>()
        var fresh = 0
        for (d in devices) {
            if (g.seenBt.add(d.id)) {
                fresh++
                g.bump(C.BT)
                if (d.name.isNotBlank()) {
                    freshNamed += d.name
                    if (d.name !in g.friendNames) { g.friendNames.add(0, d.name); if (g.friendNames.size > 200) g.friendNames.removeAt(g.friendNames.size - 1) }
                }
            }
        }
        if (fresh > 0 && b.hatched) {
            val mult = if (b.personality == Personality.SOCIAL) 2.0 else 1.0
            s.social += min(10.0, fresh * 1.5 * mult); s.happiness += min(6.0, fresh * 0.8 * mult)
            ev += gainXp(min(25, fresh * 2), now)
            if (freshNamed.isNotEmpty()) ev += say(Topic.BT_FOUND, now, Anim.HAPPY, thing = freshNamed.first())
            g.log(now, "🔵", "Met $fresh new Bluetooth device${if (fresh == 1) "" else "s"}${if (freshNamed.isNotEmpty()) ": " + freshNamed.take(3).joinToString() else ""}.")
        }
        val close = devices.filter { it.rssi > -45 }.maxByOrNull { it.rssi }
        if (close != null && b.hatched && onceEvery("bt_hug", now, 20 * 60_000L)) {
            g.bump(C.BT_CLOSE)
            s.happiness += 5; s.social += 4
            ev += say(Topic.BT_HUG, now, Anim.LOVE, thing = close.name.ifBlank { "a mystery device" })
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun onRadarLock(target: SignalInfo, now: Long): List<GameEvent> {
        if (!onceEvery("lock_${target.id}", now, 30 * 60_000L)) return emptyList()
        g.bump(C.RADAR_LOCKS)
        val ev = mutableListOf<GameEvent>(say(Topic.RADAR_LOCK, now, Anim.SPARKLE))
        if (b.hatched) { s.happiness += 6; ev += gainXp(10, now) }
        grantCoins(3, ev)
        g.log(now, "🎯", "Radar locked on to ${target.name.ifBlank { target.id }}.")
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun onSatellites(sats: List<SatelliteInfo>, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        if (sats.isEmpty()) return ev
        g.setMax(C.SATS_MAX, sats.size.toLong())
        val newSystems = sats.map { it.constellation }.toSet().filter { g.constellations.add(it) }
        for (c in sats.map { it.constellation }.toSet()) award("sat:$c", now, ev)
        if (newSystems.isNotEmpty()) {
            g.log(now, "🛰️", "First contact with ${newSystems.joinToString()} satellites!")
            if (b.hatched) ev += GameEvent.Say("New space friends: ${newSystems.joinToString()}! ${Phrases.emotes.random(rnd)}", Anim.SPARKLE)
        }
        val used = sats.count { it.usedInFix }
        if (sats.size >= 4 && b.hatched && onceEvery("stargaze", now, 3_600_000L)) {
            g.bump(C.STARGAZES)
            s.happiness += 6; s.smarts += 3
            if (b.personality == Personality.NERDY) s.happiness += 4
            ev += say(Topic.SATELLITES, now, Anim.SPARKLE, n = sats.size)
            ev += gainXp(8 + used, now)
            g.log(now, "✨", "Stargazed: ${sats.size} satellites visible, $used locked.")
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun onSteps(delta: Int, now: Long): List<GameEvent> {
        if (delta <= 0) return emptyList()
        val ev = mutableListOf<GameEvent>()
        val before = g.count(C.STEPS)
        g.bump(C.STEPS, delta.toLong())
        val after = g.count(C.STEPS)
        if (b.hatched && !b.asleep) {
            val k = delta / 100.0
            val sporty = if (b.personality == Personality.SPORTY) 1.5 else 1.0
            s.fitness += 0.8 * k * sporty; s.happiness += 0.6 * k * sporty; s.fullness -= 0.6 * k; s.energy -= 0.5 * k
            b.weight = max(5.0, b.weight - 0.03 * k)
            if (after / 500 > before / 500) {
                ev += say(Topic.STEPS, now, Anim.HAPPY, n = after.toInt())
                ev += gainXp(5, now)
            }
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun onShake(now: Long): List<GameEvent> {
        g.bump(C.SHAKES)
        if (!b.hatched) return listOf(GameEvent.Say("*the egg rattles* ...", Anim.DIZZY))
        val recent = g.daily["shake_window"] ?: 0L
        val streak = if (now - recent < 20_000) (g.daily["shake_streak"] ?: 0L) + 1 else 1L
        g.daily["shake_window"] = now; g.daily["shake_streak"] = streak
        val ev = mutableListOf<GameEvent>()
        if (b.asleep) {
            b.asleep = false; s.happiness -= 8
            ev += GameEvent.Say("WHOA! I'm awake! I'm awake!", Anim.SHOCK)
        } else if (streak > 4) {
            s.happiness -= 3; s.energy -= 2
            ev += say(Topic.DIZZY, now, Anim.DIZZY)
        } else {
            s.happiness += 2; s.energy -= 1
            ev += say(Topic.SHAKEN, now, Anim.HAPPY)
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun onDark(now: Long): List<GameEvent> {
        if (!b.hatched || b.asleep || !onceEvery("dark", now, 20 * 60_000L)) return emptyList()
        if (s.energy < 45 || isNight(now)) return listOf(say(Topic.DARK, now, Anim.SLEEP))
        return emptyList()
    }

    fun onPeekaboo(now: Long): List<GameEvent> {
        if (!b.hatched || b.asleep || !onceEvery("peek", now, 4_000L)) return emptyList()
        g.bump(C.PEEKABOO)
        s.happiness += 2; s.clampAll()
        return listOf(say(Topic.PEEKABOO, now, Anim.HAPPY)) + checkAchievements(now)
    }

    fun onCharging(now: Long): List<GameEvent> {
        if (!b.hatched || !onceEvery("charge", now, 2 * 3_600_000L)) return emptyList()
        g.bump(C.CHARGES)
        s.energy += 10; s.clampAll()
        return listOf(say(Topic.CHARGING, now, Anim.SHOCK)) + checkAchievements(now)
    }

    fun onLoudSound(now: Long): List<GameEvent> {
        if (!b.hatched || !onceEvery("dance", now, 8_000L)) return emptyList()
        if (b.asleep) { b.asleep = false; s.happiness -= 5; s.clampAll(); return listOf(GameEvent.Say("Huh?! What was that noise?!", Anim.SHOCK)) }
        g.bump(C.DANCES)
        s.happiness += 3; s.fitness += 1; s.energy -= 1; s.clampAll()
        return listOf(say(Topic.LOUD, now, Anim.DANCE)) + checkAchievements(now)
    }

    fun onMagnet(microTesla: Float, now: Long): List<GameEvent> {
        if (microTesla < 200 || !onceEvery("magnet", now, 10 * 60_000L)) return emptyList()
        g.bump(C.MAGNETS)
        return listOf(say(Topic.MAGNET, now, Anim.SHOCK)) + checkAchievements(now)
    }

    fun onAltitude(meters: Double, now: Long): List<GameEvent> {
        if (meters.isNaN()) return emptyList()
        val wasMax = g.count(C.ALTITUDE_MAX)
        g.setMax(C.ALTITUDE_MAX, meters.toLong())
        val ev = mutableListOf<GameEvent>()
        if (meters > 1000 && wasMax < 1000) ev += say(Topic.HIGH_ALTITUDE, now, Anim.SPARKLE)
        if (meters > 1500) award("special:mountain", now, ev)
        ev += checkAchievements(now)
        return ev
    }


    // ---------------------------------------------------------------- walks

    /** Called with distance actually moved (GPS), e.g. while on a walk or in background mode. */
    fun onDistance(meters: Double, now: Long, onFoot: Boolean = true): List<GameEvent> {
        if (meters <= 0 || meters > 2_000) return emptyList() // ignore GPS jumps
        val ev = mutableListOf<GameEvent>()
        val before = g.count(C.DISTANCE_M)
        g.bump(C.DISTANCE_M, meters.toLong().coerceAtLeast(1))
        val after = g.count(C.DISTANCE_M)
        if (!onFoot) {
            g.bump(C.RIDE_METERS, meters.toLong())
            // Riding in a car/bus/train: counts toward distance, but no exercise.
            if (b.hatched && after / 5000 > before / 5000) { ev += GameEvent.Say(listOf("Road trip! 🚗 ${fmtDist(after)} traveled!", "Wheee, we're going fast!", "Are we there yet?").random(rnd), Anim.HAPPY); ev += gainXp(5, now) }
            ev += checkAchievements(now)
            return ev
        }
        if (g.motion == Motion.RUNNING) g.bump(C.RUN_METERS, meters.toLong())
        if (b.hatched && !b.asleep) {
            val k = meters / 100.0
            val sporty = if (b.personality == Personality.SPORTY) 1.5 else 1.0
            s.fitness += 0.9 * k * sporty; s.happiness += 0.8 * k * sporty; s.fullness -= 0.7 * k; s.energy -= 0.6 * k
            b.weight = max(5.0, b.weight - 0.04 * k)
            if (after / 250 > before / 250) {
                ev += GameEvent.Say(listOf("We've walked ${fmtDist(after)} together!", "Ooh, new smells! ${fmtDist(after)} so far!",
                    "Walkies! I can see so many signals out here!", "Left paw, right paw! ${fmtDist(after)}!").random(rnd), Anim.HAPPY)
                ev += gainXp(4, now)
            }
            if (after / 1000 > before / 1000) {
                grantCoins(10, ev)
                g.log(now, "🥾", "Walked ${after / 1000} km in total!")
            }
        }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }

    fun startWalk(now: Long): List<GameEvent> {
        g.bump(C.WALKS)
        g.log(now, "🦮", "Started a walk with ${b.name}.")
        return listOf(GameEvent.Say(listOf("WALKIES!!! Let's go!", "Leash on! Adventure time!", "Outside? OUTSIDE!").random(rnd), Anim.HAPPY)) + checkAchievements(now)
    }

    fun fmtDist(m: Long): String = if (g.settings.metric) (if (m < 1000) "$m m" else "%.2f km".format(m / 1000.0))
        else (if (m < 1609) "${(m * 3.281).toLong()} ft" else "%.2f mi".format(m / 1609.34))

    // ---------------------------------------------------------------- device catalog & connections

    /** A surveillance camera, ALPR, mesh or LoRa node turned up — note it and tell the user. */
    fun onNotableDevice(r: DeviceRecord, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        g.bump(C.NOTABLE)
        when (r.type) {
            DeviceType.SURVEILLANCE -> { g.bump(C.SURVEILLANCE); award("special:surveillance", now, ev) }
            DeviceType.ALPR -> { g.bump(C.ALPR); award("special:surveillance", now, ev) }
            DeviceType.MESH -> g.bump(C.MESH)
            DeviceType.LORA -> { g.bump(C.LORA); award("special:lora", now, ev) }
            else -> {}
        }
        val name = r.name.ifBlank { r.vendor.ifBlank { r.id } }
        val line = when (r.type) {
            DeviceType.SURVEILLANCE -> "📷 I think there's a surveillance camera nearby: '$name'. (Just a guess from its radio.)"
            DeviceType.ALPR -> "🚘 Possible license-plate reader detected: '$name'. Eyes on the road, eyes on us!"
            DeviceType.MESH -> "🕸️ A mesh node! '$name' talks off the grid. Cool!"
            DeviceType.LORA -> "📡 A LoRa node: '$name'! Long-range radio friend. (I hear its Bluetooth, not the LoRa itself.)"
            else -> "Notable device: $name"
        }
        val alertable = r.type == DeviceType.MESH || r.type == DeviceType.LORA || g.settings.surveillanceAlerts
        if (b.hatched) {
            s.smarts += 2; s.happiness += 1; s.clampAll()
            if (alertable) ev += GameEvent.Say(line, Anim.SPARKLE)
            ev += gainXp(12, now)
        }
        g.log(now, r.type.icon, "Notable find: $name (${r.type.label}${if (r.vendor.isNotBlank()) ", ${r.vendor}" else ""}).")
        ev += checkAchievements(now)
        return ev
    }

    /** Records every signal; first sighting per device per day applies its type's effect. */
    fun recordDevices(signals: List<SignalInfo>, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        val newTypes = mutableSetOf<DeviceType>()
        var effects = 0
        for (sig in signals) {
            val key = "${sig.kind.name}:${sig.id}"
            var r = g.devices[key]
            val fresh = r == null
            val desc = DeviceClassifier.describe(sig)
            if (r == null) {
                if (g.devices.size >= 3000) g.devices.entries.minByOrNull { it.value.lastSeen }?.let { g.devices.remove(it.key) }
                r = DeviceRecord(sig.id, sig.name, sig.kind, desc.type, now, now, bestRssi = sig.rssi, vendor = desc.vendor, notable = desc.notable)
                g.devices[key] = r
                if (g.devices.values.count { it.type == r.type } == 1) newTypes += r.type
                if (desc.notable) ev += onNotableDevice(r, now)
            } else {
                if (sig.name.isNotBlank() && sig.name != r.name) { r.name = sig.name; r.type = desc.type; r.vendor = desc.vendor; r.notable = desc.notable }
                if (r.vendor.isBlank() && desc.vendor.isNotBlank()) r.vendor = desc.vendor
                if (now - r.lastSeen > 10 * 60_000L) r.timesSeen++
                r.lastSeen = now
                r.bestRssi = max(r.bestRssi, sig.rssi)
            }
            if (sig.connected) ev += onConnected(sig, now)
            if (b.hatched && !b.asleep && (fresh || onceEvery("dev_$key", now, 20 * 3_600_000L)) && effects < 4) {
                if (applyTypeEffect(r.type, 0.5)) effects++
            }
        }
        g.counters[C.DEVICE_TYPES] = g.devices.values.map { it.type }.toSet().size.toLong()
        for (nt in newTypes) award("dev:${nt.name}", now, ev)
        if (newTypes.isNotEmpty() && b.hatched) {
            val t = newTypes.first()
            ev += GameEvent.Say("New kind of gadget: ${t.icon} ${t.label}! (${t.effect})", Anim.SPARKLE)
            ev += gainXp(5 * newTypes.size, now)
            g.log(now, t.icon, "Discovered a new device type: ${newTypes.joinToString { it.label }}.")
        }
        s.clampAll()
        return ev
    }

    /** Small stat nudge for a device type; returns true if anything happened. */
    private fun applyTypeEffect(t: DeviceType, m: Double): Boolean {
        when (t) {
            DeviceType.EARBUDS, DeviceType.HEADPHONES -> s.happiness += 2 * m
            DeviceType.SPEAKER -> { s.happiness += 2 * m; s.fitness += 1 * m }
            DeviceType.WATCH, DeviceType.FITNESS, DeviceType.MOUSE -> s.fitness += 2 * m
            DeviceType.PHONE, DeviceType.HOTSPOT -> { s.social += 2 * m; s.energy += 1 * m }
            DeviceType.TABLET, DeviceType.GAMEPAD, DeviceType.CAMERA -> s.happiness += 2 * m
            DeviceType.LAPTOP, DeviceType.KEYBOARD, DeviceType.APPLIANCE -> s.smarts += 2 * m
            DeviceType.TV -> { s.happiness += 3 * m; s.energy -= 1 * m }
            DeviceType.CAR, DeviceType.TRACKER, DeviceType.CELL, DeviceType.UNKNOWN -> b.xp += (2 * m).toInt().coerceAtLeast(1)
            DeviceType.LIGHT, DeviceType.ROUTER -> s.energy += 2 * m
            DeviceType.PRINTER -> s.fullness += 2 * m
            DeviceType.HEALTH -> s.health += 3 * m
            DeviceType.BUDDY -> { s.social += 4 * m; s.happiness += 3 * m }
            DeviceType.SURVEILLANCE, DeviceType.ALPR, DeviceType.MESH, DeviceType.LORA, DeviceType.THREAD ->
                { s.smarts += 2 * m; b.xp += (2 * m).toInt().coerceAtLeast(1) }
        }
        return true
    }

    /** The phone is actually connected to this device: a much bigger bonus, once per device per 6 h. */
    fun onConnected(sig: SignalInfo, now: Long): List<GameEvent> {
        val key = "${sig.kind.name}:${sig.id}"
        val r = g.devices.getOrPut(key) { DeviceRecord(sig.id, sig.name, sig.kind, DeviceClassifier.classify(sig), now, now, bestRssi = sig.rssi) }
        if (now - r.lastConnected < 6 * 3_600_000L) return emptyList()
        r.lastConnected = now
        r.timesConnected++
        g.bump(C.CONNECTIONS)
        val ev = mutableListOf<GameEvent>()
        if (b.hatched) {
            applyTypeEffect(r.type, 5.0)
            s.happiness += 5
            s.clampAll()
            ev += GameEvent.Say("Connected to ${r.name.ifBlank { r.type.label }}! ${r.type.icon} Bonus: ${r.type.effect}!", Anim.SPARKLE)
            ev += gainXp(15, now)
            grantCoins(5, ev)
        }
        g.log(now, "🔗", "Connected to ${r.name.ifBlank { r.type.label }} (${r.timesConnected}×).")
        ev += checkAchievements(now)
        return ev
    }

    fun onCells(cells: List<SignalInfo>, now: Long): List<GameEvent> {
        if (cells.isEmpty()) return emptyList()
        val before = g.devices.keys.count { it.startsWith("CELL:") }
        val ev = recordDevices(cells, now).toMutableList()
        val fresh = g.devices.keys.count { it.startsWith("CELL:") } - before
        if (fresh > 0) {
            g.bump(C.CELLS, fresh.toLong())
            if (b.hatched && onceEvery("cell_say", now, 30 * 60_000L)) ev += GameEvent.Say("I spotted ${cells.size} cell tower${if (cells.size == 1) "" else "s"}! 🗼", Anim.SPARKLE)
            ev += gainXp(fresh * 2, now)
        }
        ev += checkAchievements(now)
        return ev
    }

    // ---------------------------------------------------------------- friends

    fun myCard(): Friend = Friend(
        id = g.myId, name = b.name, owner = g.owner.name, species = b.species, sex = b.sex, hue = b.hue,
        pattern = b.pattern, level = b.level, accessory = b.accessory, addedAt = 0L,
    )

    fun addFriend(card: Friend, now: Long, viaLink: Boolean): List<GameEvent> {
        if (card.id == g.myId) return listOf(GameEvent.Say("That's ME! Hehe.", Anim.HAPPY))
        val existing = g.friends.firstOrNull { it.id == card.id }
        if (existing != null) {
            existing.name = card.name; existing.owner = card.owner; existing.species = card.species; existing.hue = card.hue
            existing.level = card.level; existing.accessory = card.accessory; existing.pattern = card.pattern; existing.sex = card.sex
            if (!viaLink) existing.lastNearby = now
            return emptyList()
        }
        card.addedAt = now; card.viaLink = viaLink
        if (!viaLink) card.lastNearby = now
        card.onScreen = g.friends.count { it.onScreen } < 4
        g.friends.add(card)
        g.bump(C.FRIENDS)
        if (!viaLink) g.bump(C.NEARBY_MEETS)
        val ev = mutableListOf<GameEvent>()
        award("special:firstfriend", now, ev)
        if (b.hatched) {
            s.social += 10; s.happiness += 8; s.clampAll()
            ev += GameEvent.Say("A new friend! Hi ${card.name} the ${card.species.label.lowercase()}! ${card.species.emoji}", Anim.LOVE)
            ev += gainXp(20, now)
        }
        g.log(now, "🐾", "Made friends with ${card.name}${if (card.owner.isNotBlank()) " (${card.owner}'s buddy)" else ""}${if (viaLink) " via link code" else " nearby"}.")
        ev += checkAchievements(now)
        return ev
    }

    fun interact(friendId: String, action: String, now: Long): List<GameEvent> {
        val f = g.friends.firstOrNull { it.id == friendId } ?: return emptyList()
        if (!b.hatched) return listOf(GameEvent.Say("*the egg wobbles at ${f.name}*", Anim.SHOCK))
        if (b.asleep) return listOf(say(Topic.ASLEEP_TAP, now, Anim.SLEEP))
        val ev = mutableListOf<GameEvent>()
        val fresh = onceEvery("play_${f.id}_$action", now, 10 * 60_000L)
        val text = when (action) {
            "wave" -> listOf("*waves at ${f.name}* Hiii!", "Hey ${f.name}! 👋", "${f.name}! Over here!").random(rnd)
            "hug" -> { if (fresh) { s.happiness += 6; s.social += 4 }; listOf("*big hug for ${f.name}* 🤗", "Group hug!", "${f.name} gives the best hugs.").random(rnd) }
            "play" -> { if (fresh) { s.happiness += 10; s.energy -= 5; s.fitness += 2; g.bump(C.PLAYDATES) }; listOf("Tag, you're it, ${f.name}!", "Let's race to the router!", "*plays hide and seek with ${f.name}*").random(rnd) }
            "gift" -> {
                if (g.coins < 5) return listOf(GameEvent.Say("We need 5 coins for a gift!", Anim.SAD))
                g.coins -= 5; if (fresh) s.happiness += 5
                "I gave ${f.name} a ${listOf("shiny pixel", "barcode cookie", "tiny antenna", "glowing pebble", "Wi-Fi flower").random(rnd)}! 🎁"
            }
            "dance" -> { if (fresh) { s.happiness += 6; s.fitness += 3; g.bump(C.DANCES) }; "*dance battle with ${f.name}* 💃🕺" }
            else -> "Hi ${f.name}!"
        }
        if (fresh) { f.friendship = min(100, f.friendship + 5); ev += gainXp(3, now) }
        s.clampAll()
        ev += GameEvent.Say(text, when (action) { "hug" -> Anim.LOVE; "dance" -> Anim.DANCE; else -> Anim.HAPPY })
        ev += checkAchievements(now)
        return ev
    }

    fun addMessage(friendId: String, text: String, fromMe: Boolean, now: Long): List<GameEvent> {
        val f = g.friends.firstOrNull { it.id == friendId } ?: return emptyList()
        f.messages.add(FriendMsg(fromMe, text.take(300), now))
        while (f.messages.size > 100) f.messages.removeAt(0)
        if (fromMe) g.bump(C.MESSAGES)
        val ev = mutableListOf<GameEvent>()
        if (!fromMe) {
            g.log(now, "✉️", "Message from ${f.name}: $text")
            if (b.hatched) { s.social += 3; s.happiness += 3; s.clampAll(); ev += GameEvent.Say("📨 ${f.name} says: \"$text\"", Anim.HAPPY) }
        }
        f.friendship = min(100, f.friendship + 1)
        ev += checkAchievements(now)
        return ev
    }

    /** A discoverable buddy showed up in Bluetooth range. */
    fun onNearbyBuddy(card: Friend, rssi: Int, now: Long): List<GameEvent> {
        val known = g.friends.firstOrNull { it.id == card.id }
        val ev = mutableListOf<GameEvent>()
        if (known == null) ev += addFriend(card, now, viaLink = false)
        else {
            val wasAway = now - known.lastNearby > 30 * 60_000L
            known.lastNearby = now; known.level = card.level; known.name = card.name; known.hue = card.hue
            if (wasAway && b.hatched) {
                g.bump(C.NEARBY_MEETS)
                s.social += 5; s.happiness += 4; s.clampAll()
                ev += GameEvent.Say("${known.name} is nearby! ${known.species.emoji} ${if (rssi > -60) "Right next to us!" else "Somewhere close!"}", Anim.LOVE)
            }
        }
        ev += checkAchievements(now)
        return ev
    }

    // ---------------------------------------------------------------- physics reactions

    /** The physics sim bumped the buddy into a wall/floor with [force] (0..1+). */
    fun onBump(force: Float, now: Long): List<GameEvent> {
        if (!b.hatched || force < 0.35f || !onceEvery("bump", now, 1_500L)) return emptyList()
        g.bump(C.BUMPS)
        val ev = mutableListOf<GameEvent>()
        if (b.asleep) { b.asleep = false; s.happiness -= 4; ev += GameEvent.Say("Hey! I was sleeping! 😾", Anim.ANGRY) }
        else if (force > 0.9f) { s.happiness -= 2; ev += GameEvent.Say(listOf("OUCH!", "Bonk! @_@", "Whoa, careful!", "My antenna!!").random(rnd), Anim.DIZZY) }
        else { s.happiness += 1; ev += GameEvent.Say(listOf("Wheee!", "Boing!", "Again!", "*bounce bounce*", "This is fun!").random(rnd), Anim.HAPPY) }
        s.clampAll()
        ev += checkAchievements(now)
        return ev
    }


    // ---------------------------------------------------------------- stickers, quests, environment

    fun award(id: String, now: Long, events: MutableList<GameEvent>) {
        if (!g.stickers.add(id)) return
        g.bump(C.STICKERS)
        val st = Stickers.byId[id]
        val coins = when (st?.tier) {
            StickerTier.LEGENDARY -> 40; StickerTier.EPIC -> 20; StickerTier.RARE -> 12; StickerTier.UNCOMMON -> 8; else -> 5
        }
        events += GameEvent.Say("New sticker: ${st?.emoji ?: "⭐"} ${st?.name ?: id}${if (st != null && st.tier != StickerTier.COMMON) " (${st.tier.label})" else ""}!", Anim.SPARKLE)
        g.log(now, st?.emoji ?: "⭐", "Collected a sticker: ${st?.name ?: id}.")
        grantCoins(coins, events)
        // Did this complete a set?
        if (st != null && !id.startsWith("trophy:")) {
            Stickers.completable.firstOrNull { it.name == st.set }?.let { set ->
                if (set.trophyId !in g.stickers && Stickers.setMembers(set.name).all { it.id in g.stickers }) {
                    g.stickers.add(set.trophyId); g.bump(C.STICKERS)
                    events += GameEvent.Say("🎉 Album set complete: ${set.name}! Trophy ${set.trophyEmoji} earned!", Anim.LEVEL_UP)
                    g.log(now, set.trophyEmoji, "Completed the ${set.name} sticker set!")
                    grantCoins(set.reward, events)
                }
            }
        }
    }

    /** Level / distance / age milestone stickers, checked opportunistically. */
    fun milestoneStickers(now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        listOf(5, 10, 25, 50, 99).filter { b.level >= it }.forEach { award("lvl:$it", now, ev) }
        val d = g.count(C.DISTANCE_M)
        listOf(1_000L, 10_000L, 42_195L, 100_000L).filter { d >= it }.forEach { award("dist:$it", now, ev) }
        if (b.hatched && b.stage(now) == Stage.ELDER) award("special:elder", now, ev)
        return ev
    }

    fun rollQuestsIfNeeded(now: Long) {
        val day = now / 86_400_000L
        if (g.questDay == day && g.quests.isNotEmpty()) return
        g.questDay = day
        g.quests.clear()
        g.quests.addAll(Quests.rollFor(day, g))
    }

    fun claimQuest(id: String, now: Long): List<GameEvent> {
        val q = g.quests.firstOrNull { it.id == id } ?: return emptyList()
        if (q.claimed || !Quests.done(q, g)) return emptyList()
        q.claimed = true
        g.bump(C.QUESTS_DONE)
        val ev = mutableListOf<GameEvent>()
        grantCoins(q.reward, ev)
        ev += gainXp(q.reward, now)
        ev += GameEvent.Say("Quest complete! ${q.title} ${Phrases.emotes.random(rnd)}", Anim.LEVEL_UP)
        g.log(now, "✅", "Quest done: ${q.title} (+${q.reward} coins).")
        ev += checkAchievements(now)
        return ev
    }

    /** Called periodically with the current surroundings. Drives weather, biome and motion moods. */
    fun onEnvironment(devicesInRange: Int, pressureHpa: Float, speedMps: Float, cadenceSpm: Float, pocket: Boolean, now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        // Biome
        val biome = Env.biomeFor(devicesInRange)
        if (biome != g.biome) {
            g.biome = biome
            award("biome:${biome.name}", now, ev)
            if (b.hatched && onceEvery("biome_say", now, 10 * 60_000L)) ev += GameEvent.Say("${biome.icon} ${biome.blurb}", Anim.NONE)
            if (g.stickers.count { it.startsWith("biome:") } >= Biome.entries.size) g.setMax(C.BIOMES, Biome.entries.size.toLong())
        }
        // Weather (needs a barometer)
        if (pressureHpa > 0f) {
            if (g.pressureBaseline <= 0f) g.pressureBaseline = pressureHpa
            g.pressureBaseline += (pressureHpa - g.pressureBaseline) * 0.02f  // slow baseline
            val wx = Env.weatherFor(pressureHpa, g.pressureBaseline)
            if (wx != Weather.UNKNOWN && wx != g.weather) {
                val was = g.weather
                g.weather = wx
                award("wx:${wx.name}", now, ev)
                if (wx == Weather.STORMY) { g.bump(C.STORMS); award("special:storm", now, ev); if (b.hatched) { s.happiness -= 4; s.clampAll() } }
                if (was != Weather.UNKNOWN && b.hatched && onceEvery("wx_say", now, 15 * 60_000L)) {
                    ev += GameEvent.Say(when (wx) {
                        Weather.STORMY -> "The pressure's dropping... a storm is coming! ${wx.icon}"
                        Weather.CLEARING -> "The air feels lighter. Clearing up! ${wx.icon}"
                        Weather.CLEAR -> "What a clear day! ${wx.icon}"
                        Weather.CLOUDY -> "Clouds rolling in. ${wx.icon}"
                        else -> "Weather's changing. ${wx.icon}"
                    }, if (wx == Weather.STORMY) Anim.SHOCK else Anim.HAPPY)
                }
            }
        }
        // Motion
        val motion = Env.motionFor(speedMps, cadenceSpm, pocket)
        if (motion != g.motion) {
            g.motion = motion
            award("motion:${motion.name}", now, ev)
            when (motion) {
                Motion.RUNNING -> if (b.hatched) { s.fitness += 2; s.happiness += 2 }
                Motion.CYCLING -> if (b.hatched) s.fitness += 1
                Motion.POCKET -> {}
                else -> {}
            }
            s.clampAll()
        }
        if (motion == Motion.POCKET && !b.asleep && b.hatched && onceEvery("pocket_nap", now, 5 * 60_000L)) {
            // Dark + still = probably in a pocket; let the buddy doze.
            if (s.energy < 60) { b.asleep = true; b.sleptAt = now; ev += GameEvent.Say("*dozes off in the dark pocket* zzz", Anim.SLEEP) }
        }
        rollQuestsIfNeeded(now)
        ev += checkAchievements(now)
        return ev
    }

    /** Record where something was discovered (only when mapping is on). */
    fun mapDiscovery(lat: Double, lon: Double, kind: String, label: String, now: Long) {
        if (g.discoveries.size >= 2000) g.discoveries.removeAt(0)
        g.discoveries.add(Discovery(lat, lon, kind, label, now))
        g.bump(C.DISCOVERIES_MAPPED)
    }

    fun onSignificantMotion(now: Long): List<GameEvent> {
        g.bump(C.SIG_MOTION)
        if (!b.hatched || b.asleep) return emptyList()
        return if (onceEvery("sigmotion", now, 60_000L)) listOf(say(Topic.SHAKEN, now, Anim.HAPPY)) else emptyList()
    }

    fun onAmbientTemp(celsius: Float, now: Long): List<GameEvent> {
        if (!b.hatched || !onceEvery("temp", now, 20 * 60_000L)) return emptyList()
        return when {
            celsius >= 30 -> { g.bump(C.HOT); listOf(GameEvent.Say("Phew, it's warm (${celsius.toInt()}°C)! *fans self*", Anim.NONE)) }
            celsius <= 2 -> { g.bump(C.COLD); listOf(GameEvent.Say("Brrr, ${celsius.toInt()}°C! *shivers*", Anim.NONE)) }
            else -> emptyList()
        } + checkAchievements(now)
    }

    fun onTorchFirefly(now: Long): List<GameEvent> {
        if (!onceEvery("firefly", now, 5_000L)) return emptyList()
        g.bump(C.FIREFLIES)
        val ev = mutableListOf<GameEvent>()
        award("special:firefly", now, ev)
        if (b.hatched) { s.happiness += 1; s.clampAll() }
        ev += GameEvent.Say(listOf("✨ Ooh, a firefly!", "The light! So pretty!", "*chases the glow*").random(rnd), Anim.LOVE)
        ev += checkAchievements(now)
        return ev
    }

    /** Award time-of-day and altitude stickers; called from the main tick and sky/altitude handlers. */
    fun timeStickers(now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        val h = java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)
        if (h in 0..4) award("special:nightowl", now, ev)
        if (h in 4..5) award("special:earlybird", now, ev)
        return ev
    }

    // ---------------------------------------------------------------- toys

    val toyNames = linkedMapOf("ball" to "⚽", "bone" to "🦴", "mouse" to "🐭", "yarn" to "🧶", "stick" to "🥢", "disc" to "🥏", "cube" to "🧊")

    fun placeToy(type: String, x: Float, now: Long): List<GameEvent> {
        g.toy = type; g.toyX = x.coerceIn(0.06f, 0.94f)
        g.log(now, toyNames[type] ?: "🎾", "Tossed a ${type} into the room.")
        return if (b.hatched) listOf(say(if (g.settings.matureLanguage) Topic.GAME_WIN else Topic.IDLE_HAPPY, now, Anim.HAPPY)) else emptyList()
    }

    fun removeToy(now: Long): List<GameEvent> {
        g.toy = null
        return emptyList()
    }

    /** The buddy pokes the toy on its own (UI calls when they meet while wandering). */
    fun toyInteract(now: Long): List<GameEvent> {
        val toy = g.toy ?: return emptyList()
        if (!b.hatched || b.asleep || !onceEvery("toy", now, 30_000L)) return emptyList()
        val ev = mutableListOf<GameEvent>()
        // Bored + toy = fun; but sometimes the buddy is annoyed it's underfoot.
        val annoyed = s.happiness > 55 && rnd.nextInt(3) == 0
        if (annoyed) {
            g.bump(C.TOY_ANNOYED); s.happiness -= 2; s.clampAll()
            ev += GameEvent.Say(if (g.settings.matureLanguage)
                listOf("Ugh, this ${toy}'s in my {madj} way.".let { Phrases.fill(it, ctx(now), rnd) }, "Move the ${toy}, it's underfoot.", "Who left this ${toy} here? Rude.").random(rnd)
                else listOf("This ${toy} is in my way!", "*trips over the ${toy}*", "Move the ${toy} please?").random(rnd), Anim.ANGRY)
        } else {
            g.bump(C.TOY_PLAYS); s.happiness += 5; s.energy -= 3; s.fitness += 1; s.clampAll()
            ev += GameEvent.Say(if (g.settings.matureLanguage)
                listOf("Oh hell yeah, ${toy} time!", "*bats the ${toy} around* Mine!", "This ${toy} {curse}in' rules.".let { Phrases.fill(it, ctx(now), rnd) }).random(rnd)
                else listOf("Wheee, ${toy}!", "*plays with the ${toy}*", "My favorite ${toy}!").random(rnd), Anim.HAPPY)
            ev += gainXp(2, now)
        }
        ev += checkAchievements(now)
        return ev
    }

    // ---------------------------------------------------------------- moods & ambient chatter

    fun mood(now: Long): String = when {
        !b.hatched -> "Waiting to hatch"
        b.dead -> "Gone"
        b.asleep -> "Sleeping"
        b.sick -> "Sick"
        b.tantrum -> "Throwing a tantrum"
        s.fullness < 20 -> "Starving"
        s.hygiene < 25 || b.poops >= 2 -> "Stinky"
        s.energy < 20 -> "Exhausted"
        s.happiness < 25 -> "Sad"
        s.happiness > 80 && s.fullness > 50 -> "Ecstatic"
        s.happiness > 60 -> "Happy"
        else -> "Okay"
    }

    /** Something to say when the user is just watching. Picks the most pressing need first. */
    fun ambient(now: Long): GameEvent.Say {
        if (!b.hatched) return GameEvent.Say(listOf("*wobble*", "*tap tap from inside*", "*the egg glows faintly*", "Keep me warm! Tap me or scan things!").random(rnd))
        val topic = when {
            b.asleep -> Topic.ASLEEP_TAP
            b.sick -> Topic.SICK
            b.tantrum -> Topic.TANTRUM
            s.fullness < 12 -> Topic.STARVING
            s.fullness < 35 -> Topic.HUNGRY
            s.hygiene < 30 || b.poops >= 2 -> Topic.DIRTY
            s.energy < 25 -> Topic.SLEEPY
            s.happiness < 30 -> Topic.IDLE_SAD
            isNight(now) && rnd.nextInt(3) == 0 -> Topic.NIGHT
            s.happiness < 55 -> Topic.IDLE_BORED
            else -> Topic.IDLE_HAPPY
        }
        val anim = when (topic) { Topic.SICK -> Anim.SICK; Topic.TANTRUM -> Anim.ANGRY; Topic.IDLE_SAD, Topic.STARVING -> Anim.SAD; Topic.ASLEEP_TAP -> Anim.SLEEP; else -> Anim.NONE }
        if (topic == Topic.IDLE_HAPPY && rnd.nextInt(4) == 0) {
            val extras = buildList {
                add("Fun fact: ${Phrases.facts.random(rnd)}")
                if (g.favoriteFood.isNotBlank()) add("I keep thinking about that ${g.favoriteFood}...")
                if (g.friendNames.isNotEmpty()) add("I wonder how ${g.friendNames.random(rnd)} is doing.")
                if (g.likes.isNotEmpty()) add("You like ${g.likes.random(rnd)}, right? I remember!")
                if (g.count(C.WIFI) > 0) add("I've sniffed ${g.count(C.WIFI)} Wi-Fi networks so far!")
            }
            return GameEvent.Say(Phrases.stylize(extras.random(rnd), ctx(now), rnd), Anim.HAPPY)
        }
        return say(topic, now, anim)
    }

    // ---------------------------------------------------------------- progression

    fun gainXp(amount: Int, now: Long): List<GameEvent> {
        if (amount <= 0 || !b.hatched) return emptyList()
        b.xp += amount
        return settleLevels(now)
    }

    private fun settleLevels(now: Long): List<GameEvent> {
        val ev = mutableListOf<GameEvent>()
        while (b.xp >= b.xpToNext() && b.level < 99) {
            b.xp -= b.xpToNext()
            b.level += 1
            ev += GameEvent.LevelUp(b.level)
            ev += say(Topic.LEVEL_UP, now, Anim.LEVEL_UP, n = b.level)
            grantCoins(10 + b.level * 2, ev)
            if (b.level % 10 == 0 && b.lives < 5) { b.lives += 1; ev += GameEvent.Say("Bonus life for reaching level ${b.level}! ❤️", Anim.SPARKLE) }
            g.log(now, "⭐", "${b.name} reached level ${b.level}!")
            ev += milestoneStickers(now)
        }
        return ev
    }

    fun grantCoins(n: Int, ev: MutableList<GameEvent>) {
        if (n <= 0) return
        g.coins += n
        g.bump(C.COINS_EARNED, n.toLong())
        ev += GameEvent.Coins(n)
    }

    fun checkAchievements(now: Long): List<GameEvent> {
        val out = mutableListOf<GameEvent>()
        for (a in Achievements.check(g, now)) {
            out += GameEvent.Unlocked(a)
            g.log(now, a.icon, "Achievement: ${a.title}")
            grantCoins(a.coins, out)
            if (b.hatched) { b.xp += a.coins / 2 }
        }
        if (out.isNotEmpty() && b.hatched) out += settleLevels(now)
        return out
    }

    // ---------------------------------------------------------------- helpers

    fun onceEvery(key: String, now: Long, periodMs: Long): Boolean {
        val last = g.daily[key] ?: 0L
        if (abs(now - last) < periodMs) return false
        g.daily[key] = now
        return true
    }

    companion object {
        const val DEFAULT_INCUBATION = 15 * 60_000L
    }

    fun isNight(now: Long): Boolean {
        val c = Calendar.getInstance().apply { timeInMillis = now }
        val h = c.get(Calendar.HOUR_OF_DAY)
        return h >= 22 || h < 7
    }
}
