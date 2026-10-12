package com.realcak3d.scanimal.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.realcak3d.scanimal.core.Species
import com.realcak3d.scanimal.core.Stage

/**
 * Optional raster sprite art. If semi-realistic sprites are bundled under assets/sprites/, the buddy
 * renders them; otherwise it falls back to the built-in vector art. Nothing breaks if no art is present.
 *
 * Expected files (transparent PNG or WebP, ~512×512, feet near the bottom edge):
 *   sprites/<species>_<stage>.webp   e.g. dino_adult.webp, cat_baby.webp   (preferred, per life-stage)
 *   sprites/<species>.webp           e.g. dino.webp                         (fallback for all stages)
 * Species ids (lowercase): blip, dino, dog, cat, chameleon, plant, fish, bird, human, alien, robot.
 * Stage ids: baby, child, teen, adult, elder.
 */
object SpriteStore {
    private val cache = HashMap<String, ImageBitmap?>()
    @Volatile private var scanned = false
    @Volatile private var anyPresent = false

    /** True if at least one sprite file is bundled, so the UI can offer the toggle. */
    fun anyInstalled(ctx: Context): Boolean {
        if (!scanned) {
            scanned = true
            anyPresent = runCatching { ctx.assets.list("sprites")?.any { it.endsWith(".webp") || it.endsWith(".png") } }.getOrNull() == true
        }
        return anyPresent
    }

    /**
     * @param variant "" for the normal look, "_scuba" for a buddy on the fish's underwater screen
     *   (or the fish visiting land), "_bowl" for the fish's optional fishbowl look on land.
     * Falls back to the plain sprite if a variant file is missing, then to the per-species image.
     */
    fun image(ctx: Context, species: Species, stage: Stage, variant: String = ""): ImageBitmap? {
        val sp = species.name.lowercase(); val st = stage.name.lowercase()
        val key = "${sp}_$st$variant"
        if (cache.containsKey(key)) return cache[key]
        var img = load(ctx, "sprites/$key.webp") ?: load(ctx, "sprites/$key.png")
        if (img == null && variant.isNotEmpty()) img = image(ctx, species, stage, "") // variant missing → plain
        if (img == null) img = load(ctx, "sprites/$sp.webp") ?: load(ctx, "sprites/$sp.png")
        cache[key] = img
        return img
    }

    /** A home-scene background for a species, from assets/backgrounds/<species>.webp. */
    fun background(ctx: Context, species: Species): ImageBitmap? {
        val sp = species.name.lowercase()
        val key = "bg_$sp"
        if (cache.containsKey(key)) return cache[key]
        val img = load(ctx, "backgrounds/$sp.webp") ?: load(ctx, "backgrounds/$sp.png")
        cache[key] = img
        return img
    }

    private fun load(ctx: Context, path: String): ImageBitmap? =
        runCatching { ctx.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
}
