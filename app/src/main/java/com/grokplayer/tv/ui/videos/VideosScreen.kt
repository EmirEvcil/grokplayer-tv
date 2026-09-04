package com.grokplayer.tv.ui.videos

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.VideoSort
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FilterChip
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import kotlinx.coroutines.launch

private enum class VideoFilter { All, Internal, Usb }

@Composable
fun VideosScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    library: LibraryStore,
    onPlay: (List<LibraryVideo>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(VideoFilter.All) }
    var sort by remember { mutableStateOf(VideoSort.RecentlyAdded) }
    var sortOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }

    val treePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            library.addFolder(uri)
            scope.launch { library.refresh() }
        }
    }

    val videos = remember(library.videos, filter, sort) {
        library.videos
            .filter {
                when (filter) {
                    VideoFilter.All -> true
                    VideoFilter.Internal -> it.source == StorageSource.Internal
                    VideoFilter.Usb -> it.source == StorageSource.Usb
                }
            }
            .sortedWith(
                when (sort) {
                    VideoSort.RecentlyAdded -> compareByDescending { it.dateAdded }
                    VideoSort.Oldest -> compareBy { it.dateAdded }
                    VideoSort.Alphabetical -> compareBy { it.title.lowercase() }
                    VideoSort.Newest -> compareByDescending { it.lastModified }
                },
            )
    }
    val sources = library.videos.map { it.source }.toSet().size
    val tileFocus = remember(videos.size) { List(videos.size.coerceAtLeast(1)) { FocusRequester() } }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 100.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(stringResource(R.string.nav_videos), style = GrokType.pageTitle, color = GrokWhite)
                    Text(
                        text = if (videos.isEmpty()) {
                            stringResource(R.string.videos_empty_meta)
                        } else {
                            "${videos.size} video · $sources kaynak"
                        },
                        style = GrokType.heroMeta,
                        color = GrokMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        label = stringResource(R.string.add_folder),
                        icon = Icons.Outlined.CreateNewFolder,
                        onClick = { folderOpen = true },
                        modifier = Modifier.focusProperties { left = railFocus },
                    )
                    OutlineButton(
                        label = stringResource(sort.labelRes),
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        onClick = { sortOpen = true },
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(stringResource(R.string.filter_all), filter == VideoFilter.All, { filter = VideoFilter.All }, Modifier.focusProperties { left = railFocus })
                FilterChip(stringResource(R.string.filter_internal), filter == VideoFilter.Internal, { filter = VideoFilter.Internal })
                FilterChip(stringResource(R.string.filter_usb), filter == VideoFilter.Usb, { filter = VideoFilter.Usb })
            }

            if (videos.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_videos_title),
                    body = stringResource(R.string.empty_videos_body),
                    modifier = Modifier.focusRequester(firstFocus),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(videos, key = { _, item -> item.id }) { index, video ->
                        VideoTile(
                            video = video,
                            progress = library.progressFraction(video),
                            modifier = Modifier
                                .focusRequester(if (index == 0) firstFocus else tileFocus[index])
                                .focusProperties {
                                    left = if (index % 3 == 0) railFocus else FocusRequester.Default
                                },
                            onClick = { onPlay(videos, index) },
                        )
                    }
                }
            }
            if (videos.isEmpty()) Spacer(Modifier.weight(1f))
            HintBar(
                parts = listOf(stringResource(R.string.hint_play), stringResource(R.string.hint_options)),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (sortOpen) {
            SortMenu(
                selected = sort,
                onSelect = {
                    sort = it
                    sortOpen = false
                },
                onDismiss = { sortOpen = false },
            )
        }
        if (folderOpen) {
            FolderPicker(
                onPickTree = {
                    folderOpen = false
                    treePicker.launch(null)
                },
                onPickVolume = { uri ->
                    folderOpen = false
                    library.addFolder(uri)
                    scope.launch { library.refresh() }
                },
                onDismiss = { folderOpen = false },
            )
        }
    }
}

@Composable
private fun VideoTile(
    video: LibraryVideo,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableAction(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    ) { focused ->
        Column(Modifier.fillMaxWidth()) {
            VideoPoster(
                uri = video.uri,
                title = video.title,
                focused = focused,
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Text(video.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
            val source = if (video.source == StorageSource.Usb) "USB" else "Dahili"
            Text(
                text = "${video.durationMs.formatClock()} · ${video.format} · $source",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SortMenu(
    selected: VideoSort,
    onSelect: (VideoSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                .padding(top = 72.dp, end = 130.dp)
                .width(220.dp)
                .background(GrokSurface, RoundedCornerShape(10.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(8.dp),
        ) {
            VideoSort.entries.forEachIndexed { index, option ->
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                Text(
                    text = stringResource(option.labelRes),
                    style = GrokType.button,
                    color = if (focused || option == selected) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .background(
                            when {
                                focused -> GrokYellow
                                option == selected -> GrokYellow.copy(alpha = 0.35f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(interactionSource = interaction, indication = null) { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun FolderPicker(
    onPickTree: () -> Unit,
    onPickVolume: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val volumes = remember {
        val manager = context.getSystemService(StorageManager::class.java)
        manager?.storageVolumes.orEmpty().mapNotNull { volume ->
            val dir = if (Build.VERSION.SDK_INT >= 30) volume.directory else null
            if (dir != null && dir.exists()) {
                val label = volume.getDescription(context)
                Triple(label, volume.isRemovable, dir)
            } else {
                null
            }
        }
    }
    val first = remember { FocusRequester() }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.add_folder), style = GrokType.section, color = GrokWhite)
            Text(
                text = stringResource(R.string.add_folder_body),
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            volumes.forEachIndexed { index, (label, removable, dir) ->
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                Text(
                    text = if (removable) "$label · USB" else label,
                    style = GrokType.button,
                    color = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .background(if (focused) GrokYellow else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
                        .clickable(interactionSource = interaction, indication = null) {
                            onPickVolume(Uri.fromFile(dir))
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            OutlineButton(
                label = stringResource(R.string.browse_storage),
                onClick = onPickTree,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val VideoSort.labelRes: Int
    get() = when (this) {
        VideoSort.RecentlyAdded -> R.string.sort_recent
        VideoSort.Oldest -> R.string.sort_oldest
        VideoSort.Alphabetical -> R.string.sort_az
        VideoSort.Newest -> R.string.sort_newest
    }
