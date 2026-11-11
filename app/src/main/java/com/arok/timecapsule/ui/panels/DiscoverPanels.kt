package com.arok.timecapsule.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLocationAlt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.arok.timecapsule.data.SampleData
import com.arok.timecapsule.data.SampleData.PlaceKind
import com.arok.timecapsule.domain.LatLng
import com.arok.timecapsule.domain.Recommender
import com.arok.timecapsule.ui.components.PillButton
import com.arok.timecapsule.ui.components.ScenePhoto
import com.arok.timecapsule.ui.components.Segmented
import com.arok.timecapsule.ui.components.Surface2
import com.arok.timecapsule.ui.theme.Palette

private val reasons = mapOf(
    PlaceKind.HOTPLACE to "많은 사람이 목적지로 고른 곳 중 가까운 순",
    PlaceKind.PARKING to "빈자리가 있는 곳 중 가까운 순",
    PlaceKind.DRIVE_THRU to "가는 시간 + 대기 시간이 짧은 순",
)

/** +1 주변 추천: 현지 핫플 · 주차장 · 드라이브스루 → 경유지 추가. 정렬 규칙은 [Recommender] */
@Composable
fun RecommendPanel(city: String?, items: List<Recommender.Scored>, onAddWaypoint: (String, LatLng) -> Unit) {
    var kind by remember { mutableStateOf(PlaceKind.HOTPLACE) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${city ?: "현재 위치"} 주변 ${Recommender.RADIUS_KM.toInt()}km", style = MaterialTheme.typography.titleLarge)
                Segmented(PlaceKind.entries, kind, { it.label }, { kind = it })
                Text("${reasons[kind]} · 프로토타입 예시 데이터", color = Palette.Faint, style = MaterialTheme.typography.labelMedium)
            }
        }
        val shown = items.filter { it.place.kind == kind }
        if (shown.isEmpty()) item { Text("이 근처에는 아직 추천할 곳이 없어요.", color = Palette.Muted) }
        items(shown, key = { it.place.name }) { r ->
            Surface2 {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Pressed), contentAlignment = Alignment.Center) {
                        Icon(
                            when (r.place.kind) {
                                PlaceKind.HOTPLACE -> Icons.Outlined.Whatshot
                                PlaceKind.PARKING -> Icons.Outlined.LocalParking
                                PlaceKind.DRIVE_THRU -> Icons.Outlined.LocalCafe
                            },
                            null, tint = Palette.Text,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.place.name, style = MaterialTheme.typography.titleMedium)
                        Text(r.detail, color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                    PillButton("경유지", { onAddWaypoint(r.place.name, r.place.position) }, icon = Icons.Outlined.AddLocationAlt)
                }
            }
        }
    }
}

/** +5 커뮤니티: 공개한 캡슐만 보이고 공감만 가능 (댓글 없음) */
@Composable
fun CommunityPanel(liked: Set<Int>, onLike: (Int) -> Unit, onAddWaypoint: (String, LatLng) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("다른 사람들이 남긴 장소", style = MaterialTheme.typography.titleLarge)
                Text("공개된 캡슐만 보여요. 공감을 남기거나 바로 다음 목적지로 정할 수 있어요.", color = Palette.Muted)
            }
        }
        itemsIndexed(SampleData.community) { i, p ->
            Surface2 {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScenePhoto(p.photoScene, Modifier.size(112.dp, 80.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text("${p.placeName} · ${p.author}", color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val on = i in liked
                            Row(Modifier.clip(RoundedCornerShape(50)).clickable { onLike(i) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (on) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (on) Palette.Family else Palette.Muted, modifier = Modifier.size(20.dp))
                                Text(" 공감 ${p.likes + if (on) 1 else 0}", color = if (on) Palette.Family else Palette.Muted)
                            }
                            Row(Modifier.clip(RoundedCornerShape(50)).clickable { onAddWaypoint(p.placeName, p.position) }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AddLocationAlt, null, tint = Palette.Accent, modifier = Modifier.size(20.dp))
                                Text(" 경유지로", color = Palette.Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
