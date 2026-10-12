package com.realcak3d.scanimal.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import com.realcak3d.scanimal.core.BuddyAdvert
import com.realcak3d.scanimal.core.Friend
import com.realcak3d.scanimal.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.UUID

/** A Scanimal seen over Bluetooth right now. */
data class NearbyBuddy(val card: Friend, val rssi: Int, val address: String, val lastSeen: Long)

/**
 * Peer-to-peer buddy discovery and messaging over Bluetooth LE. No server, no internet:
 * discoverable phones advertise a tiny buddy card, and messages are written straight to the
 * other phone's GATT server.
 */
class BuddyLink(private val ctx: Context, private val repo: GameRepository) {
    private val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val handler = Handler(Looper.getMainLooper())
    private var server: BluetoothGattServer? = null
    private var advertising = false
    private val seen = LinkedHashMap<String, NearbyBuddy>()
    private val lastReported = mutableMapOf<String, Long>()
    private val _nearby = MutableStateFlow<List<NearbyBuddy>>(emptyList())
    val nearby: StateFlow<List<NearbyBuddy>> = _nearby.asStateFlow()
    private val _status = MutableStateFlow("Off")
    val status: StateFlow<String> = _status.asStateFlow()

    private fun canAdvertise() = if (Build.VERSION.SDK_INT >= 31)
        ctx.granted(Manifest.permission.BLUETOOTH_ADVERTISE) && ctx.granted(Manifest.permission.BLUETOOTH_CONNECT) else true

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { advertising = true; _status.value = "Discoverable" }
        override fun onStartFailure(errorCode: Int) { advertising = false; _status.value = "Couldn't advertise (error $errorCode)" }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!repo.data.settings.discoverable) { stop(); _status.value = "Hidden (discoverable is off)"; return }
        val adapter = manager?.adapter ?: run { _status.value = "No Bluetooth"; return }
        if (!adapter.isEnabled) { _status.value = "Bluetooth is off"; return }
        if (!canAdvertise()) { _status.value = "Needs Nearby devices permission"; return }
        if (!repo.data.buddy.hatched) { _status.value = "Hatch your buddy first"; return }
        startServer()
        val adv = adapter.bluetoothLeAdvertiser ?: run { _status.value = "This phone can't advertise over BLE"; return }
        if (advertising) runCatching { adv.stopAdvertising(advertiseCallback) }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(ADVERT_UUID, BuddyAdvert.encode(repo.engine.myCard()))
            .build()
        runCatching { adv.startAdvertising(settings, data, advertiseCallback) }.onFailure { _status.value = "Advertise failed: ${it.message}" }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        runCatching { if (advertising) manager?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback) }
        advertising = false
        runCatching { server?.close() }
        server = null
        _status.value = "Off"
    }

    /** Re-advertise after the buddy's name/level/look changes. */
    fun refresh() { if (advertising) start() }

    /** Called by the Bluetooth scanner whenever a Scanimal advert is heard. */
    fun onAdvert(data: ByteArray, rssi: Int, address: String) {
        val card = BuddyAdvert.decode(data) ?: return
        if (card.id == repo.data.myId) return
        val now = System.currentTimeMillis()
        seen[card.id] = NearbyBuddy(card, rssi, address, now)
        seen.entries.removeAll { now - it.value.lastSeen > 60_000 }
        _nearby.value = seen.values.sortedByDescending { it.rssi }
        if (now - (lastReported[card.id] ?: 0L) > 60_000) {
            lastReported[card.id] = now
            repo.act { n -> onNearbyBuddy(card, rssi, n) }
        }
    }

    // ------------------------------------------------------------------ messaging over GATT

    @SuppressLint("MissingPermission")
    private fun startServer() {
        if (server != null) return
        val s = runCatching { manager?.openGattServer(ctx, serverCallback) }.getOrNull() ?: return
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(BluetoothGattCharacteristic(MSG_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE))
        runCatching { s.addService(service) }
        server = s
    }

    private val serverCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?,
        ) {
            if (responseNeeded) runCatching { server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null) }
            val text = value?.let { String(it, Charsets.UTF_8) } ?: return
            handler.post { receive(text) }
        }
    }

    private fun receive(raw: String) {
        val j = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val from = j.optString("f").ifBlank { return }
        val name = j.optString("n")
        val text = j.optString("x").take(200)
        val action = j.optString("a")
        repo.act { now ->
            if (g.friends.none { it.id == from }) {
                seen[from]?.card?.let { addFriend(it, now, viaLink = false) }
            }
            when {
                action.isNotBlank() -> listOf(com.realcak3d.scanimal.core.GameEvent.Say("${name.ifBlank { "A friend" }} ${actionVerb(action)} us! ${actionEmoji(action)}", com.realcak3d.scanimal.core.Anim.LOVE)) +
                    interact(from, action, now).filter { it !is com.realcak3d.scanimal.core.GameEvent.Say }
                else -> addMessage(from, text, false, now)
            }
        }
    }

    /** Send a message or an interaction ("wave", "hug", "play", "gift", "dance") to a nearby buddy. */
    @SuppressLint("MissingPermission")
    fun send(friendId: String, text: String = "", action: String = "", done: (Boolean) -> Unit) {
        val target = seen[friendId]
        val adapter = manager?.adapter
        if (target == null || adapter == null || !canAdvertise()) { done(false); return }
        val payload = JSONObject().put("f", repo.data.myId).put("n", repo.data.buddy.name).put("x", text.take(160)).put("a", action).toString().toByteArray(Charsets.UTF_8)
        val device = runCatching { adapter.getRemoteDevice(target.address) }.getOrNull() ?: run { done(false); return }
        var finished = false
        fun finish(ok: Boolean, gatt: BluetoothGatt?) {
            if (finished) return
            finished = true
            runCatching { gatt?.disconnect(); gatt?.close() }
            handler.post { done(ok) }
        }
        val cb = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) runCatching { gatt.requestMtu(247) }.onFailure { gatt.discoverServices() }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) finish(false, gatt)
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) { gatt.discoverServices() }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val ch = gatt.getService(SERVICE_UUID)?.getCharacteristic(MSG_UUID) ?: return finish(false, gatt)
                val ok = if (Build.VERSION.SDK_INT >= 33) {
                    gatt.writeCharacteristic(ch, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    run { ch.value = payload; ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT; gatt.writeCharacteristic(ch) }
                }
                if (!ok) finish(false, gatt)
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                finish(status == BluetoothGatt.GATT_SUCCESS, gatt)
            }
        }
        val gatt = runCatching { device.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) }.getOrNull()
        if (gatt == null) { done(false); return }
        handler.postDelayed({ finish(false, gatt) }, 15_000)
    }

    companion object {
        val ADVERT_UUID: ParcelUuid = ParcelUuid.fromString("00005ca1-0000-1000-8000-00805f9b34fb")
        val SERVICE_UUID: UUID = UUID.fromString("5ca1a000-7b3c-4c1e-9a11-5ca1ab1e0001")
        val MSG_UUID: UUID = UUID.fromString("5ca1a001-7b3c-4c1e-9a11-5ca1ab1e0001")

        fun actionVerb(a: String) = when (a) { "wave" -> "waved at"; "hug" -> "hugged"; "play" -> "wants to play with"; "gift" -> "sent a gift to"; "dance" -> "started a dance-off with"; else -> "poked" }
        fun actionEmoji(a: String) = when (a) { "wave" -> "👋"; "hug" -> "🤗"; "play" -> "⚽"; "gift" -> "🎁"; "dance" -> "💃"; else -> "👉" }
    }
}
