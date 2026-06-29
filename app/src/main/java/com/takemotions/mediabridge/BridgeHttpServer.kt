package com.takemotions.mediabridge

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

/**
 * Serves the currently-playing media on http://127.0.0.1:<port>
 *
 *   GET /media
 *       -> {"enabled":true,"playing":bool,"state":"playing|paused|stopped|buffering|none",
 *           "package":..,"app":..,"title":..,"artist":..,"album":..,
 *           "position":ms,"duration":ms,"live":bool}
 *       (or {"enabled":false} when the bridge switch is off)
 *   GET /media/<action>   action = play|pause|playpause|next|prev|volup|voldown
 *       -> {"ok":bool,"action":..}
 *   GET /health
 *       -> {"ok":true,"media":bool}
 *
 * Bound to the loopback interface only (127.0.0.1) — never reachable off-device.
 * CORS "*" + Cache-Control: no-store on every response so the Even WebView companion
 * (a glasses app's React UI) can fetch it.
 */
class BridgeHttpServer(private val appContext: Context, port: Int) :
    NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
        }

        val uri = session.uri
        return when {
            // Now-playing media (read). GET /media
            uri == "/media" -> {
                val body = if (!Prefs.mediaEnabled(appContext)) {
                    JSONObject().put("enabled", false).toString()
                } else {
                    MediaHub.currentJson(appContext).put("enabled", true).toString()
                }
                cors(newFixedLengthResponse(Response.Status.OK, "application/json", body))
            }

            // Media control. /media/<action> = play|pause|playpause|next|prev|volup|voldown
            uri.startsWith("/media/") -> {
                val body = if (!Prefs.mediaEnabled(appContext)) {
                    "{\"ok\":false,\"error\":\"media disabled\"}"
                } else {
                    val action = uri.substringAfterLast('/')
                    val ok = MediaHub.command(appContext, action)
                    JSONObject().put("ok", ok).put("action", action).toString()
                }
                cors(newFixedLengthResponse(Response.Status.OK, "application/json", body))
            }

            uri == "/health" -> {
                val body = JSONObject()
                    .put("ok", true)
                    .put("media", Prefs.mediaEnabled(appContext))
                    .toString()
                cors(newFixedLengthResponse(Response.Status.OK, "application/json", body))
            }

            else -> cors(
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "application/json", "{\"error\":\"not found\"}"
                )
            )
        }
    }

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        r.addHeader("Access-Control-Allow-Headers", "*")
        r.addHeader("Cache-Control", "no-store")
        return r
    }
}
