package com.arok.timecapsule

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.arok.timecapsule.car.SpeechResult
import com.arok.timecapsule.data.SampleData
import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.domain.CapsuleRules
import com.arok.timecapsule.domain.Cities
import com.arok.timecapsule.domain.LatLng
import com.arok.timecapsule.ui.MainLayout
import com.arok.timecapsule.ui.PanelTab
import com.arok.timecapsule.ui.RevisitBanner
import com.arok.timecapsule.ui.ScreenActions
import com.arok.timecapsule.ui.ScreenState
import com.arok.timecapsule.ui.UiState
import com.arok.timecapsule.ui.map.DARK_TILE_MATRIX
import com.arok.timecapsule.ui.panels.pinColor
import com.arok.timecapsule.ui.theme.Palette
import com.arok.timecapsule.ui.theme.TimeCapsuleTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

/**
 * 에뮬레이터 없이 주요 화면을 이미지로 뽑는 테스트.
 * 실행: ./gradlew recordPaparazziDebug → app/src/test/snapshots/images/
 * 지도는 렌더링 중 인터넷을 쓸 수 없어 test/resources/tiles 의 OpenStreetMap 타일(줌 7)을 앱과 같은 색 변환으로 그린다.
 */
/** 차량 화면(가로 1920×900) */
class ScreenshotTest : ScreenshotScenes(
    DeviceConfig.PIXEL_5.copy(
        screenWidth = 1920, screenHeight = 900, xdpi = 240, ydpi = 240,
        density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE, softButtons = false,
    )
)

/** 폰 세로 화면 (Pleos 분할 화면의 좁은 칸도 같은 배치) */
class PortraitScreenshotTest : ScreenshotScenes(DeviceConfig.PIXEL_5)

abstract class ScreenshotScenes(device: DeviceConfig) {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = device,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private val capsules = SampleData.capsules.mapIndexed { i, c -> c.copy(id = i + 1L) }
    private fun byPlace(name: String) = capsules.first { it.placeName == name }

    private val suwon = LatLng(37.2636, 127.0286)
    private val gangneungEntry = LatLng(37.7400, 128.8500)
    private val gyeongpo = LatLng(37.7955, 128.9089)
    private val daerungwon = LatLng(35.8384, 129.2117)

    private fun state(
        here: LatLng,
        ui: UiState = UiState(),
        driving: Boolean = false,
        list: List<CapsuleEntity> = capsules,
        destination: String? = null,
        carFacts: List<String> = emptyList(),
    ) = ScreenState(
        ui = ui, capsules = list, here = here, city = Cities.cityOf(here), driving = driving, connectedToCar = true,
        destinationName = destination, passengers = 3, carFacts = carFacts, speech = SpeechResult.Idle,
        recommendations = SampleData.recommendationsNear(Cities.cityOf(here)),
        lockStatus = { c -> CapsuleRules.check(c, LocalDate.of(2026, 11, 10), here) },
    )

    private fun shoot(s: ScreenState) = paparazzi.snapshot {
        TimeCapsuleTheme {
            MainLayout(s, ScreenActions(), map = { m -> StaticKoreaMap(s.capsules, s.here, s.ui.selectedId, m) })
        }
    }

    @Test fun s01_home() = shoot(state(suwon))

    @Test fun s02_revisitWhileDriving() = shoot(
        state(
            gangneungEntry, driving = true, destination = "강릉 경포대",
            ui = UiState(revisit = RevisitBanner("강릉", capsules.filter { it.city == "강릉" && !it.isLocked }.sortedBy { it.createdAt })),
        )
    )

    @Test fun s03_arrival() = shoot(state(gyeongpo, destination = "강릉 경포대", ui = UiState(arrivalPrompt = true)))

    @Test fun s04_compose() = shoot(
        state(
            gyeongpo, destination = "강릉 경포대", ui = UiState(composing = true),
            carFacts = listOf("주행 238km · 3시간 51분", "3명 탑승"),
        )
    )

