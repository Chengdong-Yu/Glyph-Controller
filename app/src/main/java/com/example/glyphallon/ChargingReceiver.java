package com.example.glyphallon;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 系统插电广播（ACTION_POWER_CONNECTED 为豁免的隐式广播，清单注册即可收到） */
public class ChargingReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
            Log.d("GlyphCharger", "power connected broadcast");
            try {
                context.startService(new Intent(context, ChargingSweepService.class));
            } catch (Exception e) {
                Log.w("GlyphCharger", "start sweep failed: " + e);
            }
            // 顺带修复 root 监控循环（进程刚被广播唤醒时 sInstalled 为 false）
            final Context app = context.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    RootGlyphClient client = new RootGlyphClient();
                    RootKeepAlive.ensureInstalled(app, client);
                    client.destroy();
                }
            }).start();
        }
    }
}
