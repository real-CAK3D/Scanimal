package com.realcak3d.scanimal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.realcak3d.scanimal.core.Anim
import com.realcak3d.scanimal.core.Codec
import com.realcak3d.scanimal.core.Friend
import com.realcak3d.scanimal.core.GameEvent
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.Feeder
import com.realcak3d.scanimal.sensors.SensorHub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun qrBitmap(text: String, size: Int = 512): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
    for (y in 0 until m.height) for (x in 0 until m.width) bmp.setPixel(x, y, if (m.get(x, y)) 0xFF0B1220.toInt() else 0xFFFFFFFF.toInt())
    return bmp.asImageBitmap()
}

fun shareText(ctx: Context, subject: String, text: String) {
    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
    ctx.startActivity(Intent.createChooser(i, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

val friendActions = listOf("wave" to "👋", "hug" to "🤗", "play" to "⚽", "gift" to "🎁", "dance" to "💃")

/** Runs an interaction locally and, if the friend is nearby, tells their phone too. */
fun interactWith(repo: GameRepository, hub: SensorHub, f: Friend, action: String) {
    repo.act { n -> interact(f.id, action, n) }
    if (hub.link.nearby.value.any { it.card.id == f.id }) hub.link.send(f.id, action = action) { }
}

@Composable
fun FriendsScreen(repo: GameRepository, hub: SensorHub, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val g by rememberGame(repo)
    val nearby by hub.link.nearby.collectAsStateWithLifecycle()
    val linkStatus by hub.link.status.collectAsStateWithLifecycle()
    var thread by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val myCode = remember(g.buddy.name, g.buddy.level, g.buddy.accessory, g.buddy.hue, g.owner.name) { Codec.linkCode(repo.engine.myCard()) }
    val qr = remember(myCode) { runCatching { qrBitmap(myCode) }.getOrNull() }
    val now = System.currentTimeMillis()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { ScreenTitle("Friends", "${g.friends.size} buddy friends · ${nearby.size} nearby right now") }
        item {
            Panel(title = "Your link code") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (qr != null) Image(qr, "Your link QR", Modifier.size(130.dp).clip(RoundedCornerShape(10.dp)))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Have a friend scan this with their Scanimal (Feed tab), or share the code by text or email.", color = Pal.sub, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        SmallBtn("📤 Share code", Pal.mint) { shareText(ctx, "Add my Scanimal!", "Add ${g.buddy.name} to your Scanimal! Open Scanimal → Friends → Paste code, or share this message to the app:\n\n$myCode") }
                        Spacer(Modifier.height(6.dp))
                        SmallBtn("📋 Copy", Pal.sky) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Scanimal code", myCode))
                            note = "Copied!"
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallBtn("📥 Paste a code", Pal.amber) {
                        val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                        val ev = Feeder.special(repo, clip)
                        note = ev?.filterIsInstance<GameEvent.Say>()?.lastOrNull()?.text ?: "No Scanimal code on the clipboard."
                    }
                    SmallBtn("📷 Scan their QR", Pal.amber) { go(Screen.SCAN) }
                }
                note?.let { Text(it, color = Pal.mint, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        item {
            Panel(title = "Nearby (Bluetooth)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Discoverable", color = Pal.text, fontSize = 14.sp)
                        Text(linkStatus, color = Pal.sub, fontSize = 11.sp)
                    }
                    Switch(g.settings.discoverable, { v ->
                        repo.act { _ -> this.g.settings.discoverable = v; emptyList() }
                        if (v) hub.link.start() else hub.link.stop()
                    }, colors = SwitchDefaults.colors(checkedTrackColor = Pal.mint))
                }
                if (nearby.isEmpty()) Text("No other Scanimals nearby. Both phones need the app open with Discoverable on.", color = Pal.sub, fontSize = 12.sp)
                nearby.forEach { n ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp)) { BuddyView(n.card.look(now), Anim.NONE, 0L, Modifier.fillMaxSize()) }
                        Text("  ${n.card.name} the ${n.card.species.label} · Lv ${n.card.level}", color = Pal.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        SignalBars(n.rssi)
                    }
                }
            }
        }
        if (g.friends.isEmpty()) item { Text("No friends yet! Share your code or meet someone nearby.", color = Pal.sub, modifier = Modifier.padding(8.dp)) }
        items(g.friends, key = { it.id }) { f ->
            val isNear = nearby.any { it.card.id == f.id }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Pal.panel).border(1.dp, if (isNear) Pal.mint else Pal.line, RoundedCornerShape(16.dp)).padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp)) { BuddyView(f.look(now), Anim.NONE, 0L, Modifier.fillMaxSize()) }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("${f.name} ${f.sex.symbol}", color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${f.species.emoji} ${f.species.label} · Lv ${f.level}${if (f.owner.isNotBlank()) " · ${f.owner}'s buddy" else ""}", color = Pal.sub, fontSize = 12.sp)
                        Text("Friendship ${f.friendship}/100 · ${if (isNear) "📍 nearby now" else if (f.viaLink) "🔗 linked" else "met nearby"}", color = if (isNear) Pal.mint else Pal.sub, fontSize = 11.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("On home", color = Pal.sub, fontSize = 10.sp)
                        Switch(f.onScreen, { v ->
                            val count = g.friends.count { it.onScreen }
                            if (v && count >= 5) note = "Up to 5 visitors can be on screen at once."
                            else repo.act { _ -> this.g.friends.firstOrNull { it.id == f.id }?.onScreen = v; emptyList() }
                        }, colors = SwitchDefaults.colors(checkedTrackColor = Pal.mint))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    friendActions.forEach { (a, icon) ->
                        Text(icon, fontSize = 20.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Pal.panel2).clickable { interactWith(repo, hub, f, a); hub.haptics.buzz(15, 80) }.padding(horizontal = 9.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("💬 ${f.messages.size}", color = Pal.text, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Pal.mintDim).clickable { thread = f.id }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    thread?.let { id -> g.friends.firstOrNull { it.id == id }?.let { f -> MessageDialog(repo, hub, f, isNear = nearby.any { it.card.id == f.id }) { thread = null } } }
}

@Composable
private fun MessageDialog(repo: GameRepository, hub: SensorHub, f: Friend, isNear: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("MMM d h:mm a", Locale.getDefault()) }
    val quick = listOf("Hi! 👋", "Wanna play?", "Miss you!", "Good night 🌙", "Look at my new hat!", "Let's go for a walk!")
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Pal.panel,
        title = { Text("${f.species.emoji} ${f.name}", color = Pal.text) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(f.messages.takeLast(50)) { m ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start) {
                            Column(Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(12.dp)).background(if (m.fromMe) Pal.panel2 else Color(0xFFE9FFF6)).padding(8.dp)) {
                                Text(m.text, color = if (m.fromMe) Pal.text else Color(0xFF14202E), fontSize = 14.sp)
                                Text(df.format(Date(m.time)), color = Pal.sub, fontSize = 9.sp)
                            }
                        }
                    }
                }
                ChoiceRow(quick, "") { text = it }
                OutlinedTextField(text, { text = it.take(160) }, Modifier.fillMaxWidth(), placeholder = { Text("Message…") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.mint, unfocusedBorderColor = Pal.line))
                Text(if (isNear) "📍 Nearby: sends directly over Bluetooth." else "Not nearby: you'll share a postcard code they can paste or scan.", color = Pal.sub, fontSize = 11.sp)
                if (status.isNotBlank()) Text(status, color = Pal.mint, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton({
                val t = text.trim()
                if (t.isEmpty()) return@TextButton
                repo.act { n -> addMessage(f.id, t, true, n) }
                text = ""
                if (isNear) {
                    status = "Sending…"
                    hub.link.send(f.id, t) { ok ->
                        status = if (ok) "Delivered! ✅" else "Couldn't reach them over Bluetooth. Sharing a postcard instead."
                        if (!ok) shareText(ctx, "Scanimal postcard", "${repo.data.buddy.name} sent ${f.name} a message! Paste in Scanimal → Friends:\n\n" + Codec.postcard(repo.data.myId, repo.data.buddy.name, f.id, t, System.currentTimeMillis()))
                    }
                } else {
                    shareText(ctx, "Scanimal postcard", "${repo.data.buddy.name} sent ${f.name} a message! Paste in Scanimal → Friends:\n\n" + Codec.postcard(repo.data.myId, repo.data.buddy.name, f.id, t, System.currentTimeMillis()))
                }
            }) { Text("Send", color = Pal.mint) }
        },
        dismissButton = {
            Row {
                TextButton({ repo.act { _ -> this.g.friends.removeAll { it.id == f.id }; emptyList() }; onClose() }) { Text("Unfriend", color = Pal.red) }
                TextButton(onClose) { Text("Close") }
            }
        },
    )
}
