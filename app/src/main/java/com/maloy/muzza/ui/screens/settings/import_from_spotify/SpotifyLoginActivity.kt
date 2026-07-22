package com.maloy.muzza.ui.screens.settings.import_from_spotify

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class SpotifyLoginActivity : ComponentActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val cookieManager = CookieManager.getInstance()
                                    val cookies = cookieManager.getCookie("https://open.spotify.com")
                                    
                                    if (cookies != null && cookies.contains("sp_dc=")) {
                                        val spDc = cookies.split(";")
                                            .find { it.trim().startsWith("sp_dc=") }
                                            ?.split("=")
                                            ?.get(1)
                                            ?.trim()
                                        
                                        if (spDc != null) {
                                            val resultIntent = Intent()
                                            resultIntent.putExtra("SP_DC", spDc)
                                            setResult(RESULT_OK, resultIntent)
                                            finish()
                                        }
                                    }
                                }
                            }
                            loadUrl("https://accounts.spotify.com/en/login?continue=https:%2F%2Fopen.spotify.com%2F")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
