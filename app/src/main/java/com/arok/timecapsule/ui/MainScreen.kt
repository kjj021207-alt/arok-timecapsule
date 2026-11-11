package com.arok.timecapsule.ui

import androidx.compose.foundation.background
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.Color
import com.arok.timecapsule.domain.Recommender
import com.arok.timecapsule.ui.components.formatDay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
    val recommendations: List<Recommender.Scored>,
    val lockStatus: (CapsuleEntity) -> CapsuleRules.UnlockCheck,
) {
    /** 필터에 맞는 캡슐 (목록과 지도 핀에 같이 쓴다) */
    val shownCapsules: List<CapsuleEntity> get() = capsules.filter { MainViewModel.matches(it, ui.cityFilter, ui.yearFilter) }
}

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
    val setCityFilter: (String?) -> Unit = {},
    val setYearFilter: (Int?) -> Unit = {},
    val showCities: (Boolean) -> Unit = {},
    val pickCity: (String) -> Unit = {},
    val pickPhoto: () -> Unit = {},
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

    // 기기 사진 고르기 (Android 사진 선택기: 사진 접근 권한 없이 고른 사진만 앱에 전달된다)
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> vm.onPhotoPicked(uri?.toString()) }

    val state = ScreenState(
        ui = ui, capsules = capsules, here = here, city = city,
        driving = drive == DriveState.DRIVING, connectedToCar = vm.car.isConnectedToCar,
        destinationName = destination?.name, passengers = passengers,
        carFacts = listOfNotNull(
            trip?.let { "주행 ${"%.0f".format(it.distanceKm)}km · ${it.minutes / 60}시간 ${it.minutes % 60}분" },
            passengers?.let { "${it}명 탑승" },
            if (rainy) "비 온 날" else null,
        ),
        speech = speech, recommendations = remember(here) { vm.recommendations() }, lockStatus = vm::lockStatus,
    )
    val actions = ScreenActions(
        selectTab = vm::selectTab, select = vm::select, startCompose = vm::startCompose, cancelCompose = vm::cancelCompose,
        save = vm::save, dismissArrival = vm::dismissArrival, dismissRevisit = vm::dismissRevisit, readAloud = vm::readAloud,
        offerSend = vm::offerSend, sendToFamily = vm::sendToFamily, dismissSendOffer = vm::dismissSendOffer,
        addWaypoint = vm::addWaypoint, toggleLike = vm::toggleLike,
        startListening = vm.car::startListening, stopListening = vm.car::stopListening,
        setCityFilter = vm::setCityFilter, setYearFilter = vm::setYearFilter, showCities = vm::showCities, pickCity = vm::pickCity,
        pickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    )
    Box(Modifier.fillMaxSize()) {
        MainLayout(
            state, actions,
            map = { m -> CapsuleMap(state.shownCapsules, here, ui.selectedId, { vm.select(it) }, m) },
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
    // 화면 비율에 따라 배치를 바꾼다.
    // 가로(차량 화면): 왼쪽 지도 + 오른쪽 패널 / 세로(폰, Pleos 분할 화면의 좁은 칸): 위 지도 + 아래 패널
    BoxWithConstraints(Modifier.fillMaxSize().background(Palette.Background)) {
        val portrait = maxWidth < maxHeight || maxWidth < 760.dp
        val gap = if (portrait) 8.dp else 12.dp
        Column(Modifier.fillMaxSize().padding(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            TopBar(s, compact = portrait)
            if (portrait) {
                MapPanel(s, a, map, compact = true, modifier = Modifier.fillMaxWidth().weight(0.42f))
                SidePanel(s, a, demo, compact = true, modifier = Modifier.fillMaxWidth().weight(0.58f))
            } else {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    MapPanel(s, a, map, compact = false, modifier = Modifier.weight(1.12f).fillMaxHeight())
                    SidePanel(s, a, demo, compact = false, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
    ui.sendOfferId?.let { id -> SendDialog(onSend = { to -> a.sendToFamily(id, to) }, onDismiss = a.dismissSendOffer) }
    if (ui.showCities) CitiesDialog(MainViewModel.visitedCities(s.capsules), onPick = a.pickCity, onDismiss = { a.showCities(false) })
}

@Composable
private fun MapPanel(s: ScreenState, a: ScreenActions, map: @Composable (Modifier) -> Unit, compact: Boolean, modifier: Modifier) {
    val pad = if (compact) 10.dp else 14.dp
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Surface)) {
        map(Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopStart).padding(pad), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapChip("캡슐 ${s.capsules.size}")
            MapChip("다녀온 도시 ${s.capsules.map { it.city }.toSet().size}  ›", onClick = { a.showCities(true) })
        }
        if (!compact) Legend(Modifier.align(Alignment.BottomStart).padding(pad))
        Text(
            com.arok.timecapsule.ui.map.MAP_ATTRIBUTION, color = Palette.Faint, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = if (compact) 8.dp else 20.dp),
        )
        s.ui.revisit?.let { r ->
            Box(Modifier.align(Alignment.BottomCenter).padding(start = pad, end = pad, bottom = if (compact) 28.dp else 60.dp)) {
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
}

@Composable
private fun SidePanel(s: ScreenState, a: ScreenActions, demo: (@Composable () -> Unit)?, compact: Boolean, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Palette.Surface).padding(if (compact) 14.dp else 18.dp)) {
        val received = s.capsules.count { it.origin == CapsuleOrigin.RECEIVED }
        Segmented(
            PanelTab.entries, s.ui.tab,
            { t -> if (t == PanelTab.RECEIVED && received > 0) "${t.label} $received" else t.label },
            a.selectTab,
        )
        Box(Modifier.weight(1f).padding(top = if (compact) 12.dp else 16.dp)) { PanelContent(s, a) }
        demo?.invoke()
    }
}

@Composable
private fun PanelContent(s: ScreenState, a: ScreenActions) {
    val ui = s.ui
    val selected = s.capsules.firstOrNull { it.id == ui.selectedId }
    when {
        ui.composing -> ComposePanel(
            placeLabel = s.destinationName ?: s.city ?: "현재 위치", passengerCount = s.passengers, carFacts = s.carFacts,
            driving = s.driving, speech = s.speech, pickedPhoto = ui.pickedPhoto,
            knownCompanions = MainViewModel.knownCompanions(s.capsules), onPickPhoto = a.pickPhoto,
            onStartListening = a.startListening, onStopListening = a.stopListening,
            onCancel = a.cancelCompose, onSave = a.save,
        )
        selected != null -> CapsuleDetail(
            c = selected, lock = s.lockStatus(selected), justUnlocked = ui.justUnlockedId == selected.id,
            onBack = { a.select(null) }, onReadAloud = { a.readAloud(selected) }, onSend = { a.offerSend(selected.id) },
        )
        ui.tab == PanelTab.MINE -> CapsuleList(
            capsules = s.shownCapsules.filter { it.origin == CapsuleOrigin.MINE },
            header = {
                FilterBar(s, a)
                if (ui.cityFilter == null && ui.yearFilter == null) when {
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
            onOpen = a.select, emptyText = if (ui.cityFilter == null && ui.yearFilter == null) "아직 남긴 캡슐이 없어요." else "조건에 맞는 캡슐이 없어요.",
        )
        ui.tab == PanelTab.RECEIVED -> CapsuleList(
            capsules = s.shownCapsules.filter { it.origin == CapsuleOrigin.RECEIVED },
            header = {
                FilterBar(s, a)
                if (ui.cityFilter == null && ui.yearFilter == null) Text("가족이 보낸 캡슐은 바로 열어볼 수 있어요. 보낸 사람이 장소를 정했다면 그곳에서 열려요.", color = Palette.Muted)
            },
            onOpen = a.select, emptyText = if (ui.cityFilter == null && ui.yearFilter == null) "받은 캡슐이 없어요." else "조건에 맞는 캡슐이 없어요.",
        )
        ui.tab == PanelTab.RECOMMEND -> RecommendPanel(s.city, s.recommendations, a.addWaypoint)
        ui.tab == PanelTab.COMMUNITY -> CommunityPanel(ui.likedPosts, a.toggleLike, a.addWaypoint)
    }
}

@Composable
private fun TopBar(s: ScreenState, compact: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("추억 타임캡슐", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
            Text(s.city ?: "위치 확인 중", color = Palette.Muted, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (s.driving) Palette.Accent else Palette.Faint))
                Text(if (s.driving) "주행 중" else "정차", color = if (s.driving) Palette.Accent else Palette.Muted, maxLines = 1)
            }
            // 좁은 화면에서는 연결 상태 글자를 줄여 제목이 밀리지 않게 한다
            Text(if (s.connectedToCar) (if (compact) "Pleos" else "Pleos 연결됨") else (if (compact) "데모" else "데모 모드"), color = Palette.Faint, maxLines = 1)
        }
    }
}

@Composable
private fun MapChip(text: String, onClick: (() -> Unit)? = null) = Text(
    text, style = MaterialTheme.typography.labelLarge,
    modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(Palette.Surface.copy(alpha = 0.92f))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 8.dp),
)

/**
 * 필터 두 줄: 다녀온 도시 / 캡슐을 남긴 해. 글자 입력 없이 누르기만 하면 돼서 운전 중에도 쓸 수 있다.
 * 같은 칩을 다시 누르거나 "전체"를 누르면 해제.
 */
@Composable
private fun FilterBar(s: ScreenState, a: ScreenActions) {
    val cities = MainViewModel.visitedCities(s.capsules).map { it.city }
    val years = MainViewModel.years(s.capsules)
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterRow("도시", listOf<String?>(null) + cities, s.ui.cityFilter, { it ?: "전체" }, a.setCityFilter)
        FilterRow("날짜", listOf<Int?>(null) + years, s.ui.yearFilter, { it?.let { y -> "${y}년" } ?: "전체" }, a.setYearFilter)
    }
}

@Composable
private fun <T> FilterRow(label: String, options: List<T?>, selected: T?, text: (T?) -> String, onPick: (T?) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Faint, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(40.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o ->
                val on = o == selected
                Tag(
                    text(o),
                    color = if (on) Palette.OnAccent else Palette.Text,
                    background = if (on) Palette.Accent else Palette.Raised,
                    onClick = { onPick(if (on && o != null) null else o) },
                )
            }
        }
    }
}

/** 다녀온 도시 목록: 최근에 간 순. 도시를 누르면 그 도시 캡슐만 보여준다. */
@Composable
private fun CitiesDialog(cities: List<VisitedCity>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.Surface,
        title = { Text("다녀온 도시 ${cities.size}곳") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(cities, key = { it.city }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Raised)
                            .clickable { onPick(c.city) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Place, null, tint = Palette.Memory, modifier = Modifier.size(20.dp))
                        Text(c.city, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text("캡슐 ${c.count} · ${formatDay(c.lastVisit)}", color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = { PillButton("닫기", onDismiss) },
    )
}

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
