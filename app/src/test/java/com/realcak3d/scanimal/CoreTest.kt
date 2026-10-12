package com.realcak3d.scanimal

import com.realcak3d.scanimal.core.*
import kotlin.random.Random
import kotlin.test.*
import org.junit.Test

class CoreTest {
    private val t0 = 1_760_000_000_000L
    private val hour = 3_600_000L

    private fun fresh(): Engine {
        val e = Engine(GameData(), Random(42))
        e.newEgg(t0)
        e.hatch("Pip", Sex.FEMALE, t0)
        return e
    }

    @Test fun foodIsDeterministicPerCode() {
        val a = FoodFactory.fromScan(ScanInput("012345678905", "UPC_A"))
        val b = FoodFactory.fromScan(ScanInput("012345678905", "UPC_A"))
        assertEquals(a, b)
        assertEquals(FoodFamily.GROCERY, a.family)
        assertEquals(FoodFamily.WEB, FoodFactory.fromScan(ScanInput("https://example.com", "QR_CODE")).family)
        assertEquals(FoodFamily.SIGNAL, FoodFactory.fromScan(ScanInput("WIFI:S:home;T:WPA;P:x;;", "QR_CODE")).family)
        assertEquals(FoodFamily.ELECTRIC, FoodFactory.fromScan(ScanInput("04A2B3C4", "NFC")).family)
    }

    @Test fun rarityDistributionLooksSane() {
        val counts = (0 until 20000).map { FoodFactory.fromScan(ScanInput("code$it", "QR_CODE")).rarity }.groupingBy { it }.eachCount()
        println("rarity: $counts")
        assertTrue((counts[Rarity.COMMON] ?: 0) > 10000)
        assertTrue((counts[Rarity.LEGENDARY] ?: 0) in 50..400)
    }

    @Test fun feedingRaisesFullnessAndRefusesRepeats() {
        val e = fresh()
        e.g.buddy.stats.fullness = 20.0
        val food = FoodFactory.fromScan(ScanInput("hello", "QR_CODE"))
        val ev1 = e.feed(food, t0 + 1000)
        assertTrue(ev1.any { it is GameEvent.Ate })
        assertTrue(e.g.buddy.stats.fullness > 20.0)
        assertEquals(1L, e.g.count(C.SCANS))
        assertTrue(e.g.achievements.containsKey("first_bite"))
        e.g.buddy.stats.fullness = 20.0
        e.feed(food, t0 + 2000)
        e.g.buddy.stats.fullness = 20.0
        val ev3 = e.feed(food, t0 + 3000)
        assertFalse(ev3.any { it is GameEvent.Ate }, "third time same day should be refused")
        e.g.buddy.stats.fullness = 20.0
        assertTrue(e.feed(food, t0 + 25 * hour).any { it is GameEvent.Ate }, "next day is fine")
    }

    @Test fun neglectEventuallyCostsALifeButNotOfflineShortTerm() {
        val e = fresh()
        e.tick(t0 + 30 * hour, offline = true)
        assertEquals(3, e.g.buddy.lives, "offline < 48h must not kill")
        assertTrue(e.g.buddy.stats.health >= 10.0)
        // Online neglect kills
        var now = t0 + 30 * hour
        repeat(200) { now += hour; e.tick(now) }
        assertTrue(e.g.buddy.lives < 3)
    }

    @Test fun gameOverAfterAllLives() {
        val e = fresh()
        e.g.buddy.lives = 1
        e.g.buddy.stats.fullness = 0.0
        e.g.buddy.stats.health = 1.0
        val ev = e.tick(t0 + hour)
        assertTrue(ev.any { it is GameEvent.GameOver })
        assertTrue(e.g.buddy.dead)
        assertEquals(1, e.g.graveyard.size)
        e.startNextGeneration(t0 + 2 * hour)
        assertEquals(2, e.g.buddy.generation)
        assertFalse(e.g.buddy.hatched)
    }

    @Test fun agingAndStages() {
        val e = fresh()
        assertEquals(Stage.BABY, e.g.buddy.stage(t0 + hour))
        assertEquals(Stage.CHILD, e.g.buddy.stage(t0 + 30 * hour))
        assertEquals(Stage.ADULT, e.g.buddy.stage(t0 + 200 * hour))
        assertEquals(Stage.ELDER, e.g.buddy.stage(t0 + 800 * hour))
        assertEquals(2, e.g.buddy.ageYears(t0 + 50 * hour))
    }

