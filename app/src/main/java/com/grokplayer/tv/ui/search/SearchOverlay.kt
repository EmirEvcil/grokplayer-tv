package com.grokplayer.tv.ui.search

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StreamStore
import com.grokplayer.tv.data.WatchStatus
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.settings.SettingsCategory
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock

sealed class SearchTarget {
    data class Video(val id: String) : SearchTarget()
    data class Stream(val id: String) : SearchTarget()
    data class Setting(val category: SettingsCategory, val key: String) : SearchTarget()
}

enum class SearchSource { All, Device, Online }

enum class SearchWatch { All, Watched, Watching, Unwatched }

data class SearchHit(
    val key: String,
    val title: String,
    val lines: List<String>,
    val target: SearchTarget,
    val video: LibraryVideo? = null,
)

fun settingCatalog(): List<SearchHit> = listOf(
    hit("Oynatma", "Ayarlar", SearchTarget.Setting(SettingsCategory.Playback, "category")),
    hit("Kaldığın yerden devam et", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "resume")),
    hit("Sonraki videoyu otomatik oynat", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "autonext")),
    hit("Küçük oynatıcı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "mini")),
    hit("İleri / geri sarma adımı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "seek")),
    hit("Varsayılan oynatma hızı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "speed")),
    hit("Kontrolleri gizleme süresi", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "hide")),
    hit("Açılış ekranı", "Oynatma", SearchTarget.Setting(SettingsCategory.Playback, "start")),
    hit("Görüntü", "Ayarlar", SearchTarget.Setting(SettingsCategory.Picture, "category")),
    hit("Görüntü sığdırma", "Görüntü", SearchTarget.Setting(SettingsCategory.Picture, "fit")),
    hit("Üst çözünürlük", "Görüntü", SearchTarget.Setting(SettingsCategory.Picture, "maxh")),
    hit("Ses", "Ayarlar", SearchTarget.Setting(SettingsCategory.Audio, "category")),
    hit("Tercih edilen ses dili", "Ses", SearchTarget.Setting(SettingsCategory.Audio, "alang")),
    hit("Altyazı", "Ayarlar", SearchTarget.Setting(SettingsCategory.Captions, "category")),
    hit("Altyazı boyutu", "Altyazı", SearchTarget.Setting(SettingsCategory.Captions, "capsize")),
    hit("İndirmeler", "Ayarlar", SearchTarget.Setting(SettingsCategory.Downloads, "category")),
    hit("Yedekler", "Ayarlar", SearchTarget.Setting(SettingsCategory.Backup, "category")),
    hit("İndirme kalitesi", "İndirmeler", SearchTarget.Setting(SettingsCategory.Downloads, "dlq")),
    hit("Cihazlar", "Ayarlar", SearchTarget.Setting(SettingsCategory.Devices, "category")),
    hit("Hakkında", "Ayarlar", SearchTarget.Setting(SettingsCategory.About, "category")),
)

private fun hit(title: String, place: String, target: SearchTarget) =
    SearchHit("set:${title}", title, listOf(place), target)

data class Searchable(
    val id: String,
    val title: String,
    val matchText: String,
    val place: String,
)

fun searchHits(
    query: String,
    videos: List<Searchable>,
    streams: List<Searchable>,
    watchOf: (String) -> WatchStatus?,
    source: SearchSource,
    watch: SearchWatch,
): List<SearchHit> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val device = if (source != SearchSource.Online) {
        videos.filter { it.matchText.lowercase().contains(q) }
            .filter { matchesWatch(watchOf(it.id), watch) }
            .take(12)
            .map { item ->
                SearchHit(
                    key = "v:${item.id}",
                    title = item.title,
                    lines = listOf(item.place, watchLabel(watchOf(item.id))),
                    target = SearchTarget.Video(item.id),
                )
            }
    } else {
        emptyList()
    }
    val online = if (source != SearchSource.Device) {
        streams.filter { it.matchText.lowercase().contains(q) }
            .filter { matchesWatch(watchOf(it.id), watch) }
            .take(8)
            .map { item ->
                SearchHit(
                    key = "s:${item.id}",
                    title = item.title,
                    lines = listOf(item.place, watchLabel(watchOf(item.id))),
                    target = SearchTarget.Stream(item.id),
                )
            }
    } else {
        emptyList()
    }
    val settings = if (source == SearchSource.All && watch == SearchWatch.All) {
        settingCatalog().filter { hit ->
            hit.title.lowercase().contains(q) || hit.lines.any { it.lowercase().contains(q) }
        }
    } else {
        emptyList()
    }
    return device + online + settings
}

