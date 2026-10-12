package com.realcak3d.scanimal.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.realcak3d.scanimal.core.SignalInfo
import com.realcak3d.scanimal.core.SignalKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cell towers the phone can hear (LTE, 5G NR, GSM, WCDMA). */
class CellRadar(private val ctx: Context) {
    private val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private val handler = Handler(Looper.getMainLooper())
    private val _cells = MutableStateFlow<List<SignalInfo>>(emptyList())
    val cells: StateFlow<List<SignalInfo>> = _cells.asStateFlow()
    var onBatch: ((List<SignalInfo>) -> Unit)? = null
    private var running = false

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            poll()
            handler.postDelayed(this, 30_000)
        }
    }

    fun start() {
        if (running || tm == null || Build.VERSION.SDK_INT < 28 || !ctx.granted(Manifest.permission.ACCESS_FINE_LOCATION)) return
        running = true
        handler.post(loop)
    }

    fun stop() { running = false; handler.removeCallbacks(loop) }

    @SuppressLint("MissingPermission")
    private fun poll() {
        val t = tm ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                t.requestCellInfoUpdate(ctx.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) { publish(cellInfo) }
                })
            }.onFailure { publish(runCatching { t.allCellInfo }.getOrNull().orEmpty()) }
        } else publish(runCatching { t.allCellInfo }.getOrNull().orEmpty())
    }

    private fun publish(infos: List<CellInfo>) {
        if (Build.VERSION.SDK_INT < 28) return
        val op = runCatching { tm?.networkOperatorName }.getOrNull().orEmpty()
        val list = infos.mapNotNull { ci ->
            when {
                ci is CellInfoLte -> {
                    val id = ci.cellIdentity
                    if (id.ci == Int.MAX_VALUE || id.ci <= 0) null
                    else SignalInfo("${mcc(id.mccString)}-${id.mncString ?: "?"}-${id.ci}", "${id.operatorAlphaShort ?: op} LTE · PCI ${id.pci}", ci.cellSignalStrength.dbm, SignalKind.CELL, connected = ci.isRegistered)
                }
                Build.VERSION.SDK_INT >= 29 && ci is CellInfoNr -> {
                    val id = ci.cellIdentity as CellIdentityNr
                    val ss = ci.cellSignalStrength as CellSignalStrengthNr
                    if (id.nci == Long.MAX_VALUE || id.nci <= 0) null
                    else SignalInfo("${mcc(id.mccString)}-${id.mncString ?: "?"}-${id.nci}", "${id.operatorAlphaShort ?: op} 5G · PCI ${id.pci}", ss.dbm, SignalKind.CELL, connected = ci.isRegistered)
                }
                ci is CellInfoGsm -> {
                    val id = ci.cellIdentity
                    if (id.cid == Int.MAX_VALUE || id.cid <= 0) null
                    else SignalInfo("${mcc(id.mccString)}-${id.mncString ?: "?"}-${id.cid}", "${id.operatorAlphaShort ?: op} GSM", ci.cellSignalStrength.dbm, SignalKind.CELL, connected = ci.isRegistered)
                }
                ci is CellInfoWcdma -> {
                    val id = ci.cellIdentity
                    if (id.cid == Int.MAX_VALUE || id.cid <= 0) null
                    else SignalInfo("${mcc(id.mccString)}-${id.mncString ?: "?"}-${id.cid}", "${id.operatorAlphaShort ?: op} 3G", ci.cellSignalStrength.dbm, SignalKind.CELL, connected = ci.isRegistered)
                }
                else -> null
            }
        }.sortedByDescending { it.rssi }
        _cells.value = list
        if (list.isNotEmpty()) onBatch?.invoke(list)
    }

    private fun mcc(s: String?) = s ?: "?"
}
