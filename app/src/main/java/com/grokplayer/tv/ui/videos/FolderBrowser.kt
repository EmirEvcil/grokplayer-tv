package com.grokplayer.tv.ui.videos

import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import java.io.File

data class StorageVolumeItem(
    val label: String,
    val source: StorageSource,
    val directory: File,
    val description: String,
)

@Composable
fun FolderBrowser(
    onPick: (File) -> Unit,
    onDismiss: () -> Unit,
    restoreFocus: FocusRequester? = null,
) {
    val context = LocalContext.current
    val volumes = remember { listVolumes(context) }
    var current by remember { mutableStateOf<File?>(null) }
    val first = remember { FocusRequester() }
    RememberFocusLock()

    var lastBackAt by remember { mutableStateOf(0L) }
    fun close() {
        runCatching { restoreFocus?.requestFocus() }
        onDismiss()
    }
    fun goBack() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastBackAt < 280L) return
        lastBackAt = now
        val parent = current?.parentFile
        val atVolumeRoot = current != null && volumes.any { it.directory == current }
        when {
            current == null -> close()
            atVolumeRoot -> current = null
            parent != null -> current = parent
            else -> close()
        }
    }

    InterceptBack { goBack(); true }
    BackHandler { goBack() }

    val folders = remember(current) {
        current?.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith('.') }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }
    val videoCount = remember(current) {
        current?.listFiles()?.count { it.isFile && LibraryStore.isVideoName(it.name) } ?: 0
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.62f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Back || event.key == Key.Escape)
                ) {
                    goBack()
                    true
                } else {
                    false
                }
            }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .heightIn(max = 520.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.add_folder), style = GrokType.section, color = GrokWhite)
            Text(
                text = if (current == null) {
                    stringResource(R.string.add_folder_body)
                } else {
                    displayPath(current!!, volumes)
                },
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (current == null) {
                    if (volumes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_storage),
                            style = GrokType.cardMeta,
                            color = GrokMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    volumes.forEachIndexed { index, volume ->
                        BrowserRow(
                            title = volume.label,
                            subtitle = volume.description,
                            modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                            onClick = { current = volume.directory },
                        )
                    }
                } else {
                    val parent = current?.parentFile
                    BrowserRow(
                        title = stringResource(R.string.folder_up),
                        subtitle = parent?.let { displayPath(it, volumes) },
                        modifier = Modifier.focusRequester(first),
                        onClick = {
                            if (volumes.any { it.directory == current }) {
                                current = null
                            } else {
                                current = parent
                            }
                        },
                    )
                    if (folders.isEmpty()) {
                        Text(
                            text = stringResource(R.string.folder_empty),
                            style = GrokType.cardMeta,
                            color = GrokMuted,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                    folders.forEach { folder ->
                        val childVideos = folder.listFiles()?.count { it.isFile && LibraryStore.isVideoName(it.name) } ?: 0
                        BrowserRow(
                            title = folder.name,
                            subtitle = if (childVideos > 0) "$childVideos video" else stringResource(R.string.folder_dir),
                            onClick = { current = folder },
                        )
                    }
                }
            }

            if (current != null) {
                Text(
                    text = if (videoCount > 0) {
                        "$videoCount video · ${stringResource(R.string.folder_add_hint)}"
                    } else {
                        stringResource(R.string.folder_add_hint)
                    },
                    style = GrokType.cardMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
                OutlineButton(
                    label = stringResource(R.string.folder_add_here),
                    onClick = { current?.let { file ->
                        runCatching { restoreFocus?.requestFocus() }
                        onPick(file)
                    } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    LaunchedEffect(current) { runCatching { first.requestFocus() } }
}

@Composable
private fun BrowserRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .background(if (focused) GrokYellow else androidx.compose.ui.graphics.Color.Transparent, shape)
            .then(if (focused) Modifier.border(1.5.dp, GrokYellow, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, style = GrokType.button, color = if (focused) GrokInk else GrokWhite)
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = GrokType.cardMeta,
                color = if (focused) GrokInk.copy(alpha = 0.7f) else GrokMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun listVolumes(context: android.content.Context): List<StorageVolumeItem> {
    val manager = context.getSystemService(StorageManager::class.java)
    val fromManager = manager?.storageVolumes.orEmpty().mapNotNull { volume ->
        val rawDir: File? = if (Build.VERSION.SDK_INT >= 30) {
            volume.directory
        } else if (volume.isPrimary) {
            Environment.getExternalStorageDirectory()
        } else {
            null
        }
        val dir = rawDir ?: return@mapNotNull null
        if (!dir.exists()) return@mapNotNull null
        val removable = volume.isRemovable && !volume.isPrimary
        val source = if (removable) StorageSource.Usb else StorageSource.Internal
        val raw = volume.getDescription(context)
        StorageVolumeItem(
            label = if (source == StorageSource.Usb) {
                context.getString(R.string.volume_usb, raw)
            } else {
                context.getString(R.string.volume_internal)
            },
            source = source,
            directory = dir,
            description = if (source == StorageSource.Usb) {
                context.getString(R.string.volume_usb_desc)
            } else {
                context.getString(R.string.volume_internal_desc)
            },
        )
    }
    if (fromManager.isNotEmpty()) return fromManager

    val fallback = mutableListOf<StorageVolumeItem>()
    val internal = Environment.getExternalStorageDirectory()
    if (internal.exists()) {
        fallback += StorageVolumeItem(
            label = context.getString(R.string.volume_internal),
            source = StorageSource.Internal,
            directory = internal,
            description = context.getString(R.string.volume_internal_desc),
        )
    }
    File("/storage").listFiles()?.forEach { child ->
        if (child.isDirectory && child.name.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
            fallback += StorageVolumeItem(
                label = context.getString(R.string.volume_usb, child.name),
                source = StorageSource.Usb,
                directory = child,
                description = context.getString(R.string.volume_usb_desc),
            )
        }
    }
    return fallback
}

private fun displayPath(file: File, volumes: List<StorageVolumeItem>): String {
    val match = volumes.firstOrNull { file.path.startsWith(it.directory.path) }
    return if (match != null) {
        val rest = file.path.removePrefix(match.directory.path).trim('/')
        if (rest.isBlank()) match.label else "${match.label}/$rest"
    } else {
        file.path
    }
}
