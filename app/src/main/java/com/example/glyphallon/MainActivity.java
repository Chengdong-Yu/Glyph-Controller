package com.example.glyphallon;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends Activity {
    private static final String TAG = "GlyphAllOn";
    private static final String PREFS = "settings";

    private RootGlyphClient mClient;
    private boolean mIsOn = false;

    private TextView tvStatus;
    private TextView tvNotify;
    private Button btnToggle;
    private Button btnListener;
    private SwitchMaterial swNotify;
    private SwitchMaterial swCharger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvNotify = findViewById(R.id.tvNotify);
        btnToggle = findViewById(R.id.btnToggle);
        btnListener = findViewById(R.id.btnListener);
        swNotify = findViewById(R.id.swNotify);
        swCharger = findViewById(R.id.swCharger);

        mClient = new RootGlyphClient();

        if (mClient.isRootAvailable()) {
            tvStatus.setText(R.string.status_root_ok);
            btnToggle.setEnabled(true);
            // root 保活 + 充电监控：安装 service.d 看门狗（幂等）
            RootKeepAlive.ensureInstalled(this, mClient);
        } else {
            tvStatus.setText(R.string.status_no_root);
            btnToggle.setEnabled(false);
        }

        btnToggle.setOnClickListener(v -> {
            if (mIsOn) {
                turnOff();
            } else {
                turnOnAll();
            }
        });

        btnListener.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        swNotify.setChecked(sp.getBoolean("notify_enabled", true));
        swCharger.setChecked(sp.getBoolean("charger_enabled", true));
        swNotify.setOnCheckedChangeListener((b, checked) ->
                sp.edit().putBoolean("notify_enabled", checked).apply());
        swCharger.setOnCheckedChangeListener((b, checked) ->
                sp.edit().putBoolean("charger_enabled", checked).apply());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshNotifyState();
    }

    private void refreshNotifyState() {
        boolean listenerOn = isListenerEnabled();
        tvNotify.setText(listenerOn ? R.string.notify_ready : R.string.notify_need_listener);
    }

    private boolean isListenerEnabled() {
        String listeners = Settings.Secure.getString(
                getContentResolver(), "enabled_notification_listeners");
        return listeners != null && listeners.contains(getPackageName());
    }

    private void turnOnAll() {
        mClient.turnOnAll();
        mIsOn = true;
        tvStatus.setText(R.string.status_all_on);
        btnToggle.setText(R.string.btn_turn_off);
    }

    private void turnOff() {
        mClient.turnOff();
        mIsOn = false;
        tvStatus.setText(R.string.status_all_off);
        btnToggle.setText(R.string.btn_turn_on);
    }

    @Override
    protected void onDestroy() {
        if (mClient != null) {
            mClient.destroy();
        }
        super.onDestroy();
    }
}
