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
