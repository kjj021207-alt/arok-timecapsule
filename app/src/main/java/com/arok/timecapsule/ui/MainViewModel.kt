package com.arok.timecapsule.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arok.timecapsule.car.CarSignals
import com.arok.timecapsule.data.CapsuleRepository
import com.arok.timecapsule.data.PhotoStore
import com.arok.timecapsule.data.SampleData
import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.domain.CapsuleRules
import com.arok.timecapsule.domain.Cities
import com.arok.timecapsule.domain.LatLng
import com.arok.timecapsule.domain.Recommender
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
    /** 필터: 다녀온 도시 (null = 전체) */
    val cityFilter: String? = null,
    /** 필터: 캡슐을 남긴 해 (null = 전체) */
    val yearFilter: Int? = null,
    /** 다녀온 도시 목록 창 */
    val showCities: Boolean = false,
    /** 캡슐 남기기 중 기기에서 고른 사진 (content:// 주소) */
    val pickedPhoto: String? = null,
)

/** 다녀온 도시 한 줄: 도시 이름, 캡슐 수, 마지막으로 남긴 날 */
data class VisitedCity(val city: String, val count: Int, val lastVisit: Long)

class MainViewModel(
    private val repo: CapsuleRepository,
    val car: CarSignals,
    private val photos: PhotoStore,
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
    fun select(id: Long?) = _ui.update { it.copy(selectedId = id, composing = false, showCities = false, justUnlockedId = if (id == it.justUnlockedId) id else null) }
    fun startCompose() = _ui.update { it.copy(composing = true, arrivalPrompt = false, selectedId = null, tab = PanelTab.MINE, pickedPhoto = null) }
    fun cancelCompose() = _ui.update { it.copy(composing = false, pickedPhoto = null) }
    fun dismissArrival() = _ui.update { it.copy(arrivalPrompt = false) }
    fun dismissRevisit() = _ui.update { it.copy(revisit = null) }
    fun consumeMessage() = _ui.update { it.copy(message = null) }
    fun updateSettings(transform: (Settings) -> Settings) = _ui.update { it.copy(settings = transform(it.settings)) }

    /** 필터를 바꾸면 목록과 지도 핀이 함께 걸러진다. 같은 값을 다시 누르면 해제 */
    fun setCityFilter(city: String?) = _ui.update { it.copy(cityFilter = city, selectedId = null) }
    fun setYearFilter(year: Int?) = _ui.update { it.copy(yearFilter = year, selectedId = null) }

    fun showCities(show: Boolean) = _ui.update { it.copy(showCities = show) }

    /** 다녀온 도시 목록에서 도시를 고르면 그 도시 캡슐만 보이게 */
    fun pickCity(city: String) = _ui.update { it.copy(cityFilter = city, showCities = false, selectedId = null, composing = false, tab = PanelTab.MINE) }

    fun onPhotoPicked(uri: String?) { if (uri != null) _ui.update { it.copy(pickedPhoto = uri) } }

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
        // 기기에서 고른 사진은 앱 안으로 복사해 둔다 (원본이 지워져도 캡슐에 남도록)
        val photoPath = _ui.value.pickedPhoto?.let { photos.copyIn(it) }
        val id = repo.add(
            CapsuleEntity(
                title = d.title.ifBlank { "${dest?.name ?: city}에서" },
                memo = d.memo,
                photoScene = d.photoScene,
                photoPath = photoPath,
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
        _ui.update { it.copy(composing = false, selectedId = id, sendOfferId = id, tab = PanelTab.MINE, pickedPhoto = null) }
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

    fun readAloud(c: CapsuleEntity) {
        car.speak("${c.placeName}에서. ${c.title}. ${c.memo}")
        _ui.update { it.copy(message = "\"${c.title}\"을(를) 읽어 드릴게요") }
    }

    /** 주변 추천: 현재 위치에서 가까운 후보를 종류별 기준으로 정렬 ([Recommender]) */
    fun recommendations(): List<Recommender.Scored> = Recommender.rank(car.location.value, SampleData.places)

    companion object {
        /** 함께 간 사람 후보: 가족 + 예전에 입력한 이름 (자주 쓴 순) */
        fun knownCompanions(capsules: List<CapsuleEntity>): List<String> {
            val used = capsules.flatMap { it.companionList }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
            return (SampleData.family + used).distinct()
        }

        /** 필터: 도시와 해가 모두 맞는 캡슐만 */
        fun matches(c: CapsuleEntity, city: String?, year: Int?): Boolean =
            (city == null || c.city == city) && (year == null || yearOf(c.createdAt) == year)

        fun yearOf(millis: Long): Int =
            java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).year

        /** 날짜 필터 후보: 캡슐을 남긴 해 (최근 순) */
        fun years(capsules: List<CapsuleEntity>): List<Int> = capsules.map { yearOf(it.createdAt) }.distinct().sortedDescending()

        fun visitedCities(capsules: List<CapsuleEntity>): List<VisitedCity> =
            capsules.groupBy { it.city }.map { (city, list) -> VisitedCity(city, list.size, list.maxOf { it.createdAt }) }
                .sortedByDescending { it.lastVisit }
    }

    override fun onCleared() {
        car.stop()
    }
}
