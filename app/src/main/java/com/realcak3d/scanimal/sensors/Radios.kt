package com.realcak3d.scanimal.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.realcak3d.scanimal.core.SignalInfo
import com.realcak3d.scanimal.core.SignalKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun Context.granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

/** Periodic Wi-Fi scans. Android throttles apps to ~4 scans per 2 minutes; cached results still update. */
class WifiRadar(private val ctx: Context) {
    private val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private val _results = MutableStateFlow<List<SignalInfo>>(emptyList())
    val results: StateFlow<List<SignalInfo>> = _results.asStateFlow()
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _connectedSsid = MutableStateFlow<String?>(null)
    val connectedSsid: StateFlow<String?> = _connectedSsid.asStateFlow()
    var onBatch: ((List<SignalInfo>) -> Unit)? = null
    private var running = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { publish() }
    }

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            requestScan()
            handler.postDelayed(this, 12_000)
        }
    }

    fun canScan() = ctx.granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        (Build.VERSION.SDK_INT >= 33 && ctx.granted(Manifest.permission.NEARBY_WIFI_DEVICES))

    fun start() {
        if (running) return
        if (!canScan()) { _status.value = "Needs location / nearby-devices permission"; return }
        running = true
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION), ContextCompat.RECEIVER_EXPORTED)
        handler.post(loop)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(loop)
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    @Suppress("DEPRECATION")
    private fun requestScan() {
        if (!wifi.isWifiEnabled) _status.value = "Wi-Fi is off (scans may still work if 'Wi-Fi scanning' is on)"
        val ok = runCatching { wifi.startScan() }.getOrDefault(false)
        _status.value = if (ok) "Scanning…" else "Using cached results (Android scan throttle)"
        publish()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun publish() {
        if (!canScan()) return
        val connectedBssid = runCatching { wifi.connectionInfo?.bssid }.getOrNull()?.lowercase()
        _connectedSsid.value = runCatching { wifi.connectionInfo?.ssid?.trim('"') }.getOrNull()?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val list = runCatching { wifi.scanResults }.getOrNull().orEmpty().map {
            val ssid = if (Build.VERSION.SDK_INT >= 33) it.wifiSsid?.toString()?.trim('"').orEmpty() else it.SSID.orEmpty()
            val caps = it.capabilities.orEmpty()
            SignalInfo(
                id = it.BSSID ?: ssid, name = ssid, rssi = it.level, kind = SignalKind.WIFI, frequencyMhz = it.frequency,
                secured = caps.contains("WPA") || caps.contains("WEP") || caps.contains("SAE") || caps.contains("EAP"),
                connected = connectedBssid != null && it.BSSID?.lowercase() == connectedBssid,
                widthMhz = when (it.channelWidth) { 1 -> 40; 2 -> 80; 3, 4 -> 160; 5 -> 320; else -> 20 },
            )
        }.sortedByDescending { it.rssi }
        _results.value = list
        if (list.isNotEmpty()) _status.value = "${list.size} networks"
        onBatch?.invoke(list)
    }
}

/** BLE + classic discovery, merged and smoothed. BLE gives fast RSSI updates, which the radar needs. */
class BluetoothRadar(private val ctx: Context) {
    private val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = manager?.adapter
    private val handler = Handler(Looper.getMainLooper())
    private val seen = LinkedHashMap<String, Entry>()
    private val _devices = MutableStateFlow<List<SignalInfo>>(emptyList())
    val devices: StateFlow<List<SignalInfo>> = _devices.asStateFlow()
    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status.asStateFlow()
    var onBatch: ((List<SignalInfo>) -> Unit)? = null
    private var running = false
    private var lastBatch = 0L

    private class Entry(var info: SignalInfo, var smooth: Double, var lastSeen: Long)

    private val connectedAddrs = mutableSetOf<String>()
    /** Called when a discoverable Scanimal's advertisement is heard: (service data, rssi, address). */
    var onBuddyAdvert: ((ByteArray, Int, String) -> Unit)? = null
    private val _connectedCount = MutableStateFlow(0)
    val connectedCount: StateFlow<Int> = _connectedCount.asStateFlow()

    fun available() = adapter != null

