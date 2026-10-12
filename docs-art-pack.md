# Scanimal hacker art pack

AI-generated, semi-realistic "hacker buddy" art. Drop-in for the existing `SpriteStore` pipeline.

## Sprites — `app/src/main/assets/sprites/` (115 files, 512×512 transparent WebP, feet on bottom edge)

| Set | Files | When to show |
|---|---|---|
| Base | `<species>_<stage>.webp` (55) | Normal home screen. Already picked up by `SpriteStore.image()` |
| Scuba | `<species>_<stage>_scuba.webp` (55, all 11 species) | A buddy visiting the **fish's** underwater screen wears scuba. The **fish** also wears scuba when it visits any other buddy's (land) screen |
| Fishbowl | `fish_<stage>_bowl.webp` (5) | Alternate look for the fish visiting land (fishbowl helmet + scuba). Optional |

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
