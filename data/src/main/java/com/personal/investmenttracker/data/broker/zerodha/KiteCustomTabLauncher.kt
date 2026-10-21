package com.personal.investmenttracker.data.broker.zerodha

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object KiteCustomTabLauncher {
    fun launchLoginTab(
        context: Context,
        apiKey: String,
        redirectParams: String? = null,
    ) {
        val url = KiteAuthHelper.buildLoginUrl(apiKey, redirectParams)
        val customTabsIntent =
            CustomTabsIntent
                .Builder()
                .setShowTitle(true)
                .build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }
}
