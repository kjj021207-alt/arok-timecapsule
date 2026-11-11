package com.arok.timecapsule.car.pleos

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import com.arok.timecapsule.car.CarSignals
import com.arok.timecapsule.car.Destination
import com.arok.timecapsule.car.DriveState
import com.arok.timecapsule.car.SpeechResult
import com.arok.timecapsule.car.TripInfo
import com.arok.timecapsule.domain.LatLng
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import ai.pleos.playground.tts.constant.Mode as TtsMode

/**
 * Pleos Connect SDK로 실제 차량(에뮬레이터)과 연결하는 구현.
 *
 * | 신호 | SDK |
 * | --- | --- |
 * | 현재 위치, 장소 진입 알림 | FusedLocation (registerFusedLocationCallback, addProximityAlert) |
 * | 목적지, 도착, 주행 중 여부, 경유지 추가 | NaviHelper (onDestinationInfo, onDestinationArrived, onNavigationStatus, addWaypoint) |
 * | 탑승 인원 | Vehicle Door (registerDoorPosition) — 이번 주행 동안 열린 문 수로 추정 |
 * | 비 온 날 | Vehicle Wiper (registerWiperState) |
 * | 주행 거리·시간 | Vehicle Odometer (getTripCurrentDrivingDistance / Time) — 도착 시 조회 |
 * | 음성 안내 | TextToSpeech (ON_DEVICE: 앱 등록 없이 동작) |
 * | 음성 메모 | SpeechToText ([PleosSpeech]) |
 *
 * 팀 결정에 따라 기어(Gear) API는 쓰지 않는다.
 * 각 SDK는 실패해도 앱 전체가 멈추지 않도록 따로 감싼다.
 */
class PleosCarSignals private constructor(private val context: Context) : CarSignals {

    override val isConnectedToCar = true

    private val _drive = MutableStateFlow(DriveState.PARKED)
    private val _arrivals = MutableSharedFlow<Destination>(extraBufferCapacity = 4)
    private val _location = MutableStateFlow<LatLng?>(null)
    private val _passengers = MutableStateFlow<Int?>(null)
    private val _trip = MutableStateFlow<TripInfo?>(null)
    private val _rainy = MutableStateFlow(false)
    private val _destination = MutableStateFlow<Destination?>(null)

    override val driveState: StateFlow<DriveState> = _drive
    override val arrivals: SharedFlow<Destination> = _arrivals
    override val location: StateFlow<LatLng?> = _location
    override val passengerCount: StateFlow<Int?> = _passengers
    override val trip: StateFlow<TripInfo?> = _trip
    override val rainy: StateFlow<Boolean> = _rainy
    override val destination: StateFlow<Destination?> = _destination

    private val fused = FusedLocation(context)
    private val navi = NaviHelper(context)
    private val vehicle = Vehicle(context)
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val voice = PleosSpeech(context)
    override val speech: StateFlow<SpeechResult> = voice.state

    private var locationStarted = false
    private var naviStarted = false
    private var vehicleStarted = false
    private var ttsReady = false
    private val alertIds = mutableListOf<String>()

