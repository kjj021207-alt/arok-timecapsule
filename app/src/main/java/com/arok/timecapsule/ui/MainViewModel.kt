package com.arok.timecapsule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arok.timecapsule.car.CarSignals
import com.arok.timecapsule.data.CapsuleRepository
import com.arok.timecapsule.data.SampleData
import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.domain.CapsuleRules
import com.arok.timecapsule.domain.Cities
import com.arok.timecapsule.domain.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class PanelTab(val label: String) { MINE("내 캡슐"), RECEIVED("받은 캡슐"), RECOMMEND("주변 추천"), COMMUNITY("커뮤니티") }

data class Settings(
    val homeCity: String = "수원",
    /** 팀 결정: 다시 지나갈 때 안내를 띄울지 사용자가 선택 */
    val revisitBanner: Boolean = true,
    val revisitVoice: Boolean = true,
)

data class RevisitBanner(val city: String, val memories: List<CapsuleEntity>)

data class UiState(
    val tab: PanelTab = PanelTab.MINE,
    val selectedId: Long? = null,
    val composing: Boolean = false,
    val arrivalPrompt: Boolean = false,
    val revisit: RevisitBanner? = null,
    val justUnlockedId: Long? = null,
    /** 저장 직후 "가족에게 전달하시겠어요?" 대상 캡슐 */
    val sendOfferId: Long? = null,
    val message: String? = null,
    val likedPosts: Set<Int> = emptySet(),
    val settings: Settings = Settings(),
)

