package com.arok.timecapsule.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val latitude: Double, val longitude: Double)

/** 두 좌표 사이 거리 (미터, 하버사인) */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

/**
 * "다시 지나가면" 기능의 도시 판단용 오프라인 도시 목록.
 * 팀 결정: 기억 알림은 도시 단위로, 진입했을 때 보여준다.
 */
object Cities {
    data class City(val name: String, val center: LatLng, val radiusKm: Double)

    val all = listOf(
        City("서울", LatLng(37.5665, 126.9780), 18.0),
        City("인천", LatLng(37.4563, 126.7052), 14.0),
        City("수원", LatLng(37.2636, 127.0286), 10.0),
        City("춘천", LatLng(37.8813, 127.7298), 12.0),
        City("강릉", LatLng(37.7519, 128.8761), 14.0),
        City("속초", LatLng(38.2070, 128.5918), 9.0),
        City("대전", LatLng(36.3504, 127.3845), 14.0),
        City("전주", LatLng(35.8242, 127.1480), 10.0),
        City("광주", LatLng(35.1595, 126.8526), 13.0),
        City("대구", LatLng(35.8714, 128.6014), 15.0),
        City("경주", LatLng(35.8562, 129.2247), 14.0),
        City("부산", LatLng(35.1796, 129.0756), 18.0),
        City("여수", LatLng(34.7604, 127.6622), 12.0),
        City("제주", LatLng(33.4996, 126.5312), 16.0),
        City("서귀포", LatLng(33.2541, 126.5600), 16.0),
    )

    /** 좌표가 속한 도시. 반경 안에 여러 개면 가장 가까운 도시 */
    fun cityOf(p: LatLng): String? = all
        .map { it to distanceMeters(p, it.center) }
        .filter { (c, d) -> d <= c.radiusKm * 1000 }
        .minByOrNull { it.second }
        ?.first?.name
}
