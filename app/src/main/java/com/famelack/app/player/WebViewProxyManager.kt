package com.famelack.app.player

import android.util.Log
import androidx.webkit.ProxyConfig as WebkitProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature

/**
 * Configures WebView process-level proxy routing.
 * Used to route YouTube WebView traffic through FCAE HTTP proxy (127.0.0.1:1820).
 *
 * WebView does not support SOCKS5 proxy natively — this uses the FCAE HTTP proxy port
 * which is available on the same engine. The ProxyController API from AndroidX WebKit
 * sets the proxy for ALL WebView instances in the process.
 */
object WebViewProxyManager {
    private const val TAG = "WebViewProxyManager"

    /** Currently active proxy target, null = direct (no proxy) */
    private var activeHost: String? = null

    /**
     * Route all WebView traffic through the specified HTTP proxy.
     * Falls back to direct if WebKit ProxyController is not available.
     */
    fun setProxy(host: String, port: Int) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w(TAG, "WebViewFeature.PROXY_OVERRIDE not supported on this device — WebView will not use proxy")
            return
        }

        val rule = "$host:$port"
        if (activeHost == rule) {
            Log.d(TAG, "Proxy already set to $rule — skipping")
            return
        }

        try {
            val config = WebkitProxyConfig.Builder()
                .addProxyRule(rule)
                .addDirect()
                .build()

            ProxyController.getInstance().setProxyOverride(
                config,
                Runnable::run,
                {
                    Log.i(TAG, "WebView proxy set to $rule")
                    activeHost = rule
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception setting WebView proxy", e)
            activeHost = null
        }
    }

    /**
     * Reset WebView proxy to direct (no proxy).
     */
    fun clearProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        if (activeHost == null) return

        try {
            ProxyController.getInstance().clearProxyOverride(
                Runnable::run,
                {
                    Log.i(TAG, "WebView proxy cleared — direct connection")
                    activeHost = null
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception clearing WebView proxy", e)
            activeHost = null
        }
    }

    /**
     * Convenience: apply proxy based on current ProxyConfig state.
     * Call this before loading YouTube in WebView.
     *
     * SOCKS-only tunnels (V2RayNG, FCAE SOCKS port) are bridged through a local
     * HTTP proxy because Chromium's ProxyController cannot speak SOCKS.
     */
    fun applyFromProxyConfig() {
        if (!ProxyConfig.isProxyEnabled) {
            clearProxy()
            return
        }
        when (ProxyConfig.proxyMode) {
            ProxyMode.FCAE_VPN -> {
                if (HttpToSocks5Bridge.isTcpOpen(FcaeVpnManager.HTTP_PORT)) {
                    setProxy("127.0.0.1", FcaeVpnManager.HTTP_PORT)
                } else {
                    bridgeThroughSocks(ProxyConfig.fcaePort)
                }
            }
            ProxyMode.LOCAL_SOCKS5 -> bridgeThroughSocks(ProxyConfig.localSocksPort)
            ProxyMode.LOCAL_HTTP -> setProxy("127.0.0.1", ProxyConfig.localHttpPort)
            ProxyMode.URL_RELAY -> clearProxy()
        }
    }

    private fun bridgeThroughSocks(socksPort: Int) {
        val p = HttpToSocks5Bridge.ensureStarted(socksPort)
        if (p > 0) setProxy("127.0.0.1", p) else clearProxy()
    }

    /** True if proxy is currently applied to WebView */
    fun isProxyActive(): Boolean = activeHost != null
}