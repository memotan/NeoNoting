package com.neonoting.widget

import android.content.Context

/**
 * GAS URL を SharedPreferences に保存する。
 * NeoNoting 本体（PWA）が localStorage の nt_gas_url に持つのと同じ値を入れる。
 */
object Prefs {
    private const val FILE = "neonoting_widget"
    private const val KEY_GAS_URL = "gas_url"

    private fun sp(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getGasUrl(ctx: Context): String =
        sp(ctx).getString(KEY_GAS_URL, "") ?: ""

    fun setGasUrl(ctx: Context, url: String) {
        sp(ctx).edit().putString(KEY_GAS_URL, url.trim()).apply()
    }
}
