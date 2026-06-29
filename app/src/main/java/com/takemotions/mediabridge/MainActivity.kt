package com.takemotions.mediabridge

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The single Media Bridge screen: one switch, the Notification Access grant, a live
 * now-playing readout for sanity-checking, and the plain-language privacy statement.
 * Deliberately one screen — this app does one thing.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var mediaSwitch: SwitchCompat
    private lateinit var accessText: TextView
    private lateinit var nowPlayingText: TextView

    private var syncing = false

    private val uiHandler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            updateUi()
            uiHandler.postDelayed(this, 1500)
        }
    }

    private val postNotifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        applyInsets(R.id.main)

        findViewById<TextView>(R.id.title).text = versionedTitle("Media Bridge")
        mediaSwitch = findViewById(R.id.mediaSwitch)
        accessText = findViewById(R.id.accessText)
        nowPlayingText = findViewById(R.id.nowPlayingText)

        mediaSwitch.setOnCheckedChangeListener { _, checked ->
            if (syncing) return@setOnCheckedChangeListener
            Prefs.setMediaEnabled(this, checked)
            if (checked) {
                ensurePostNotifPermission()
                if (!hasNotificationAccess()) openNotificationAccessSettings()
            }
            syncService()
            updateUi()
        }
        findViewById<Button>(R.id.grantAccessBtn).setOnClickListener { openNotificationAccessSettings() }
        findViewById<Button>(R.id.appSettingsBtn).setOnClickListener { openAppSettings() }
    }

    override fun onResume() {
        super.onResume()
        uiHandler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(refresh)
    }

    private fun updateUi() {
        val access = hasNotificationAccess()
        syncing = true
        mediaSwitch.isChecked = Prefs.mediaEnabled(this)
        syncing = false

        accessText.text =
            if (access) "Notification access: granted"
            else "Notification access: not granted — tap below"
        findViewById<Button>(R.id.grantAccessBtn).visibility =
            if (access) android.view.View.GONE else android.view.View.VISIBLE

        nowPlayingText.text = when {
            !Prefs.mediaEnabled(this) -> "Bridge off"
            !access -> "Grant notification access to read media"
            else -> {
                val j = MediaHub.currentJson(this)
                val state = j.optString("state", "none")
                if (state == "none") {
                    "Nothing playing"
                } else {
                    val app = j.optString("app")
                    val title = j.optString("title")
                    val artist = j.optString("artist")
                    val live = if (j.optBoolean("live")) "  (live)" else ""
                    "[$state] $app$live\n${title.ifBlank { "(no title)" }}" +
                        if (artist.isNotBlank()) " — $artist" else ""
                }
            }
        }
    }

    // ---- helpers ----------------------------------------------------------------

    private fun applyInsets(rootViewId: Int) {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(rootViewId)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        val me = packageName
        return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == me }
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun ensurePostNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            postNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Start the bridge service when the feature is on, stop it when off. */
    private fun syncService() {
        val i = Intent(this, BridgeService::class.java)
        if (Prefs.mediaEnabled(this)) {
            i.action = BridgeService.ACTION_START
            ContextCompat.startForegroundService(this, i)
        } else {
            i.action = BridgeService.ACTION_STOP
            startService(i)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    @Suppress("DEPRECATION")
    private fun appVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: Exception) {
        ""
    }

    /** "Title  vX.Y.Z" with the version dimmed and smaller. */
    private fun versionedTitle(base: String): CharSequence {
        val v = appVersion()
        val suffix = if (v.isEmpty()) "" else "  v$v"
        val full = "$base$suffix"
        return SpannableString(full).apply {
            if (suffix.isNotEmpty()) {
                val s = full.length - suffix.length
                setSpan(RelativeSizeSpan(0.6f), s, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(0xFF7B7B7B.toInt()), s, full.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
