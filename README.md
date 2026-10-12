# Scanimal 🐾📡

A pocket buddy for Android, tamagotchi-style, that you raise by **scanning the world around you**. Feed it barcodes, QR codes, shipping labels, tickets and NFC tags. Take it on **walks**. It sniffs **Wi-Fi**, **Bluetooth** and **cell towers** with a pulsing, vibrating **radar**, stargazes at **GPS satellites**, and meets **other Scanimals nearby**. It talks back with an old-school, rule-based phrase brain. There's no AI service and no account, and everything is saved on your phone.

## Download

Get `Scanimal-x.y.z.apk` from [Releases](../../releases/latest). Open it on your phone and allow "Install unknown apps" when asked. After that, the app checks for updates itself. You can turn this off in Settings → Updates.

## Getting started

1. **Intro.** Tell your buddy a little about yourself: name, birthday, favorite color and food, hobby, pet. It remembers these and brings them up in conversation, and it throws a party on your birthday.
2. **Choose a species:** Blip, Dino, Dog, Cat, Chameleon, Sprout (grows from sprout to flower to tree), Fish, Bird, Human, Alien or Robot. Every egg also rolls a random color, pattern and personality.
3. **Name it and pick a sex** (boy, girl or mystery).
4. **Incubate.** The egg takes about 15 minutes to hatch. Tap it to warm it, and scan things to give it energy and hatch it faster.
5. **Raise it.** It grows from Baby to Child, Teen, Adult and Elder (1 real day = 1 buddy year).

## Features

**Life sim**
- Vitals: fullness, happiness, energy, hygiene and health. Traits: fitness, smarts, social and discipline. Also weight.
- Poop, sickness, medicine, baths, sleep, tantrums (scold or praise) and night-time auto-sleep
- **Lives:** 3 to start, up to 5. Neglect costs a life. When all are gone, a new generation starts, and achievements, coins, items and friends carry over. A Hall of Memories lists past buddies.
- Levels, XP, coins, a journal and 85 achievements

**Feeding by scanning** (works offline)
- Every code always makes the same food: name, family, rarity (Common to Legendary) and a trait (Healthy, Junk, Spicy, Spoiled and others).
- Grocery barcodes can look up the real product name and Nutri-Score from Open Food Facts. You can turn this off.
- QR codes for links, Wi-Fi, contacts and maps each taste different. Shipping labels, tickets and **NFC tags** count as their own food types.

**Walks and the outdoors**
- GPS **distance traveled**, walk sessions (distance, time, steps, pace) and a step counter
- Walking or running gives exercise. Car rides still count toward distance, but they count as road trips, not exercise.
- **Background mode** (optional): keeps counting walks, steps and signals with the app closed.

**Signals**
- **Radar:** pulsing pings and vibration that speed up as you get closer to a signal. Turn in a circle and it estimates which direction the signal is coming from.
- **Device catalog:** every Bluetooth device, Wi-Fi network and cell tower it finds. It guesses what each one is (earbuds, watch, TV, car, tracker, speaker and others) and counts how often it has been seen and connected.
- Each kind of device has its own effect on your buddy. Being **connected** to a device or network gives a bigger bonus.
- **Spectrum lab:** Wi-Fi channel analyzer (2.4, 5 and 6 GHz), signal-strength history graph, microphone oscilloscope with FFT spectrum and note detection, and an EMF (magnetometer) graph
- **Sky:** GPS, GLONASS, Galileo, BeiDou, QZSS, SBAS and NavIC satellites plotted overhead

**Friends**
- **Link codes:** a QR code and a text code you can share through any app. Friends scan or paste the code to add your buddy.
- **Nearby:** discoverable Scanimals find each other over Bluetooth LE, with no internet needed. You can turn discoverable on or off.
- Up to **5 visiting buddies** can hang out on your home screen. Wave, hug, play, give gifts, have dance-offs and send messages.
- Messages go directly over Bluetooth when the friend is nearby. Otherwise the app makes a postcard code you can send any way you like.

**Fun**
- **Physics:** tilt to slide your buddy and shake to fling it off the walls.
- **8 themes:** Radar, Sunset, Ocean, Forest, Retro LCD, Candy, Daylight and Midnight
- 15 hats, 9 outfits and 6 paint jobs in the shop
- Mini-games: Byte Catcher, Signal Simon, Quick Blip and Code Match
- ELIZA-style chat with memory, jokes, riddles, stories, songs, fortunes, guess-the-number, rock-paper-scissors, dice and math
- Sensors: light (sleepy in the dark), proximity (peekaboo), barometer and altitude, magnet detector, charging, microphone (dance party), NFC

**Settings:** updates, information about you, theme, physics, visitors, chatter, background mode, discoverable, units, product lookup, reminders, vibration, beeps, a stats summary, backup export and import, permissions, and reset.

## Build

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # game-rule tests
```

Requires JDK 17+ and the Android SDK (compileSdk 35, minSdk 26).

**Release:** change `version.txt` (for example to `1.0.1`) and push to `main`. GitHub Actions then builds a signed APK and publishes a GitHub Release `v1.0.1`, and installed apps pick it up through the in-app updater. Pushing a `v*` tag works too.

**Signing:** CI uses the secrets `SCANIMAL_KEYSTORE` (path), `SCANIMAL_KEYSTORE_PASSWORD`, `SCANIMAL_KEY_ALIAS` and `SCANIMAL_KEY_PASSWORD` when they are set. Otherwise it falls back to the bundled hobby keystore `app/scanimal-release.jks` (password `scanimal`), so every release can update the last one. That key is public, so switch to your own before distributing widely. Note that changing keys means uninstalling once.

## Code map

| Path | What |
|---|---|
| `core/` | Pure Kotlin game rules (unit-tested): `Engine`, `Food`, `Phrases`, `Chat`, `Achievements`, `Items`, `Devices`, `RadarTracker`, `BuddyAdvert`, `Dsp`, `Codec` |
| `data/GameRepository` | Single source of truth and save file |
| `sensors/` | Wi-Fi, Bluetooth, cells, GNSS, motion and environment sensors, mic, distance tracker, BLE buddy link, haptics |
| `ui/` | Jetpack Compose screens, the vector buddy renderer (`BuddyView`, `SpeciesArt`), physics, themes |
| `work/` | Care reminders, background service, GitHub updater |
