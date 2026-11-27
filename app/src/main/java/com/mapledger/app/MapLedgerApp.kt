package com.mapledger.app

import android.app.Application
import com.mapledger.app.map.amap.AmapPrivacy

class MapLedgerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Privacy-compliance state must be declared before Amap is touched (no-op agreement until user consents).
        AmapPrivacy.apply(this)
    }
}
