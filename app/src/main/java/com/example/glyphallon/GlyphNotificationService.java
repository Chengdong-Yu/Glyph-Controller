package com.example.glyphallon;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * 通知触发闪烁：来通知时三组灯带依次迅速亮起并熄灭一次——
 * 最下方感叹号竖条+圆点 -> 主环右上方弧段 -> 相机 deco 周围两条灯带。
 */
public class GlyphNotificationService extends NotificationListenerService {
    private static final String TAG = "GlyphFlash";
    private static final long ON_MS = 90;   // 每组亮起时长
    private static final long GAP_MS = 40;  // 组间熄灭间隔

    /** 依次闪烁的三组灯带（frame 索引） */
    private static final int[][] GROUPS = {
            {25, 26, 27, 28, 29, 30, 31, 32, 24},   // 最下方：感叹号竖条 + 圆点
            {13, 14, 15, 16, 17, 18},               // 主环右上方弧段
            {0, 1}                                   // 相机 deco 周围两条灯带
    };

    private final Object mLock = new Object();
    private RootGlyphClient mClient;
    private boolean mFlashing;
    private boolean mKeepAliveInstalled;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service created");
        // 开机时系统会自动绑定通知监听 -> 借机安装/修复 root 监控循环（幂等）
        if (!mKeepAliveInstalled) {
            mKeepAliveInstalled = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    synchronized (mLock) {
                        if (mClient == null) mClient = new RootGlyphClient();
                    }
                    RootKeepAlive.ensureInstalled(GlyphNotificationService.this, mClient);
                    // 通知监听注册自愈：独立会话执行（已注册时为 no-op）
                    RootKeepAlive.ensureListenerRegistered();
                }
            }).start();
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 功能开关：关闭时直接忽略通知
        if (!getSharedPreferences("settings", MODE_PRIVATE).getBoolean("notify_enabled", true)) {
            return;
        }
        Notification n = sbn.getNotification();
        // 跳过分组摘要和常驻通知，避免重复触发
        if ((n.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT)) != 0) {
            return;
        }
        synchronized (mLock) {
            if (mFlashing) return;
            mFlashing = true;
        }
        new Thread(mFlash).start();
    }

    private final Runnable mFlash = new Runnable() {
        @Override
        public void run() {
            RootGlyphClient client;
            synchronized (mLock) {
                if (mClient == null) mClient = new RootGlyphClient();
                client = mClient;
            }
            if (!client.isRootAvailable()) {
                Log.w(TAG, "no root, skip flash");
                synchronized (mLock) {
                    mFlashing = false;
                }
                return;
            }
            try {
                int[] off = new int[33];
                for (int[] g : GROUPS) {
                    int[] on = new int[33];
                    for (int idx : g) on[idx] = 255;
                    synchronized (mLock) {
                        client.setZoneColors(on);
                    }
                    Thread.sleep(ON_MS);
                    synchronized (mLock) {
                        client.setZoneColors(off);
                    }
                    Thread.sleep(GAP_MS);
                }
            } catch (InterruptedException ignored) {
            } finally {
                client.turnOff();
                synchronized (mLock) {
                    mFlashing = false;
                }
            }
        }
    };

    @Override
    public void onDestroy() {
        synchronized (mLock) {
            if (mClient != null) {
                mClient.destroy();
                mClient = null;
            }
        }
        super.onDestroy();
    }
}
