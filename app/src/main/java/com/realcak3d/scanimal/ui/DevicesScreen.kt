package com.realcak3d.scanimal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.realcak3d.scanimal.core.DeviceRecord
import com.realcak3d.scanimal.core.DeviceType
import com.realcak3d.scanimal.core.SignalKind
import com.realcak3d.scanimal.data.GameRepository
import com.realcak3d.scanimal.sensors.SensorHub
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every device ever spotted, grouped by kind, with seen/connected counts. */
@Composable
fun DevicesScreen(repo: GameRepository, hub: SensorHub) {
    val g by rememberGame(repo)
    val bt by hub.bt.devices.collectAsStateWithLifecycle()
    val wifi by hub.wifi.results.collectAsStateWithLifecycle()
    val cells by hub.cells.cells.collectAsStateWithLifecycle()
    val connectedSsid by hub.wifi.connectedSsid.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<DeviceType?>(null) }
    var kind by rememberSaveable { mutableStateOf("all") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("recent") }
    val df = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val inRange = remember(bt, wifi, cells) { (bt.map { "${it.kind.name}:${it.id}" } + wifi.map { "WIFI:${it.id}" } + cells.map { "CELL:${it.id}" }).toSet() }

    val all = g.devices.entries.map { it.key to it.value }
    val byType = all.groupBy { it.second.type }.mapValues { it.value.size }
    val list = all.filter { (_, d) ->
        (filter == null || d.type == filter) &&
            (kind == "all" || (kind == "bt" && d.kind != SignalKind.WIFI && d.kind != SignalKind.CELL) || (kind == "wifi" && d.kind == SignalKind.WIFI) || (kind == "cell" && d.kind == SignalKind.CELL)) &&
            (query.isBlank() || d.name.contains(query, true) || d.id.contains(query, true) || d.type.label.contains(query, true))
    }.sortedWith(
        when (sort) {
            "seen" -> compareByDescending<Pair<String, DeviceRecord>> { it.second.timesSeen }
            "connected" -> compareByDescending<Pair<String, DeviceRecord>> { it.second.timesConnected }
            "signal" -> compareByDescending<Pair<String, DeviceRecord>> { it.second.bestRssi }
            "name" -> compareBy<Pair<String, DeviceRecord>> { it.second.name.ifBlank { "~" }.lowercase() }
            else -> compareByDescending<Pair<String, DeviceRecord>> { it.second.lastSeen }
        }
    )

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            ScreenTitle("Devices", "${g.devices.size} logged · ${byType.size} kinds · ${g.devices.values.sumOf { it.timesConnected }} connections")
            Panel(title = "Connected right now") {
                KeyValue("Wi-Fi", connectedSsid ?: "not connected")
                KeyValue("Bluetooth", bt.filter { it.connected }.joinToString { it.name.ifBlank { it.id } }.ifBlank { "none" })
                KeyValue("Cell", cells.firstOrNull { it.connected }?.name ?: "—")
                Text("Being connected to something gives your buddy a bonus once every 6 hours per device.", color = Pal.sub, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip("All ${g.devices.size}", filter == null) { filter = null } }
                items(DeviceType.entries.filter { (byType[it] ?: 0) > 0 }.sortedByDescending { byType[it] ?: 0 }) { t ->
                    FilterChip("${t.icon} ${t.label} ${byType[t]}", filter == t) { filter = if (filter == t) null else t }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("all" to "Everything", "bt" to "Bluetooth", "wifi" to "Wi-Fi", "cell" to "Cell").forEach { (k, l) -> FilterChip(l, kind == k) { kind = k } }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Sort:", color = Pal.sub, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                listOf("recent" to "Recent", "seen" to "Most seen", "connected" to "Connected", "signal" to "Strongest", "name" to "A-Z").forEach { (k, l) -> FilterChip(l, sort == k) { sort = k } }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(vertical = 6.dp), placeholder = { Text("Search name, address or type…") }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.mint, unfocusedBorderColor = Pal.line))
            if (filter != null) Text("${filter!!.icon} ${filter!!.label}: ${filter!!.effect}", color = Pal.amber, fontSize = 12.sp)
        }
        items(list.take(400), key = { it.first }) { (key, d) ->
            val live = key in inRange
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Pal.panel)
                    .border(1.dp, if (live) Pal.mint.copy(alpha = 0.5f) else Pal.line, RoundedCornerShape(14.dp)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(d.type.icon, fontSize = 24.sp, modifier = Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name.ifBlank { "(no name)" }, color = Pal.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                    Text("${d.type.label}${if (d.vendor.isNotBlank()) " · ${d.vendor}" else ""} · ${kindLabel(d.kind)} · ${d.id}", color = Pal.sub, fontSize = 10.sp, fontFamily = Mono, maxLines = 1)
                    Text("Seen ${d.timesSeen}× · connected ${d.timesConnected}× · best ${d.bestRssi} dBm · last ${df.format(Date(d.lastSeen))}", color = Pal.sub, fontSize = 11.sp)
                }
                if (d.notable) Chip("NOTABLE", Pal.amber)
                else if (live) Chip("IN RANGE", Pal.mint)
            }
        }
        if (list.isEmpty()) item { Text("Nothing here yet. Keep the app open near gadgets, or turn on Background mode.", color = Pal.sub, modifier = Modifier.padding(16.dp)) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

fun kindLabel(k: SignalKind) = when (k) { SignalKind.WIFI -> "Wi-Fi"; SignalKind.BLE -> "BLE"; SignalKind.BLUETOOTH -> "Bluetooth"; SignalKind.CELL -> "Cell" }

@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(text, color = if (selected) Pal.bg else Pal.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Pal.mint else Pal.panel2).clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 6.dp))
}
