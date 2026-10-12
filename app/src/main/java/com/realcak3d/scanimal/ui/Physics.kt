package com.realcak3d.scanimal.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A tiny 2D physics toy for the buddy: tilt makes it slide, shakes fling it, walls and floor bounce it.
 * Units: x in -1..1 of the room half-width, y in "room heights" above the floor (0 = standing).
 */
class BuddyPhysics {
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var spin = 0f; var squash = 0f
    var onBump: ((Float) -> Unit)? = null

    fun step(dtMs: Long, tiltX: Float, linX: Float, linY: Float, enabled: Boolean, asleep: Boolean) {
        val dt = min(dtMs, 40L) / 1000f
        if (!enabled) { x *= 0.9f; y = 0f; vx = 0f; vy = 0f; spin *= 0.8f; return }
        // Tilt: gravity component along the screen's x axis (phone held upright).
        val slide = if (asleep) 0.25f else 1f
        vx += (-tiltX / 9.81f) * 2.2f * slide * dt
        // Shakes: sharp linear acceleration flings the buddy.
        val jolt = kotlin.math.sqrt(linX * linX + linY * linY)
        if (jolt > 6f) {
            vx += (-linX / 30f).coerceIn(-1.4f, 1.4f)
            vy += (abs(linY) / 25f + jolt / 60f).coerceIn(0f, 1.6f)
        }
        // Gravity
        vy -= 4.5f * dt
        x += vx * dt; y += vy * dt
        spin += vx * 220f * dt
        // Floor
        if (y < 0f) {
            if (vy < -0.6f) { onBump?.invoke(min(1.5f, -vy / 2.2f)); squash = min(1f, -vy / 3f) }
            y = 0f; vy = -vy * 0.42f
            if (abs(vy) < 0.15f) vy = 0f
            vx *= 0.92f
            spin *= 0.6f
        }
        // Walls
        if (x < -1f || x > 1f) {
            if (abs(vx) > 0.5f) { onBump?.invoke(min(1.5f, abs(vx) / 2f)); squash = min(1f, abs(vx) / 3f) }
            x = x.coerceIn(-1f, 1f); vx = -vx * 0.55f
        }
        // Ceiling
        if (y > 1.1f) { y = 1.1f; vy = -abs(vy) * 0.5f }
        // Settle the spin and squash; drift back to center when calm.
        if (y == 0f) {
            spin += (-spin) * min(1f, dt * 6f)
            if (abs(vx) < 0.05f) vx += -x * 0.6f * dt
        }
        squash = max(0f, squash - dt * 3f)
        vx *= (1f - 0.5f * dt)
    }
}
