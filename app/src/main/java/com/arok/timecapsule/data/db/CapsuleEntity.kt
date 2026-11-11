package com.arok.timecapsule.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 누가 만든 캡슐인가 */
enum class CapsuleOrigin { MINE, RECEIVED }

/**
 * 타임캡슐 한 개.
 *
 * - 열림 조건: [lockYear]가 있으면 잠긴 캡슐. 연도는 필수, 월·일은 선택.
 *   [lockAtPlace]가 true면 그 장소에 다시 가야 열린다.
 * - 받은 캡슐([CapsuleOrigin.RECEIVED])은 기본적으로 바로 열리고, 보낸 사람이 장소 조건을 걸었을 때만 잠긴다.
 */
@Entity(tableName = "capsules")
data class CapsuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val memo: String,
    /** 앱 내부 저장소의 녹음 파일 경로. 없으면 null */
    val voicePath: String? = null,
    /** 사진이 없을 때 보여줄 예시 장면 키 (sea, hanok, night, green, stone, autumn) */
    val photoScene: String,
    /** 사용자가 고른 사진 (앱 폴더에 복사된 파일 경로). 없으면 [photoScene]을 보여준다 */
    val photoPath: String? = null,
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val city: String,
    val createdAt: Long,
    /** 함께 간 사람 (쉼표 구분) */
    val companions: String = "",
    // 차량에서 자동으로 붙는 정보
    val passengerCount: Int? = null,
    val tripDistanceKm: Double? = null,
    val tripMinutes: Int? = null,
    val rainy: Boolean = false,
    // 열림 조건
    val lockYear: Int? = null,
    val lockMonth: Int? = null,
    val lockDay: Int? = null,
    val lockAtPlace: Boolean = false,
    val opened: Boolean = true,
    // 공유
    val origin: CapsuleOrigin = CapsuleOrigin.MINE,
    val senderName: String? = null,
    val isPublic: Boolean = false,
) {
    val companionList: List<String> get() = companions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val isLocked: Boolean get() = !opened
}
