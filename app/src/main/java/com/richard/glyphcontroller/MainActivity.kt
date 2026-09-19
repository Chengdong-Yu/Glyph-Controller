package com.richard.glyphcontroller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : Activity() {

    private lateinit var mClient: RootGlyphClient
    private var mIsOn = false

    private lateinit var tvStatus: TextView
    private lateinit var tvNotify: TextView
    private lateinit var btnToggle: Button
    private lateinit var btnListener: Button
    private lateinit var swNotify: SwitchMaterial
    private lateinit var swCharger: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvNotify = findViewById(R.id.tvNotify)
        btnToggle = findViewById(R.id.btnToggle)
        btnListener = findViewById(R.id.btnListener)
        swNotify = findViewById(R.id.swNotify)
        swCharger = findViewById(R.id.swCharger)

        mClient = RootGlyphClient()

        if (mClient.isRootAvailable()) {
            tvStatus.setText(R.string.status_root_ok)
            btnToggle.isEnabled = true
            // root 保活 + 充电监控：安装 service.d 看门狗（幂等）
            RootKeepAlive.ensureInstalled(this, mClient)
        } else {
            tvStatus.setText(R.string.status_no_root)
            btnToggle.isEnabled = false
        }

        btnToggle.setOnClickListener {
            if (mIsOn) turnOff() else turnOnAll()
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
        refreshNotifyState()
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

    private fun turnOnAll() {
        mClient.turnOnAll()
        mIsOn = true
        tvStatus.setText(R.string.status_all_on)
        btnToggle.setText(R.string.btn_turn_off)
    }

    private fun turnOff() {
        mClient.turnOff()
        mIsOn = false
        tvStatus.setText(R.string.status_all_off)
        btnToggle.setText(R.string.btn_turn_on)
    }

    override fun onDestroy() {
        if (this::mClient.isInitialized) {
            mClient.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "settings"
    }
}
