package com.richard.glyphcontroller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : Activity() {

    private var mClient: RootGlyphClient? = null
    private var mIsOn = false
    private var mRootAvailable = false
    private var mToggleInProgress = false

    private lateinit var tvStatus: TextView
    private lateinit var tvNotify: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnListener: Button
    private lateinit var swNotify: SwitchMaterial
    private lateinit var swCharger: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvNotify = findViewById(R.id.tvNotify)
        btnToggle = findViewById(R.id.btnToggle)
        btnListener = findViewById(R.id.btnListener)
        swNotify = findViewById(R.id.swNotify)
        swCharger = findViewById(R.id.swCharger)

        mIsOn = GlyphPowerState.isOn(this)
        tvStatus.setText(R.string.status_unknown)
        btnToggle.isEnabled = false
        val showUnsupportedDialog = savedInstanceState == null
        val client = RootGlyphClient()
        Thread {
            val rootAvailable = client.isRootAvailable()
            val glyphSupported = GlyphHardware.isSupported(rootAvailable)
            if (rootAvailable && glyphSupported != false) {
                // root 保活 + 充电监控：安装 service.d 看门狗（幂等）
                RootKeepAlive.ensureInstalled(applicationContext, client)
            }
            runOnUiThread {
                if (isDestroyed) {
                    Thread { client.destroy() }.start()
                    return@runOnUiThread
                }
                if (glyphSupported == false) {
                    Thread { client.destroy() }.start()
                    tvStatus.setText(R.string.status_no_glyph)
                    btnToggle.isEnabled = false
                    if (showUnsupportedDialog) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.glyph_unsupported_title)
                            .setMessage(R.string.glyph_unsupported_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                } else if (rootAvailable) {
                    mClient = client
                    mRootAvailable = true
                    mIsOn = GlyphPowerState.isOn(this)
                    renderPowerState()
                } else {
                    client.destroy()
                    tvStatus.setText(R.string.status_no_root)
                    btnToggle.isEnabled = false
                }
            }
        }.start()

        btnToggle.setOnClickListener {
            togglePower()
        }

        findViewById<Button>(R.id.btnDetail).setOnClickListener {
            startActivity(Intent(this, GlyphDetailActivity::class.java))
        }

        btnListener.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
        swNotify.isChecked = sp.getBoolean("notify_enabled", true)
        swCharger.isChecked = sp.getBoolean("charger_enabled", true)
        swNotify.setOnCheckedChangeListener { _, checked ->
            sp.edit().putBoolean("notify_enabled", checked).apply()
        }
        swCharger.setOnCheckedChangeListener { _, checked ->
            sp.edit().putBoolean("charger_enabled", checked).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        mIsOn = GlyphPowerState.isOn(this)
        if (mRootAvailable) renderPowerState()
        refreshNotifyState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && mRootAvailable) {
            mIsOn = GlyphPowerState.isOn(this)
            renderPowerState()
        }
    }

    private fun renderPowerState() {
        val frame = GlyphPowerState.getFrame(this)
        mIsOn = frame.any { it > 0 }
        tvStatus.setText(
            when {
                !mIsOn -> R.string.status_all_off
                frame.all { it == 255 } -> R.string.status_all_on
                else -> R.string.status_custom
            }
        )
        btnToggle.setText(if (mIsOn) R.string.btn_turn_off else R.string.btn_turn_on)
        btnToggle.isEnabled = !mToggleInProgress
    }

    private fun refreshNotifyState() {
        val listenerOn = isListenerEnabled()
        tvNotify.setText(if (listenerOn) R.string.notify_ready else R.string.notify_need_listener)
    }

    private fun isListenerEnabled(): Boolean {
        val listeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        )
        return listeners != null && listeners.contains(packageName)
    }

    private fun togglePower() {
        val client = mClient ?: return
        if (mToggleInProgress) return
        mToggleInProgress = true
        btnToggle.isEnabled = false
        Thread {
            var success = false
            var rootAvailable = false
            try {
                rootAvailable = client.isRootAvailable()
                if (rootAvailable) success = GlyphEffectCoordinator.toggleManual(applicationContext)
            } catch (e: Exception) {
                Log.e("GlyphController", "Failed to toggle glyphs", e)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                mToggleInProgress = false
                if (success) {
                    mIsOn = GlyphPowerState.isOn(this)
                    renderPowerState()
                } else if (rootAvailable) {
                    tvStatus.setText(R.string.status_write_failed)
                    btnToggle.isEnabled = true
                } else {
                    mRootAvailable = false
                    tvStatus.setText(R.string.status_no_root)
                    btnToggle.isEnabled = false
                }
            }
        }.start()
    }

    override fun onDestroy() {
        mClient?.let { client -> Thread { client.destroy() }.start() }
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "settings"
    }
}
