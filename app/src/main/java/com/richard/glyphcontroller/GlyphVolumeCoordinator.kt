package com.richard.glyphcontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 监听音量变化并在 Nothing Phone (2) 的 C1 环形灯带 (16段: 索引 3..18)
 * 上以即时响应 + 平滑淡出动效呈现音量变化。
 *
 * 核心优化：
 * 1. 即时生效（0ms 延迟）：调节音量时取消阶梯插值与渐显延迟，瞬时点亮目标段位。
 * 2. 按键抢先响应：监听硬件 /dev/input/event2 与 event3，在系统音量广播到达前预估新音量并点亮。
 * 3. 满音量长按保持：当音量已达到最大值时，
 *    按住音量增加键保持 C1 灯环 16 段全部常亮，松开后 1.2 秒自然平滑淡出。
 * 4. 动态平滑淡出：停止按键后驻留 1.2 秒，随后连续非线性平滑淡出并恢复原态。
 */
object GlyphVolumeCoordinator {
    private const val TAG = "GlyphVolume"
    private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
    private const val EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
    private const val KEY_VOLUME_DOWN = 114
    private const val KEY_VOLUME_UP = 115

    // C1 灯环段位定义：索引 3..18，共 16 段
    private const val C1_START_INDEX = 3
    private const val C1_SEGMENTS_COUNT = 16

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var animationJob: Job? = null
    private val inputMonitorJobs = mutableMapOf<String, Job>()

    @Volatile private var targetSegments = 0f
    @Volatile private var currentSegments = 0f
    @Volatile private var alpha = 1.0f
    @Volatile private var deadline = 0L
    @Volatile private var isVolumeUpHeldAtMax = false
    @Volatile private var lastActiveStream = AudioManager.STREAM_MUSIC
    private var registered = false
    private val lastKnownVolumes = ConcurrentHashMap<Int, Int>()

