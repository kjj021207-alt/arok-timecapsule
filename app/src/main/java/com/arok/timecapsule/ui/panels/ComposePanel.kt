package com.arok.timecapsule.ui.panels

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arok.timecapsule.car.SpeechResult
import com.arok.timecapsule.data.SampleData
import com.arok.timecapsule.ui.MainViewModel
import com.arok.timecapsule.ui.components.PillButton
import com.arok.timecapsule.ui.components.ScenePhoto
import com.arok.timecapsule.ui.components.Scenes
import com.arok.timecapsule.ui.components.SectionLabel
import com.arok.timecapsule.ui.components.Surface2
import com.arok.timecapsule.ui.components.Tag
import com.arok.timecapsule.ui.components.formatDateTime
import com.arok.timecapsule.ui.theme.Palette
import java.time.LocalDate

/**
 * 캡슐 남기기.
 * 운전 중([driving])에는 글자 입력을 막고 음성 받아쓰기만 허용한다 (주행 중 화면 제한).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ComposePanel(
    placeLabel: String,
    passengerCount: Int?,
    carFacts: List<String>,
    driving: Boolean,
    speech: SpeechResult,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onCancel: () -> Unit,
    onSave: (MainViewModel.Draft) -> Unit,
) {
    var scene by remember { mutableStateOf("sea") }
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    val companions = remember { mutableStateListOf<String>() }
    var locked by remember { mutableStateOf(false) }
    var lockYear by remember { mutableStateOf((LocalDate.now().year + 1).toString()) }
    var lockMonth by remember { mutableStateOf("") }
    var lockDay by remember { mutableStateOf("") }
    var lockAtPlace by remember { mutableStateOf(true) }
    var isPublic by remember { mutableStateOf(false) }

    // 받아쓰기 결과를 메모에 붙인다
    LaunchedEffect(speech) {
        if (speech is SpeechResult.Final) memo = listOf(memo, speech.text).filter { it.isNotBlank() }.joinToString(" ")
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PillButton("취소", onCancel, icon = Icons.AutoMirrored.Outlined.ArrowBack) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("$placeLabel 캡슐 남기기", style = MaterialTheme.typography.titleLarge)
                Text(formatDateTime(System.currentTimeMillis()), color = Palette.Muted)
            }
        }
        if (driving) item {
            NoticeCard(Icons.Outlined.DirectionsCar, "운전 중에는 음성으로만 남겨요", "사진과 글은 정차한 뒤에 붙일 수 있어요.")
        }
        item {
            SectionLabel("사진")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scenes.all.forEach { s ->
                    ScenePhoto(
                        s.key,
                        Modifier
                            .size(76.dp, 56.dp)
                            .border(2.dp, if (scene == s.key) Palette.Accent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable(enabled = !driving) { scene = s.key },
                    )
                }
            }
        }
        item { Field("제목", title, !driving, single = true) { title = it } }
        item { Field("메모", memo, !driving, single = false) { memo = it } }
        item {
            val listening = speech is SpeechResult.Listening || speech is SpeechResult.Partial
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    if (listening) "그만 듣기" else "음성으로 메모", if (listening) onStopListening else onStartListening,
                    icon = if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic, primary = driving,
                )
                Text(
                    when (speech) {
                        is SpeechResult.Partial -> "\"${speech.text}…\""
                        SpeechResult.Listening -> "듣고 있어요"
                        is SpeechResult.Error -> speech.message
                        else -> ""
                    },
                    color = Palette.Muted,
                )
            }
        }
        item {
            SectionLabel("함께 간 사람 (선택)" + (passengerCount?.let { " · 탑승 ${it}명 감지" } ?: ""))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SampleData.family.forEach { name ->
                    val on = name in companions
                    Tag(
                        name,
                        color = if (on) Palette.OnAccent else Palette.Text,
                        background = if (on) Palette.Accent else Palette.Pressed,
                        onClick = { if (on) companions.remove(name) else companions.add(name) },
                    )
                }
            }
        }
        if (carFacts.isNotEmpty()) item {
            SectionLabel("자동으로 붙는 차 기록")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { carFacts.forEach { Tag(it) } }
        }
        item {
            Surface2 {
                ToggleRow("조건부 캡슐", "정한 시기에 이곳에 다시 와야 열려요", locked, !driving) { locked = it }
                if (locked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("연도 (필수)", lockYear, true, single = true, modifier = Modifier.weight(1.3f), digits = true) { lockYear = it }
                        Field("월", lockMonth, true, single = true, modifier = Modifier.weight(1f), digits = true) { lockMonth = it }
                        Field("일", lockDay, true, single = true, modifier = Modifier.weight(1f), digits = true) { lockDay = it }
                    }
                    ToggleRow("이 장소에 다시 와야 열림", null, lockAtPlace, true) { lockAtPlace = it }
                }
                ToggleRow("커뮤니티에 공개", "공개한 캡슐만 다른 사람에게 보이고, 공감만 받을 수 있어요", isPublic, !driving) { isPublic = it }
            }
        }
        item {
            PillButton(
                "저장하기",
                onClick = {
                    onSave(
                        MainViewModel.Draft(
                            title = title, memo = memo, photoScene = scene, companions = companions.toList(),
                            lockYear = if (locked) lockYear.toIntOrNull() else null,
                            lockMonth = if (locked) lockMonth.toIntOrNull()?.takeIf { it in 1..12 } else null,
                            lockDay = if (locked) lockDay.toIntOrNull()?.takeIf { it in 1..31 } else null,
                            lockAtPlace = lockAtPlace, isPublic = isPublic,
                        )
                    )
                },
                primary = true,
                enabled = !locked || lockYear.toIntOrNull() != null,
            )
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, enabled: Boolean, single: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(), digits: Boolean = false, onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(if (digits) v.filter(Char::isDigit).take(4) else v) },
        enabled = enabled, singleLine = single, minLines = if (single) 1 else 2,
        label = { Text(label) }, modifier = modifier, shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.Divider,
            focusedContainerColor = Palette.Raised, unfocusedContainerColor = Palette.Raised, disabledContainerColor = Palette.Raised,
        ),
    )
}

@Composable
private fun ToggleRow(title: String, sub: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Palette.Accent, uncheckedTrackColor = Palette.Pressed, uncheckedBorderColor = Palette.Divider),
        )
    }
}
