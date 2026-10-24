package com.personal.investmenttracker.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.investmenttracker.data.broker.zerodha.KiteAuthHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KiteLoginWebViewDialog(
    loginUrl: String,
    onDismiss: () -> Unit,
    onTokenCaptured: (String) -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Zerodha Kite Login", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close login")
                        }
                    },
                )
            },
        ) { padding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                KiteWebViewContent(
                    url = loginUrl,
                    onLoadingChanged = { isLoading = it },
                    onTokenCaptured = onTokenCaptured,
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KiteWebViewContent(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    onTokenCaptured: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient =
                    createKiteWebViewClient(
                        onLoadingChanged = onLoadingChanged,
                        onTokenCaptured = onTokenCaptured,
                    )
                loadUrl(url)
            }
        },
    )
}

private fun createKiteWebViewClient(
    onLoadingChanged: (Boolean) -> Unit,
    onTokenCaptured: (String) -> Unit,
): WebViewClient =
    object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?,
        ): Boolean {
            val targetUrl = request?.url?.toString() ?: ""
            return handleRedirectUrl(targetUrl, onTokenCaptured)
        }

        override fun onPageStarted(
            view: WebView?,
            url: String?,
            favicon: Bitmap?,
        ) {
            super.onPageStarted(view, url, favicon)
            onLoadingChanged(true)
            if (url != null) {
                handleRedirectUrl(url, onTokenCaptured)
            }
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?,
        ) {
            super.onPageFinished(view, url)
            onLoadingChanged(false)
        }
    }

private fun handleRedirectUrl(
    url: String,
    onTokenCaptured: (String) -> Unit,
): Boolean {
    val token = KiteAuthHelper.extractRequestToken(url)
    return if (token != null) {
        onTokenCaptured(token)
        true
    } else {
        false
    }
}