    @Test fun s05_detail() = shoot(state(suwon, ui = UiState(selectedId = byPlace("강릉 경포대").id)))

    @Test fun s06_locked() = shoot(state(suwon, ui = UiState(selectedId = byPlace("제주 성산일출봉").id)))

    @Test fun s07_familyUnlocked() {
        val gift = byPlace("경주 대릉원")
        val list = capsules.map { if (it.id == gift.id) it.copy(opened = true) else it }
        shoot(state(daerungwon, list = list, ui = UiState(tab = PanelTab.RECEIVED, selectedId = gift.id, justUnlockedId = gift.id)))
    }

    @Test fun s08_recommend() = shoot(state(gyeongpo, ui = UiState(tab = PanelTab.RECOMMEND)))

    @Test fun s09_community() = shoot(state(suwon, ui = UiState(tab = PanelTab.COMMUNITY)))
}

/** 줌 7 타일을 이어 붙인 정적 한국 지도 + 핀 (스크린샷 전용) */
@Composable
internal fun StaticKoreaMap(capsules: List<CapsuleEntity>, here: LatLng?, selectedId: Long?, modifier: Modifier) {
    val zoom = 7
    val tiles = remember {
        (107..111).flatMap { x -> (48..51).map { y -> Triple(x, y, "tiles/${zoom}_${x}_${y}.png") } }.mapNotNull { (x, y, path) ->
            val stream = ScreenshotTest::class.java.classLoader!!.getResourceAsStream(path) ?: return@mapNotNull null
            Triple(x, y, BitmapFactory.decodeStream(stream).asImageBitmap())
        }
    }
    fun world(p: LatLng): Offset {
        val n = 256.0 * (1 shl zoom)
        val x = (p.longitude + 180) / 360 * n
        val lat = Math.toRadians(p.latitude)
        val y = (1 - ln(tan(lat) + 1 / kotlin.math.cos(lat)) / PI) / 2 * n
        return Offset(x.toFloat(), y.toFloat())
    }
    val filter = ColorFilter.colorMatrix(ColorMatrix(DARK_TILE_MATRIX))
    Canvas(modifier.fillMaxSize()) {
        drawRect(Palette.Surface)
        // 한국 영역(제주 포함)이 화면에 들어오도록 배율 계산. 아래쪽은 범례 자리만큼 여유를 둔다.
        val tl = world(LatLng(38.75, 125.6)); val br = world(LatLng(32.55, 130.2))
        val scale = minOf(size.width / (br.x - tl.x), size.height / (br.y - tl.y))
        val center = Offset((tl.x + br.x) / 2, (tl.y + br.y) / 2)
        fun screen(w: Offset) = Offset((w.x - center.x) * scale + size.width / 2, (w.y - center.y) * scale + size.height / 2)
        tiles.forEach { (x, y, img) ->
            val o = screen(Offset(x * 256f, y * 256f))
            val s = (256 * scale).toInt() + 1
            drawImage(img, dstOffset = IntOffset(o.x.toInt(), o.y.toInt()), dstSize = IntSize(s, s), colorFilter = filter)
        }
        capsules.forEach { c ->
            val p = screen(world(LatLng(c.latitude, c.longitude)))
            val sel = c.id == selectedId
            val r = if (sel) 22f else 17f
            drawCircle(c.pinColor().copy(alpha = 0.22f), r * 1.6f, p)
            drawCircle(c.pinColor(), r * 0.65f, p)
            drawCircle(if (sel) androidx.compose.ui.graphics.Color.White else Palette.Background, r * 0.65f, p, style = Stroke(4f))
        }
        here?.let {
            val p = screen(world(it))
            drawCircle(Palette.Accent.copy(alpha = 0.25f), 30f, p)
            drawCircle(Palette.Accent, 11f, p)
            drawCircle(androidx.compose.ui.graphics.Color.White, 11f, p, style = Stroke(4f))
        }
    }
}