    fun canScan(): Boolean = if (Build.VERSION.SDK_INT >= 31)
        ctx.granted(Manifest.permission.BLUETOOTH_SCAN) && ctx.granted(Manifest.permission.BLUETOOTH_CONNECT)
    else ctx.granted(Manifest.permission.ACCESS_FINE_LOCATION)

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val data = result.scanRecord?.getServiceData(BuddyLink.ADVERT_UUID)
            if (data != null) { onBuddyAdvert?.invoke(data, result.rssi, result.device.address ?: ""); return }
            val rec = result.scanRecord
            val services = rec?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList()
            var mfgId = -1
            val msd = rec?.manufacturerSpecificData
            if (msd != null && msd.size() > 0) mfgId = msd.keyAt(0)
            record(result.device, result.rssi, SignalKind.BLE, rec?.deviceName, services, mfgId)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(errorCode: Int) { _status.value = "BLE scan failed ($errorCode)" }
    }

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev = deviceExtra(i)
                    val rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    if (dev != null && rssi != Short.MIN_VALUE.toInt()) record(dev, rssi, SignalKind.BLUETOOTH, null)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (running) handler.postDelayed({ startClassic() }, 4_000)
                BluetoothDevice.ACTION_ACL_CONNECTED -> deviceExtra(i)?.let { d -> d.address?.let { connectedAddrs += it }; record(d, -45, SignalKind.BLUETOOTH, null) }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> deviceExtra(i)?.address?.let { connectedAddrs -= it }
            }
        }
    }

    private fun deviceExtra(i: Intent): BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    /** Devices the phone is actually connected to (headphones, watches, cars...) often don't advertise, so ask directly. */
    @SuppressLint("MissingPermission")
    private fun queryConnected() {
        val a = adapter ?: return
        runCatching { manager?.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)?.forEach { d -> connectedAddrs += d.address; record(d, -50, SignalKind.BLE, null) } }
        for (profile in listOf(android.bluetooth.BluetoothProfile.A2DP, android.bluetooth.BluetoothProfile.HEADSET)) {
            runCatching {
                a.getProfileProxy(ctx, object : android.bluetooth.BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: android.bluetooth.BluetoothProfile) {
                        runCatching { proxy.connectedDevices.forEach { d -> connectedAddrs += d.address; record(d, -45, SignalKind.BLUETOOTH, null) } }
                        runCatching { a.closeProfileProxy(p, proxy) }
                    }
                    override fun onServiceDisconnected(p: Int) {}
                }, profile)
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            emit()
            handler.postDelayed(this, 400)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        val a = adapter ?: run { _status.value = "No Bluetooth on this device"; return }
        if (!canScan()) { _status.value = "Needs Bluetooth permission"; return }
        if (!a.isEnabled) { _status.value = "Bluetooth is off"; return }
        running = true
        _status.value = "Scanning…"
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { a.bluetoothLeScanner?.startScan(null, settings, callback) }
            .onFailure { _status.value = "BLE scan unavailable: ${it.message}" }
        ContextCompat.registerReceiver(ctx, classicReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED); addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }, ContextCompat.RECEIVER_EXPORTED)
        startClassic()
        queryConnected()
        handler.post(ticker)
    }

    @SuppressLint("MissingPermission")
    private fun startClassic() {
        if (!running) return
        runCatching { adapter?.startDiscovery() }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(ticker)
        runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        runCatching { adapter?.cancelDiscovery() }
        runCatching { ctx.unregisterReceiver(classicReceiver) }
        _status.value = "Stopped"
    }

    @SuppressLint("MissingPermission")
    private fun record(dev: BluetoothDevice, rssi: Int, kind: SignalKind, advName: String?, services: List<String> = emptyList(), mfgId: Int = -1) {
        val id = dev.address ?: return
        val name = advName ?: runCatching { dev.name }.getOrNull() ?: ""
        val cls = runCatching { dev.bluetoothClass?.majorDeviceClass }.getOrNull() ?: -1
        val now = System.currentTimeMillis()
        val e = seen[id]
        if (e == null) {
            seen[id] = Entry(SignalInfo(id, name, rssi, kind, classHint = cls, services = services, mfgId = mfgId), rssi.toDouble(), now)
        } else {
            e.smooth = e.smooth * 0.6 + rssi * 0.4
            e.info = SignalInfo(id, name.ifBlank { e.info.name }, e.smooth.toInt(), kind, classHint = if (cls >= 0) cls else e.info.classHint,
                services = services.ifEmpty { e.info.services }, mfgId = if (mfgId >= 0) mfgId else e.info.mfgId)
            e.lastSeen = now
        }
    }

    private fun emit() {
        val now = System.currentTimeMillis()
        seen.entries.removeAll { now - it.value.lastSeen > 15_000 && it.key !in connectedAddrs }
        val list = seen.values.map { it.info.copy(connected = it.info.id in connectedAddrs) }.sortedByDescending { it.rssi }
        _connectedCount.value = connectedAddrs.size
        _devices.value = list
        _status.value = "${list.size} devices"
        if (now - lastBatch > 5_000 && list.isNotEmpty()) { lastBatch = now; onBatch?.invoke(list) }
    }
}
