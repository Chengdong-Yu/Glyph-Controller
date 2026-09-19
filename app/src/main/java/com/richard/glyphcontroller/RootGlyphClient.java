package com.richard.glyphcontroller;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Root-based Glyph controller that directly writes to AW20036 sysfs nodes.
 *
 * Hardware: AW20036 LED driver on I2C bus (address 0x3a)
 * Matrix: 3x12 = 36 LEDs, 33 controllable zones on Nothing Phone (2)
 *
 * Sysfs paths:
 *   /sys/class/leds/led_strips/operating_mode  (0=off, 1=active, 2=standby)
 *   /sys/class/leds/led_strips/single_brightness  (format: "LED_ID BRIGHTNESS")
 *   /sys/class/leds/led_strips/frame_brightness   (space-separated brightness values)
 *   /sys/class/leds/led_strips/all_brightness      (single value for all LEDs)
 *
 * frame_brightness 33-LED mapping (input index -> AW20036 register):
 *   {12, 0, 24, 2, 3, 4, 5, 6, 7, 8, 14, 15, 16, 17, 18, 19, 20, 26, 27, 28, 29, 30, 31, 32, 9, 21, 33, 10, 22, 34, 11, 23, 35}
 */
public class RootGlyphClient {
    private static final String TAG = "RootGlyphClient";

    private static final String SYSFS_BASE = "/sys/class/leds/led_strips";
    private static final String SYSFS_OPERATING_MODE = SYSFS_BASE + "/operating_mode";
    private static final String SYSFS_FRAME_BRIGHTNESS = SYSFS_BASE + "/frame_brightness";
    private static final String SYSFS_SINGLE_BRIGHTNESS = SYSFS_BASE + "/single_brightness";
    private static final String SYSFS_ALL_BRIGHTNESS = SYSFS_BASE + "/all_brightness";

    // Nothing Phone (2) has 33 addressable glyph zones
    private static final int ZONE_COUNT = 33;

    private boolean mActive = false;

    // Root shell
    private Process mSuProcess;
    private OutputStream mSuInput;
    private boolean mSuReady = false;

    private synchronized boolean ensureRoot() {
        if (mSuReady) return true;
        try {
            mSuProcess = Runtime.getRuntime().exec("su");
            mSuInput = mSuProcess.getOutputStream();
            // Test root
            exec("id");
            mSuReady = true;
            Log.d(TAG, "Root access obtained");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get root: " + e.getMessage());
            return false;
        }
    }

