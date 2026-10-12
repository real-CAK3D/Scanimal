package com.realcak3d.scanimal.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.realcak3d.scanimal.core.Discovery
import org.json.JSONArray
import org.json.JSONObject

/** A WebView running the bundled Leaflet map (the MapPI3 stack): real tiles + 3D pitch toggle. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LeafletMap(discoveries: List<Discovery>, me: Pair<Double, Double>?, modifier: Modifier = Modifier) {
    val pinsJson = remember(discoveries.size) {
        JSONArray().apply {
            discoveries.takeLast(1500).forEach { put(JSONObject().put("lat", it.lat).put("lon", it.lon).put("kind", it.kind)) }
        }.toString()
    }
    var ready by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }

    fun push(w: WebView) {
        w.evaluateJavascript("window.sc && window.sc.setPins(${JSONObject.quote(pinsJson)});", null)
        me?.let { w.evaluateJavascript("window.sc && window.sc.setMe(${it.first}, ${it.second});", null) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(false)
                setBackgroundColor(0xFF05120B.toInt())
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) { ready = true; push(view) }
                }
                loadUrl("file:///android_asset/map/map.html")
                web = this
            }
        },
        update = { w -> if (ready) push(w) },
    )
}
