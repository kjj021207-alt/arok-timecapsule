package com.arok.timecapsule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arok.timecapsule.car.DemoCarSignals
import com.arok.timecapsule.car.DriveState
import com.arok.timecapsule.car.SpeechResult
import com.arok.timecapsule.data.SampleData
import com.arok.timecapsule.data.db.CapsuleEntity
import com.arok.timecapsule.data.db.CapsuleOrigin
import com.arok.timecapsule.domain.CapsuleRules
import com.arok.timecapsule.domain.LatLng
import com.arok.timecapsule.ui.components.PillButton
import com.arok.timecapsule.ui.components.Segmented
import com.arok.timecapsule.ui.components.Tag
import com.arok.timecapsule.ui.map.CapsuleMap
import com.arok.timecapsule.ui.panels.CapsuleDetail
import com.arok.timecapsule.ui.panels.CapsuleList
import com.arok.timecapsule.ui.panels.CommunityPanel
import com.arok.timecapsule.ui.panels.ComposePanel
import com.arok.timecapsule.ui.panels.NoticeCard
import com.arok.timecapsule.ui.panels.RecommendPanel
import com.arok.timecapsule.ui.theme.Palette

/** 화면에 그릴 모든 상태 (ViewModel과 스크린샷 테스트가 같은 레이아웃을 쓰도록 분리) */
data class ScreenState(
    val ui: UiState,
    val capsules: List<CapsuleEntity>,
    val here: LatLng?,
    val city: String?,
    val driving: Boolean,
    val connectedToCar: Boolean,
    val destinationName: String?,
    val passengers: Int?,
    val carFacts: List<String>,
    val speech: SpeechResult,
    val recommendations: List<SampleData.Recommendation>,
    val lockStatus: (CapsuleEntity) -> CapsuleRules.UnlockCheck,
)

