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
import com.arok.timecapsule.data.SampleData.Recommendation
import com.arok.timecapsule.domain.LatLng
import com.arok.timecapsule.ui.components.PillButton
import com.arok.timecapsule.ui.components.ScenePhoto
import com.arok.timecapsule.ui.components.Segmented
import com.arok.timecapsule.ui.components.Surface2
import com.arok.timecapsule.ui.theme.Palette

/** +1 주변 추천: 현지 핫플(많은 사람이 목적지로 고른 곳) · 주차장 · 드라이브스루 → 경유지 추가 */
@Composable
fun RecommendPanel(city: String?, items: List<Recommendation>, onAddWaypoint: (String, LatLng) -> Unit) {
    var kind by remember { mutableStateOf(Recommendation.Kind.HOTPLACE) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${city ?: "현재 위치"} 주변", style = MaterialTheme.typography.titleLarge)
                Segmented(Recommendation.Kind.entries, kind, { it.label }, { kind = it })
            }
        }
        val shown = items.filter { it.kind == kind }
        if (shown.isEmpty()) item { Text("이 근처에는 아직 추천이 없어요.", color = Palette.Muted) }
        itemsIndexed(shown) { _, r ->
            Surface2 {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Pressed), contentAlignment = Alignment.Center) {
                        Icon(
                            when (r.kind) {
                                Recommendation.Kind.HOTPLACE -> Icons.Outlined.Whatshot
                                Recommendation.Kind.PARKING -> Icons.Outlined.LocalParking
                                Recommendation.Kind.DRIVE_THRU -> Icons.Outlined.LocalCafe
                            },
                            null, tint = Palette.Text,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(r.detail, color = Palette.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                    PillButton("경유지", { onAddWaypoint(r.name, r.position) }, icon = Icons.Outlined.AddLocationAlt)
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
