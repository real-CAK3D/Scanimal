# Scanimal semi-realistic sprite pack (drop-in)

Scanimal renders bundled raster sprites when present, else its built-in vector art.
Add images here, rebuild, and turn on **Settings → Appearance → Sprite art**:

    app/src/main/assets/sprites/

## Files
Transparent background, square, ~512×512, the creature centered with its feet near the bottom edge
(the engine anchors the sprite on the ground and applies bob / tilt / squash / bounce physics).

Preferred — one per life stage:
    <species>_<stage>.(webp|png)
Fallback — one per species (used for every stage):
    <species>.(webp|png)

species: blip dino dog cat chameleon plant fish bird human alien robot
stage:   baby child teen adult elder

Examples: dino_adult.webp, cat_baby.png, robot.webp

## Art direction (semi-realistic, friendly)
- Soft studio lighting from upper-left, gentle rim light, contact shadow baked out (the app draws the shadow).
- Readable silhouette per species; expressive but not photoreal; think "stylized 3D render / claymation".
- Consistent camera: 3/4 front view, same framing across stages so growth reads as the same creature.

## Image-generator prompts (route 1 — you generate, drop the PNGs in)

Paste one of these into any image generator (Midjourney, DALL·E, SDXL, etc.).
Append this shared suffix to every prompt so the pack stays consistent and drops in cleanly:

> **Shared suffix:** `stylized 3D render, claymation vibe, soft studio key light from upper-left, subtle rim light, 3/4 front view, expressive friendly face, semi-realistic (not photoreal, not cute-chibi, not childish), full body with feet near the bottom edge, centered, transparent background, no ground shadow, no text, square 512x512`

Generate 5 life stages per species (baby → child → teen → adult → elder): same character, growing up — baby is rounder/softer with a big head, elder is weathered with greyer tones. Keep palette and markings identical across a species' stages.

| species | prompt (prepend to the shared suffix) |
|---|---|
| **dino** | a small theropod dinosaur, pebbled olive-green hide with amber belly, stubby arms, chunky tail, curious tilted head |
| **dog** | a scruffy mutt pup, warm tan fur with a darker muzzle, floppy ears, waggy posture, tongue slightly out |
| **cat** | a sleek house cat, grey tabby with faint stripes, green eyes, tail curled around its feet |
| **chameleon** | a round-eyed chameleon, teal-and-lime mottled skin, curled grippy tail, independently swiveling turret eyes |
| **plant** | a potted sprout creature, thick succulent-green leaves for a body, two leaf-arms, a single bud with a shy face |
| **fish** | a chubby pufferfish-like creature standing upright, coral-orange with white belly, big fins used like little hands |
| **bird** | a plump songbird, slate-blue feathers with a cream chest, stubby wings, alert bright eye and tiny beak |
| **human** | a tiny stylized explorer kid-adult, denim jacket and boots, tousled hair, confident grin, hands on hips |
| **alien** | a friendly grey-green alien, oversized glossy black eyes, smooth domed head, slim body, faint violet glow |
| **robot** | a boxy retro desk-robot, brushed-metal body, single round CRT eye, antenna, articulated clamp hands |
| **blip** (default blob) | a translucent gel blob creature, mint-green jelly body with a soft inner glow, two dot eyes, gummy little feet |

Save each as `app/src/main/assets/sprites/<species>_<stage>.webp` (or `.png`), or a single
`<species>.webp` to reuse one image for all stages. Rebuild and flip on **Sprite art** in Settings.
