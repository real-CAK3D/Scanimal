package com.realcak3d.scanimal.ui

import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Stage

/**
 * v2 animation: three sheets per life stage — move / act / face — each a 6x6 grid (row = action).
 *   assets/sprites/anim/<species>_<stage>_<pack>.webp
 * The app auto-detects how many frames a row uses (trailing cells are transparent), so frame counts can
 * vary per sheet. A behavior state resolves to one (pack, row) to play; see [resolve].
 */
object AnimSheets {
    const val ROWS = 6
    const val COLS = 6

    enum class Pack(val file: String) { MOVE("move"), ACT("act"), FACE("face") }

    // Row order inside each pack (must match the generated sheets / docs-anim-v2.md).
    // MOVE: 0 idle_front, 1 walk_side(right), 2 idle_side, 3 walk_front, 4 walk_back, 5 turn
    // ACT : 0 bored, 1 interested, 2 scan, 3 play, 4 eat, 5 sleep
    // FACE: 0 talk, 1 happy, 2 sad, 3 angry, 4 surprised, 5 sleepy

    /** A chosen clip: which sheet, which row, and how fast to play it. */
    data class Shot(val pack: Pack, val row: Int, val fps: Float, val mirrorable: Boolean)

    private val happy = setOf(Anim.HAPPY, Anim.LEVEL_UP, Anim.LOVE, Anim.HATCH)

    /**
     * Behavior state machine → the clip to play. [bored] = idle a long time, [talking] = a speech
     * bubble is up. Only side-view walking mirrors with the facing flip; front/face clips don't care.
     */
    fun resolve(walking: Boolean, anim: Anim, asleep: Boolean, bored: Boolean, talking: Boolean): Shot = when {
        asleep -> Shot(Pack.ACT, 5, 2f, false)            // sleep, curled
        talking -> Shot(Pack.FACE, 0, 5f, false)          // mouth flap
        anim == Anim.EAT -> Shot(Pack.ACT, 4, 4f, false)  // eat
        anim == Anim.ANGRY -> Shot(Pack.FACE, 3, 3.5f, false)
        anim == Anim.SAD || anim == Anim.SICK -> Shot(Pack.FACE, 2, 3f, false)
        anim == Anim.SHOCK -> Shot(Pack.FACE, 4, 4f, false)       // surprised
        anim in happy -> Shot(Pack.FACE, 1, 4f, false)           // happy
        anim == Anim.DANCE -> Shot(Pack.ACT, 3, 5f, true)        // play
        anim == Anim.SPARKLE || anim == Anim.CLEAN -> Shot(Pack.ACT, 1, 4f, false) // interested / found something
        walking -> Shot(Pack.MOVE, 1, 5f, true)                  // walk, side view
        bored -> Shot(Pack.ACT, 0, 3f, false)                    // loiter
        else -> Shot(Pack.MOVE, 0, 3f, false)                    // idle, facing the user
    }
}
