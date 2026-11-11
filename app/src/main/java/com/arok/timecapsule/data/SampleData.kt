package com.arok.timecapsule.data

import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.data.db.CapsuleOrigin
import com.arok.timecapsule.domain.LatLng
import java.time.LocalDateTime
import java.time.ZoneId

/** 프로토타입 시연용 예시 데이터. 첫 실행 때 DB에 한 번 넣는다. */
object SampleData {

    private fun at(y: Int, m: Int, d: Int, h: Int = 14, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val capsules = listOf(
        CapsuleEntity(
            title = "처음 본 동해", memo = "차에서 내리자마자 신발 벗고 뛰어간 날. 돌아오는 길에 셋 다 잠들었다.",
            photoScene = "sea", latitude = 37.7955, longitude = 128.9089, placeName = "강릉 경포대", city = "강릉",
            createdAt = at(2026, 8, 15, 11, 20), companions = "엄마,아빠,동생",
            passengerCount = 4, tripDistanceKm = 238.0, tripMinutes = 231, isPublic = true,
        ),
        CapsuleEntity(
            title = "커피거리에서 비 피하기", memo = "갑자기 비가 와서 차 안에서 커피 마시며 한 시간 수다.",
            photoScene = "autumn", latitude = 37.7720, longitude = 128.9470, placeName = "강릉 안목해변", city = "강릉",
            createdAt = at(2025, 10, 2, 15, 5), companions = "엄마",
            passengerCount = 2, tripDistanceKm = 221.0, tripMinutes = 205, rainy = true,
        ),
        CapsuleEntity(
            title = "어린이날 비빔밥", memo = "주차장 찾느라 20분 돌았지만 비빔밥이 다 용서했다.",
            photoScene = "hanok", latitude = 35.8151, longitude = 127.1530, placeName = "전주 한옥마을", city = "전주",
            createdAt = at(2026, 5, 5, 12, 40), companions = "엄마,동생",
            passengerCount = 3, tripDistanceKm = 196.0, tripMinutes = 160, rainy = true,
        ),
        CapsuleEntity(
            title = "모빌리티쇼 끝나고 야경", memo = "광안대교 불 켜지는 순간을 차 안에서 같이 봤다.",
            photoScene = "night", latitude = 35.1532, longitude = 129.1186, placeName = "부산 광안리", city = "부산",
            createdAt = at(2026, 7, 26, 20, 10), companions = "팀원들",
            passengerCount = 3, tripDistanceKm = 412.0, tripMinutes = 298, isPublic = true,
        ),
        CapsuleEntity(
            title = "1년 뒤의 우리에게", memo = "내년에도 같이 일출 보러 오자. 그때 이 메모 같이 읽기.",
            photoScene = "green", latitude = 33.4580, longitude = 126.9425, placeName = "제주 성산일출봉", city = "서귀포",
            createdAt = at(2026, 3, 21, 7, 2), companions = "엄마,아빠",
            passengerCount = 3, tripDistanceKm = 74.0, tripMinutes = 92,
            lockYear = 2027, lockMonth = 3, lockAtPlace = true, opened = false,
        ),
        CapsuleEntity(
            title = "네 아빠 다섯 살 때 여기서", memo = "아빠가 고분 위로 올라가려다 혼났단다. 너도 여기 오면 생각나라고 남긴다.",
            photoScene = "stone", latitude = 35.8384, longitude = 129.2117, placeName = "경주 대릉원", city = "경주",
            createdAt = at(2026, 10, 30, 13, 0), companions = "할머니",
            origin = CapsuleOrigin.RECEIVED, senderName = "할머니", lockAtPlace = true, lockYear = 2026, opened = false,
        ),
        CapsuleEntity(
            title = "엄마가 보낸 단풍", memo = "회사 근처 공원 단풍이 너무 예뻐서 보낸다. 주말에 같이 가자.",
            photoScene = "autumn", latitude = 37.2850, longitude = 127.0190, placeName = "수원 팔달산", city = "수원",
            createdAt = at(2026, 11, 2, 17, 30), companions = "엄마",
            origin = CapsuleOrigin.RECEIVED, senderName = "엄마",
        ),
    )

    val family = listOf("엄마", "아빠", "동생", "할머니")

    /**
     * 주변 추천 후보 (프로토타입 예시 데이터).
     * 실제 서비스에서는 popularity = 내비 목적지 선택 수 집계(서버), freeSpots = 공공데이터포털 주차장 실시간 정보,
     * waitingCars = 매장 제휴 정보로 채운다. 정렬 규칙은 [com.arok.timecapsule.domain.Recommender].
     */
    data class Place(
        val kind: PlaceKind,
        val name: String,
        val position: LatLng,
        val popularity: Int = 0,
        val freeSpots: Int? = null,
        val waitingCars: Int? = null,
    )

    enum class PlaceKind(val label: String) { HOTPLACE("현지 핫플"), PARKING("주차장"), DRIVE_THRU("드라이브스루") }

    val places = listOf(
        // 수원
        Place(PlaceKind.HOTPLACE, "행궁동 카페거리", LatLng(37.2830, 127.0130), popularity = 980),
        Place(PlaceKind.HOTPLACE, "수원 통닭거리", LatLng(37.2790, 127.0170), popularity = 1410),
        Place(PlaceKind.HOTPLACE, "광교호수공원", LatLng(37.2830, 127.0660), popularity = 760),
        Place(PlaceKind.PARKING, "화성행궁 공영주차장", LatLng(37.2820, 127.0140), freeSpots = 23),
        Place(PlaceKind.PARKING, "팔달문 노상주차장", LatLng(37.2770, 127.0170), freeSpots = 4),
        Place(PlaceKind.PARKING, "수원천 공영주차장", LatLng(37.2740, 127.0190), freeSpots = 0),
        Place(PlaceKind.DRIVE_THRU, "커피 드라이브스루 (수원역)", LatLng(37.2660, 127.0010), waitingCars = 3),
        Place(PlaceKind.DRIVE_THRU, "버거 드라이브스루 (인계동)", LatLng(37.2650, 127.0300), waitingCars = 6),
        // 강릉
        Place(PlaceKind.HOTPLACE, "안목해변 커피거리", LatLng(37.7720, 128.9470), popularity = 1240),
        Place(PlaceKind.HOTPLACE, "주문진 수산시장", LatLng(37.8930, 128.8300), popularity = 860),
        Place(PlaceKind.HOTPLACE, "오죽헌", LatLng(37.7790, 128.8780), popularity = 530),
        Place(PlaceKind.PARKING, "경포해변 공영주차장", LatLng(37.8040, 128.9080), freeSpots = 41),
        Place(PlaceKind.PARKING, "안목해변 공영주차장", LatLng(37.7710, 128.9440), freeSpots = 7),
        Place(PlaceKind.DRIVE_THRU, "커피 드라이브스루 (교동)", LatLng(37.7600, 128.8950), waitingCars = 3),
        Place(PlaceKind.DRIVE_THRU, "버거 드라이브스루 (포남동)", LatLng(37.7700, 128.9150), waitingCars = 1),
        // 경주
        Place(PlaceKind.HOTPLACE, "황리단길", LatLng(35.8380, 129.2100), popularity = 2010),
        Place(PlaceKind.HOTPLACE, "동궁과 월지", LatLng(35.8340, 129.2270), popularity = 1350),
        Place(PlaceKind.PARKING, "대릉원 공영주차장", LatLng(35.8370, 129.2130), freeSpots = 18),
        Place(PlaceKind.PARKING, "황남동 공영주차장", LatLng(35.8360, 129.2080), freeSpots = 2),
        Place(PlaceKind.DRIVE_THRU, "버거 드라이브스루 (보문)", LatLng(35.8420, 129.2830), waitingCars = 0),
        // 부산
        Place(PlaceKind.HOTPLACE, "광안리 해변", LatLng(35.1532, 129.1186), popularity = 2400),
        Place(PlaceKind.PARKING, "광안리 공영주차장", LatLng(35.1550, 129.1170), freeSpots = 12),
    )

    data class CommunityPost(val author: String, val title: String, val placeName: String, val photoScene: String, val likes: Int, val position: LatLng)

    /** 팀 결정: 원하는 캡슐만 공개, 다른 사용자는 공감만 남김 (댓글 없음) */
    val community = listOf(
        CommunityPost("바다좋아", "속초 영금정 일출, 차 안에서 본 게 더 좋았다", "속초 영금정", "sea", 312, LatLng(38.2130, 128.6000)),
        CommunityPost("주말드라이버", "여수 밤바다 드라이브 코스", "여수 돌산대교", "night", 207, LatLng(34.7340, 127.7440)),
        CommunityPost("캠핑하는곰", "춘천 의암호 단풍길", "춘천 의암호", "autumn", 154, LatLng(37.8580, 127.6900)),
        CommunityPost("아빠운전사", "아이랑 처음 간 대전 엑스포다리", "대전 엑스포과학공원", "green", 98, LatLng(36.3760, 127.3870)),
    )
}
