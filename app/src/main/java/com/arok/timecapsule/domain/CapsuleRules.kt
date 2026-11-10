package com.arok.timecapsule.domain

import com.arok.timecapsule.data.db.CapsuleEntity
import java.time.LocalDate

/** 팀이 정한 규칙을 한곳에 모은 것. 화면과 차량 연동 코드는 이 규칙만 호출한다. */
object CapsuleRules {

    /** 잠긴 캡슐이 열리는 장소 반경 */
    const val UNLOCK_RADIUS_M = 500.0

    sealed interface UnlockCheck {
        data object Open : UnlockCheck
        data class Waiting(val dateOk: Boolean, val placeOk: Boolean) : UnlockCheck
    }

    /**
     * 열림 조건: 연도는 필수, 월·일은 선택(지정한 만큼만 비교), 장소 조건은 선택.
     * 두 조건이 모두 맞아야 열린다.
     */
    fun check(capsule: CapsuleEntity, today: LocalDate, here: LatLng?): UnlockCheck {
        if (!capsule.isLocked) return UnlockCheck.Open
        val dateOk = capsule.lockYear?.let { y ->
            val target = LocalDate.of(y, capsule.lockMonth ?: 1, capsule.lockDay ?: 1)
            !today.isBefore(target)
        } ?: true
        val placeOk = !capsule.lockAtPlace || (here != null &&
            distanceMeters(here, LatLng(capsule.latitude, capsule.longitude)) <= UNLOCK_RADIUS_M)
        return if (dateOk && placeOk) UnlockCheck.Open else UnlockCheck.Waiting(dateOk, placeOk)
    }

    /**
     * "다시 지나가면": 거주 도시가 아닌 도시에 진입했고, 그 도시에 열린 기록이 있으면 모아서 보여준다.
     */
    fun memoriesForCityEntry(city: String?, homeCity: String, capsules: List<CapsuleEntity>): List<CapsuleEntity> {
        if (city == null || city == homeCity) return emptyList()
        return capsules.filter { it.city == city && !it.isLocked }.sortedBy { it.createdAt }
    }

    /** 내비 목적지에 도착했을 때 기록을 제안할지: 거주 도시 밖 도착일 때만 (출퇴근 같은 일상 도착은 제외) */
    fun shouldSuggestRecord(arrivedAt: LatLng, homeCity: String): Boolean = Cities.cityOf(arrivedAt) != homeCity
}
