package com.arok.timecapsule.car

import android.content.Context
import android.util.Log
import com.arok.timecapsule.domain.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Pleos 서비스가 없는 기기에서 쓰는 데모 신호.
 * 화면 하단의 데모 패널에서 시나리오를 바꾸면 차가 실제로 그렇게 움직인 것처럼 신호를 낸다.
 * 읽어주기와 음성 메모는 기기의 안드로이드 기본 음성 기능으로 실제로 동작한다.
 */
class DemoCarSignals(context: Context) : CarSignals {

    private val voice = AndroidVoice(context)

    enum class Scenario(val label: String) {
        HOME_PARKED("수원 집 앞 주차"),
        DRIVING_TO_GANGNEUNG("강릉으로 주행 중"),
        ARRIVED_GANGNEUNG("강릉 경포대 도착"),
        ARRIVED_GYEONGJU("경주 대릉원 도착"),
    }

    override val isConnectedToCar = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _drive = MutableStateFlow(DriveState.PARKED)
    private val _location = MutableStateFlow<LatLng?>(SUWON)
    private val _passengers = MutableStateFlow<Int?>(1)
    private val _trip = MutableStateFlow<TripInfo?>(null)
    private val _rainy = MutableStateFlow(false)
    private val _destination = MutableStateFlow<Destination?>(null)
    private val _speech = MutableStateFlow<SpeechResult>(SpeechResult.Idle)
    private val _scenario = MutableStateFlow(Scenario.HOME_PARKED)

    private val _arrivals = MutableSharedFlow<Destination>(extraBufferCapacity = 4)

    override val driveState: StateFlow<DriveState> = _drive
    override val arrivals: SharedFlow<Destination> = _arrivals
    override val location: StateFlow<LatLng?> = _location
    override val passengerCount: StateFlow<Int?> = _passengers
    override val trip: StateFlow<TripInfo?> = _trip
    override val rainy: StateFlow<Boolean> = _rainy
    override val destination: StateFlow<Destination?> = _destination
    /** 기기에 음성 인식이 있으면 실제 받아쓰기, 없으면 예시 문장으로 흉내 */
    override val speech: StateFlow<SpeechResult> = if (voice.canListen) voice.speech else _speech
    val scenario: StateFlow<Scenario> = _scenario

    override fun start() = Unit
    override fun stop() = voice.release()

    fun apply(s: Scenario) {
        _scenario.value = s
        when (s) {
            Scenario.HOME_PARKED -> set(DriveState.PARKED, SUWON, 1, null, null, false)
            Scenario.DRIVING_TO_GANGNEUNG -> set(DriveState.DRIVING, GANGNEUNG_ENTRY, 3, TripInfo(205.0, 162), Destination("강릉 경포대", GYEONGPO), false)
            Scenario.ARRIVED_GANGNEUNG -> set(DriveState.PARKED, GYEONGPO, 3, TripInfo(238.4, 231), Destination("강릉 경포대", GYEONGPO), false)
            Scenario.ARRIVED_GYEONGJU -> set(DriveState.PARKED, DAERUNGWON, 2, TripInfo(301.2, 215), Destination("경주 대릉원", DAERUNGWON), true)
        }
        // 내비 목적지 도착 이벤트 흉내
        if (s == Scenario.ARRIVED_GANGNEUNG || s == Scenario.ARRIVED_GYEONGJU) _destination.value?.let { _arrivals.tryEmit(it) }
    }

    private fun set(d: DriveState, l: LatLng, p: Int, t: TripInfo?, dest: Destination?, rain: Boolean) {
        _drive.value = d; _location.value = l; _passengers.value = p; _trip.value = t; _destination.value = dest; _rainy.value = rain
    }

    override fun addWaypoint(name: String, position: LatLng, onResult: (Boolean) -> Unit) {
        Log.i(TAG, "demo waypoint: $name $position")
        onResult(true)
    }

    override fun speak(text: String) = voice.speak(text)

    override fun startListening() {
        if (voice.canListen) return voice.startListening()
        scope.launch {
            _speech.value = SpeechResult.Listening
            delay(900)
            _speech.value = SpeechResult.Partial("바다 보면서")
            delay(900)
            _speech.value = SpeechResult.Final("바다 보면서 다 같이 컵라면 먹은 날. 내년에도 또 오자.")
        }
    }

    override fun stopListening() {
        if (voice.canListen) return voice.stopListening()
        if (_speech.value is SpeechResult.Listening) _speech.value = SpeechResult.Idle
    }

    companion object {
        private const val TAG = "DemoCarSignals"
        val SUWON = LatLng(37.2636, 127.0286)
        val GANGNEUNG_ENTRY = LatLng(37.7400, 128.8500)
        val GYEONGPO = LatLng(37.7955, 128.9089)
        val DAERUNGWON = LatLng(35.8384, 129.2117)
    }
}