private fun LibraryVideo.asSearchable(online: Boolean) = Searchable(
    id = id,
    title = title,
    matchText = "$title $format $uri",
    place = when {
        isLive -> "Canlı"
        online -> "Çevrimiçi"
        else -> "Cihazda · $sourceLabel"
    },
)

private fun matchesWatch(status: WatchStatus?, filter: SearchWatch): Boolean = when (filter) {
    SearchWatch.All -> true
    SearchWatch.Watched -> status == WatchStatus.Watched
    SearchWatch.Watching -> status == WatchStatus.Watching
    SearchWatch.Unwatched -> status == null || status == WatchStatus.Unwatched
}

private fun watchLabel(status: WatchStatus?): String = when (status) {
    WatchStatus.Watched -> "İzlendi"
    WatchStatus.Watching -> "İzleniyor"
    WatchStatus.Unwatched, null -> "İzlenmedi"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    library: LibraryStore,
    streams: StreamStore,
    query: String,
    onQuery: (String) -> Unit,
    source: SearchSource,
    onSource: (SearchSource) -> Unit,
    watch: SearchWatch,
    onWatch: (SearchWatch) -> Unit,
    focusKey: String?,
    onFocusKey: (String?) -> Unit,
    onOpen: (SearchTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    RememberFocusLock()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible
    val fieldFocus = remember { FocusRequester() }
    val sourceFocus = remember { FocusRequester() }
    val watchFocus = remember { FocusRequester() }
    val hitFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = hitFocus.getOrPut(key) { FocusRequester() }
    val streamVideos = streams.items.map { it.toVideo() }
    val hits = searchHits(
        query,
        library.videos.map { it.asSearchable(online = false) },
        streamVideos.map { it.asSearchable(online = true) },
        { id ->
            (library.videos + streamVideos).firstOrNull { it.id == id }?.let(library.watch::status)
        },
        source,
        watch,
    ).map { hit ->
        val video = when (val target = hit.target) {
            is SearchTarget.Video -> library.videos.firstOrNull { it.id == target.id }
            is SearchTarget.Stream -> streamVideos.firstOrNull { it.id == target.id }
            is SearchTarget.Setting -> null
        }
        hit.copy(video = video)
    }
    fun hideKeyboard() {
        keyboard?.hide()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
    fun closeImeOrDismiss() {
        if (imeVisible) {
            hideKeyboard()
            val key = focusKey?.takeIf { id -> hits.any { it.key == id } } ?: hits.firstOrNull()?.key
            if (key != null) runCatching { requester(key).requestFocus() }
            return
        }
        onDismiss()
    }
    InterceptBack { closeImeOrDismiss(); true }
    BackHandler { closeImeOrDismiss() }
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.72f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 48.dp)
                .width(760.dp)
                .heightIn(max = 640.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                .padding(16.dp)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Text(stringResource(R.string.search), style = GrokType.section, color = GrokWhite)
            val fieldInteraction = remember { MutableInteractionSource() }
            val fieldFocused = fieldInteraction.collectIsFocusedAsState().value
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = GrokWhite, fontSize = 16.sp),
                cursorBrush = SolidColor(GrokYellow),
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    .focusProperties { down = hits.firstOrNull()?.let { requester(it.key) } ?: sourceFocus }
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
                interactionSource = fieldInteraction,
            )
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isNotBlank() && hits.isEmpty()) {
                    Text(stringResource(R.string.search_empty), style = GrokType.cardMeta, color = GrokMuted)
                }
                hits.forEachIndexed { index, hit ->
                    val previous = if (index == 0) fieldFocus else requester(hits[index - 1].key)
                    val next = if (index == hits.lastIndex) sourceFocus else requester(hits[index + 1].key)
                    SearchCard(
                        hit = hit,
                        modifier = Modifier
                            .focusRequester(requester(hit.key))
                            .onFocusChanged { if (it.isFocused) onFocusKey(hit.key) }
                            .focusProperties {
                                up = previous
                                down = next
                            },
                        onClick = { onOpen(hit.target) },
                    )
                }
            }
            FilterLine(
                label = "Kaynak",
                options = listOf(SearchSource.All to "Tümü", SearchSource.Device to "Cihazda", SearchSource.Online to "Çevrimiçi"),
                selected = source,
                onSelect = onSource,
                first = sourceFocus,
                up = hits.lastOrNull()?.let { requester(it.key) } ?: fieldFocus,
                down = watchFocus,
                modifier = Modifier.padding(top = 12.dp),
            )
            FilterLine(
                label = "İzleme",
                options = listOf(
                    SearchWatch.All to "Tümü",
                    SearchWatch.Watched to "İzlendi",
                    SearchWatch.Watching to "İzleniyor",
                    SearchWatch.Unwatched to "İzlenmedi",
                ),
                selected = watch,
                onSelect = onWatch,
                first = watchFocus,
                up = sourceFocus,
                down = null,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    LaunchedEffect(focusKey) {
        val key = focusKey?.takeIf { id -> hits.any { it.key == id } }
        val target = if (key != null) requester(key) else fieldFocus
        repeat(8) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            kotlinx.coroutines.delay(40)
        }
    }
}

