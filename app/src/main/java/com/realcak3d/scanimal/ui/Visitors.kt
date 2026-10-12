package com.realcak3d.scanimal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Friend
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.SensorHub
import kotlin.math.sin

/** Up to 5 friend buddies hanging out in your room. Tap one to interact. */
@Composable
fun VisitorLayer(repo: GameRepository, hub: SensorHub, friends: List<Friend>, nearbyIds: Set<String>, go: (Screen) -> Unit, modifier: Modifier = Modifier) {
    if (friends.isEmpty()) return
    var t by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) withFrameMillis { t = it } }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var reaction by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val now = System.currentTimeMillis()
    BoxWithConstraints(modifier) {
        val w = maxWidth
        friends.take(5).forEachIndexed { i, f ->
            val seed = (f.id.hashCode() and 0xFFFF) / 65535f
            val slot = listOf(0.1f, 0.9f, 0.22f, 0.78f, 0.5f)[i]
            val wander = sin(t / 1000f * (0.25f + seed * 0.3f) + seed * 10f) * 0.07f
            val x = (slot + wander).coerceIn(0.04f, 0.96f)
            val size = 74.dp
            val anim = if (reaction?.first == f.id && now - (reaction?.second ?: 0L) < 1800) Anim.HAPPY else Anim.NONE
            Box(Modifier.offset(x = w * x - size / 2, y = maxHeight - size - 6.dp).size(size)) {
                BuddyView(f.look(now), anim, reaction?.second ?: 0L, Modifier.fillMaxSize(), onTap = { menuFor = f.id })
                if (f.id in nearbyIds) Text("📍", fontSize = 12.sp, modifier = Modifier.align(Alignment.TopEnd))
                Text(f.name, color = Pal.text, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(6.dp)).background(Pal.bg.copy(alpha = 0.6f)).padding(horizontal = 4.dp))
                DropdownMenu(expanded = menuFor == f.id, onDismissRequest = { menuFor = null }) {
                    Column(Modifier.padding(horizontal = 12.dp)) { Text("${f.species.emoji} ${f.name} · Lv ${f.level}", color = Pal.sub, fontSize = 12.sp) }
                    friendActions.forEach { (a, icon) ->
                        DropdownMenuItem(text = { Text("$icon ${a.replaceFirstChar { it.uppercase() }}") }, onClick = {
                            menuFor = null
                            reaction = f.id to System.currentTimeMillis()
                            interactWith(repo, hub, f, a)
                        })
                    }
                    DropdownMenuItem(text = { Text("💬 Message") }, onClick = { menuFor = null; go(Screen.FRIENDS) })
                }
            }
        }
    }
}