    @Test fun levelingGivesCoins() {
        val e = fresh()
        val coins = e.g.coins
        val ev = e.gainXp(1000, t0)
        assertTrue(ev.any { it is GameEvent.LevelUp })
        assertTrue(e.g.buddy.level > 3)
        assertTrue(e.g.coins > coins)
    }

    @Test fun signalsAndSky() {
        val e = fresh()
        val wifi = (1..5).map { SignalInfo("aa:$it", "Net$it", -60, SignalKind.WIFI, if (it == 1) 5180 else 2412, secured = it != 2) }
        e.onWifiScan(wifi, t0 + 1000)
        assertEquals(5L, e.g.count(C.WIFI))
        assertTrue("open_net" in e.g.achievements)
        assertTrue("five_g" in e.g.achievements)
        e.onWifiScan(wifi, t0 + 2000)
        assertEquals(5L, e.g.count(C.WIFI), "same networks don't recount")
        e.onBluetooth(listOf(SignalInfo("bt1", "Buds", -40, SignalKind.BLE)), t0 + 3000)
        assertTrue("bt_hug" in e.g.achievements)
        assertTrue("Buds" in e.g.friendNames)
        val sats = (1..14).map { SatelliteInfo(it, if (it % 2 == 0) "GPS" else if (it % 3 == 0) "Galileo" else "GLONASS", 30f, 0f, 45f, it < 8) }
        e.onSatellites(sats, t0 + 4000)
        assertTrue("stargazer" in e.g.achievements)
        assertTrue("constellations_3" in e.g.achievements)
        assertTrue("sats_12" in e.g.achievements)
    }

    @Test fun shopAndItems() {
        val e = fresh()
        e.g.coins = 100
        e.buy("medicine", t0)
        assertEquals(80, e.g.coins)
        e.g.buddy.sick = true
        e.useItem("medicine", t0)
        assertFalse(e.g.buddy.sick)
        e.buy("hat_party", t0)
        e.useItem("hat_party", t0)
        assertEquals("hat_party", e.g.buddy.accessory)
        e.g.coins = 0
        val ev = e.buy("cake", t0)
        assertTrue(ev.first() is GameEvent.Say)
        assertFalse(e.g.has("cake"))
    }

    @Test fun codecRoundTrip() {
        val e = fresh()
        e.feed(FoodFactory.fromScan(ScanInput("abc", "QR_CODE")), t0 + 5)
        e.onWifiScan(listOf(SignalInfo("x", "Home", -50, SignalKind.WIFI, 2412)), t0 + 10)
        e.g.ownerName = "Sam"; e.g.likes.add("pizza")
        e.g.graveyard.add(Memorial("Old", Sex.MALE, 1, 4, 3, t0, "illness"))
        val json = Codec.encode(e.g)
        val back = Codec.decode(json)
        assertEquals(json, Codec.encode(back))
        assertEquals("Pip", back.buddy.name)
        assertEquals(Sex.FEMALE, back.buddy.sex)
        assertEquals(1, back.graveyard.size)
        assertTrue(back.seenWifi.contains("x"))
    }

    @Test fun chatBrain() {
        val e = fresh()
        val c = ChatBrain(e, Random(1))
        val now = t0 + 1000
        fun say(s: String) = c.respond(s, now).text.also { println("> $s\n< $it") }
        say("hello")
        say("my name is sam")
        assertEquals("Sam", e.g.ownerName)
        assertTrue(say("what's my name").contains("Sam"))
        say("I like pizza and robots")
        assertTrue(e.g.likes.isNotEmpty())
        assertTrue(say("what is 12 * 7").contains("84"))
        assertTrue(say("roll 2d6").contains("🎲"))
        say("tell me a joke")
        say("riddle")
        say("I give up")
        say("how are you")
        say("how old are you")
        say("I feel sad")
        say("I want a new bike")
        say("are you a robot")
        say("the weather was strange today at the park")
        say("guess a number")
        repeat(8) { say("50") }
        say("rock")
        say("my favorite color is green")
        assertTrue(say("what is my favorite color").contains("green"))
        say("remember I parked on level 3")
        say("what do you remember")
        say("help")
        assertTrue(e.g.count(C.CHATS) > 10)
    }

