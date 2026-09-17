package com.example.glyphallon;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Root 保活：看门狗脚本安装到 /data/adb/service.d/（Magisk/KernelSU 开机自启目录）。
 * 脚本内容保持 v1.16 版本（单 GKA_LOOP 循环：看门狗 + 充电上升沿），
 * 通知监听注册（allow_listener）由 Java 侧独立执行，不放进脚本，
 * 避免脚本执行期间触发监听重绑定、销毁 su 会话打断安装序列。
 */
public final class RootKeepAlive {
    private static final String TAG = "GlyphKeepAlive";
    private static final String PKG = "com.example.glyphallon";
    private static final String SCRIPT = "/data/adb/service.d/glyph_keepalive.sh";
    private static final String TMP_NAME = "glyph_keepalive.sh";

    private static boolean sInstalled;

    private RootKeepAlive() {
    }

    public static synchronized void ensureInstalled(Context context, RootGlyphClient client) {
        if (sInstalled || client == null || !client.isRootAvailable()) return;
        try {
            writeScript(context);
            String tmp = context.getFileStreamPath(TMP_NAME).getAbsolutePath();
            client.shell("mkdir -p /data/adb/service.d");
            // 清理旧脚本与旧循环
            client.shell("pkill -f GKA_LOOP >/dev/null 2>&1; pkill -f glyph_keepalive.sh >/dev/null 2>&1; echo cleaned");
            client.shell("cp " + tmp + " " + SCRIPT);
            client.shell("chmod 700 " + SCRIPT);
            // 立即启动新脚本
            client.shell("setsid sh " + SCRIPT + " >/dev/null 2>&1 </dev/null &");
            // 自检：循环没起来就补一次
            Thread.sleep(1500);
            String chk = client.shell("pgrep -f GKA_LOOP");
            if (chk == null || chk.trim().isEmpty()) {
                Log.w(TAG, "loop not running, retry");
                client.shell("setsid sh " + SCRIPT + " >/dev/null 2>&1 </dev/null &");
            }
            sInstalled = true;
            Log.d(TAG, "keepalive script installed: " + SCRIPT);
        } catch (Exception e) {
            Log.w(TAG, "install keepalive failed: " + e);
        }
    }

    /** 通知监听注册：独立短会话执行，不影响脚本（幂等，已注册时为 no-op） */
    public static void ensureListenerRegistered() {
        RootGlyphClient client = new RootGlyphClient();
        try {
            if (client.isRootAvailable()) {
                client.shell("cmd notification allow_listener " + PKG
                        + "/.GlyphNotificationService >/dev/null 2>&1; echo done");
            }
        } finally {
            client.destroy();
        }
    }

    private static void writeScript(Context context) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/system/bin/sh\n");
        sb.append("# GlyphAllOn keepalive + charger monitor (installed by app)\n");
        sb.append("PKG=").append(PKG).append("\n");
        // 一次性设置（幂等）：电池白名单 + 后台运行 + 待机桶豁免 + MIUI 自启动
        sb.append("dumpsys deviceidle whitelist +$PKG >/dev/null 2>&1\n");
        sb.append("appops set $PKG RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1\n");
        sb.append("am set-standby-bucket $PKG exempted >/dev/null 2>&1\n");
        sb.append("appops set $PKG AUTO_START allow >/dev/null 2>&1\n");
        sb.append("appops set $PKG BOOT_COMPLETED allow >/dev/null 2>&1\n");
        // 防重复：已有监控循环在跑则退出
        sb.append("pgrep -f GKA_LOOP >/dev/null 2>&1 && exit 0\n");
        sb.append("log -t GlyphKA keepalive-loop-start\n");
        // 单循环：看门狗 + 充电上升沿（setsid 脱离会话防被 su 会话清理）
        sb.append("setsid sh -c 'GKA_LOOP=1; prev=0; t=0; while true; do")
                // 心跳：每 60 秒报一次活，便于确认循环是否被杀
                .append(" t=$((t+1)); if [ $((t % 60)) -eq 0 ]; then log -t GlyphKA alive; fi;")
                // 看门狗
                .append(" if ! pidof ").append(PKG).append(" >/dev/null 2>&1; then")
                .append(" log -t GlyphKA resurrect; am startservice -n ")
                .append(PKG).append("/.KeepAliveService >/dev/null 2>&1; fi;")
                // 充电检测：battery/status 为主（Discharging/Charging/Full），usb/online 为辅
                .append(" st=0;")
                .append(" s=$(cat /sys/class/power_supply/battery/status 2>/dev/null);")
                .append(" if [ \"$s\" = \"Charging\" ] || [ \"$s\" = \"Full\" ]; then st=1; fi;")
                .append(" if [ \"$st\" = \"0\" ]; then u=$(cat /sys/class/power_supply/usb/online 2>/dev/null);")
                .append(" if [ \"$u\" = \"1\" ]; then st=1; fi; fi;")
                .append(" if [ \"$st\" = \"1\" ] && [ \"$prev\" = \"0\" ]; then")
                .append(" log -t GlyphKA charger-edge; am startservice -n ")
                .append(PKG).append("/.ChargingSweepService >/dev/null 2>&1; fi;")
                .append(" prev=$st; sleep 1; done' ")
                .append(">/dev/null 2>&1 </dev/null &\n");
        try (FileOutputStream fos = context.openFileOutput(TMP_NAME, Context.MODE_PRIVATE)) {
            fos.write(sb.toString().getBytes());
        }
    }
}
