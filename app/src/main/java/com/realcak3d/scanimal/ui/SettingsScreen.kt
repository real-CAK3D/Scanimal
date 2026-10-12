package com.realcak3d.scanimal.ui

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realcak3d.scanimal.core.Achievements
import com.realcak3d.scanimal.core.C
import com.realcak3d.scanimal.core.Codec
import com.realcak3d.scanimal.core.Items
import com.realcak3d.scanimal.core.Settings
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.SensorHub
import com.realcak3d.scanimal.work.BuddyService
import com.realcak3d.scanimal.work.Updater
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repo: GameRepository, hub: SensorHub, requestPermissions: () -> Unit, go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val g by rememberGame(repo)
    val upd by Updater.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var editOwner by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val s = g.settings
    fun set(block: Settings.() -> Unit) {
        repo.act { _ -> this.g.settings.block(); emptyList() }
        hub.applySettings()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        ScreenTitle("Settings", "Scanimal ${Updater.currentVersion}")
        note?.let { Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { SpeechBubble(it) }; Spacer(Modifier.height(8.dp)) }

        Section("Updates") {
            Toggle("Check for updates automatically", "Looks for new releases on GitHub a few times a day and downloads them for you", s.autoUpdate) { v -> set { autoUpdate = v } }
            val status = when (val u = upd) {
                Updater.State.Idle -> "Tap to check for a new version."
                Updater.State.Checking -> "Checking GitHub…"
                Updater.State.UpToDate -> "You're on the latest version. ✅"
                is Updater.State.Available -> "Version ${u.release.version} is available!"
                is Updater.State.Downloading -> "Downloading ${u.release.version}… ${u.percent}%"
                is Updater.State.Ready -> "Version ${u.release.version} is ready to install."
                is Updater.State.Failed -> "Couldn't update: ${u.message}"
            }
            Text(status, color = Pal.text, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val u = upd) {
                    is Updater.State.Available -> SmallBtn("⬇️ Download", Pal.mint) { scope.launch { Updater.download(ctx, u.release) } }
                    is Updater.State.Ready -> SmallBtn("📦 Install now", Pal.amber) { Updater.install(ctx, u.file) }
                    is Updater.State.Downloading, Updater.State.Checking -> {}
                    else -> SmallBtn("🔄 Check now", Pal.mint) { scope.launch { Updater.check() } }
                }
            }
        }

        Section("About you") {
            val o = g.owner
            KeyValue("Name", o.name.ifBlank { "—" })
            KeyValue("Birthday", o.birthday.ifBlank { "—" })
            KeyValue("Favorite color", o.favoriteColor.ifBlank { "—" })
            KeyValue("Favorite food", o.favoriteFood.ifBlank { "—" })
            KeyValue("Hobby", o.hobby.ifBlank { "—" })
            KeyValue("Pet", o.pet.ifBlank { "—" })
            Spacer(Modifier.height(6.dp))
            SmallBtn("✏️ Edit", Pal.mint) { editOwner = true }
        }

        Section("Appearance") {
            Text("Theme", color = Pal.sub, fontSize = 12.sp)
            Themes.all.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t ->
                        val sel = s.theme == t.id
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(t.bg)
                                .border(if (sel) 3.dp else 1.dp, if (sel) t.mint else t.line, RoundedCornerShape(12.dp))
                                .clickable { set { theme = t.id } }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(t.mint, t.amber, t.pink, t.sky).forEach { c -> Box(Modifier.size(9.dp).clip(CircleShape).background(c)) }
                            }
                            Text(t.label, color = t.text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Toggle("Physics", "Shake and tilt the phone to bounce your buddy around", s.physics) { v -> set { physics = v } }
            Toggle("Show visiting friends", "Up to 5 friend buddies hang out on your home screen", s.showVisitors) { v -> set { showVisitors = v } }
            Toggle("Ambient chatter", "Your buddy talks on its own every so often", s.ambientChatter) { v -> set { ambientChatter = v } }
            Text("Outfits, hats and paint jobs are in the Shop.", color = Pal.sub, fontSize = 11.sp, modifier = Modifier.clickable { go(Screen.SHOP) })
        }

        Section("Exploring") {
            Toggle("Background mode", "Keeps counting walks, steps and signals with the app closed (shows a notification, uses some battery)", s.backgroundMode) { v ->
                set { backgroundMode = v }
                if (v) BuddyService.start(ctx) else BuddyService.stop(ctx)
            }
            Toggle("Discoverable to nearby Scanimals", "Other buddies within Bluetooth range can see yours and send messages", s.discoverable) { v ->
                set { discoverable = v }
                if (v) hub.link.start() else hub.link.stop()
            }
            Toggle("Metric units", "Kilometers instead of miles", s.metric) { v -> set { metric = v } }
            Toggle("Look up product names online", "Asks Open Food Facts what a grocery barcode is", s.onlineProductLookup) { v -> set { onlineProductLookup = v } }
        }

        Section("Care") {
            Toggle("Care reminders", "Notifications when your buddy is hungry, sick or lonely", s.notifications) { v -> set { notifications = v } }
            Toggle("Vibration", null, s.vibration) { v -> set { vibration = v } }
            Toggle("Beeps", null, s.sounds) { v -> set { sounds = v } }
            Toggle("Auto-sleep at night", "10pm to 7am", s.autoSleepAtNight) { v -> set { autoSleepAtNight = v } }
        }

        Section("Stats") {
            KeyValue("Buddy", "${g.buddy.name} · Lv ${g.buddy.level} · gen ${g.buddy.generation}")
            KeyValue("Achievements", "${g.achievements.size} / ${Achievements.all.size}")
            KeyValue("Codes eaten", "${g.count(C.SCANS)} (${g.count(C.UNIQUE_CODES)} unique)")
            KeyValue("Distance traveled", repo.engine.fmtDist(g.count(C.DISTANCE_M)))
            KeyValue("Steps", "${g.count(C.STEPS)}")
            KeyValue("Devices logged", "${g.devices.size} (${g.devices.values.map { it.type }.toSet().size} kinds)")
            KeyValue("Connections", "${g.count(C.CONNECTIONS)}")
            KeyValue("Cell towers", "${g.count(C.CELLS)}")
            KeyValue("Wi-Fi networks", "${g.count(C.WIFI)}")
            KeyValue("Bluetooth devices", "${g.count(C.BT)}")
            KeyValue("Max satellites", "${g.count(C.SATS_MAX)}")
            KeyValue("Friends", "${g.friends.size} (${g.count(C.NEARBY_MEETS)} nearby meetups)")
            KeyValue("Coins earned", "${g.count(C.COINS_EARNED)}")
            KeyValue("Playing since", if (g.created > 0) java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(g.created)) else "—")
            Spacer(Modifier.height(6.dp))
            SmallBtn("📊 Full profile", Pal.mint) { go(Screen.PROFILE) }
        }

        Section("Backup") {
            Text("Your save lives only on this phone. Export it to keep a copy (e.g. in Google Drive), and import it on a new phone.", color = Pal.sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallBtn("📤 Export save", Pal.mint) { shareText(ctx, "Scanimal backup", "SCANIMALSAVE1:" + java.util.Base64.getEncoder().encodeToString(Codec.encode(repo.data).toByteArray())) }
                SmallBtn("📥 Import from clipboard", Pal.amber) { importing = true }
            }
        }

        Section("Permissions") {
            Text(
                "Camera → scanning\nLocation → Wi-Fi/Bluetooth/cell scans, GPS satellites, walks\nNearby devices → Bluetooth radar and nearby buddies\n" +
                    "Physical activity → steps\nMicrophone → dance party and audio spectrum (only when switched on)\nNotifications → care reminders",
                color = Pal.sub, fontSize = 12.sp, lineHeight = 18.sp,
            )
            Spacer(Modifier.height(6.dp))
            BigButton("Grant permissions", Pal.mint, Modifier.fillMaxWidth()) { requestPermissions() }
            Text("Tip: Developer options → \"Wi-Fi scan throttling\" off makes the Wi-Fi radar much faster.", color = Pal.sub, fontSize = 11.sp)
        }

        Section("About") {
            KeyValue("Version", Updater.currentVersion)
            KeyValue("Brain", "Rule-based phrase engine (no AI)")
            KeyValue("Achievements", "${Achievements.all.size}")
            KeyValue("Shop items", "${Items.all.size}")
            KeyValue("Source", "github.com/${Updater.REPO}")
        }

        Section("Danger zone") {
            BigButton("Reset everything", Pal.red, Modifier.fillMaxWidth()) { confirmReset = true }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Start over?") },
            text = { Text("This deletes your buddy, coins, items, achievements, friends and history. It can't be undone.") },
            confirmButton = { TextButton({ repo.resetEverything(); confirmReset = false }) { Text("Delete everything", color = Pal.red) } },
            dismissButton = { TextButton({ confirmReset = false }) { Text("Cancel") } },
            containerColor = Pal.panel,
        )
    }
    if (importing) {
        AlertDialog(
            onDismissRequest = { importing = false },
            title = { Text("Import save?") },
            text = { Text("This replaces everything on this phone with the backup on your clipboard.") },
            confirmButton = {
                TextButton({
                    val clip = (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    note = if (repo.importSave(clip)) "Backup restored! 🎉" else "No valid Scanimal backup on the clipboard."
                    importing = false
                }) { Text("Import", color = Pal.amber) }
            },
            dismissButton = { TextButton({ importing = false }) { Text("Cancel") } },
            containerColor = Pal.panel,
        )
    }
    if (editOwner) OwnerDialog(repo) { editOwner = false }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Panel(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), title = title) { content() }
}