class MainViewModel(
    private val repo: CapsuleRepository,
    val car: CarSignals,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val capsules: StateFlow<List<CapsuleEntity>> =
        repo.capsules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentCity: StateFlow<String?> =
        car.location.map { it?.let(Cities::cityOf) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        car.start()
        viewModelScope.launch { repo.seedIfEmpty() }
        watchCityEntry()
        watchArrival()
        watchUnlocks()
    }

    // ── 차량 신호에 반응하는 규칙 ─────────────────────────────

    /** 도시 진입 → 그 도시의 기억 모아 보여주기 (+2) */
    private fun watchCityEntry() = viewModelScope.launch {
        // StateFlow는 같은 값을 다시 내보내지 않으므로 도시가 바뀔 때만 실행된다
        currentCity.collect { city ->
            val s = _ui.value.settings
            val memories = CapsuleRules.memoriesForCityEntry(city, s.homeCity, capsules.value.ifEmpty { repo.capsules.first() })
            if (memories.isEmpty() || city == null) {
                _ui.update { it.copy(revisit = null) }
                return@collect
            }
            if (s.revisitBanner) _ui.update { it.copy(revisit = RevisitBanner(city, memories)) }
            if (s.revisitVoice) {
                val first = memories.first()
                car.speak("$city, ${memories.size}번 왔었어요. 처음 온 날은 ${first.title}.")
            }
        }
    }

    /**
     * 기록 제안 (메인). 팀 결정: 기어(P/D)는 쓰지 않고 "내비 목적지 도착" 이벤트로 제안한다.
     * 거주 도시 안의 도착(출퇴근 등)은 제안하지 않는다. 그 밖에는 앱의 "캡슐 남기기" 버튼으로 언제든 기록할 수 있다.
     */
    private fun watchArrival() = viewModelScope.launch {
        car.arrivals.collect { dest ->
            if (CapsuleRules.shouldSuggestRecord(dest.position, _ui.value.settings.homeCity)) {
                _ui.update { it.copy(arrivalPrompt = true, revisit = null, tab = PanelTab.MINE, selectedId = null, composing = false) }
                car.speak("${dest.name}에 도착했어요. 오늘을 캡슐로 남길까요?")
            }
        }
    }

    /** 위치·날짜 조건이 맞은 잠긴 캡슐 열기 (+3). 잠긴 캡슐 장소는 지오펜스로 등록해 들어오는 순간 확인한다. */
    private fun watchUnlocks() = viewModelScope.launch {
        launch {
            capsules.collect { list ->
                car.watchPlaces(
                    list.filter { it.isLocked && it.lockAtPlace }.associate { it.id.toString() to LatLng(it.latitude, it.longitude) },
                    CapsuleRules.UNLOCK_RADIUS_M.toFloat(),
                )
            }
        }
        combine(car.location, capsules) { here, list -> here to list }.collect { (here, list) ->
            val today = LocalDate.now()
            list.filter { it.isLocked && CapsuleRules.check(it, today, here) == CapsuleRules.UnlockCheck.Open }
                .forEach { c ->
                    repo.open(c.id)
                    _ui.update { it.copy(justUnlockedId = c.id, selectedId = c.id, tab = if (c.senderName != null) PanelTab.RECEIVED else PanelTab.MINE) }
                    car.speak("${c.senderName ?: "예전의 내"}가 남긴 캡슐이 열렸어요.")
                }
        }
    }

    // ── 화면 동작 ──────────────────────────────────────────

    fun selectTab(t: PanelTab) = _ui.update { it.copy(tab = t, selectedId = null, composing = false) }
    fun select(id: Long?) = _ui.update { it.copy(selectedId = id, composing = false, justUnlockedId = if (id == it.justUnlockedId) id else null) }
    fun startCompose() = _ui.update { it.copy(composing = true, arrivalPrompt = false, selectedId = null, tab = PanelTab.MINE) }
    fun cancelCompose() = _ui.update { it.copy(composing = false) }
    fun dismissArrival() = _ui.update { it.copy(arrivalPrompt = false) }
    fun dismissRevisit() = _ui.update { it.copy(revisit = null) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
    fun updateSettings(transform: (Settings) -> Settings) = _ui.update { it.copy(settings = transform(it.settings)) }

    fun lockStatus(c: CapsuleEntity): CapsuleRules.UnlockCheck = CapsuleRules.check(c, LocalDate.now(), car.location.value)

    data class Draft(
        val title: String,
        val memo: String,
        val photoScene: String,
        val companions: List<String>,
        val lockYear: Int?,
        val lockMonth: Int?,
        val lockDay: Int?,
        val lockAtPlace: Boolean,
        val isPublic: Boolean,
    )

    fun save(d: Draft) = viewModelScope.launch {
        val here = car.location.value ?: LatLng(37.2636, 127.0286)
        val city = Cities.cityOf(here) ?: "기타"
        val dest = car.destination.value
        val locked = d.lockYear != null
        val id = repo.add(
            CapsuleEntity(
                title = d.title.ifBlank { "$city 에서" },
                memo = d.memo,
                photoScene = d.photoScene,
                latitude = here.latitude,
                longitude = here.longitude,
                placeName = dest?.name ?: city,
                city = city,
                createdAt = System.currentTimeMillis(),
                companions = d.companions.joinToString(","),
                passengerCount = car.passengerCount.value,
                tripDistanceKm = car.trip.value?.distanceKm,
                tripMinutes = car.trip.value?.minutes,
                rainy = car.rainy.value,
                lockYear = d.lockYear,
                lockMonth = d.lockMonth,
                lockDay = d.lockDay,
                lockAtPlace = locked && d.lockAtPlace,
                opened = !locked,
                isPublic = d.isPublic,
            )
        )
        // 팀 결정: 저장 후 "가족에게 전달하시겠어요?" 정도만 제안
        _ui.update { it.copy(composing = false, selectedId = id, sendOfferId = id, tab = PanelTab.MINE) }
    }

    fun offerSend(id: Long) = _ui.update { it.copy(sendOfferId = id) }

    /** 프로토타입: 전송은 화면 피드백까지만. 실제 전달은 2차에서 서버(Firebase)로 연결 */
    @Suppress("UNUSED_PARAMETER")
    fun sendToFamily(id: Long, to: List<String>) {
        _ui.update { it.copy(sendOfferId = null, message = if (to.isEmpty()) null else "${to.joinToString(", ")}에게 캡슐을 보냈어요") }
    }

    fun dismissSendOffer() = _ui.update { it.copy(sendOfferId = null) }

    fun addWaypoint(name: String, position: LatLng) {
        car.addWaypoint(name, position) { ok ->
            _ui.update { it.copy(message = if (ok) "\"$name\"을(를) 경유지로 추가했어요" else "경유지를 추가하지 못했어요") }
        }
    }

    fun toggleLike(index: Int) = _ui.update {
        it.copy(likedPosts = if (index in it.likedPosts) it.likedPosts - index else it.likedPosts + index)
    }

    fun readAloud(c: CapsuleEntity) = car.speak("${c.title}. ${c.memo}")

    val recommendations get() = SampleData.recommendationsNear(currentCity.value)

    override fun onCleared() {
        car.stop()
    }
}