@Composable
private fun SearchCard(
    hit: SearchHit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val video = hit.video
    FocusableAction(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) GrokYellow.copy(alpha = 0.16f) else Color.Transparent, RoundedCornerShape(8.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (video != null) {
                VideoPoster(
                    uri = video.uri,
                    title = video.title,
                    focused = focused,
                    path = video.path,
                    format = video.format,
                    posterUrl = video.posterUrl,
                    durationMs = video.durationMs,
                    isLive = video.isLive,
                    enablePreview = focused,
                    originUrl = video.originUrl,
                    referer = video.referer,
                    userAgent = video.userAgent,
                    modifier = Modifier.width(168.dp).aspectRatio(16f / 9f),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    hit.title,
                    style = GrokType.button,
                    color = GrokWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                hit.lines.filter { it.isNotBlank() }.forEach { line ->
                    Text(line, style = GrokType.cardMeta, color = GrokMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun <T> FilterLine(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    first: FocusRequester,
    up: FocusRequester,
    down: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val extra = remember(options.size) { List(options.size) { FocusRequester() } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.width(72.dp))
        options.forEachIndexed { index, (value, name) ->
            val focus = if (index == 0) first else extra[index]
            FocusableAction(
                onClick = { onSelect(value) },
                modifier = Modifier
                    .focusRequester(focus)
                    .focusProperties {
                        this.up = up
                        if (down != null) this.down = down
                    },
                shape = RoundedCornerShape(16.dp),
            ) { focused ->
                val on = value == selected
                Text(
                    name,
                    style = GrokType.cardMeta,
                    color = if (focused) GrokInk else if (on) GrokWhite else GrokMuted,
                    modifier = Modifier
                        .background(if (focused) GrokYellow else if (on) GrokInk else Color.Transparent, RoundedCornerShape(16.dp))
                        .border(1.dp, if (focused || on) GrokYellow else GrokLine, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
