package com.arok.timecapsule.car

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 안드로이드 기본 음성 기능 (Pleos가 없는 폰·태블릿용).
 * - 읽어주기: android.speech.tts.TextToSpeech
 * - 받아쓰기: android.speech.SpeechRecognizer (기기에 음성 인식 서비스가 있을 때)
 * Pleos 차량에서는 Pleos TTS가 준비되지 않았을 때 읽어주기 대체용으로만 쓴다.
 */
class AndroidVoice(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pending = mutableListOf<String>()

    private val _speech = MutableStateFlow<SpeechResult>(SpeechResult.Idle)
    val speech: StateFlow<SpeechResult> = _speech
    private var recognizer: SpeechRecognizer? = null

    val canListen: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun speak(text: String) {
        if (tts == null) {
            // 처음 부를 때 엔진을 만들고, 준비되기 전 요청은 모아 두었다가 읽는다
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.KOREAN
                    pending.forEach { say(it) }
                } else {
                    Log.w(TAG, "android tts init failed: $status")
                }
                pending.clear()
            }
        }
        if (ttsReady) say(text) else pending += text
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "capsule-${text.hashCode()}")
    }

    fun stopSpeaking() { tts?.stop() }

    fun startListening() {
        if (!canListen) { _speech.value = SpeechResult.Error("이 기기에서는 음성 인식을 쓸 수 없어요"); return }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also { recognizer = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { _speech.value = SpeechResult.Listening }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) {
                _speech.value = when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechResult.Error("마이크 권한을 허용해 주세요")
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechResult.Error("말소리를 듣지 못했어요")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechResult.Error("인터넷 연결을 확인해 주세요")
                    else -> SpeechResult.Error("음성을 인식하지 못했어요")
                }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                _speech.value = if (text.isNullOrBlank()) SpeechResult.Idle else SpeechResult.Final(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }?.let { _speech.value = SpeechResult.Partial(it) }
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        )
        _speech.value = SpeechResult.Listening
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun release() {
        tts?.shutdown(); tts = null; ttsReady = false
        recognizer?.destroy(); recognizer = null
    }

    companion object { private const val TAG = "AndroidVoice" }
}
