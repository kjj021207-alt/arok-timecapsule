package com.arok.timecapsule.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.domain.LatLng
import com.arok.timecapsule.ui.panels.pinColor
import com.arok.timecapsule.ui.theme.Palette
import android.graphics.ColorMatrixColorFilter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val KOREA_CENTER = GeoPoint(36.2, 127.9)

/** 지도 출처 표기 (OpenStreetMap 이용 조건) */
const val MAP_ATTRIBUTION = "© OpenStreetMap contributors"

/**
 * OpenStreetMap 기본 타일을 Pleos 내비처럼 차분한 회색 다크 톤으로 바꾸는 색 행렬.
 * 흑백으로 만든 뒤 반전하고 어둡게 눌러서, 육지는 짙은 회색·글자는 밝은 회색이 된다.
 */
val DARK_TILE_MATRIX: FloatArray = run {
    val s = 0.55f
    val off = 255f * s + 18f
    floatArrayOf(
        -0.30f * s, -0.59f * s, -0.11f * s, 0f, off,
        -0.30f * s, -0.59f * s, -0.11f * s, 0f, off + 2f,
        -0.30f * s, -0.59f * s, -0.11f * s, 0f, off + 6f,
        0f, 0f, 0f, 1f, 0f,
    )
}

/**
 * 메인 지도 (osmdroid). 캡슐 상태별 색으로 핀을 찍고 현재 위치를 파란 점으로 표시한다.
 * 지도 제공자를 바꾸려면 이 파일만 교체하면 된다.
 */
@Composable
fun CapsuleMap(
    capsules: List<CapsuleEntity>,
    here: LatLng?,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(DARK_TILE_MATRIX))
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setBackgroundColor(Palette.Surface.toArgb())
            minZoomLevel = 6.0
            controller.setZoom(7.3)
            controller.setCenter(KOREA_CENTER)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.removeAll { it is Marker }
            capsules.forEach { c ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(c.latitude, c.longitude)
                    icon = pin(context, c.pinColor(), selected = c.id == selectedId)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = c.placeName
                    setOnMarkerClickListener { _, _ -> onSelect(c.id); true }
                })
            }
            here?.let {
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(it.latitude, it.longitude)
                    icon = pin(context, Palette.Accent, selected = true)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                })
            }
            selectedId?.let { id ->
                capsules.firstOrNull { it.id == id }?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
            }
            map.invalidate()
        },
    )
}

/** 원형 핀: 옅은 바깥 원 + 진한 점 + 테두리 */
fun pin(context: Context, color: Color, selected: Boolean): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val size = ((if (selected) 34 else 26) * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val r = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = color.copy(alpha = 0.22f).toArgb()
    canvas.drawCircle(r, r, r, paint)
    paint.color = color.toArgb()
    canvas.drawCircle(r, r, r * 0.42f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * density
    paint.color = if (selected) android.graphics.Color.WHITE else Palette.Background.toArgb()
    canvas.drawCircle(r, r, r * 0.42f, paint)
    return BitmapDrawable(context.resources, bmp)
}
