package com.realcak3d.scanimal.sensors

import android.location.Location
import com.realcak3d.scanimal.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/** Live walk session shown on the home screen. */
data class WalkSession(val active: Boolean = false, val startedAt: Long = 0L, val meters: Double = 0.0, val steps: Int = 0)

/**
 * Turns GPS fixes into trustworthy distance (filters jitter and jumps). Shared by the foreground
 * app and the background service so the same movement is never counted twice.
 */
object DistanceTracker {
    private var last: Location? = null
    private val _walk = MutableStateFlow(WalkSession())
    val walk: StateFlow<WalkSession> = _walk.asStateFlow()

    fun startWalk(repo: GameRepository) {
        _walk.value = WalkSession(true, System.currentTimeMillis())
        repo.act { now -> startWalk(now) }
    }

    fun stopWalk(repo: GameRepository): WalkSession {
        val w = _walk.value
        _walk.value = WalkSession()
        if (w.active && w.meters > 50) {
            val mins = (System.currentTimeMillis() - w.startedAt) / 60_000L
            repo.act { now ->
                gainXp((w.meters / 50).toInt().coerceAtMost(200), now) +
                    listOf(com.realcak3d.scanimal.core.GameEvent.Say("Great walk! ${fmtDist(w.meters.toLong())} in $mins min. My legs are happy! 🦮", com.realcak3d.scanimal.core.Anim.LOVE))
            }
        }
        return w
    }

    fun addSteps(n: Int) {
        val w = _walk.value
        if (w.active) _walk.value = w.copy(steps = w.steps + n)
    }

    @Synchronized
    fun feed(repo: GameRepository, loc: Location) {
        if (loc.hasAccuracy() && loc.accuracy > 40f) return
        val prev = last
        if (prev == null || loc.time <= prev.time) { if (prev == null) last = loc; return }
        val d = prev.distanceTo(loc).toDouble()
        val dt = (loc.time - prev.time) / 1000.0
        if (d < max(10.0, (loc.accuracy * 0.8).toDouble())) return  // GPS jitter while standing still
        last = loc
        val speed = d / dt
        if (speed > 70 || dt > 600) return                            // teleport / long gap: just re-anchor
        val onFoot = speed < 4.0
        val w = _walk.value
        if (w.active) _walk.value = w.copy(meters = w.meters + d)
        repo.act { now -> onDistance(d, now, onFoot) }
    }
}
