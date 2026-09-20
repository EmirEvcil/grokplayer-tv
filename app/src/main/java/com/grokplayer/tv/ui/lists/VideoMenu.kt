package com.grokplayer.tv.ui.lists

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.CollectionStore
import com.grokplayer.tv.data.FolderPlaylist
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.ImeBack
import com.grokplayer.tv.data.MenuFocus
import com.grokplayer.tv.data.NestedMenu
import com.grokplayer.tv.data.PlaylistEntry
import com.grokplayer.tv.data.PlaylistStore
import com.grokplayer.tv.data.WatchFeedback
import com.grokplayer.tv.data.WatchStatus
import com.grokplayer.tv.data.WatchStore
import com.grokplayer.tv.data.isVod
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.grokplayer.tv.ui.components.ModalAction
import com.grokplayer.tv.ui.components.ModalMenu
import com.grokplayer.tv.ui.components.VideoDetailsBody
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

private enum class VideoMenuPage {
    Root,
    Details,
    PickPlaylist,
    PickCollection,
    PickPlaylistForNewCollection,
    NamePlaylist,
    NamePlaylistThenCollection,
    NameCollection,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoMenuHost(
    video: LibraryVideo,
    extraActions: List<ModalAction>,
    trailingActions: List<ModalAction> = emptyList(),
    onDismiss: () -> Unit,
    meta: String? = null,
    width: androidx.compose.ui.unit.Dp = 380.dp,
    startIndex: Int = 0,
    playlists: PlaylistStore? = null,
    collections: CollectionStore? = null,
    onNotice: (String) -> Unit = {},
    showDetails: Boolean = true,
    showAddToList: Boolean = video.isVod(),
    moveTargets: List<Pair<String, String>>? = null,
    onMoveTo: (String) -> Unit = {},
    onCreateAndMove: (String) -> Unit = {},
    watch: WatchStore? = null,
) {
    var stack by remember(video.id) { mutableStateOf(listOf(VideoMenuPage.Root)) }
    var targetPlaylist by remember(video.id) { mutableStateOf<FolderPlaylist?>(null) }
    var draftName by remember(video.id) { mutableStateOf("") }
    var lastIndex by remember(video.id) { mutableStateOf(mapOf<VideoMenuPage, Int>()) }
    val nameFocus = remember { FocusRequester() }
    val page = stack.lastOrNull() ?: VideoMenuPage.Root
    val naming = page == VideoMenuPage.NamePlaylist ||
        page == VideoMenuPage.NamePlaylistThenCollection ||
        page == VideoMenuPage.NameCollection
    val imeVisible = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current
    var hidIme by remember(page) { mutableStateOf(false) }
    var imeWasOpen by remember(page) { mutableStateOf(naming) }
    LaunchedEffect(imeVisible, page) {
        if (imeVisible) {
            imeWasOpen = true
            hidIme = false
        }
    }
    fun hideIme() {
        keyboard?.hide()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun push(next: VideoMenuPage) {
        stack = stack + next
        if (next == VideoMenuPage.NamePlaylist ||
            next == VideoMenuPage.NamePlaylistThenCollection ||
            next == VideoMenuPage.NameCollection
        ) {
            draftName = ""
        }
    }

    fun back() {
        val next = NestedMenu.pop(stack)
        if (next.isEmpty()) onDismiss() else stack = next
    }

    fun handleBack() {
        if (naming && ImeBack.shouldHide(imeVisible || imeWasOpen, hidIme)) {
            hideIme()
            hidIme = true
            imeWasOpen = false
            return
        }
        back()
    }

    fun finish(message: String) {
        onNotice(message)
        onDismiss()
    }

    fun putInCollection(playlistId: String, collectionId: String) {
        val store = playlists ?: return
        store.addVideo(playlistId, video)
        collections?.assign(PlaylistEntry.listKey(video), collectionId)
        finish("Koleksiyona eklendi")
    }

    val geri = ModalAction("Geri", id = "back", icon = Icons.AutoMirrored.Outlined.ArrowBack) { back() }

    val title: String
    val pageMeta: String?
    val header: (@Composable (FocusRequester) -> Unit)?
    val actions: List<ModalAction>
    val focusHeader: Boolean
    val headerFocus: FocusRequester?

    when (page) {
        VideoMenuPage.Root -> {
            title = video.title
            pageMeta = meta
            header = null
            focusHeader = false
            headerFocus = null
            actions = buildList {
                addAll(extraActions)
                if (showDetails) {
                    add(ModalAction("Ayrıntılar", id = "details", icon = Icons.Outlined.Info) { push(VideoMenuPage.Details) })
                }
                if (showAddToList) {
                    add(ModalAction(stringResource(R.string.add_to_playlist), id = "playlist", icon = Icons.AutoMirrored.Outlined.PlaylistAdd) { push(VideoMenuPage.PickPlaylist) })
                    add(ModalAction(stringResource(R.string.add_to_collection), id = "collection", icon = Icons.Outlined.Folder) { push(VideoMenuPage.PickCollection) })
                }
                if (moveTargets != null) {
                    add(ModalAction("Koleksiyona taşı", id = "move", icon = Icons.AutoMirrored.Outlined.DriveFileMove) { push(VideoMenuPage.PickCollection) })
                }
                if (watch != null && video.isVod()) {
                    val status = watch.status(video)
                    add(
                        if (status == WatchStatus.Watched) {
                            ModalAction(stringResource(R.string.mark_unwatched), id = "watch", icon = Icons.Outlined.VisibilityOff) { watch.markUnwatched(video) }
                        } else {
                            ModalAction(stringResource(R.string.mark_watched), id = "watch", icon = Icons.Outlined.Visibility) { watch.markWatched(video) }
                        },
                    )
                    val feedback = watch.feedback(video)
                    add(
                        if (feedback == WatchFeedback.Liked) {
                            ModalAction(stringResource(R.string.liked_clear), id = "like", icon = Icons.Outlined.ThumbUp) { watch.toggleFeedback(video, WatchFeedback.Liked) }
                        } else {
                            ModalAction(stringResource(R.string.liked), id = "like", icon = Icons.Outlined.ThumbUp) { watch.toggleFeedback(video, WatchFeedback.Liked) }
                        },
                    )
                    add(
                        if (feedback == WatchFeedback.Disliked) {
                            ModalAction(stringResource(R.string.disliked_clear), id = "dislike", icon = Icons.Outlined.ThumbDown) { watch.toggleFeedback(video, WatchFeedback.Disliked) }
                        } else {
                            ModalAction(stringResource(R.string.disliked), id = "dislike", icon = Icons.Outlined.ThumbDown) { watch.toggleFeedback(video, WatchFeedback.Disliked) }
                        },
                    )
                }
                addAll(trailingActions)
                add(ModalAction(stringResource(R.string.close), id = "close", icon = Icons.Outlined.Close, onClick = onDismiss))
            }
        }
        VideoMenuPage.Details -> {
            title = video.title
            pageMeta = "${video.format} · ${video.sourceLabel}"
            header = { _ -> VideoDetailsBody(video) }
            focusHeader = false
            headerFocus = null
            actions = listOf(geri)
        }
        VideoMenuPage.PickPlaylist -> {
            title = "Oynatma listesine ekle"
            pageMeta = "İndirmeden eklenir"
            header = null
            focusHeader = false
            headerFocus = null
            val custom = playlists?.items?.filter { it.custom }.orEmpty()
            actions = custom.map { item ->
                ModalAction(item.title, icon = Icons.AutoMirrored.Outlined.PlaylistPlay) {
                    val store = playlists ?: return@ModalAction
                    finish(if (store.addVideo(item.id, video)) "Listeye eklendi" else "Bu video zaten listede")
                }
            } + listOf(
                ModalAction("Yeni oynatma listesi", icon = Icons.Outlined.Add) { push(VideoMenuPage.NamePlaylist) },
                geri,
            )
        }
        VideoMenuPage.PickCollection -> {
            title = if (moveTargets != null && !showAddToList) "Koleksiyona taşı" else "Koleksiyona ekle"
            pageMeta = if (moveTargets != null && !showAddToList) video.title else "İndirmeden eklenir"
            header = null
            focusHeader = false
            headerFocus = null
            actions = if (moveTargets != null && !showAddToList) {
                moveTargets.map { (id, label) ->
                    ModalAction(label, icon = Icons.Outlined.Folder) {
                        onMoveTo(id)
                        onDismiss()
                    }
                } + listOf(
                    ModalAction("Yeni koleksiyon", icon = Icons.Outlined.Add) { push(VideoMenuPage.NameCollection) },
                    geri,
                )
            } else {
                val store = playlists
                val cols = collections
                val targets = if (store != null && cols != null) collectionTargets(store, cols) else emptyList()
                targets.map { (playlistId, collectionId, label) ->
                    ModalAction(label, icon = Icons.Outlined.Folder) { putInCollection(playlistId, collectionId) }
                } + listOf(
                    ModalAction("Yeni koleksiyon", icon = Icons.Outlined.Add) { push(VideoMenuPage.PickPlaylistForNewCollection) },
                    geri,
                )
            }
        }
        VideoMenuPage.PickPlaylistForNewCollection -> {
            title = "Koleksiyonun oynatma listesi"
            pageMeta = "İndirmeden eklenir"
            header = null
            focusHeader = false
            headerFocus = null
            val custom = playlists?.items?.filter { it.custom }.orEmpty()
            actions = custom.map { item ->
                ModalAction(item.title, icon = Icons.AutoMirrored.Outlined.PlaylistPlay) {
                    targetPlaylist = item
                    push(VideoMenuPage.NameCollection)
                }
            } + listOf(
                ModalAction("Yeni oynatma listesi", icon = Icons.Outlined.Add) { push(VideoMenuPage.NamePlaylistThenCollection) },
                geri,
            )
        }
        VideoMenuPage.NamePlaylist,
        VideoMenuPage.NamePlaylistThenCollection,
        VideoMenuPage.NameCollection,
        -> {
            title = if (page == VideoMenuPage.NameCollection) {
                stringResource(R.string.create_collection)
            } else {
                stringResource(R.string.create_playlist)
            }
            pageMeta = null
            header = { first ->
                MenuNameField(
                    value = draftName,
                    onValue = { draftName = it },
                    requester = nameFocus,
                    down = first,
                )
            }
            focusHeader = true
            headerFocus = nameFocus
            actions = listOf(
                ModalAction("Kaydet", icon = Icons.Outlined.Check) {
                    val name = draftName.trim()
                    if (name.isEmpty()) return@ModalAction
                    when (page) {
                        VideoMenuPage.NamePlaylist -> {
                            val store = playlists ?: return@ModalAction
                            val created = store.createCustom(name)
                            finish(if (store.addVideo(created.id, video)) "Listeye eklendi" else "Bu video zaten listede")
                        }
                        VideoMenuPage.NamePlaylistThenCollection -> {
                            targetPlaylist = playlists?.createCustom(name)
                            push(VideoMenuPage.NameCollection)
                        }
                        VideoMenuPage.NameCollection -> {
                            if (moveTargets != null && !showAddToList) {
                                onCreateAndMove(name)
                                onDismiss()
                            } else {
                                val playlist = targetPlaylist ?: return@ModalAction
                                playlists?.addVideo(playlist.id, video)
                                val id = collections?.create(name, playlist.id)
                                if (id != null) collections.assign(PlaylistEntry.listKey(video), id)
                                finish("Koleksiyona eklendi")
                            }
                        }
                        else -> Unit
                    }
                },
                geri,
            )
        }
    }

    val index = MenuFocus.restore(lastIndex[page], actions.size, if (page == VideoMenuPage.Root) startIndex else 0)
    ModalMenu(
        title = title,
        meta = pageMeta,
        width = width,
        startIndex = index,
        focusNonce = page,
        onDismiss = { back() },
        onBack = { handleBack() },
        header = header,
        headerFocus = headerFocus,
        focusHeader = focusHeader,
        onFocusedIndex = { lastIndex = lastIndex + (page to it) },
        actions = actions,
    )
}

@Composable
private fun MenuNameField(
    value: String,
    onValue: (String) -> Unit,
    requester: FocusRequester,
    down: FocusRequester,
) {
    BasicTextField(
        value = value,
        onValueChange = onValue,
        singleLine = true,
        textStyle = TextStyle(color = GrokWhite, fontSize = 16.sp),
        cursorBrush = SolidColor(GrokYellow),
        modifier = Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .focusRequester(requester)
            .focusProperties {
                this.down = down
                up = FocusRequester.Cancel
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        down.requestFocus()
                        true
                    }
                    Key.DirectionUp, Key.DirectionLeft, Key.DirectionRight -> true
                    else -> false
                }
            }
            .background(GrokInk, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