    @Test fun phrasesStageStyling() {
        val r = Random(3)
        repeat(5) { println(Phrases.say(Topic.HUNGRY, Phrases.Ctx(name = "Pip", stage = Stage.BABY), r)) }
        repeat(5) { println(Phrases.say(Topic.IDLE_HAPPY, Phrases.Ctx(name = "Pip", stage = Stage.TEEN), r)) }
        Topic.entries.forEach { t -> val s = Phrases.say(t, Phrases.Ctx(), r); assertFalse(s.contains("{"), "unfilled token in $t: $s") }
    }

    @Test fun ambientNeverCrashes() {
        val e = fresh()
        var now = t0
        repeat(300) { now += 7 * 60_000L; e.tick(now); e.ambient(now) }
        println("after 35h: ${e.mood(now)} lives=${e.g.buddy.lives} journal=${e.g.journal.size}")
    }
}

class RadarTest {
    @Test fun findsStrongestHeading() {
        val r = RadarTracker()
        r.reset("x")
        val rnd = Random(9)
        repeat(3) {
            for (h in 0 until 360 step 5) {
                val diff = kotlin.math.abs(RadarTracker.angleDiff(h.toFloat(), 120f))
                val rssi = (-60 - diff / 18f + rnd.nextInt(-2, 3)).toInt()
                r.add(h.toFloat(), rssi)
            }
        }
        val b = r.bearing()
        println("bearing=$b confidence=${r.confidence()}")
        assertNotNull(b)
        assertTrue(kotlin.math.abs(RadarTracker.angleDiff(b, 120f)) < 25f)
        assertTrue(RadarTracker.distanceMeters(-59, SignalKind.BLE) in 0.9..1.1)
    }
}

class FeatureTest {
    private val t0 = 1_760_000_000_000L
    private fun hatched(): Engine {
        val e = Engine(GameData(), Random(5)); e.newEgg(t0)
        e.layEgg("Rex", Sex.MALE, Species.DINO, t0)
        return e
    }

    @Test fun timedHatching() {
        val e = Engine(GameData(), Random(5)); e.newEgg(t0)
        e.layEgg("Rex", Sex.MALE, Species.DINO, t0)
        assertFalse(e.g.buddy.hatched)
        assertEquals(15, e.eggMinutesLeft(t0))
        e.warmEgg(t0 + 1000, 5 * 60_000L)
        assertFalse(e.g.buddy.hatched)
        assertTrue(e.eggMinutesLeft(t0 + 1000) in 9..10)
        val ev = e.tick(t0 + 16 * 60_000L)
        assertTrue(e.g.buddy.hatched, "auto-hatches when time is up")
        assertEquals(Species.DINO, e.g.buddy.species)
        assertTrue(ev.isNotEmpty())
        assertTrue(e.g.myId.length == 8)
    }

    @Test fun walkingAndDistance() {
        val e = hatched(); e.tick(t0 + 20 * 60_000L)
        val now = t0 + 21 * 60_000L
        e.startWalk(now)
        repeat(60) { e.onDistance(25.0, now + it * 1000L) }
        assertEquals(1500L, e.g.count(C.DISTANCE_M))
        assertTrue("dist_1k" in e.g.achievements)
        assertTrue("first_walk" in e.g.achievements)
        assertTrue(e.onDistance(5000.0, now).isEmpty(), "GPS jumps ignored")
        e.g.settings.metric = false
        assertTrue(e.fmtDist(1500).endsWith("ft"))
    }

    @Test fun deviceCatalogAndConnections() {
        val e = hatched(); val now = t0 + 20 * 60_000L; e.tick(now)
        assertEquals(DeviceType.EARBUDS, DeviceClassifier.classify(SignalInfo("a", "Skullcandy Indy Evo", -60, SignalKind.BLE)))
        assertEquals(DeviceType.WATCH, DeviceClassifier.classify(SignalInfo("b", "Galaxy Watch5 (A1B2)", -60, SignalKind.BLE)))
        assertEquals(DeviceType.HEADPHONES, DeviceClassifier.classify(SignalInfo("c", "", -60, SignalKind.BLUETOOTH, classHint = 0x0400)))
        assertEquals(DeviceType.HOTSPOT, DeviceClassifier.classify(SignalInfo("d", "Andy's iPhone", -60, SignalKind.WIFI)))
        e.onBluetooth(listOf(SignalInfo("a", "Skullcandy Indy Evo", -60, SignalKind.BLE), SignalInfo("b", "Galaxy Watch5", -70, SignalKind.BLE)), now)
        assertEquals(2, e.g.devices.size)
        val ev = e.onBluetooth(listOf(SignalInfo("a", "Skullcandy Indy Evo", -50, SignalKind.BLE, connected = true)), now + 60_000)
        val rec = e.g.devices["BLE:a"]!!
        assertEquals(1, rec.timesConnected)
        assertTrue(ev.any { it is GameEvent.Say && (it as GameEvent.Say).text.contains("Connected") })
        assertTrue("connect_1" in e.g.achievements)
        e.onCells(listOf(SignalInfo("310-260-1234", "T-Mobile LTE", -95, SignalKind.CELL)), now)
        assertEquals(1L, e.g.count(C.CELLS))
    }

