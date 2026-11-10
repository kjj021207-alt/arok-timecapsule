package com.arok.timecapsule.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arok.timecapsule.ui.theme.Palette
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 프로토타입용 사진 자리. 차량에는 카메라 API가 없어 실제 서비스는 휴대폰에서 사진을 올리며,
 * 여기서는 장면별 풍경 일러스트(하늘·해·지형)를 그려 대신한다.
 */
object Scenes {
    data class Scene(val key: String, val sky: List<Color>, val sun: Color, val far: Color, val near: Color, val water: Boolean)

    val all = listOf(
        Scene("sea", listOf(Color(0xFFF7B48A), Color(0xFFE98579)), Color(0xFFFFE2B8), Color(0xFF6E8FB5), Color(0xFF2E5B86), water = true),
        Scene("autumn", listOf(Color(0xFFF6D59A), Color(0xFFE9A15E)), Color(0xFFFFF1CF), Color(0xFFB4562E), Color(0xFF6E2E1A), water = false),
        Scene("green", listOf(Color(0xFFBFE1EE), Color(0xFF8CC3D9)), Color(0xFFFFFFFF), Color(0xFF6E9E73), Color(0xFF34603F), water = false),
        Scene("night", listOf(Color(0xFF1B1F3B), Color(0xFF3C3263)), Color(0xFFF4E3B0), Color(0xFF2B2D52), Color(0xFF151733), water = true),
        Scene("hanok", listOf(Color(0xFFF3DDB5), Color(0xFFDDAE77)), Color(0xFFFFF6E2), Color(0xFF8C6446), Color(0xFF4A3426), water = false),
        Scene("stone", listOf(Color(0xFFDCE5EC), Color(0xFFAFC0CD)), Color(0xFFFFFFFF), Color(0xFF8E9A78), Color(0xFF55613F), water = false),
    )

    fun of(key: String) = all.firstOrNull { it.key == key } ?: all.first()
}

@Composable
fun ScenePhoto(scene: String, modifier: Modifier = Modifier, caption: String? = null) {
    val s = Scenes.of(scene)
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRect(Brush.verticalGradient(s.sky))
            drawCircle(s.sun, radius = h * 0.11f, center = Offset(w * 0.72f, h * 0.36f))
            val far = Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.2f, h * 0.48f, w * 0.35f, h * 0.58f, w * 0.5f, h * 0.52f)
                cubicTo(w * 0.68f, h * 0.45f, w * 0.85f, h * 0.6f, w, h * 0.55f)
                lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(far, s.far)
            if (s.water) {
                drawRect(s.near, topLeft = Offset(0f, h * 0.7f))
                drawRect(s.sun.copy(alpha = 0.35f), topLeft = Offset(w * 0.62f, h * 0.74f), size = androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.02f))
            } else {
                val near = Path().apply {
                    moveTo(0f, h * 0.82f)
                    cubicTo(w * 0.3f, h * 0.68f, w * 0.6f, h * 0.9f, w, h * 0.74f)
                    lineTo(w, h); lineTo(0f, h); close()
                }
                drawPath(near, s.near)
            }
        }
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** 패널 안의 카드 (테두리 없이 한 단계 밝은 면) */
@Composable
fun Surface2(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.Raised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun Tag(text: String, color: Color = Palette.Muted, background: Color = Palette.Pressed, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = if (onClick != null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(if (onClick != null) 50 else 8))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (onClick != null) 16.dp else 10.dp, vertical = if (onClick != null) 9.dp else 4.dp),
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, color = Palette.Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = modifier)

/** 둥근 알약 모양 버튼. primary = 파랑, 아니면 회색 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = when { !enabled -> Palette.Raised; primary -> Palette.Accent; else -> Palette.Pressed }
    val fg = if (!enabled) Palette.Faint else if (primary) Palette.OnAccent else Palette.Text
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

/** 둥근 세그먼트 선택 (Pleos 화면의 칩 톤) */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    // 좁은 화면(세로 모드)에서는 옆으로 밀어서 볼 수 있게
    Row(
        modifier.horizontalScroll(rememberScrollState()).clip(RoundedCornerShape(50)).background(Palette.Raised).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Text(
                label(o),
                color = if (on) Palette.Text else Palette.Muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) Palette.Pressed else Color.Transparent)
                    .clickable { onSelect(o) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun IconLine(icon: ImageVector, text: String, color: Color = Palette.Muted, content: @Composable RowScope.() -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm", Locale.KOREAN)
private val dayFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)

fun formatDateTime(millis: Long): String = dateFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
fun formatDay(millis: Long): String = dayFmt.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
