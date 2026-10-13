# Scanimal hacker art pack

AI-generated, semi-realistic "hacker buddy" art. Drop-in for the existing `SpriteStore` pipeline.

## Sprites — `app/src/main/assets/sprites/` (117 files, 512×512 transparent WebP, feet on bottom edge)

| Set | Files | When to show |
|---|---|---|
| Base | `<species>_<stage>.webp` (55) | Normal home screen. Already picked up by `SpriteStore.image()` |
| Scuba | `<species>_<stage>_scuba.webp` (55, all 11 species) | A buddy visiting the **fish's** underwater screen wears scuba. The **fish** also wears scuba when it visits any other buddy's (land) screen |
| Fishbowl | `fish_<stage>_bowl.webp` (5) | Alternate look for the fish visiting land (fishbowl helmet + scuba). Optional |
| Bubbles FX | `fx_bubble.webp` (256², one bubble) · `fx_bubbles.webp` (519×1024, rising column) | Underwater particles: spawn `fx_bubble` scaled 8-40 dp rising from the regulator / mouth, overlay `fx_bubbles` drifting up occasionally |

species: `blob dino dog cat chameleon plant fish bird human alien robot` · stage: `baby child teen adult elder`

Stage gear progression (for animation/phrase hooks): baby = USB-plug pacifier, child = headphones + crossed arms,
teen = backwards cap + wrist radar, adult = AR visor + handheld scanner, elder = tattered cloak + glowing cane.

## Home backgrounds — `app/src/main/assets/backgrounds/<species>.webp` (11, 1080×1920 portrait)

One home scene per species: fish = underwater seafloor (fish always lives underwater), robot = workshop,
cat = cat-tree lounge, dog = junkyard doghouse, dino = jungle ruins with fossils, chameleon = jungle branch,
plant = greenhouse, bird = treehouse deck, human = hacker apartment, alien = alien-planet outpost,
blob = signal command center. Each leaves an open floor area in the lower half for the buddy.

## Wiring suggestions
- `SpriteStore`: add a `variant` param (`""`, `"_scuba"`, `"_bowl"`) and try `sprites/${sp}_${st}${variant}.webp` first.
- Variant rule: host screen is fish → visitor uses `_scuba`; visitor is fish and host isn't → fish uses `_scuba` (or `_bowl`).
- Draw `backgrounds/<host species>.webp` behind the room (center-crop to screen).
- Underwater (fish home): add rising air-bubble particles from the buddy's regulator / mouth; slow sway bob instead of hop.
- Sprites are drawn bigger now: consider `w = bw * 2.6` in `drawBuddy` since the art has no empty margins at the sides.

Note: `fish_<stage>.webp` is the fish at home (no scuba, underwater background). `fish_<stage>_scuba.webp` is the fish visiting a land buddy.

## Animation frames (the app now plays these)

To make a species actually walk / eat / idle (not just a transformed still), drop frame
sequences here:

    app/src/main/assets/sprites/anim/<species>_<stage>_<action>_<n>.webp   (n starts at 1)

- actions: `idle`, `walk`, `eat`, `happy`, `sleep`
- same framing/size as the base sprites (512×512, transparent, feet on the bottom edge, no side margins)
- number frames from 1 with no gaps; the app loops them at ~10 fps and stops at the first missing n (max 16)
- any action with no frames falls back to the single `<species>_<stage>.webp` plus procedural motion,
  so you can ship one action (e.g. `walk`) for one species and it just works
- a good first set per species/stage: `walk` 6–8 frames (contact→passing→contact for each leg),
  `idle` 2–4 frames (breathing/blink), `eat` 4–6, `happy` 4–6, `sleep` 2

Pilot ask: start with one species' `walk` and `idle` at the ADULT stage so we can see a real walk cycle
end-to-end, then fan out to the other stages/species.
