package com.realcak3d.scanimal.ui

import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Stage

/** One action's position in a stage sheet: which row, and how many frames it uses (left to right). */
data class ActionDef(val name: String, val row: Int, val frames: Int)

/**
 * The fixed, canonical layout of the per-life-stage animation sheets (see docs-anim-sheets.md).
 * Every species conforms to the same 6-col x 5-row grid, so no per-species config is needed — the app
 * slices row `row` for an action and plays `frames` cells. Row order here IS the sheet row order.
 */
object AnimSheets {
    const val ROWS = 5
    const val COLS = 6

    val layout: Map<Stage, List<ActionDef>> = mapOf(
        Stage.BABY to listOf(
            ActionDef("cry", 0, 4), ActionDef("crawl", 1, 6), ActionDef("situp", 2, 4),
            ActionDef("stand", 3, 4), ActionDef("walk", 4, 6),
        ),
        Stage.CHILD to listOf(
            ActionDef("idle", 0, 4), ActionDef("walk", 1, 6), ActionDef("jump", 2, 5),
            ActionDef("sit", 3, 4), ActionDef("play", 4, 6),
        ),
        Stage.TEEN to listOf(
            ActionDef("idle", 0, 4), ActionDef("walk", 1, 6), ActionDef("turn", 2, 5),
            ActionDef("climb", 3, 6), ActionDef("showoff", 4, 6),
        ),
        Stage.ADULT to listOf(
            ActionDef("idle", 0, 4), ActionDef("walk", 1, 6), ActionDef("jump", 2, 5),
            ActionDef("tongue", 3, 6), ActionDef("colorshift", 4, 6),
        ),
        Stage.ELDER to listOf(
            ActionDef("idle", 0, 4), ActionDef("walk", 1, 6), ActionDef("sit", 2, 4),
            ActionDef("stretch", 3, 6), ActionDef("yawn", 4, 5),
        ),
    )

    fun find(stage: Stage, action: String): ActionDef? = layout[stage]?.firstOrNull { it.name == action }

    /** The baby grows up through its first day: cry -> crawl -> sit up -> stand -> walk. */
    private val babyMilestones = listOf("cry", "crawl", "situp", "stand", "walk")

    private fun List<String>.firstPresent(vararg cands: String): String? = cands.firstOrNull { it in this }

    /**
     * The behavior state machine: pick the action name to play for the current situation, then map it to
     * the best available row for this stage (falling back to "idle"). [babyProgress] is 0..1 through the
     * baby stage; [bored] = idle a long time with nothing happening; [talking] = a speech bubble is up.
     */
    fun chooseAction(
        stage: Stage, walking: Boolean, anim: Anim, asleep: Boolean, babyProgress: Float,
        bored: Boolean = false, talking: Boolean = false,
    ): String {
        val names = layout[stage]?.map { it.name } ?: return "idle"
        if (stage == Stage.BABY) {
            val idx = (babyProgress * babyMilestones.size).toInt().coerceIn(0, babyMilestones.size - 1)
            return if (walking && idx >= 4) "walk" else babyMilestones[idx]
        }
        val want = when {
            asleep -> names.firstPresent("sleep", "sit", "idle") ?: "idle"
            anim == Anim.EAT -> names.firstPresent("eat", "idle") ?: "idle"
            anim == Anim.HAPPY || anim == Anim.LEVEL_UP || anim == Anim.LOVE || anim == Anim.DANCE || anim == Anim.HATCH ->
                names.firstPresent("jump", "play", "showoff") ?: "idle"
            // Found a signal / curious → a little flourish (interested / scanning).
            anim == Anim.SPARKLE || anim == Anim.CLEAN ->
                names.firstPresent("tongue", "colorshift", "showoff", "climb", "play") ?: "idle"
            walking -> "walk"
            // Nothing going on for a while → loiter with a distinct idle move.
            bored && !talking -> names.firstPresent("sit", "yawn", "stretch", "colorshift", "showoff") ?: "idle"
            else -> "idle"
        }
        return if (want in names) want else "idle"
    }
}
