package com.realcak3d.scanimal.work

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.realcak3d.scanimal.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updater: checks the latest GitHub release of the Scanimal repo, downloads the APK and hands it
 * to Android's installer (Android always asks the user to confirm an install).
 */
object Updater {
    const val REPO = "real-CAK3D/Scanimal"

    data class Release(val version: String, val apkUrl: String, val notes: String, val page: String)

    sealed class State {
        object Idle : State()
        object Checking : State()
        object UpToDate : State()
        data class Available(val release: Release) : State()
        data class Downloading(val release: Release, val percent: Int) : State()
        data class Ready(val release: Release, val file: File) : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** True when [remote] (e.g. "1.2.0") is newer than [local] (e.g. "1.1.3" or "0.0.12-dev"). */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trimStart('v', 'V').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote); val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return local.contains("-") && !remote.contains("-")
    }

    suspend fun check(): State = withContext(Dispatchers.IO) {
        _state.value = State.Checking
        val result = runCatching {
            val c = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000; readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Scanimal/${currentVersion}")
            }
            if (c.responseCode == 404) return@runCatching State.UpToDate
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val assets = o.optJSONArray("assets")
            var apk = ""
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) { apk = a.optString("browser_download_url"); break }
            }
            if (apk.isBlank() || !isNewer(tag, currentVersion)) State.UpToDate
            else State.Available(Release(tag.trimStart('v'), apk, o.optString("body").take(1500), o.optString("html_url")))
        }.getOrElse { State.Failed(it.message ?: "Network error") }
        _state.value = result
        result
    }

    suspend fun download(ctx: Context, rel: Release): State = withContext(Dispatchers.IO) {
        val result = runCatching {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "Scanimal-${rel.version}.apk")
            var url = URL(rel.apkUrl)
            var conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10000; conn.readTimeout = 20000
            // GitHub redirects to a CDN on another host; follow manually to be safe.
            var hops = 0
            while (conn.responseCode in 300..399 && hops++ < 5) {
                url = URL(conn.getHeaderField("Location")); conn = url.openConnection() as HttpURLConnection
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024); var done = 0L; var n: Int
                    while (input.read(buf).also { n = it } >= 0) {
                        o.write(buf, 0, n); done += n
                        if (total > 0) _state.value = State.Downloading(rel, (done * 100 / total).toInt())
                    }
                }
            }
            State.Ready(rel, out)
        }.getOrElse { State.Failed("Download failed: ${it.message}") }
        _state.value = result
        result
    }

    /** Opens the system installer, or the "allow from this source" screen first if needed. */
    fun install(ctx: Context, file: File) {
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
