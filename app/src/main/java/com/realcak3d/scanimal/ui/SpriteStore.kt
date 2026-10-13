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

    /**
     * An animation frame sequence for a species/stage/action, from
     *   assets/sprites/anim/<species>_<stage>_<action>_1.webp, _2.webp, ...
     * Returns the frames in order (empty if none are bundled, so callers fall back to the single sprite
     * plus procedural motion). Actions: "idle", "walk", "eat", "happy", "sleep".
     */
    fun frames(ctx: Context, species: Species, stage: Stage, action: String): List<ImageBitmap> {
        val sp = species.name.lowercase(); val st = stage.name.lowercase()
        val key = "${sp}_${st}_$action"
        frameCache[key]?.let { return it }
        // 1) Preferred: slice the action's row out of the stage sheet (the modular standard).
        val def = AnimSheets.find(stage, action)
        if (def != null) {
            val sheetImg = sheet(ctx, species, stage)
            if (sheetImg != null) {
                val sliced = sliceRow(sheetImg, def.row, def.frames)
                if (sliced.isNotEmpty()) { frameCache[key] = sliced; return sliced }
            }
        }
        // 2) Fallback: loose per-frame files <species>_<stage>_<action>_<n>.webp.
        val list = ArrayList<ImageBitmap>()
        for (i in 1..16) {
            val img = load(ctx, "sprites/anim/${key}_$i.webp") ?: load(ctx, "sprites/anim/${key}_$i.png") ?: break
            list.add(img)
        }
        frameCache[key] = list
        return list
    }

    private val sheetCache = HashMap<String, ImageBitmap?>()

    /** The whole per-stage animation sheet (6x5 grid), or null if none is bundled. */
    private fun sheet(ctx: Context, species: Species, stage: Stage): ImageBitmap? {
        val keyName = "${species.name.lowercase()}_${stage.name.lowercase()}"
        if (sheetCache.containsKey(keyName)) return sheetCache[keyName]
        val img = load(ctx, "sprites/anim/$keyName.webp") ?: load(ctx, "sprites/anim/$keyName.png")
        sheetCache[keyName] = img
        return img
    }

    /** Crop [count] square cells from [row] of a 6x5 grid sheet (cell = height / rows). */
    private fun sliceRow(sheet: ImageBitmap, row: Int, count: Int): List<ImageBitmap> {
        val src = runCatching { sheet.asAndroidBitmap() }.getOrNull() ?: return emptyList()
        val cell = src.height / AnimSheets.ROWS
        if (cell <= 0) return emptyList()
        val out = ArrayList<ImageBitmap>(count)
        for (f in 0 until count) {
            val x = f * cell; val y = row * cell
            if (x + cell > src.width || y + cell > src.height) break
            out.add(runCatching { android.graphics.Bitmap.createBitmap(src, x, y, cell, cell).asImageBitmap() }.getOrNull() ?: break)
        }
        return out
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
