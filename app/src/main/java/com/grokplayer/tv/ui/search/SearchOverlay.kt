package com.grokplayer.tv.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.StreamStore
import com.grokplayer.tv.ui.settings.SettingsCategory
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.RememberFocusLock

sealed class SearchTarget {
    data class Video(val id: String) : SearchTarget()
    data class Stream(val id: String) : SearchTarget()
    data class Setting(val category: SettingsCategory, val key: String) : SearchTarget()
}

data class SearchHit(
    val title: String,
    val subtitle: String,
    val target: SearchTarget,
)

fun settingCatalog(): List<SearchHit> = listOf(
    SearchHit("Oynatma", "Ayarlar", SearchTarget.Setting(SettingsCategory.Playback, "category")),
    SearchHit("Kaldığın yerden devam et", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "resume")),
    SearchHit("Sonraki videoyu otomatik oynat", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "autonext")),
    SearchHit("İleri / geri sarma adımı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "seek")),
    SearchHit("Varsayılan oynatma hızı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "speed")),
    SearchHit("Kontrolleri gizleme süresi", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "hide")),
    SearchHit("Açılış ekranı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "start")),
    SearchHit("Görüntü", "Ayarlar", SearchTarget.Setting(SettingsCategory.Picture, "category")),
    SearchHit("Görüntü sığdırma", "Görüntü", SearchTarget.Setting(SettingsCategory.Picture, "fit")),
    SearchHit("Üst çözünürlük", "Görüntü", SearchTarget.Setting(SettingsCategory.Picture, "maxh")),
    SearchHit("Ses", "Ayarlar", SearchTarget.Setting(SettingsCategory.Audio, "category")),
    SearchHit("Tercih edilen ses dili", "Ses", SearchTarget.Setting(SettingsCategory.Audio, "alang")),
    SearchHit("Altyazı", "Ayarlar", SearchTarget.Setting(SettingsCategory.Captions, "category")),
    SearchHit("Altyazı boyutu", "Altyazı", SearchTarget.Setting(SettingsCategory.Captions, "capsize")),
    SearchHit("İndirmeler", "Ayarlar", SearchTarget.Setting(SettingsCategory.Downloads, "category")),
    SearchHit("İndirme kalitesi", "İndirmeler", SearchTarget.Setting(SettingsCategory.Downloads, "dlq")),
    SearchHit("Cihazlar", "Ayarlar", SearchTarget.Setting(SettingsCategory.Devices, "category")),
    SearchHit("Hakkında", "Ayarlar", SearchTarget.Setting(SettingsCategory.About, "category")),
)

@Composable
fun SearchOverlay(
    library: LibraryStore,
    streams: StreamStore,
    onOpen: (SearchTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    RememberFocusLock()
    val fieldFocus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val hits = remember(query, library.videos, streams.items) {
        val q = query.trim().lowercase()
        if (q.length < 1) emptyList()
        else {
            val videos = library.videos
                .filter { it.title.lowercase().contains(q) || it.format.lowercase().contains(q) }
                .take(8)
                .map { SearchHit(it.title, "${it.format} · ${it.sourceLabel}", SearchTarget.Video(it.id)) }
            val streamHits = streams.items
                .filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
                .take(6)
                .map { SearchHit(it.title, it.kind.name, SearchTarget.Stream(it.id)) }
            val settings = settingCatalog().filter {
                it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q)
            }
            videos + streamHits + settings
        }
    }
    com.grokplayer.tv.ui.theme.InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.72f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 72.dp)
                .width(520.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.search), style = GrokType.section, color = GrokWhite)
            val fieldInteraction = remember { MutableInteractionSource() }
            val fieldFocused = fieldInteraction.collectIsFocusedAsState().value
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = GrokWhite, fontSize = 16.sp),
                cursorBrush = SolidColor(GrokYellow),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .background(GrokInk, RoundedCornerShape(8.dp))
                    .then(
                        if (fieldFocused) Modifier.border(1.5.dp, GrokYellow, RoundedCornerShape(8.dp))
                        else Modifier.border(1.dp, GrokMuted.copy(0.3f), RoundedCornerShape(8.dp)),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    if (query.isBlank()) {
                        Text(stringResource(R.string.search_hint), style = GrokType.heroMeta, color = GrokMuted)
                    }
                    inner()
                },
            )
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (query.isNotBlank() && hits.isEmpty()) {
                    Text(stringResource(R.string.search_empty), style = GrokType.cardMeta, color = GrokMuted)
                }
                hits.forEach { hit ->
                    val interaction = remember { MutableInteractionSource() }
                    val focused = interaction.collectIsFocusedAsState().value
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (focused) GrokYellow else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable(interactionSource = interaction, indication = null) { onOpen(hit.target) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(hit.title, style = GrokType.button, color = if (focused) GrokInk else GrokWhite)
                        Text(hit.subtitle, style = GrokType.cardMeta, color = if (focused) GrokInk.copy(0.7f) else GrokMuted)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
}