    @Test fun friendsAndLinkCodes() {
        val e = hatched(); val now = t0 + 20 * 60_000L; e.tick(now)
        e.g.owner.name = "Andy"
        val code = Codec.linkCode(e.myCard())
        assertTrue(code.startsWith("SCANIMAL1:"))
        val parsed = Codec.parseLink("hey add me $code thanks")!!
        assertEquals("Rex", parsed.name); assertEquals(Species.DINO, parsed.species); assertEquals("Andy", parsed.owner)
        assertTrue(e.addFriend(parsed, now, true).any { it is GameEvent.Say }, "own card is refused politely")
        assertEquals(0, e.g.friends.size)
        val other = Friend("ZZZZ1111", "Mochi", "Sam", Species.CAT, Sex.FEMALE, 30f, Pattern.SPOTS, 4, "bow", 0)
        e.addFriend(other, now, true)
        assertEquals(1, e.g.friends.size)
        e.interact("ZZZZ1111", "play", now)
        assertEquals(1L, e.g.count(C.PLAYDATES))
        val pc = Codec.postcard("ZZZZ1111", "Mochi", e.g.myId, "hi Rex!", now)
        val p = Codec.parsePostcard(pc)!!
        e.addMessage(p.fromId, p.text, false, now)
        assertEquals("hi Rex!", e.g.friends[0].messages[0].text)
        e.onNearbyBuddy(Friend("NEAR0001", "Zap", "", Species.ALIEN, Sex.MYSTERY, 140f, Pattern.PLAIN, 2, null, 0), -55, now)
        assertTrue("nearby_1" in e.g.achievements)
        val back = Codec.decode(Codec.encode(e.g))
        assertEquals(2, back.friends.size)
        assertEquals(1, back.friends[0].messages.size)
        assertEquals(e.g.devices.size, back.devices.size)
        assertEquals(Codec.encode(e.g), Codec.encode(back))
    }

    @Test fun bumps() {
        val e = hatched(); val now = t0 + 20 * 60_000L; e.tick(now)
        assertTrue(e.onBump(1.2f, now).isNotEmpty())
        assertTrue(e.onBump(1.2f, now + 100).isEmpty(), "rate limited")
    }
}

class AdvertTest {
    @Test fun roundTrip() {
        val f = Friend("AB12CD34", "Mochiko!", "Sam", Species.CAT, Sex.FEMALE, 30f, Pattern.SPOTS, 12, "crown", 0)
        val bytes = BuddyAdvert.encode(f)
        assertTrue(bytes.size <= 24, "fits legacy advert (${bytes.size})")
        val back = BuddyAdvert.decode(bytes)!!
        assertEquals("AB12CD34", back.id); assertEquals("Mochiko!", back.name); assertEquals(Species.CAT, back.species)
        assertEquals(12, back.level); assertEquals("crown", back.accessory); assertTrue(kotlin.math.abs(back.hue - 30f) < 2f)
        assertNull(BuddyAdvert.decode(byteArrayOf(1, 2, 3)))
        val long = BuddyAdvert.encode(f.also { it.name = "Ünïcödé Name" })
        assertTrue(long.size <= 24)
        assertNotNull(BuddyAdvert.decode(long))
    }
}

