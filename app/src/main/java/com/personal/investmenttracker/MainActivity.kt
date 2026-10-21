package com.personal.investmenttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.personal.investmenttracker.ui.PlaceholderScreen
import com.personal.investmenttracker.ui.theme.InvestmentTrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            InvestmentTrackerTheme {
                PlaceholderScreen()
            }
        }
    }
}
