package com.realcak3d.scanimal.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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

    private val frameCache = HashMap<String, List<ImageBitmap>>()
    private val sheetCache = HashMap<String, android.graphics.Bitmap?>()

    /** The whole pack sheet (6x6 grid) as an Android bitmap, or null if that pack isn't bundled. */
    private fun packSheet(ctx: Context, species: Species, stage: Stage, pack: String): android.graphics.Bitmap? {
        val keyName = "${species.name.lowercase()}_${stage.name.lowercase()}_$pack"
        if (sheetCache.containsKey(keyName)) return sheetCache[keyName]
        val img = (load(ctx, "sprites/anim/$keyName.webp") ?: load(ctx, "sprites/anim/$keyName.png"))
            ?.let { runCatching { it.asAndroidBitmap() }.getOrNull() }
        sheetCache[keyName] = img
        return img
    }

    /**
     * Frames of one action = a row from a 6x6 pack sheet. The cell size comes from the sheet
     * (height / 6) and the frame count is auto-detected (trailing transparent cells are skipped), so the
     * art can use different numbers of frames per row. Empty list → caller falls back to the single sprite.
     */
    fun packFrames(ctx: Context, species: Species, stage: Stage, pack: String, row: Int): List<ImageBitmap> {
        val key = "${species.name.lowercase()}_${stage.name.lowercase()}_${pack}_$row"
        frameCache[key]?.let { return it }
        val src = packSheet(ctx, species, stage, pack)
        if (src == null) { frameCache[key] = emptyList(); return emptyList() }
        val cell = src.height / AnimSheets.ROWS
        val out = ArrayList<ImageBitmap>(AnimSheets.COLS)
        if (cell > 0 && row in 0 until AnimSheets.ROWS) {
            for (c in 0 until AnimSheets.COLS) {
                val x = c * cell; val y = row * cell
                if (x + cell > src.width || y + cell > src.height) break
                if (!cellHasContent(src, x, y, cell)) break   // trailing transparent cell → end of this action
                out.add(runCatching { android.graphics.Bitmap.createBitmap(src, x, y, cell, cell).asImageBitmap() }.getOrNull() ?: break)
            }
        }
        frameCache[key] = out
        return out
    }

    /** True if a cell has any meaningfully opaque pixel (coarse stride sample for speed). */
    private fun cellHasContent(bmp: android.graphics.Bitmap, x0: Int, y0: Int, cell: Int): Boolean {
        val step = (cell / 20).coerceAtLeast(4)
        var y = y0
        while (y < y0 + cell) {
            var x = x0
            while (x < x0 + cell) {
                if ((bmp.getPixel(x, y) ushr 24) and 0xFF > 16) return true
                x += step
            }
            y += step
        }
        return false
    }

    /** A loose effect sprite from assets/sprites/<name>.webp (e.g. "fx_bubble", "fx_bubbles"). */
    fun fx(ctx: Context, name: String): ImageBitmap? {
        val key = "fx_$name"
        if (cache.containsKey(key)) return cache[key]
        val img = load(ctx, "sprites/$name.webp") ?: load(ctx, "sprites/$name.png")
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
