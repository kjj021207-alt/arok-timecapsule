package com.arok.timecapsule.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.data.db.CapsuleOrigin
import com.arok.timecapsule.domain.CapsuleRules
import com.arok.timecapsule.ui.components.PillButton
import com.arok.timecapsule.ui.components.ScenePhoto
import com.arok.timecapsule.ui.components.SectionLabel
import com.arok.timecapsule.ui.components.Surface2
import com.arok.timecapsule.ui.components.Tag
import com.arok.timecapsule.ui.components.formatDateTime
import com.arok.timecapsule.ui.components.formatDay
import com.arok.timecapsule.ui.theme.Palette

/** 캡슐 상태별 핀 색 (지도와 목록에서 같이 씀) */
fun CapsuleEntity.pinColor(): Color = when {
    isLocked -> Palette.Locked
    origin == CapsuleOrigin.RECEIVED -> Palette.Family
    else -> Palette.Memory
}

@Composable
fun CapsuleList(
    capsules: List<CapsuleEntity>,
    header: (@Composable () -> Unit)?,
    onOpen: (Long) -> Unit,
    emptyText: String,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (header != null) item { header() }
        if (capsules.isEmpty()) item { Text(emptyText, color = Palette.Muted) }
        items(capsules, key = { it.id }) { c -> CapsuleRow(c) { onOpen(c.id) } }
    }
}

@Composable
private fun CapsuleRow(c: CapsuleEntity, onClick: () -> Unit) {
    Surface2(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.isLocked) {
                Box(Modifier.size(80.dp, 60.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Pressed), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null, tint = Palette.Muted)
                }
            } else {
                ScenePhoto(c.photoScene, Modifier.size(80.dp, 60.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.pinColor()))
                    Text(c.placeName, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    if (c.isLocked) lockSummary(c) else "${formatDay(c.createdAt)} · ${c.title}",
                    style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1,
                )
            }
            when {
                c.origin == CapsuleOrigin.RECEIVED -> Tag("${c.senderName}", color = Palette.Family)
                c.isPublic -> Tag("공개")
            }
        }
    }
}

private fun lockSummary(c: CapsuleEntity): String {
    val date = c.lockYear?.let { y -> listOfNotNull("${y}년", c.lockMonth?.let { "${it}월" }, c.lockDay?.let { "${it}일" }).joinToString(" ") + " 이후" }
    val place = if (c.lockAtPlace) "이곳에 다시 가면 열림" else null
    return listOfNotNull(date, place).joinToString(" · ")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CapsuleDetail(
    c: CapsuleEntity,
    lock: CapsuleRules.UnlockCheck,
    justUnlocked: Boolean,
    onBack: () -> Unit,
    onReadAloud: () -> Unit,
    onSend: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PillButton("목록", onBack, icon = Icons.AutoMirrored.Outlined.ArrowBack) }
        if (lock is CapsuleRules.UnlockCheck.Waiting) {
            item { LockCard(c, lock) }
            return@LazyColumn
        }
        if (justUnlocked) item {
            Surface2 {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.LockOpen, null, tint = Palette.Family)
                    Column {
                        Text("캡슐이 열렸어요", style = MaterialTheme.typography.titleMedium)
                        Text("${c.senderName ?: "예전의 내"}가 이곳에 남긴 기억이에요.", color = Palette.Muted)
                    }
                }
            }
        }
        item { ScenePhoto(c.photoScene, Modifier.fillMaxWidth().aspectRatio(21f / 8f), caption = "사진 · 예시") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(c.title, style = MaterialTheme.typography.headlineSmall)
                Text("${c.placeName} · ${formatDateTime(c.createdAt)}", color = Palette.Muted)
            }
        }
        item { Text(c.memo, style = MaterialTheme.typography.bodyLarge) }
        if (c.companionList.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("함께 간 사람")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    c.companionList.forEach { Tag(it, color = Palette.Text) }
                }
            }
        }
        val carFacts = listOfNotNull(
            c.tripDistanceKm?.let { km -> "주행 ${"%.0f".format(km)}km" + (c.tripMinutes?.let { " · ${it / 60}시간 ${it % 60}분" } ?: "") },
            c.passengerCount?.let { "${it}명 탑승" },
            if (c.rainy) "비 온 날" else null,
        )
        if (carFacts.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("그날의 차 기록")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { carFacts.forEach { Tag(it) } }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("읽어주기", onReadAloud, icon = Icons.Outlined.VolumeUp)
                if (c.origin == CapsuleOrigin.MINE) PillButton("가족에게 보내기", onSend, icon = Icons.Outlined.Send, primary = true)
            }
        }
    }
}

@Composable
private fun LockCard(c: CapsuleEntity, w: CapsuleRules.UnlockCheck.Waiting) {
    Surface2 {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Pressed), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Lock, null, tint = Palette.Text, modifier = Modifier.size(30.dp))
            }
            Text("아직 열리지 않은 캡슐", style = MaterialTheme.typography.titleLarge)
            Text(
                (if (c.senderName != null) "${c.senderName}이(가) ${c.placeName}에 남겼어요" else "${formatDay(c.createdAt)}, ${c.placeName}에서 남겼어요"),
                color = Palette.Muted,
            )
        }
        c.lockYear?.let { ConditionRow(Icons.Outlined.CalendarMonth, lockSummary(c.copy(lockAtPlace = false)), w.dateOk) }
        if (c.lockAtPlace) ConditionRow(Icons.Outlined.Place, "${c.placeName}에 도착", w.placeOk)
        Text("두 조건이 모두 맞으면 차가 알려드려요.", color = Palette.Faint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ConditionRow(icon: ImageVector, label: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Pressed).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Palette.Muted, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, null, tint = if (ok) Palette.Ok else Palette.Faint, modifier = Modifier.size(20.dp))
        Text(if (ok) " 충족" else " 대기", color = if (ok) Palette.Ok else Palette.Faint, fontWeight = FontWeight.SemiBold)
    }
}

/** 패널 위쪽 안내 카드 (도착, 운전 중 등) */
@Composable
fun NoticeCard(
    icon: ImageVector,
    title: String,
    body: String,
    accent: Color = Palette.Accent,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    onDismiss: (() -> Unit)? = null,
) {
    Surface2 {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, color = Palette.Muted)
                if (actionLabel != null || onDismiss != null) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (actionLabel != null) PillButton(actionLabel, onAction, primary = true)
                    if (onDismiss != null) PillButton("나중에", onDismiss)
                }
            }
        }
    }
}
