package com.example.glyphallon;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/** 空的粘性服务：root 看门狗用它把被杀的进程拉活；拉活的同时顺带修复监控循环 */
public class KeepAliveService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("GlyphKeepAlive", "resurrected");
        final RootGlyphClient client = new RootGlyphClient();
        new Thread(new Runnable() {
            @Override
            public void run() {
                RootKeepAlive.ensureInstalled(KeepAliveService.this, client);
                client.destroy();
            }
        }).start();
        return START_STICKY;
    }
}