class DspTest {
    @Test fun fftFindsTone() {
        val n = 1024; val rate = 22050.0; val f = 1000.0
        val s = FloatArray(n) { (kotlin.math.sin(2 * Math.PI * f * it / rate)).toFloat() }
        val spec = Dsp.spectrum(s)
        val peak = spec.indices.maxByOrNull { spec[it] }!!
        val peakHz = peak * rate / n
        assertTrue(kotlin.math.abs(peakHz - f) < 30, "peak at $peakHz")
        assertEquals(6, Dsp.wifiChannel(2437)); assertEquals(36, Dsp.wifiChannel(5180)); assertEquals(1, Dsp.wifiChannel(5955))
    }
}

class EnvTest {
    private val t0 = 1_760_000_000_000L
    private fun hatched(): Engine {
        val e = Engine(GameData(), Random(5)); e.newEgg(t0)
        e.layEgg("Rex", Sex.MALE, Species.DINO, t0); e.tick(t0 + 16 * 60_000L)
        return e
    }

    @Test fun biomeWeatherMotion() {
        assertEquals(Biome.WILDERNESS, Env.biomeFor(0))
        assertEquals(Biome.CITY, Env.biomeFor(20))
        assertEquals(Biome.HOTSPOT, Env.biomeFor(50))
        assertEquals(Weather.STORMY, Env.weatherFor(1005f, 1013f))
        assertEquals(Weather.CLEAR, Env.weatherFor(1016f, 1013f))
        assertEquals(Weather.UNKNOWN, Env.weatherFor(0f, 1013f))
        assertEquals(Motion.VEHICLE, Env.motionFor(12f, 0f, false))
        assertEquals(Motion.RUNNING, Env.motionFor(0f, 150f, false))
        assertEquals(Motion.WALKING, Env.motionFor(1.2f, 40f, false))
        assertEquals(Motion.POCKET, Env.motionFor(0f, 0f, true))
        assertEquals("mountains", Env.altitudeZone(2000.0))
    }

    @Test fun environmentAwardsStickers() {
        val e = hatched()
        val before = e.g.stickers.size
        e.onEnvironment(50, 1013f, 0f, 0f, false, t0 + 17 * 60_000L)       // hotspot biome
        e.onEnvironment(50, 1004f, 0f, 0f, false, t0 + 40 * 60_000L)       // storm
        e.onEnvironment(50, 1004f, 12f, 0f, false, t0 + 60 * 60_000L)      // vehicle
        assertTrue(e.g.stickers.size > before)
        assertTrue("biome:HOTSPOT" in e.g.stickers)
        assertTrue("motion:VEHICLE" in e.g.stickers)
        assertTrue(e.g.stickers.any { it.startsWith("wx:") })
        assertTrue("sp:DINO" in e.g.stickers, "species sticker on hatch")
    }

    @Test fun questsRollAndClaim() {
        val e = hatched()
        e.rollQuestsIfNeeded(t0 + 17 * 60_000L)
        assertEquals(3, e.g.quests.size)
        // Force one quest to completion by feeding its counter.
        val q = e.g.quests.first()
        e.g.counters[q.counterKey] = q.baseline + q.target
        assertTrue(Quests.done(q, e.g))
        val coins = e.g.coins
        val ev = e.claimQuest(q.id, t0 + 20 * 60_000L)
        assertTrue(ev.isNotEmpty())
        assertTrue(e.g.coins > coins)
        assertTrue(e.g.quests.first { it.id == q.id }.claimed)
        assertEquals(1L, e.g.count(C.QUESTS_DONE))
        assertTrue(e.claimQuest(q.id, t0).isEmpty(), "can't double-claim")
    }

    @Test fun newFieldsSurviveCodec() {
        val e = hatched()
        e.onEnvironment(20, 1013f, 1.2f, 40f, false, t0 + 20 * 60_000L)
        e.mapDiscovery(40.0, -74.0, "wifi", "HomeNet", t0)
        e.rollQuestsIfNeeded(t0)
        val back = Codec.decode(Codec.encode(e.g))
        assertEquals(Codec.encode(e.g), Codec.encode(back))
        assertEquals(e.g.stickers, back.stickers)
        assertEquals(1, back.discoveries.size)
        assertEquals(3, back.quests.size)
        assertEquals(e.g.biome, back.biome)
    }

    @Test fun stickerRegistryCoversEnums() {
        assertTrue(Stickers.all.size > 40)
        assertNotNull(Stickers.byId["food:GROCERY"])
        assertNotNull(Stickers.byId["dev:EARBUDS"])
        assertNotNull(Stickers.byId["sp:ALIEN"])
    }
}