    private val supportedStreams = intArrayOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
        AudioManager.STREAM_VOICE_CALL
    )

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == VOLUME_CHANGED_ACTION) {
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, AudioManager.STREAM_MUSIC)
                val currentVal = intent.getIntExtra(EXTRA_VOLUME_STREAM_VALUE, -1)
                onVolumeChanged(context.applicationContext, streamType, currentVal)
            }
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (!registered) {
                try {
                    val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                    ContextCompat.registerReceiver(
                        app,
                        volumeReceiver,
                        filter,
                        ContextCompat.RECEIVER_EXPORTED
                    )
                    registered = true
                    Log.d(TAG, "Volume receiver registered")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register volume receiver", e)
                }
            }
        }
        captureCurrentVolumes(app)
        startInputMonitors(app)
    }

    private fun captureCurrentVolumes(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        supportedStreams.forEach { stream ->
            lastKnownVolumes[stream] = audioManager.getStreamVolume(stream)
        }
    }

    private fun startInputMonitors(context: Context) {
        synchronized(this) {
            listOf("/dev/input/event2", "/dev/input/event3").forEach { path ->
                if (inputMonitorJobs[path]?.isActive == true) return@forEach
                inputMonitorJobs[path] = scope.launch(Dispatchers.IO) {
                    val app = context.applicationContext
                    val eventFile = File(path)

                    Log.d(TAG, "Input monitor started, waiting for $path")
                    while (isActive) {
                        if (!eventFile.exists() || !eventFile.canRead()) {
                            delay(1000L)
                            continue
                        }
                        try {
                            FileInputStream(eventFile).use { fis ->
                                Log.d(TAG, "Successfully opened $path for hardware key monitoring")
                                val buffer = ByteArray(24)
                                while (isActive) {
                                    var offset = 0
                                    while (offset < 24) {
                                        val read = fis.read(buffer, offset, 24 - offset)
                                        if (read < 0) break
                                        offset += read
                                    }
                                    if (offset < 24) break

                                    val type = (buffer[16].toInt() and 0xFF) or
                                        ((buffer[17].toInt() and 0xFF) shl 8)
                                    val code = (buffer[18].toInt() and 0xFF) or
                                        ((buffer[19].toInt() and 0xFF) shl 8)
                                    val value = (buffer[20].toInt() and 0xFF) or
                                        ((buffer[21].toInt() and 0xFF) shl 8) or
                                        ((buffer[22].toInt() and 0xFF) shl 16) or
                                        ((buffer[23].toInt() and 0xFF) shl 24)

                                    // 1 = EV_KEY, 114/115 = KEY_VOLUMEDOWN/KEY_VOLUMEUP
                                    if (type == 1 && (code == KEY_VOLUME_DOWN || code == KEY_VOLUME_UP)) {
                                        handleVolumeKeyEvent(app, code, value)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Input reader $path failed, retrying: ${e.message}")
                            delay(1000L)
                        }
                    }
                }
            }
        }
    }

    private fun getActiveStream(audioManager: AudioManager): Int {
        return when {
            audioManager.mode == AudioManager.MODE_IN_CALL || audioManager.mode == AudioManager.MODE_IN_COMMUNICATION -> AudioManager.STREAM_VOICE_CALL
            audioManager.isMusicActive -> AudioManager.STREAM_MUSIC
            lastActiveStream in supportedStreams -> lastActiveStream
            else -> AudioManager.STREAM_MUSIC
        }
    }

    private fun handleVolumeKeyEvent(context: Context, keyCode: Int, value: Int) {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!settings.getBoolean("volume_enabled", true)) return

        if (value == 0) {
            if (keyCode == KEY_VOLUME_UP && isVolumeUpHeldAtMax) {
                isVolumeUpHeldAtMax = false
                deadline = System.currentTimeMillis() + 1200L
            }
            return
        }
        if (value != 1 && value != 2) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val stream = getActiveStream(audioManager)
        val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val minVolume = getMinVolume(audioManager, stream)
        val current = audioManager.getStreamVolume(stream)
        val known = lastKnownVolumes[stream]
        val systemAlreadyAdjusted = known != null && current != known
        val direction = if (keyCode == KEY_VOLUME_UP) 1 else -1
        val predicted = if (systemAlreadyAdjusted) {
            lastKnownVolumes[stream] = current
            current
        } else {
            (current + direction).coerceIn(minVolume, maxVolume)
        }

        lastActiveStream = stream
        isVolumeUpHeldAtMax = keyCode == KEY_VOLUME_UP && predicted >= maxVolume
        Log.d(
            TAG,
            "hardware key: code=$keyCode value=$value current=$current predicted=$predicted/$maxVolume"
        )
        showVolume(context, stream, predicted, minVolume, maxVolume)
    }

    fun onVolumeChanged(context: Context, streamType: Int, currentVal: Int? = null) {
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!settings.getBoolean("volume_enabled", true)) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val stream = if (streamType in supportedStreams) streamType else AudioManager.STREAM_MUSIC
        lastActiveStream = stream
        val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val minVolume = getMinVolume(audioManager, stream)
        val current = if (currentVal != null && currentVal >= 0) currentVal else audioManager.getStreamVolume(stream)
        lastKnownVolumes[stream] = current
        showVolume(context, stream, current, minVolume, maxVolume)
    }

    private fun getMinVolume(audioManager: AudioManager, stream: Int): Int = try {
        audioManager.getStreamMinVolume(stream)
    } catch (_: Throwable) {
        0
    }

    private fun showVolume(context: Context, stream: Int, current: Int, minVolume: Int, maxVolume: Int) {
        val range = (maxVolume - minVolume).coerceAtLeast(1)
        val normalized = (current - minVolume).coerceIn(0, range)
        val ratio = normalized.toFloat() / range.toFloat()

        var litSegments = (ratio * C1_SEGMENTS_COUNT).roundToInt().coerceIn(0, C1_SEGMENTS_COUNT)
        if (normalized > 0 && litSegments == 0) {
            litSegments = 1 // 只要音量大于0，至少点亮1段
        }

        Log.d(TAG, "onVolumeChanged: stream=$stream current=$current/$maxVolume litSegments=$litSegments")
        targetSegments = litSegments.toFloat()
        currentSegments = targetSegments
        alpha = 1.0f
        deadline = System.currentTimeMillis() + 1200L
        if (normalized < range) {
            isVolumeUpHeldAtMax = false
        }

        triggerAnimation(context)
    }

    private fun triggerAnimation(context: Context) {
        synchronized(this) {
            if (animationJob?.isActive != true) {
                animationJob = scope.launch {
                    runAnimationLoop(context)
                }
            }
        }
    }

    private suspend fun runAnimationLoop(context: Context) {
        val lease = GlyphEffectCoordinator.beginEffect(context, GlyphSession.Effect.VOLUME)
        if (lease == null) {
            Log.d(TAG, "Cannot begin VOLUME effect, hardware busy")
            return
        }

        try {
            val frame = IntArray(GlyphFrame.ZONE_COUNT)

            while (scope.isActive) {
                val now = System.currentTimeMillis()
                val holding = isVolumeUpHeldAtMax
                val targetAlpha = if (now < deadline || holding) 1.0f else 0.0f

                // 平滑过渡透明度（仅在超时后平滑淡出）
                if (targetAlpha >= 1.0f) {
                    alpha = 1.0f // 即时生效，全亮不延迟
                } else {
                    alpha = (alpha - 0.08f).coerceAtLeast(0.0f)
                }

                // 淡出完成且无按键按住，结束动画并恢复
                if (alpha <= 0.01f && now >= deadline && !holding) {
                    break
                }

                // 即时生效：段位直接跟随 targetSegments
                currentSegments = targetSegments

                // 组装 33 驱动区帧数据 (C1: 3..18)
                java.util.Arrays.fill(frame, 0)
                val fullCount = currentSegments.toInt().coerceIn(0, C1_SEGMENTS_COUNT)
                val remainder = (currentSegments - fullCount).coerceIn(0f, 1f)

                // 已完整覆盖的段
                val baseBrightness = (255 * alpha).toInt().coerceIn(0, 255)
                for (i in 0 until fullCount) {
                    frame[C1_START_INDEX + i] = baseBrightness
                }

                // 头部正在充盈/消退的段以比例亮度柔和发光 (亚段抗锯齿)
                if (fullCount < C1_SEGMENTS_COUNT && remainder > 0.02f) {
                    frame[C1_START_INDEX + fullCount] = (255 * remainder * alpha).toInt().coerceIn(0, 255)
                }

                // 50 FPS 纯异步管道冲刷
                if (!GlyphEffectCoordinator.renderFrame(lease, frame, sync = false)) {
                    break
                }

                delay(20L)
            }
        } catch (_: Exception) {
        } finally {
            GlyphEffectCoordinator.finishEffect(context, lease)
        }
    }
}
