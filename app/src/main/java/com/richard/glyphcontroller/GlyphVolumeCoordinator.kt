package com.richard.glyphcontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * 监听音量变化并在 Nothing Phone (2) 的 C1 环形灯带 (16段: 索引 3..18)
 * 上以即时响应 + 平滑淡出动效呈现音量变化。
 *
 * 核心优化：
 * 1. 即时生效（0ms 延迟）：调节音量时取消阶梯插值与渐显延迟，瞬时点亮目标段位。
 * 2. 按键抢先响应：通过 root 动态发现音量键输入节点（解析 /proc/bus/input/devices）并监听，在系统音量广播到达前预估新音量并点亮。
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
    private const val INPUT_RETRY_MIN_MS = 2_000L
    private const val INPUT_RETRY_MAX_MS = 30_000L
    private const val INPUT_STABLE_MS = 10_000L
    private const val READER_PID_TIMEOUT_MS = 2_000L

    // C1 灯环段位定义：索引 3..18，共 16 段
    private const val C1_START_INDEX = 3
    private const val C1_SEGMENTS_COUNT = 16

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var animationJob: Job? = null
    private var inputMonitorJob: Job? = null

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
        if (!app.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("volume_enabled", true)
        ) {
            stop(app)
            return
        }
        synchronized(this) {
            if (!registered) {
                try {
                    val filter = IntentFilter(VOLUME_CHANGED_ACTION)
                    ContextCompat.registerReceiver(
                        app,
                        volumeReceiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED
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

    fun stop(context: Context) {
        val app = context.applicationContext
        val monitor: Job?
        val animation: Job?
        synchronized(this) {
            if (registered) {
                try {
                    app.unregisterReceiver(volumeReceiver)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Volume receiver was already unregistered", e)
                }
                registered = false
            }
            monitor = inputMonitorJob
            inputMonitorJob = null
            animation = animationJob
            animationJob = null
            isVolumeUpHeldAtMax = false
            lastKnownVolumes.clear()
        }
        monitor?.cancel()
        animation?.cancel()
    }

    private fun captureCurrentVolumes(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        supportedStreams.forEach { stream ->
            lastKnownVolumes[stream] = audioManager.getStreamVolume(stream)
        }
    }

    private fun startInputMonitors(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            if (inputMonitorJob?.isActive == true) return
            inputMonitorJob = scope.launch(Dispatchers.IO) {
                var backoffMs = INPUT_RETRY_MIN_MS
                while (isActive) {
                    val startedAt = System.currentTimeMillis()
                    val nodes = discoverVolumeKeyNodes()
                    if (nodes.isEmpty()) {
                        if (RootCommand.areRootAttemptsSuppressed()) {
                            Log.d(TAG, "Input monitor paused until the app is opened again")
                            break
                        }
                        // 没有 root 或暂未找到音量键节点：退避重试，避免频繁拉起 su
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(INPUT_RETRY_MAX_MS)
                        continue
                    }
                    Log.d(TAG, "Volume key input nodes: $nodes")
                    coroutineScope {
                        val readerEnded = CompletableDeferred<Unit>()
                        val readers = nodes.map { path ->
                            launch {
                                try {
                                    readInputNode(app, path)
                                } finally {
                                    readerEnded.complete(Unit)
                                }
                            }
                        }
                        readerEnded.await()
                        readers.forEach { it.cancel() }
                        readers.forEach { it.join() }
                    }
                    // 任一读取器结束就重建整组，避免另一个阻塞读取让失效节点永远得不到恢复。
                    if (System.currentTimeMillis() - startedAt < INPUT_STABLE_MS) {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(INPUT_RETRY_MAX_MS)
                    } else {
                        backoffMs = INPUT_RETRY_MIN_MS
                        delay(INPUT_RETRY_MIN_MS)
                    }
                }
            }
        }
    }

    /** 通过 root 解析 /proc/bus/input/devices，找出声明了音量加/减键能力的输入节点。 */
    private fun discoverVolumeKeyNodes(): List<String> {
        val result = RootCommand.run(
            "cat /proc/bus/input/devices",
            5,
            suppressOnFailure = true,
        )
        if (!result.success) return emptyList()
        return parseVolumeKeyNodes(result.output)
    }

    internal fun parseVolumeKeyNodes(
        devices: String,
        wordBits: Int = if (android.os.Process.is64Bit()) 64 else 32,
    ): List<String> {
        val nodes = linkedSetOf<String>()
        devices.split(Regex("\\r?\\n\\s*\\r?\\n")).forEach { block ->
            var handlers: String? = null
            var keyWords: List<String>? = null
            block.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("H: Handlers=") -> handlers = line.removePrefix("H: Handlers=")
                    line.startsWith("B: KEY=") ->
                        keyWords = line.removePrefix("B: KEY=").trim().split(Regex("\\s+"))
                }
            }
            val words = keyWords ?: return@forEach
            val event = handlers
                ?.split(Regex("\\s+"))
                ?.firstOrNull { Regex("event\\d+").matches(it) }
                ?: return@forEach
            if (hasKeyBit(words, KEY_VOLUME_DOWN, wordBits) || hasKeyBit(words, KEY_VOLUME_UP, wordBits)) {
                nodes.add("/dev/input/$event")
            }
        }
        return nodes.toList()
    }

    private fun hasKeyBit(words: List<String>, bit: Int, wordBits: Int): Boolean {
        val index = bit / wordBits
        if (index >= words.size) return false
        val word = words[words.size - 1 - index].toULongOrNull(16) ?: return false
        return (word shr (bit % wordBits)) and 1uL == 1uL
    }

    /** 以 root 身份 cat 事件节点并解析 input_event，无需放宽 /dev/input 的全局权限。 */
    private suspend fun readInputNode(context: Context, path: String) = coroutineScope {
        if (!Regex("/dev/input/event\\d+").matches(path)) return@coroutineScope
        val command = "reader_pid=\$\$; echo \"\$reader_pid\"; " +
            "(read watcher_signal || :; kill -9 \"\$reader_pid\" 2>/dev/null) " +
            ">/dev/null 2>&1 & exec cat $path 2>/dev/null"
        val process = try {
            // The background read watches the app-owned stdin pipe. Android closes that pipe
            // when the app dies, so the root reader cannot remain orphaned after force-stop.
            ProcessBuilder("su", "-c", command).start()
        } catch (e: IOException) {
            RootCommand.suppressRootAttempts()
            Log.w(TAG, "Root denied input reader for $path: ${e.message}")
            return@coroutineScope
        } catch (e: Exception) {
            Log.w(TAG, "Cannot start input reader for $path: ${e.message}")
            return@coroutineScope
        }
        val input = process.inputStream
        val readerPid = try {
            readReaderPid(input, process)
        } catch (e: CancellationException) {
            signalReaderShutdown(process)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot read root input reader PID for $path: ${e.message}")
            null
        }
        if (readerPid == null) {
            val exited = runCatching { process.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS) }
                .getOrDefault(false)
            val exitCode = if (exited) process.exitValue() else null
            if (exitCode in setOf(1, 126, 127)) RootCommand.suppressRootAttempts()
            signalReaderShutdown(process)
            Log.w(TAG, "Root input reader did not provide a PID for $path (exit=$exitCode)")
            return@coroutineScope
        }
        // 阻塞读不响应协程取消，取消时由看门狗销毁进程来打断读取
        val watchdog = launch {
            try {
                awaitCancellation()
            } finally {
                signalReaderShutdown(process)
            }
        }
        var receivedInput = false
        try {
            // struct input_event: struct timeval (2 个 long) + u16 type + u16 code + s32 value
            val header = if (android.os.Process.is64Bit()) 16 else 8
            val size = header + 8
            val buffer = ByteArray(size)
            Log.d(TAG, "Input reader started for $path")
            input.use {
                while (isActive) {
                    var offset = 0
                    while (offset < size) {
                        val read = input.read(buffer, offset, size - offset)
                        if (read < 0) return@use
                        offset += read
                    }
                    receivedInput = true
                    val type = (buffer[header].toInt() and 0xFF) or
                        ((buffer[header + 1].toInt() and 0xFF) shl 8)
                    val code = (buffer[header + 2].toInt() and 0xFF) or
                        ((buffer[header + 3].toInt() and 0xFF) shl 8)
                    val value = (buffer[header + 4].toInt() and 0xFF) or
                        ((buffer[header + 5].toInt() and 0xFF) shl 8) or
                        ((buffer[header + 6].toInt() and 0xFF) shl 16) or
                        ((buffer[header + 7].toInt() and 0xFF) shl 24)

                    // 1 = EV_KEY, 114/115 = KEY_VOLUMEDOWN/KEY_VOLUMEUP
                    if (type == 1 && (code == KEY_VOLUME_DOWN || code == KEY_VOLUME_UP)) {
                        handleVolumeKeyEvent(context, code, value)
                    }
                }
            }
        } catch (e: Exception) {
            if (isActive) Log.w(TAG, "Input reader $path failed: ${e.message}")
        } finally {
            watchdog.cancel()
            val exited = runCatching { process.waitFor(250, java.util.concurrent.TimeUnit.MILLISECONDS) }
                .getOrDefault(false)
            val exitCode = if (exited) process.exitValue() else null
            if (isActive && !receivedInput && exitCode in setOf(1, 126, 127)) {
                RootCommand.suppressRootAttempts()
                Log.w(TAG, "Root input reader rejected for $path (exit=$exitCode)")
            }
            terminateRootReader(process, readerPid)
        }
    }

    private fun terminateRootReader(process: Process, pid: Long) {
        runCatching { process.outputStream.close() }
        if (process.isAlive && !RootCommand.areRootAttemptsSuppressed()) {
            RootCommand.run("kill -9 $pid", 2)
        }
        process.destroyForcibly()
    }

    private fun signalReaderShutdown(process: Process) {
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
    }

    private suspend fun readReaderPid(input: InputStream, process: Process): Long? =
        withTimeoutOrNull<Long?>(READER_PID_TIMEOUT_MS) {
            val digits = StringBuilder(12)
            while (true) {
                var progressed = false
                while (input.available() > 0) {
                    progressed = true
                    val next = input.read()
                    if (next < 0) return@withTimeoutOrNull null
                    if (next == '\n'.code) return@withTimeoutOrNull digits.toString().toLongOrNull()
                    if (next != '\r'.code && next !in '0'.code..'9'.code) {
                        return@withTimeoutOrNull null
                    }
                    if (next != '\r'.code) {
                        if (digits.length >= 12) return@withTimeoutOrNull null
                        digits.append(next.toChar())
                    }
                }
                if (!process.isAlive && input.available() == 0) return@withTimeoutOrNull null
                if (!progressed) delay(10L)
            }
            @Suppress("UNREACHABLE_CODE")
            null
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Volume animation loop failed", e)
        } finally {
            GlyphEffectCoordinator.finishEffect(context, lease)
        }
    }
}
