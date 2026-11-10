package com.arok.timecapsule.car

import com.arok.timecapsule.domain.LatLng
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 차량이 주행 중인지, 멈춰(주차) 있는지 */
enum class DriveState { PARKED, DRIVING, UNKNOWN }

data class TripInfo(val distanceKm: Double, val minutes: Int)

data class Destination(val name: String, val position: LatLng)

/**
 * 앱이 차에서 받아오는 모든 신호와 차에 요청하는 동작.
 *
 * - [com.arok.timecapsule.car.pleos.PleosCarSignals]: Pleos Connect SDK로 실제 차량(에뮬레이터)과 연결
 * - [DemoCarSignals]: Pleos 서비스가 없는 기기(폰, 일반 에뮬레이터)에서 쓰는 데모용 신호
 *
 * 화면과 규칙 코드는 이 인터페이스만 보므로, 어느 쪽이든 똑같이 동작한다.
 */
interface CarSignals {
    /** true면 Pleos SDK로 실제 차량에 연결된 상태 */
    val isConnectedToCar: Boolean

    /**
     * 주행 중인지 여부. 팀 결정에 따라 기어(P/D)는 쓰지 않고,
     * 내비 경로 안내 상태(경로 주행 중이면 DRIVING)로 판단한다. 운전 중 입력 잠금에만 쓴다.
     */
    val driveState: StateFlow<DriveState>

    /** 내비 목적지에 도착했을 때 한 번씩 오는 이벤트 → 캡슐 기록 제안 */
    val arrivals: SharedFlow<Destination>
    val location: StateFlow<LatLng?>
    /** 문 열림 기록으로 추정한 탑승 인원 */
    val passengerCount: StateFlow<Int?>
    val trip: StateFlow<TripInfo?>
    /** 와이퍼가 동작 중이면 비 오는 날로 기록 */
    val rainy: StateFlow<Boolean>
    /** 내비게이션에 설정된 목적지 */
    val destination: StateFlow<Destination?>
    /** 음성 인식 중간/최종 결과 */
    val speech: StateFlow<SpeechResult>

    fun start()
    fun stop()

    /** 내비게이션에 경유지 추가 */
    fun addWaypoint(name: String, position: LatLng, onResult: (Boolean) -> Unit)

    /** 차량 스피커로 음성 안내 */
    fun speak(text: String)

    /** 음성 메모 받아쓰기 시작/중지 */
    fun startListening()
    fun stopListening()

    /**
     * 이 장소들 근처에 들어오면 알려 달라고 등록 (잠긴 캡슐 장소 등).
     * 들어오면 [location]이 바로 갱신되어 열림 조건을 다시 확인하게 된다.
     */
    fun watchPlaces(places: Map<String, LatLng>, radiusMeters: Float) {}
}

sealed interface SpeechResult {
    data object Idle : SpeechResult
    data object Listening : SpeechResult
    data class Partial(val text: String) : SpeechResult
    data class Final(val text: String) : SpeechResult
    data class Error(val message: String) : SpeechResult
}
