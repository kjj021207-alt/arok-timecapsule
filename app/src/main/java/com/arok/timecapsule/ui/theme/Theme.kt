package com.arok.timecapsule.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect 인포테인먼트 화면 톤에 맞춘 팔레트.
 * 거의 검은 바탕 위에 테두리 없는 회색 패널, 포인트 색은 파랑 하나.
 * 캡슐 상태 색(기억·잠김·가족)은 지도 핀과 작은 표시에만 쓴다.
 */
object Palette {
    val Background = Color(0xFF07080A)
    val Surface = Color(0xFF15181C)      // 앱 패널
    val Raised = Color(0xFF1F2328)       // 패널 안 카드
    val Pressed = Color(0xFF2A2F35)
    val Divider = Color(0xFF2A2E33)
    val Text = Color(0xFFF2F4F6)
    val Muted = Color(0xFF969DA6)
    val Faint = Color(0xFF666D75)
    val Accent = Color(0xFF3D8BFF)       // Pleos 계열 파랑
    val OnAccent = Color.White
    val Memory = Color(0xFFF4B740)       // 열린 기록 핀
    val Locked = Color(0xFF7D858E)       // 잠긴 캡슐 핀
    val Family = Color(0xFFFF7A98)       // 가족이 보낸 캡슐 핀
    val Ok = Color(0xFF4CC38A)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.OnAccent,
    secondary = Palette.Raised,
    onSecondary = Palette.Text,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Surface,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Raised,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerHigh = Palette.Raised,
    outline = Palette.Divider,
    outlineVariant = Palette.Divider,
)

// 차량 화면은 팔 길이 너머에서 보므로 기본 크기를 키우고 굵기 차이로 위계를 만든다.
private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 29.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun TimeCapsuleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        // Surface 밖에서 그린 글자도 기본으로 밝은 색이 되도록
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