class ScreenActions(
    val selectTab: (PanelTab) -> Unit = {},
    val select: (Long?) -> Unit = {},
    val startCompose: () -> Unit = {},
    val cancelCompose: () -> Unit = {},
    val save: (MainViewModel.Draft) -> Unit = {},
    val dismissArrival: () -> Unit = {},
    val dismissRevisit: () -> Unit = {},
    val readAloud: (CapsuleEntity) -> Unit = {},
    val offerSend: (Long) -> Unit = {},
    val sendToFamily: (Long, List<String>) -> Unit = { _, _ -> },
    val dismissSendOffer: () -> Unit = {},
    val addWaypoint: (String, LatLng) -> Unit = { _, _ -> },
    val toggleLike: (Int) -> Unit = {},
    val startListening: () -> Unit = {},
    val stopListening: () -> Unit = {},
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val capsules by vm.capsules.collectAsStateWithLifecycle()
    val here by vm.car.location.collectAsStateWithLifecycle()
    val city by vm.currentCity.collectAsStateWithLifecycle()
    val drive by vm.car.driveState.collectAsStateWithLifecycle()
    val speech by vm.car.speech.collectAsStateWithLifecycle()
    val passengers by vm.car.passengerCount.collectAsStateWithLifecycle()
    val trip by vm.car.trip.collectAsStateWithLifecycle()
    val rainy by vm.car.rainy.collectAsStateWithLifecycle()
    val destination by vm.car.destination.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(ui.message) { ui.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val state = ScreenState(
        ui = ui, capsules = capsules, here = here, city = city,
        driving = drive == DriveState.DRIVING, connectedToCar = vm.car.isConnectedToCar,
        destinationName = destination?.name, passengers = passengers,
        carFacts = listOfNotNull(
            trip?.let { "주행 ${"%.0f".format(it.distanceKm)}km · ${it.minutes / 60}시간 ${it.minutes % 60}분" },
            passengers?.let { "${it}명 탑승" },
            if (rainy) "비 온 날" else null,
        ),
        speech = speech, recommendations = vm.recommendations, lockStatus = vm::lockStatus,
    )
    val actions = ScreenActions(
        selectTab = vm::selectTab, select = vm::select, startCompose = vm::startCompose, cancelCompose = vm::cancelCompose,
        save = vm::save, dismissArrival = vm::dismissArrival, dismissRevisit = vm::dismissRevisit, readAloud = vm::readAloud,
        offerSend = vm::offerSend, sendToFamily = vm::sendToFamily, dismissSendOffer = vm::dismissSendOffer,
        addWaypoint = vm::addWaypoint, toggleLike = vm::toggleLike,
        startListening = vm.car::startListening, stopListening = vm.car::stopListening,
    )
    Box(Modifier.fillMaxSize()) {
        MainLayout(
            state, actions,
            map = { m -> CapsuleMap(capsules, here, ui.selectedId, { vm.select(it) }, m) },
            demo = (vm.car as? DemoCarSignals)?.let { d -> { DemoBar(d) } },
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
    }
}

@Composable
fun MainLayout(
    s: ScreenState,
    a: ScreenActions,
    map: @Composable (Modifier) -> Unit,
    demo: (@Composable () -> Unit)? = null,
) {
    val ui = s.ui
    Column(Modifier.fillMaxSize().background(Palette.Background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar(s)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── 지도 패널 ──
            Box(Modifier.weight(1.12f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Surface)) {
                map(Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapChip("캡슐 ${s.capsules.size}")
                    MapChip("다녀온 도시 ${s.capsules.map { it.city }.toSet().size}")
                }
                Legend(Modifier.align(Alignment.BottomStart).padding(14.dp))
                Text(
                    com.arok.timecapsule.ui.map.MAP_ATTRIBUTION, color = Palette.Faint, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 20.dp),
                )
                ui.revisit?.let { r ->
                    Box(Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 60.dp)) {
                        NoticeCard(
                            Icons.Outlined.History,
                            title = "${r.city}, ${r.memories.size}번 왔었어요",
                            body = r.memories.joinToString(" · ") { "${it.placeName}${it.companionList.takeIf { l -> l.isNotEmpty() }?.let { l -> " (${l.joinToString(", ")})" } ?: ""}" } +
                                if (s.driving) "\n정차하면 자세히 보여드릴게요." else "",
                            accent = Palette.Memory,
                            actionLabel = if (s.driving) null else "기억 보기",
                            onAction = { a.select(r.memories.first().id) },
                            onDismiss = a.dismissRevisit,
                        )
                    }
                }
            }
            // ── 오른쪽 패널 ──
            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Palette.Surface).padding(18.dp)) {
                val received = s.capsules.count { it.origin == CapsuleOrigin.RECEIVED }
                Segmented(
                    PanelTab.entries, ui.tab,
                    { t -> if (t == PanelTab.RECEIVED && received > 0) "${t.label} $received" else t.label },
                    a.selectTab,
                )
                Box(Modifier.weight(1f).padding(top = 16.dp)) { PanelContent(s, a) }
                demo?.invoke()
            }
        }
    }
    ui.sendOfferId?.let { id -> SendDialog(onSend = { to -> a.sendToFamily(id, to) }, onDismiss = a.dismissSendOffer) }
}

