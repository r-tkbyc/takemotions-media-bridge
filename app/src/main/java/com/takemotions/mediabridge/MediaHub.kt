package com.takemotions.mediabridge

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import org.json.JSONObject

/**
 * Reads the currently-playing media and sends transport / volume commands to the
 * active player.
 *
 * Uses [MediaSessionManager] / [MediaController], gated by Notification Access (the
 * [MediaAccessService] component is the listener handle MediaSessionManager requires).
 * That listener is empty — it reads no notifications. Everything here is queried on
 * demand per HTTP request; no persistent listeners are kept.
 */
object MediaHub {

    private fun manager(ctx: Context): MediaSessionManager? =
        ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private fun listenerComponent(ctx: Context) =
        ComponentName(ctx, MediaAccessService::class.java)

    private fun activeSessions(ctx: Context): List<MediaController> = try {
        manager(ctx)?.getActiveSessions(listenerComponent(ctx)) ?: emptyList()
    } catch (_: SecurityException) {
        emptyList() // Notification Access not granted
    } catch (_: Exception) {
        emptyList()
    }

    // Distinguish a ring-initiated pause (keep — so the glasses can resume it) from a
    // session an app simply left PAUSED behind when it was closed / swiped away (drop —
    // so a finished app's track stops showing). Both look identical from the playback
    // state alone (PAUSED + active), so we remember which session WE paused from the ring.
    // Anything PAUSED that we did not pause is treated as a stale remnant.
    @Volatile private var ringPausedPkg: String? = null
    @Volatile private var ringPausedAt: Long = 0L
    private const val PAUSE_KEEP_MS = 5 * 60 * 1000L // safety expiry for a kept ring-pause

    private fun markRingPause(c: MediaController) {
        ringPausedPkg = c.packageName
        ringPausedAt = SystemClock.elapsedRealtime()
    }

    private fun clearRingPause() {
        ringPausedPkg = null
    }

    private fun isOurRingPause(c: MediaController): Boolean {
        val pkg = ringPausedPkg ?: return false
        return c.packageName == pkg &&
            SystemClock.elapsedRealtime() - ringPausedAt < PAUSE_KEEP_MS
    }

    /**
     * A session is "live" (worth reporting) while playing / buffering. A PAUSED session
     * is kept ONLY if the ring is what paused it, so play/pause from the glasses can
     * resume it; a session an app left PAUSED on its own (closed / swiped away) is
     * dropped immediately so the closed app's track stops showing. Stopped / none /
     * error are always dropped.
     */
    private fun isLive(c: MediaController): Boolean = when (c.playbackState?.state) {
        PlaybackState.STATE_PLAYING,
        PlaybackState.STATE_BUFFERING -> true
        PlaybackState.STATE_PAUSED -> isOurRingPause(c)
        else -> false // STOPPED / NONE / ERROR / CONNECTING / null
    }

    /** The most relevant live controller: a playing one if any, else the first live one. */
    private fun pick(ctx: Context): MediaController? {
        val sessions = activeSessions(ctx).filter { isLive(it) }
        if (sessions.isEmpty()) return null
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.first()
    }

    /** Now-playing as JSON. `state` = none when nothing is active. */
    fun currentJson(ctx: Context): JSONObject {
        val c = pick(ctx)
            ?: return JSONObject().put("playing", false).put("state", "none").put("live", false)
        val md = c.metadata
        val ps = c.playbackState
        val state = when (ps?.state) {
            PlaybackState.STATE_PLAYING -> "playing"
            PlaybackState.STATE_PAUSED -> "paused"
            PlaybackState.STATE_STOPPED -> "stopped"
            PlaybackState.STATE_BUFFERING -> "buffering"
            else -> "unknown"
        }
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        // No fixed length (live stream / radio / endless) comes through as duration 0 or -1.
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
        return JSONObject()
            .put("playing", ps?.state == PlaybackState.STATE_PLAYING)
            .put("state", state)
            .put("package", c.packageName ?: "")
            .put("app", appLabel(ctx, c.packageName))
            .put("title", md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "")
            .put("artist", artist)
            .put("album", md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "")
            .put("position", ps?.position ?: -1L)
            .put("duration", duration)
            .put("live", duration <= 0L)
    }

    /** Apply a transport / volume command to the active player. @return true if dispatched. */
    fun command(ctx: Context, action: String): Boolean {
        val c = pick(ctx) ?: return false
        val tc = c.transportControls
        when (action) {
            "play" -> { tc.play(); clearRingPause() }
            "pause" -> { tc.pause(); markRingPause(c) }
            "playpause" ->
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    tc.pause(); markRingPause(c)
                } else {
                    tc.play(); clearRingPause()
                }
            "next" -> { tc.skipToNext(); clearRingPause() }
            "prev", "previous" -> { tc.skipToPrevious(); clearRingPause() }
            "volup", "volume_up" -> c.adjustVolume(AudioManager.ADJUST_RAISE, 0)
            "voldown", "volume_down" -> c.adjustVolume(AudioManager.ADJUST_LOWER, 0)
            else -> return false
        }
        return true
    }

    private fun appLabel(ctx: Context, pkg: String?): String {
        pkg ?: return ""
        return try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
    }
}