    // ── 위치 (FusedLocation) ───────────────────────────────

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            _location.value = LatLng(location.latitude, location.longitude)
        }
        override fun onError(exception: Exception) = log("location", exception)
    }

    @SuppressLint("MissingPermission")
    private fun startLocation() {
        if (locationStarted) return
        safe("location") {
            fused.initialize()
            fused.getLastKnownLocation(locationListener)
            fused.registerFusedLocationCallback(locationListener)
            locationStarted = true
        }
    }

    @SuppressLint("MissingPermission")
    override fun watchPlaces(places: Map<String, LatLng>, radiusMeters: Float) {
        if (!locationStarted) return
        safe("proximity") {
            alertIds.forEach { fused.removeProximityAlert(it) }
            alertIds.clear()
            places.values.forEach { p ->
                fused.addProximityAlert(
                    ProximityAlertRequest(p.latitude, p.longitude, radiusMeters, setOf(ProximityTransition.ENTER)),
                    object : ProximityAlertListener {
                        override fun onRegistered(alertId: String) { alertIds += alertId }
                        // 들어온 순간 위치를 다시 받아 열림 조건을 확인하게 한다
                        override fun onTransition(event: ProximityAlertEvent) { fused.getLastKnownLocation(locationListener) }
                        override fun onError(exception: Exception) = log("proximity", exception)
                    },
                )
            }
        }
    }

    // ── 내비 (NaviHelper) ──────────────────────────────────

    private val naviListener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            _destination.value = Destination(destinationInfo.name, LatLng(destinationInfo.latitude, destinationInfo.longitude))
        }

        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {
            _drive.value = DriveState.DRIVING
            resetTrip()
            navi.getDestinationInfo()
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            _drive.value = if (status == NavigationStatus.ROUTE_DRIVE) DriveState.DRIVING else DriveState.PARKED
        }

        /** 캡슐 기록을 제안하는 시점: 내비 목적지 도착 */
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {
            _drive.value = DriveState.PARKED
            readTrip()
            val dest = _destination.value
                ?: _location.value?.let { Destination(destinationArrivedInfo.name, it) }
                ?: return
            _arrivals.tryEmit(dest.copy(name = destinationArrivedInfo.name.ifBlank { dest.name }))
        }

        override fun onRouteCancelled() { _drive.value = DriveState.PARKED }
        override fun onRouteEnded() { _drive.value = DriveState.PARKED }
        override fun onError(errorCode: NaviErrorCode) { Log.w(TAG, "navi error: $errorCode") }
    }

    private fun startNavi() {
        if (naviStarted) return
        safe("navi") {
            navi.initialize(
                onConnected = { navi.addListener(naviListener); navi.getDestinationInfo(); navi.getRouteStateInfo() },
                onFailed = { log("navi connect", it) },
            )
            naviStarted = true
        }
    }

    override fun addWaypoint(name: String, position: LatLng, onResult: (Boolean) -> Unit) {
        val ok = safe("waypoint") {
            navi.addWaypoint(
                RequestWaypointInfo(
                    latitude = position.latitude, longitude = position.longitude, poiName = name,
                    waypointIndex = WaypointIndex.FIRST, poiId = "", address = "", poiSubId = "0",
                )
            )
        }
        onResult(ok)
    }

    // ── 차량 (Vehicle) ─────────────────────────────────────

    /** 이번 주행에서 한 번이라도 열린 문 (운전석 제외) */
    private val openedDoors = mutableSetOf<DoorValue>()
    private val passengerDoors = listOf(DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)

    private fun doorListener(door: DoorValue) = object : DoorPositionListener {
        override fun onPositionUpdated(pos: Int?) {
            if ((pos ?: 0) > 0) {
                openedDoors += door
                _passengers.value = 1 + openedDoors.size
            }
        }
        override fun onFailed(e: Exception) = log("door $door", e)
    }

    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) {
            if (state == WiperState.ON) _rainy.value = true
        }
        override fun onFailed(e: Exception) = log("wiper", e)
    }

    private fun startVehicle() {
        if (vehicleStarted) return
        safe("vehicle") {
            vehicle.initialize()
            passengerDoors.forEach { d -> vehicle.getDoor().registerDoorPosition(DoorArea(d), doorListener(d)) }
            vehicle.getWiper().registerWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), wiperListener)
            vehicleStarted = true
        }
    }

    private fun resetTrip() {
        openedDoors.clear(); _passengers.value = 1; _rainy.value = false; _trip.value = null
    }

    /** 도착 시 이번 주행 거리(m)·시간(분)을 읽어 둔다 */
    private fun readTrip() = safe("trip") {
        val odo = vehicle.getOdometer()
        odo.getTripCurrentDrivingDistance(
            onComplete = { meters ->
                odo.getTripCurrentDrivingTime(
                    onComplete = { minutes -> if (meters != null) _trip.value = TripInfo(meters / 1000.0, minutes ?: 0) },
                    onFailed = { log("trip time", it) },
                )
            },
            onFailed = { log("trip distance", it) },
        )
    }

    // ── 음성 ──────────────────────────────────────────────

    private fun startTts() {
        if (ttsReady) return
        safe("tts") {
            tts.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                override fun onSuccess() { ttsReady = true; tts.setLocale(Locale.KOREA) }
                override fun onFailure(errorType: ai.pleos.playground.tts.constant.ErrorType) { Log.w(TAG, "tts init: $errorType") }
            })
        }
    }

    /** Pleos TTS가 준비되지 않았거나 실패하면 안드로이드 기본 음성으로 대신 읽는다 */
    private val fallbackVoice = com.arok.timecapsule.car.AndroidVoice(context)

    override fun speak(text: String) {
        val ok = ttsReady && safe("speak") { tts.speak(text, true) }
        if (!ok) fallbackVoice.speak(text)
    }

    override fun startListening() = voice.start()
    override fun stopListening() = voice.stop()

    // ── 수명 ──────────────────────────────────────────────

    /** 여러 번 불러도 된다 (권한을 나중에 허용한 경우 다시 호출) */
    override fun start() {
        startLocation(); startNavi(); startVehicle(); startTts(); voice.init()
    }

    override fun stop() {
        safe("stop") {
            if (locationStarted) { fused.unregisterFusedLocationCallback(locationListener); fused.release() }
            if (naviStarted) { navi.removeListener(naviListener); navi.release() }
            if (vehicleStarted) vehicle.release()
            tts.release(); voice.release(); fallbackVoice.release()
        }
        locationStarted = false; naviStarted = false; vehicleStarted = false; ttsReady = false
    }

    private inline fun safe(what: String, block: () -> Unit): Boolean =
        try { block(); true } catch (e: Exception) { log(what, e); false }

    private fun log(what: String, e: Throwable) {
        Log.w(TAG, "$what failed: ${e.message}", e)
    }

    companion object {
        private const val TAG = "PleosCarSignals"

        /** Pleos 서비스가 있는 기기(차량·Pleos 에뮬레이터)일 때만 만든다. 없으면 null → 데모 모드 */
        fun createIfAvailable(context: Context): CarSignals? {
            val supported = runCatching { FusedLocation(context.applicationContext).isSupported() }.getOrDefault(false)
            return if (supported) PleosCarSignals(context.applicationContext) else null
        }
    }
}
