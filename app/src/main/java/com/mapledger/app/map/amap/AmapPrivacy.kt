package com.mapledger.app.map.amap

import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings

/** Amap requires privacy-compliance calls before any SDK object is created (合规接口). */
object AmapPrivacy {
    private const val PREFS = "privacy"
    private const val KEY = "agreed_v1"

    fun isAgreed(ctx: Context) = ctx.getSharedPreferences(PREFS, 0).getBoolean(KEY, false)

    fun agree(ctx: Context) {
        ctx.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY, true).apply()
        apply(ctx)
    }

    /** Must run on every process start (before using Amap) once the user has agreed. */
    fun apply(ctx: Context) {
        val app = ctx.applicationContext
        val agreed = isAgreed(app)
        MapsInitializer.updatePrivacyShow(app, true, true)
        MapsInitializer.updatePrivacyAgree(app, agreed)
        AMapLocationClient.updatePrivacyShow(app, true, true)
        AMapLocationClient.updatePrivacyAgree(app, agreed)
        ServiceSettings.updatePrivacyShow(app, true, true)
        ServiceSettings.updatePrivacyAgree(app, agreed)
    }
}
