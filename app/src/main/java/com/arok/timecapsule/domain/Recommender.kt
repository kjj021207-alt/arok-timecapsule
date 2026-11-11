package com.arok.timecapsule.domain

import com.arok.timecapsule.data.SampleData.Place
import com.arok.timecapsule.data.SampleData.PlaceKind
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 주변 추천 로직.
 *
 * 1. 현재 위치에서 [RADIUS_KM] 안에 있는 후보만 남긴다.
 * 2. 종류마다 기준을 달리해 점수를 매기고 높은 순으로 보여준다.
 *    - 현지 핫플: 많은 사람이 목적지로 고른 곳일수록, 가까울수록 위 (인기 ÷ (1 + 거리/3km))
 *    - 주차장: 빈자리가 없는 곳은 빼고, 가까운 순 (거리가 같으면 빈자리 많은 곳)
 *    - 드라이브스루: 가는 시간 + 대기 시간(대기 차 1대당 2분)이 짧은 순
 * 3. 가는 시간은 시내 평균 30km/h로 어림한다. (실제 서비스는 NaviHelper requestTrip의 ETA로 대체)
 */
object Recommender {

    const val RADIUS_KM = 15.0
    private const val CITY_SPEED_KMH = 30.0
    private const val MINUTES_PER_WAITING_CAR = 2

    data class Scored(val place: Place, val distanceKm: Double, val minutes: Int, val detail: String)

    fun rank(here: LatLng?, candidates: List<Place>): List<Scored> {
        if (here == null) return emptyList()
        val near = candidates.map { p ->
            val km = distanceMeters(here, p.position) / 1000
            p to km
        }.filter { (_, km) -> km <= RADIUS_KM }

        fun minutes(km: Double) = (km / CITY_SPEED_KMH * 60).roundToInt().coerceAtLeast(1)
        val fmt = NumberFormat.getIntegerInstance(Locale.KOREA)

        val hot = near.filter { it.first.kind == PlaceKind.HOTPLACE }
            .sortedByDescending { (p, km) -> p.popularity / (1 + km / 3) }
            .map { (p, km) -> Scored(p, km, minutes(km), "약 ${"%.1f".format(km)}km · 이번 주 목적지 선택 ${fmt.format(p.popularity)}회") }

        val parking = near.filter { it.first.kind == PlaceKind.PARKING && (it.first.freeSpots ?: 1) > 0 }
            .sortedWith(compareBy<Pair<Place, Double>> { it.second }.thenByDescending { it.first.freeSpots ?: 0 })
            .map { (p, km) -> Scored(p, km, minutes(km), "약 ${"%.1f".format(km)}km · 남은 자리 ${p.freeSpots ?: "?"}면") }

        val driveThru = near.filter { it.first.kind == PlaceKind.DRIVE_THRU }
            .map { (p, km) -> Triple(p, km, minutes(km) + (p.waitingCars ?: 0) * MINUTES_PER_WAITING_CAR) }
            .sortedBy { it.third }
            .map { (p, km, total) -> Scored(p, km, total, "약 ${"%.1f".format(km)}km · 대기 ${p.waitingCars ?: 0}대 · 받기까지 약 ${total}분") }

        return hot + parking + driveThru
    }
}
