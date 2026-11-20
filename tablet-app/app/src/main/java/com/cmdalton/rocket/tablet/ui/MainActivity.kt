package com.cmdalton.rocket.tablet.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.cmdalton.rocket.tablet.R
import com.cmdalton.rocket.tablet.RocketBridge
import com.cmdalton.rocket.tablet.BuildConfig

/**
 * v17 cockpit + Dad Control in a full-screen WebView. Firebase and all v17 UI/logic stay in the
 * bundled assets; the shell only adds Clock timer, widget, landing, and big kid buttons.
 */
class MainActivity : ComponentActivity() {
    companion object {
        /**
         * The bundle is served from an HTTPS origin, not file://. From file:// the WebView gives
         * the page a null origin and blocks its ES modules (kid-sync.js / dad-sync.js import
         * ./firebase.js), so Firebase sign-in, linking, requests and approvals never start.
         * https://developer.android.com/develop/ui/views/layout/webapps/load-local-content
         */
        const val ORIGIN = "https://appassets.androidplatform.net"
        const val START_URL = "$ORIGIN/assets/www/index.html"
    }

    lateinit var webView: WebView
    var landingOpen = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        landingOpen = false
        setContentView(R.layout.activity_main)
        askForNotifications()
        webView = findViewById(R.id.webview)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            // Everything is served through WebViewAssetLoader below; no file:// access needed.
            allowFileAccess = false
            allowContentAccess = false
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(this, true)
            }
        }
        webView.addJavascriptInterface(RocketBridge(this), RocketBridge.NAME)
        webView.webChromeClient = WebChromeClient()
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val path = request.url.lastPathSegment ?: return false
                if (path.endsWith(".html")) {
                    view.loadUrl(request.url.toString())
                    return true
                }
                return false
            }
        }
        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        webView.restoreState(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        landingOpen = false
        webView.onResume()
        webView.visibility = View.VISIBLE
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    fun showClockToast() {
        Toast.makeText(this, getString(R.string.clock_failed), Toast.LENGTH_LONG).show()
    }
}