@Composable
fun Toggle(label: String, hint: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Pal.text, fontSize = 14.sp)
            if (hint != null) Text(hint, color = Pal.sub, fontSize = 11.sp)
        }
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Pal.mint))
    }
}

@Composable
private fun OwnerDialog(repo: GameRepository, onClose: () -> Unit) {
    val o = repo.data.owner
    var name by remember { mutableStateOf(o.name) }
    var bday by remember { mutableStateOf(o.birthday) }
    var color by remember { mutableStateOf(o.favoriteColor) }
    var food by remember { mutableStateOf(o.favoriteFood) }
    var hobby by remember { mutableStateOf(o.hobby) }
    var pet by remember { mutableStateOf(o.pet) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.mint, unfocusedBorderColor = Pal.line)
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Pal.panel,
        title = { Text("About you") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name") }, singleLine = true, colors = colors)
                OutlinedTextField(bday, { bday = it.take(5) }, label = { Text("Birthday (MM-DD)") }, singleLine = true, colors = colors)
                OutlinedTextField(color, { color = it.take(20) }, label = { Text("Favorite color") }, singleLine = true, colors = colors)
                OutlinedTextField(food, { food = it.take(30) }, label = { Text("Favorite food") }, singleLine = true, colors = colors)
                OutlinedTextField(hobby, { hobby = it.take(30) }, label = { Text("Hobby") }, singleLine = true, colors = colors)
                OutlinedTextField(pet, { pet = it.take(20) }, label = { Text("Pet's name") }, singleLine = true, colors = colors)
            }
        },
        confirmButton = {
            TextButton({
                repo.act { _ ->
                    this.g.owner.name = name.trim(); this.g.owner.birthday = if (Regex("\\d\\d-\\d\\d").matches(bday)) bday else ""
                    this.g.owner.favoriteColor = color.trim(); this.g.owner.favoriteFood = food.trim(); this.g.owner.hobby = hobby.trim(); this.g.owner.pet = pet.trim()
                    this.g.ownerName = name.trim()
                    emptyList()
                }
                onClose()
            }) { Text("Save", color = Pal.mint) }
        },
        dismissButton = { TextButton(onClose) { Text("Cancel") } },
    )
}