@Composable
private fun PanelContent(s: ScreenState, a: ScreenActions) {
    val ui = s.ui
    val selected = s.capsules.firstOrNull { it.id == ui.selectedId }
    when {
        ui.composing -> ComposePanel(
            placeLabel = s.destinationName ?: s.city ?: "현재 위치", passengerCount = s.passengers, carFacts = s.carFacts,
            driving = s.driving, speech = s.speech, onStartListening = a.startListening, onStopListening = a.stopListening,
            onCancel = a.cancelCompose, onSave = a.save,
        )
        selected != null -> CapsuleDetail(
            c = selected, lock = s.lockStatus(selected), justUnlocked = ui.justUnlockedId == selected.id,
            onBack = { a.select(null) }, onReadAloud = { a.readAloud(selected) }, onSend = { a.offerSend(selected.id) },
        )
        ui.tab == PanelTab.MINE -> CapsuleList(
            capsules = s.capsules.filter { it.origin == CapsuleOrigin.MINE },
            header = {
                when {
                    ui.arrivalPrompt -> NoticeCard(
                        Icons.Outlined.Place, "${s.destinationName ?: s.city ?: "목적지"}에 도착했어요",
                        "오늘을 캡슐로 남길까요? 날짜·시간·함께 탄 인원·주행 기록은 자동으로 붙어요.",
                        actionLabel = "캡슐 남기기", onAction = a.startCompose, onDismiss = a.dismissArrival,
                    )
                    s.driving -> NoticeCard(
                        Icons.Outlined.DirectionsCar, "운전 중이에요", "음성으로 먼저 남기고, 사진과 글은 정차한 뒤에 붙일 수 있어요.",
                        actionLabel = "음성으로 남기기", onAction = a.startCompose,
                    )
                    else -> NoticeCard(Icons.Outlined.Inventory2, "지금 이곳을 남겨 둘까요?", "사진·메모·음성과 함께 간 사람을 기록해요.", actionLabel = "캡슐 남기기", onAction = a.startCompose)
                }
            },
            onOpen = a.select, emptyText = "아직 남긴 캡슐이 없어요.",
        )
        ui.tab == PanelTab.RECEIVED -> CapsuleList(
            capsules = s.capsules.filter { it.origin == CapsuleOrigin.RECEIVED },
            header = { Text("가족이 보낸 캡슐은 바로 열어볼 수 있어요. 보낸 사람이 장소를 정했다면 그곳에서 열려요.", color = Palette.Muted) },
            onOpen = a.select, emptyText = "받은 캡슐이 없어요.",
        )
        ui.tab == PanelTab.RECOMMEND -> RecommendPanel(s.city, s.recommendations, a.addWaypoint)
        ui.tab == PanelTab.COMMUNITY -> CommunityPanel(ui.likedPosts, a.toggleLike, a.addWaypoint)
    }
}

@Composable
private fun TopBar(s: ScreenState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("추억 타임캡슐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(s.city ?: "위치 확인 중", color = Palette.Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.driving) Palette.Accent else Palette.Faint))
                Text(if (s.driving) "주행 중" else "정차", color = if (s.driving) Palette.Accent else Palette.Muted)
            }
            Text(if (s.connectedToCar) "Pleos 연결됨" else "데모 모드", color = Palette.Faint)
        }
    }
}

@Composable
private fun MapChip(text: String) = Text(
    text, style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Surface.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 8.dp),
)

@Composable
private fun Legend(modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Palette.Surface.copy(alpha = 0.92f)).padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(Palette.Memory to "내 기록", Palette.Family to "가족", Palette.Locked to "잠김", Palette.Accent to "현재 위치").forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                Text(l, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
        }
    }
}

/** 팀 결정: 저장 후 "가족에게 전달하시겠어요?" 정도만 제안 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SendDialog(onSend: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val picked = remember { mutableStateListOf<String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("가족에게 전달하시겠어요?") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SampleData.family.forEach { n ->
                    val on = n in picked
                    Tag(n, color = if (on) Palette.OnAccent else Palette.Text, background = if (on) Palette.Accent else Palette.Pressed,
                        onClick = { if (on) picked.remove(n) else picked.add(n) })
                }
            }
        },
        confirmButton = { PillButton("보내기", { onSend(picked.toList()) }, primary = true, enabled = picked.isNotEmpty()) },
        dismissButton = { PillButton("괜찮아요", onDismiss) },
    )
}

/** Pleos 차량이 아닐 때만 보이는 데모 바: 시나리오로 차량 신호를 흉내 낸다 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoBar(demo: DemoCarSignals) {
    val cur by demo.scenario.collectAsStateWithLifecycle()
    DemoBarContent(cur, demo::apply)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoBarContent(current: DemoCarSignals.Scenario, onPick: (DemoCarSignals.Scenario) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("데모 시나리오", color = Palette.Faint, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DemoCarSignals.Scenario.entries.forEach { sc ->
                val on = sc == current
                Tag(sc.label, color = if (on) Palette.Text else Palette.Muted, background = if (on) Palette.Pressed else Palette.Raised, onClick = { onPick(sc) })
            }
        }
    }
}
