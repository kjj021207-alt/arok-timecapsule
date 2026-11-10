package com.arok.timecapsule.car.pleos

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import com.arok.timecapsule.BuildConfig
import com.arok.timecapsule.car.SpeechResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.concurrent.thread

/**
 * 음성 메모 받아쓰기 (Pleos SpeechToText).
 *
 * - Pleos Playground 프로젝트 키(local.properties의 pleos.clientId / pleos.clientSecret)가 있으면
 *   HYBRID 모드로 앱을 등록하고 SDK가 마이크를 직접 연다 (request()).
 * - 키가 없으면 ON_DEVICE 모드로 동작한다. 이 모드는 앱이 녹음한 PCM(16kHz, 16bit, mono)을
 *   sendAudio()로 넘겨야 해서 AudioRecord로 직접 녹음한다.
 */
class PleosSpeech(private val context: Context) {

    private val _state = MutableStateFlow<SpeechResult>(SpeechResult.Idle)
    val state: StateFlow<SpeechResult> = _state

    private val hasKeys = BuildConfig.PLEOS_CLIENT_ID.isNotBlank() && BuildConfig.PLEOS_CLIENT_SECRET.isNotBlank()
    private val stt = SpeechToText(context, if (hasKeys) Mode.HYBRID else Mode.ON_DEVICE)
    private var ready = false
    private var initStarted = false
    @Volatile private var recording = false
    private var recorder: AudioRecord? = null

    private val results = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() { _state.value = SpeechResult.Listening }
        override fun onUpdated(stt: String, completed: Boolean) {
            _state.value = if (completed) SpeechResult.Final(stt) else SpeechResult.Partial(stt)
            if (completed) stopRecorder()
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() {
            stopRecorder()
            if (_state.value is SpeechResult.Listening) _state.value = SpeechResult.Idle
        }
        override fun onError(errorType: ErrorType) {
            stopRecorder()
            _state.value = SpeechResult.Error(
                when (errorType) {
                    ErrorType.PERMISSION_DENIED, ErrorType.MIC_ERROR -> "마이크를 쓸 수 없어요"
                    ErrorType.NETWORK_ERROR -> "인터넷 연결을 확인해 주세요"
                    ErrorType.UNAUTHENTICATED -> "Pleos 앱 등록이 필요해요"
                    else -> "음성을 인식하지 못했어요"
                }
            )
        }
    }

    fun init() {
        if (initStarted) return
        initStarted = true
        try {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    stt.setLocale(Locale.KOREA)
                    stt.addListener(results)
                    if (hasKeys) {
                        stt.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET, object : OnServerConnectionListener {
                            override fun onConnected() { ready = true }
                            override fun onFailed(errorType: ErrorType) { Log.w(TAG, "stt register: $errorType") }
                        })
                    } else {
                        ready = true
                    }
                }
                override fun onFailure(errorType: ErrorType) { Log.w(TAG, "stt init: $errorType") }
            })
        } catch (e: Exception) {
            Log.w(TAG, "stt init failed", e)
        }
    }

    fun start() {
        if (!ready) { _state.value = SpeechResult.Error("음성 인식을 준비 중이에요"); return }
        _state.value = SpeechResult.Listening
        try {
            if (hasKeys) stt.request() else startRecorder()
        } catch (e: Exception) {
            Log.w(TAG, "stt start failed", e)
            _state.value = SpeechResult.Error("음성 인식을 시작하지 못했어요")
        }
    }

    fun stop() {
        try {
            if (hasKeys) stt.stop() else stopRecorder()
        } catch (e: Exception) {
            Log.w(TAG, "stt stop failed", e)
        }
    }

    fun release() {
        stopRecorder()
        try { stt.removeListener(results); stt.release() } catch (_: Exception) {}
        ready = false; initStarted = false
    }

    // ON_DEVICE 모드: 16kHz / 16bit / mono PCM을 직접 녹음해 SDK로 넘긴다
    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        val rate = 16_000
        val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(3200)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        recorder = rec
        recording = true
        rec.startRecording()
        thread(name = "stt-pcm") {
            val buf = ByteArray(size)
            while (recording) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) stt.sendAudio(buf.copyOf(n))
            }
            runCatching { stt.completeAudioSend() }
        }
    }

    private fun stopRecorder() {
        if (!recording) return
        recording = false
        recorder?.let { runCatching { it.stop(); it.release() } }
        recorder = null
    }

    companion object { private const val TAG = "PleosSpeech" }
}
