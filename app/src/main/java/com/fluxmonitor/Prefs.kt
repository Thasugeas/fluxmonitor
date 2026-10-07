package com.fluxmonitor

import android.content.Context

object Prefs {
    private const val FILE = "fluxmonitor"

    fun getNode(c: Context): String =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("node", "") ?: ""

    fun setNode(c: Context, v: String) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("node", v.trim()).apply()

    fun getCost(c: Context): Double =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getFloat("cost", 0f).toDouble()

    fun setCost(c: Context, v: Double) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putFloat("cost", v.toFloat()).apply()

    fun isCostInFlux(c: Context): Boolean =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("costFlux", false)

    fun setCostInFlux(c: Context, v: Boolean) =
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("costFlux", v).apply()
}