    private synchronized String exec(String cmd) {
        if (!mSuReady) return null;
        try {
            // Write command and capture output
            String taggedCmd = cmd + " 2>&1 && echo __ROOT_OK__ || echo __ROOT_FAIL__";
            mSuInput.write((taggedCmd + "\n").getBytes());
            mSuInput.flush();

            // Read output
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(mSuProcess.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            long start = System.currentTimeMillis();
            while ((line = reader.readLine()) != null) {
                if (line.contains("__ROOT_OK__")) break;
                if (line.contains("__ROOT_FAIL__")) break;
                if (System.currentTimeMillis() - start > 5000) break;
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            Log.e(TAG, "exec failed: " + e.getMessage());
            return null;
        }
    }

    private synchronized void execVoid(String cmd) {
        if (!mSuReady) return;
        try {
            mSuInput.write((cmd + "\n").getBytes());
            mSuInput.flush();
        } catch (Exception e) {
            Log.e(TAG, "execVoid failed: " + e.getMessage());
        }
    }

    public boolean isRootAvailable() {
        return ensureRoot();
    }

    /**
     * Activate the AW20036 chip (operating_mode = 1)
     */
    public synchronized void activate() {
        if (!ensureRoot()) {
            Log.e(TAG, "No root access");
            return;
        }
        execVoid("echo 1 > " + SYSFS_OPERATING_MODE);
        mActive = true;
        Log.d(TAG, "AW20036 activated (mode=1)");
    }

    /**
     * Put AW20036 into standby (operating_mode = 2)
     */
    public synchronized void standby() {
        if (!ensureRoot()) return;
        execVoid("echo 2 > " + SYSFS_OPERATING_MODE);
        mActive = false;
        Log.d(TAG, "AW20036 standby (mode=2)");
    }

    /**
     * Set brightness for all 33 glyph zones.
     * values[i] = 0-255 brightness for zone i
     *
     * Zone mapping (Nothing Phone 2, 33 zones):
     *   Zone 0: A1 (top right)
     *   Zone 1: A2 (top left)
     *   Zone 2: B1 (center top)
     *   Zone 3-18: C1 (outer ring, 16 zones)
     *   Zone 19-23: C2-C6 (inner elements)
     *   Zone 24: E1 (center)
     *   Zone 25-32: D1 (bottom vertical bar, 8 zones)
     */
    public synchronized void setZoneColors(int[] values) {
        if (!ensureRoot()) {
            Log.e(TAG, "No root access");
            return;
        }
        if (values == null || values.length == 0) return;

        // Ensure chip is active
        if (!mActive) activate();

        // Build frame_brightness string
        // The kernel driver parses space-separated integers
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(values.length, ZONE_COUNT); i++) {
            if (i > 0) sb.append(" ");
            sb.append(Math.max(0, Math.min(255, values[i])));
        }

        execVoid("echo '" + sb.toString() + "' > " + SYSFS_FRAME_BRIGHTNESS);
        Log.d(TAG, "setZoneColors: " + values.length + " zones");
    }

    /**
     * Set brightness for a single LED by AW20036 register ID (0-35)
     */
    public synchronized void setSingleLed(int ledId, int brightness) {
        if (!ensureRoot()) return;
        if (!mActive) activate();

        ledId = Math.max(0, Math.min(35, ledId));
        brightness = Math.max(0, Math.min(255, brightness));

        execVoid("echo '" + ledId + " " + brightness + "' > " + SYSFS_SINGLE_BRIGHTNESS);
    }

    /**
     * Set all LEDs to the same brightness
     */
    public synchronized void setAllBrightness(int brightness) {
        if (!ensureRoot()) return;
        brightness = Math.max(0, Math.min(255, brightness));
        execVoid("echo " + brightness + " > " + SYSFS_ALL_BRIGHTNESS);
    }

    /**
     * Turn on all 33 glyph zones at max brightness
     */
    public synchronized void turnOnAll() {
        activate();
        int[] allOn = new int[ZONE_COUNT];
        for (int i = 0; i < ZONE_COUNT; i++) {
            allOn[i] = 255;
        }
        setZoneColors(allOn);
        Log.d(TAG, "All Glyph ON (33 zones)");
    }

    /**
     * Turn off all glyphs
     */
    public synchronized void turnOff() {
        int[] allOff = new int[ZONE_COUNT];
        setZoneColors(allOff);
        standby();
        Log.d(TAG, "All Glyph OFF");
    }

    /**
     * Set a specific zone pattern (for testing)
     * Uses the kernel's 5-zone mode (frame_num == 5)
     * Zones: 0=ring top, 1=center, 2=ring main, 3=vertical bars, 4=bottom
     */
    public synchronized void setZonePattern5(int[] values) {
        if (!ensureRoot()) return;
        if (!mActive) activate();

        if (values == null || values.length < 5) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) sb.append(" ");
            sb.append(Math.max(0, Math.min(255, values[i])));
        }

        execVoid("echo '" + sb.toString() + "' > " + SYSFS_FRAME_BRIGHTNESS);
        Log.d(TAG, "setZonePattern5: " + sb.toString());
    }

    /**
     * Execute a raw shell command as root (for advanced usage)
     */
    public String shell(String cmd) {
        if (!ensureRoot()) return null;
        return exec(cmd);
    }

    /**
     * Cleanup: release root shell
     */
    public synchronized void destroy() {
        try {
            if (mSuInput != null) {
                mSuInput.write("exit\n".getBytes());
                mSuInput.flush();
                mSuInput.close();
            }
            if (mSuProcess != null) {
                mSuProcess.waitFor();
            }
        } catch (Exception e) {
            // ignore
        }
        mSuReady = false;
        Log.d(TAG, "Root shell released");
    }
}
