package com.takemotions.mediabridge

import android.content.Context

/**
 * User-changeable settings, persisted in SharedPreferences.
 * Media Bridge has a single feature, so there is a single switch.
 */
object Prefs {
    private const val NAME = "mediabridge_prefs"
    private const val KEY_MEDIA_ENABLED = "media_enabled"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Master switch for the media-info + media-control bridge. */
    fun mediaEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_MEDIA_ENABLED, false)

    fun setMediaEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_MEDIA_ENABLED, value).apply()
}
