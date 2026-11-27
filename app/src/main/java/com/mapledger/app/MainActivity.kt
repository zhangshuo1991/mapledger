package com.mapledger.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.mapledger.app.map.amap.AmapPrivacy
import com.mapledger.app.ui.AppRoot
import com.mapledger.app.ui.screens.PrivacyScreen
import com.mapledger.app.ui.theme.MapLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MapLedgerTheme {
                var agreed by remember { mutableStateOf(AmapPrivacy.isAgreed(this)) }
                Crossfade(agreed, animationSpec = tween(320), label = "gate") { ok ->
                    if (ok) AppRoot()
                    else PrivacyScreen(
                        onAgree = { AmapPrivacy.agree(this@MainActivity); agreed = true },
                        onDecline = { finish() },
                    )
                }
            }
        }
    }
}
