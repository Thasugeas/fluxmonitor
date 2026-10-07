package com.fluxmonitor

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("flux", Context.MODE_PRIVATE)

    var nodeIp: String
        get() = sp.getString("ip", "111.111.111.111") ?: "111.111.111.111"
        set(v) = sp.edit().putString("ip", v).apply()

    var rewardPerBlock: Double
        get() = sp.getFloat("reward", 22.5f).toDouble()
        set(v) = sp.edit().putFloat("reward", v.toFloat()).apply()

    var costValue: Double
        get() = sp.getFloat("cost", 0f).toDouble()
        set(v) = sp.edit().putFloat("cost", v.toFloat()).apply()

    // true = coût en EUR, false = coût en FLUX
    var costInEur: Boolean
        get() = sp.getBoolean("costEur", true)
        set(v) = sp.edit().putBoolean("costEur", v).apply()
}
