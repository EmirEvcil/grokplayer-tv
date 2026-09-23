@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.grokplayer.tv.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import android.view.inputmethod.InputMethodManager
import com.grokplayer.tv.ui.theme.InterceptBack
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.data.BackupArchive
import com.grokplayer.tv.data.BackupPreview
import com.grokplayer.tv.data.BackupStore
import com.grokplayer.tv.data.SharedRoots
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.ModalAction
import com.grokplayer.tv.ui.components.ModalMenu
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.GrokPink
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class MorePage(
    val title: String,
    val rows: List<BackupPreview.Line>,
    val index: Int = 0,
)

private sealed interface BackupScreen {
    data object Home : BackupScreen
    data object Create : BackupScreen
    data class Detail(val path: String) : BackupScreen
    data class Category(val path: String, val catalogId: String) : BackupScreen
    data class Entries(val path: String, val catalogId: String, val index: Int) : BackupScreen
    data class Preview(val path: String) : BackupScreen
    data class MergeReview(val paths: List<String>) : BackupScreen
}

@Composable
fun BackupSection(
    firstFocus: FocusRequester,
    leftFocus: FocusRequester,
    onEnterDetails: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { BackupStore(context) }
    var backups by remember { mutableStateOf(store.list()) }
    var screen by remember { mutableStateOf<BackupScreen>(BackupScreen.Home) }
    var merging by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreStack by remember { mutableStateOf<List<MorePage>>(emptyList()) }
    var moreOrigin by remember { mutableStateOf<String?>(null) }
    var summaries by remember { mutableStateOf(mapOf<String, String>()) }
    var catalogs by remember { mutableStateOf(mapOf<String, List<BackupPreview.Catalog>>()) }
    var reviews by remember { mutableStateOf(mapOf<String, BackupPreview.Review>()) }
    var mergeNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    var mergeSummary by remember { mutableStateOf("") }
    val focusMemory = remember { mutableMapOf<String, String>() }
    val scrolls = remember { mutableMapOf<String, ScrollState>() }
    fun scrollOf(id: String): ScrollState = scrolls.getOrPut(id) { ScrollState(0) }
    val focusScope = when (val current = screen) {
        BackupScreen.Home -> "home"
        BackupScreen.Create -> "create"
        is BackupScreen.Detail -> "detail:${current.path}"
        is BackupScreen.Category -> "category:${current.path}:${current.catalogId}"
        is BackupScreen.Entries -> "entries:${current.path}:${current.catalogId}:${current.index}"
        is BackupScreen.Preview -> "preview:${current.path}"
        is BackupScreen.MergeReview -> "merge-review"
    }
    val focusOf = remember(focusScope) { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String): FocusRequester = focusOf.getOrPut(id) { FocusRequester() }
    fun refresh() {
        backups = store.list()
    }
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    fun versionName() = packageInfo?.versionName ?: "0.2.70"
    fun versionCode() = if (Build.VERSION.SDK_INT >= 28) {
        packageInfo?.longVersionCode?.toInt() ?: 72
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode ?: 72
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val imeVisible = WindowInsets.isImeVisible
    fun hideKeyboard() {
        keyboard?.hide()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
    fun backupOf(path: String) = backups.firstOrNull { it.file.absolutePath == path }
    fun closeMore() {
        if (moreStack.size > 1) {
            moreStack = moreStack.dropLast(1)
        } else {
            pendingFocus = moreOrigin
            moreStack = emptyList()
            moreOrigin = null
        }
    }
    fun back() {
        if (imeVisible) {
            hideKeyboard()
            return
        }
        if (moreStack.isNotEmpty()) {
            closeMore()
            return
        }
        if (confirmDelete) {
            confirmDelete = false
            pendingFocus = "delete"
            return
        }
        when (val current = screen) {
            BackupScreen.Home -> if (merging) {
                merging = false
                pendingFocus = "merge"
            }
            BackupScreen.Create -> {
                screen = BackupScreen.Home
                pendingFocus = "create"
            }
            is BackupScreen.Detail -> {
                screen = BackupScreen.Home
                pendingFocus = focusMemory["home"] ?: "backup:${current.path}"
            }
            is BackupScreen.Category -> {
                screen = BackupScreen.Detail(current.path)
                pendingFocus = focusMemory["detail:${current.path}"] ?: "cat:${current.catalogId}"
            }
            is BackupScreen.Entries -> {
                screen = BackupScreen.Category(current.path, current.catalogId)
                pendingFocus = focusMemory["category:${current.path}:${current.catalogId}"] ?: "entry:${current.index}"
            }
            is BackupScreen.Preview -> {
                screen = BackupScreen.Detail(current.path)
                pendingFocus = "review"
            }
            is BackupScreen.MergeReview -> {
                screen = BackupScreen.Home
                merging = true
                pendingFocus = "continue"
            }
        }
    }
    val nested = screen != BackupScreen.Home || merging || confirmDelete || moreStack.isNotEmpty() || imeVisible
    BackHandler(enabled = nested, onBack = { back() })
    InterceptBack(enabled = nested) {
        back()
        true
    }
    LaunchedEffect(backups.map { it.file.absolutePath to it.bytes }) {
        val listed = backups
        summaries = withContext(Dispatchers.IO) {
            listed.associate { backup ->
                val text = runCatching {
                    BackupPreview.summaryText(BackupPreview.summary(BackupArchive.readSections(backup.file)))
                }.getOrDefault("")
                backup.file.absolutePath to text
            }
        }
    }
    LaunchedEffect(screen) {
        val path = when (val current = screen) {
            is BackupScreen.Detail -> current.path
            is BackupScreen.Category -> current.path
            is BackupScreen.Entries -> current.path
            is BackupScreen.Preview -> current.path
            else -> null
        }
        if (path != null && path !in catalogs) {
            val file = backups.firstOrNull { it.file.absolutePath == path }?.file
            if (file != null) {
                val loaded = withContext(Dispatchers.IO) { BackupPreview.catalog(BackupArchive.readSections(file)) }
                catalogs = catalogs + (path to loaded)
            }
        }
        val preview = screen as? BackupScreen.Preview
        if (preview != null) {
            val file = backups.firstOrNull { it.file.absolutePath == preview.path }?.file
            if (file != null) {
                val loaded = withContext(Dispatchers.IO) {
                    BackupPreview.review(store.currentSections(), BackupArchive.readSections(file))
                }
                reviews = reviews + (preview.path to loaded)
            }
        }
        val merge = screen as? BackupScreen.MergeReview
        if (merge != null) {
            val chosen = backups.filter { it.file.absolutePath in merge.paths }.sortedBy { it.manifest.createdAt }
            val notes = withContext(Dispatchers.IO) { mergeNotes(chosen) }
            mergeNotes = notes.first
            mergeSummary = notes.second
        }
    }
    LaunchedEffect(pendingFocus, focusScope, confirmDelete, moreStack.isNotEmpty(), catalogs.keys.toList()) {
        var key = pendingFocus ?: return@LaunchedEffect
        if (confirmDelete || moreStack.isNotEmpty()) return@LaunchedEffect
        if (key == "cat:first") {
            val path = (screen as? BackupScreen.Detail)?.path
            val first = path?.let { catalogs[it]?.firstOrNull()?.id }
            if (first == null) return@LaunchedEffect
            key = "cat:$first"
            pendingFocus = key
        }
        val target = focusOf[key] ?: return@LaunchedEffect
        repeat(24) {
            if (target.takeFocus()) {
                pendingFocus = null
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(16)
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val current = screen) {
            BackupScreen.Home -> HomePage(
                shared = SharedRoots.usingSharedStorage(context),
                backups = backups,
                summaries = summaries,
                merging = merging,
                picked = picked,
                context = context,
                firstFocus = firstFocus,
                leftFocus = leftFocus,
                scroll = scrollOf("home"),
                onEnterDetails = onEnterDetails,
                requester = ::requester,
                onFocus = { focusMemory["home"] = it },
                onGrant = { requestSharedStorage(context) },
                onCreate = {
                    name = defaultBackupName()
                    screen = BackupScreen.Create
                    pendingFocus = "save"
                },
                onMerge = {
                    merging = true
                    picked = emptySet()
                },
                onOpen = { backup ->
                    focusMemory["home"] = "backup:${backup.file.absolutePath}"
                    screen = BackupScreen.Detail(backup.file.absolutePath)
                    pendingFocus = focusMemory["detail:${backup.file.absolutePath}"] ?: "cat:first"
                },
                onToggle = { backup ->
                    val path = backup.file.absolutePath
                    picked = if (path in picked) picked - path else picked + path
                },
                onContinue = {
                    if (picked.size >= 2) {
                        name = "Birleşik yedek"
                        screen = BackupScreen.MergeReview(picked.toList())
                        pendingFocus = "save"
                    }
                },
            )
            BackupScreen.Create -> NamePage(
                title = "Yedeğin adı",
                hint = "Yukarı ile adı değiştir. Tamam kaydeder.",
                name = name,
                busy = busy,
                saveLabel = if (busy) "Yazılıyor…" else "Kaydet",
                leftFocus = leftFocus,
                firstFocus = firstFocus,
                onEnterDetails = onEnterDetails,
                requester = ::requester,
                onName = { name = it.replace("\n", "").replace("\r", "") },
                onSave = save@{
                    if (busy) return@save
                    val label = name
                    val version = versionName()
                    val code = versionCode()
                    busy = true
                    hideKeyboard()
                    scope.launch {
                        val created = withContext(Dispatchers.IO) { store.create(label, version, code) }
                        refresh()
                        busy = false
                        screen = BackupScreen.Home
                        pendingFocus = "backup:${created.file.absolutePath}"
                    }
                },
                onCancel = { back() },
                onDone = {
                    hideKeyboard()
                    requester("save").takeFocus()
                },
            )
            is BackupScreen.Detail -> DetailPage(
                backup = backupOf(current.path),
                catalog = catalogs[current.path],
                context = context,
                leftFocus = leftFocus,
                onEnterDetails = onEnterDetails,
                requester = ::requester,
                scroll = scrollOf("detail:${current.path}"),
                onFocus = { focusMemory["detail:${current.path}"] = it },
                onOpen = { catalogId ->
                    focusMemory["detail:${current.path}"] = "cat:$catalogId"
                    screen = BackupScreen.Category(current.path, catalogId)
                    pendingFocus = focusMemory["category:${current.path}:$catalogId"] ?: "entry:0"
                },
                onReview = {
                    screen = BackupScreen.Preview(current.path)
                    pendingFocus = "cancel"
                },
                onDelete = { confirmDelete = true },
            )
            is BackupScreen.Category -> {
                val catalog = catalogs[current.path]?.firstOrNull { it.id == current.catalogId }
                CategoryPage(
                    title = catalog?.title ?: "Ayrıntı",
                    rows = catalog?.rows.orEmpty(),
                    leftFocus = leftFocus,
                    onEnterDetails = onEnterDetails,
                    requester = ::requester,
                    scroll = scrollOf("category:${current.path}:${current.catalogId}"),
                    onFocus = { focusMemory["category:${current.path}:${current.catalogId}"] = it },
                    onOpen = { index ->
                        val row = catalog?.rows?.getOrNull(index)
                        if (row != null && row.children.isNotEmpty()) {
                            focusMemory["category:${current.path}:${current.catalogId}"] = "entry:$index"
                            screen = BackupScreen.Entries(current.path, current.catalogId, index)
                            pendingFocus = "line:0"
                        }
                    },
                )
            }
            is BackupScreen.Entries -> {
                val row = catalogs[current.path]
                    ?.firstOrNull { it.id == current.catalogId }
                    ?.rows
                    ?.getOrNull(current.index)
                LinesPage(
                    title = row?.title ?: "Ayrıntı",
                    rows = row?.children?.map { it.title to it.detail }.orEmpty(),
                    leftFocus = leftFocus,
                    onEnterDetails = onEnterDetails,
                    requester = ::requester,
                    scroll = scrollOf("entries:${current.path}:${current.catalogId}:${current.index}"),
                )
            }
            is BackupScreen.Preview -> PreviewPage(
                backup = backupOf(current.path),
                review = reviews[current.path],
                leftFocus = leftFocus,
                onEnterDetails = onEnterDetails,
                requester = ::requester,
                scroll = scrollOf("preview:${current.path}"),
                onMore = { title, lines, origin ->
                    moreOrigin = origin
                    moreStack = listOf(MorePage(title, lines))
                },
                onCancel = { back() },
                onRestore = restore@{
                    val target = backupOf(current.path) ?: return@restore
                    if (busy) return@restore
                    busy = true
                    val appContext = context.applicationContext
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                        runCatching { withContext(Dispatchers.IO) { store.restore(target) } }
                        restartProcess(appContext)
                    }
                },
            )
            is BackupScreen.MergeReview -> MergeReviewPage(
                name = name,
                busy = busy,
                summary = mergeSummary,
                kept = mergeNotes,
                leftFocus = leftFocus,
                firstFocus = firstFocus,
                onEnterDetails = onEnterDetails,
                requester = ::requester,
                scroll = scrollOf("merge-review"),
                onName = { name = it.replace("\n", "").replace("\r", "") },
                onSave = save@{
                    if (busy) return@save
                    val chosen = backups.filter { it.file.absolutePath in current.paths }
                    val label = name
                    val version = versionName()
                    val code = versionCode()
                    busy = true
                    hideKeyboard()
                    scope.launch {
                        val created = withContext(Dispatchers.IO) {
                            if (chosen.size >= 2) store.merge(chosen, label, version, code) else null
                        }
                        picked = emptySet()
                        merging = false
                        refresh()
                        busy = false
                        screen = BackupScreen.Home
                        pendingFocus = created?.let { "backup:${it.file.absolutePath}" } ?: "merge"
                    }
                },
                onCancel = { back() },
                onDone = {
                    hideKeyboard()
                    requester("save").takeFocus()
                },
            )
        }
        val info = moreStack.lastOrNull()
        if (info != null) {
            val actions = info.rows.mapIndexed { index, line ->
                val label = if (line.detail.isBlank()) line.title else "${line.title} · ${line.detail}"
                ModalAction(label) {
                    if (line.children.isNotEmpty()) {
                        val current = moreStack.last()
                        moreStack = moreStack.dropLast(1) +
                            current.copy(index = index) +
                            MorePage(line.title.ifBlank { "Ayrıntı" }, line.children)
                    }
                }
            } + ModalAction("Kapat") {
                pendingFocus = moreOrigin
                moreStack = emptyList()
                moreOrigin = null
            }
            ModalMenu(
                title = info.title,
                meta = "${info.rows.size} kayıt",
                width = 520.dp,
                startIndex = info.index.coerceIn(0, actions.lastIndex),
                focusNonce = moreStack.size to info.index,
                dismissOnScrim = false,
                onDismiss = { closeMore() },
                onBack = { closeMore() },
                actions = actions,
            )
        }
        val deleting = confirmDelete && screen is BackupScreen.Detail
        if (deleting) {
            val backup = (screen as BackupScreen.Detail).let { backupOf(it.path) }
            ModalMenu(
                title = "Yedeği sil",
                meta = "${backup?.manifest?.name.orEmpty()} silinsin mi? Video dosyaları durur.",
                width = 460.dp,
                startIndex = 1,
                dismissOnScrim = false,
                onDismiss = { confirmDelete = false; pendingFocus = "delete" },
                onBack = { confirmDelete = false; pendingFocus = "delete" },
                actions = listOf(
                    ModalAction("Sil") {
                        val target = backup ?: return@ModalAction
                        val paths = backups.map { it.file.absolutePath }
                        val index = paths.indexOf(target.file.absolutePath)
                        val neighbor = paths.getOrNull(index + 1) ?: paths.getOrNull(index - 1)
                        store.delete(target)
                        picked = picked - target.file.absolutePath
                        confirmDelete = false
                        refresh()
                        screen = BackupScreen.Home
                        pendingFocus = if (neighbor != null && neighbor != target.file.absolutePath) "backup:$neighbor" else "create"
                    },
                    ModalAction("Vazgeç") {
                        confirmDelete = false
                        pendingFocus = "delete"
                    },
                ),
            )
        }
    }
}

@Composable
private fun HomePage(
    shared: Boolean,
    backups: List<BackupStore.StoredBackup>,
    summaries: Map<String, String>,
    merging: Boolean,
    picked: Set<String>,
    context: android.content.Context,
    firstFocus: FocusRequester,
    leftFocus: FocusRequester,
    scroll: ScrollState,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    onFocus: (String) -> Unit,
    onGrant: () -> Unit,
    onCreate: () -> Unit,
    onMerge: () -> Unit,
    onOpen: (BackupStore.StoredBackup) -> Unit,
    onToggle: (BackupStore.StoredBackup) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (merging) {
                if (picked.isEmpty()) "Seçmek için Tamam" else "${picked.size} yedek seçili"
            } else if (shared) {
                "Tamam yedeği açar"
            } else {
                "Paylaşılan depo kapalı. Yedek silinince gider."
            },
            style = GrokType.cardMeta,
            color = if (merging && picked.isNotEmpty()) GrokWhite else GrokMuted,
        )
        if (!shared) {
            ActionRow(
                title = "Paylaşılan depoya izin ver",
                subtitle = null,
                checked = null,
                left = leftFocus,
                up = null,
                down = requester("create"),
                focus = requester("grant"),
                onEnterDetails = onEnterDetails,
                onClick = onGrant,
            )
        }
        val firstBackup = backups.firstOrNull()?.let { requester("backup:${it.file.absolutePath}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionRow(
                title = "Yedek oluştur",
                subtitle = null,
                checked = null,
                left = leftFocus,
                up = if (!shared) requester("grant") else null,
                down = firstBackup,
                focus = requester("create"),
                onEnterDetails = onEnterDetails,
                also = firstFocus,
                modifier = Modifier.weight(1f),
                right = requester("merge"),
                onClick = onCreate,
                onFocused = { onFocus("create") },
            )
            ActionRow(
                title = "Birleştir",
                subtitle = null,
                checked = if (merging) true else null,
                left = requester("create"),
                up = if (!shared) requester("grant") else null,
                down = firstBackup,
                focus = requester("merge"),
                onEnterDetails = onEnterDetails,
                modifier = Modifier.weight(1f),
                onClick = onMerge,
                onFocused = { onFocus("merge") },
            )
        }
        val grouped = backups.groupBy { dayKey(it.manifest.createdAt) }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (backups.isEmpty()) {
                Text("Henüz yedek yok.", style = GrokType.cardMeta, color = GrokMuted)
            }
            val flat = backups
            grouped.forEach { (keyDay, items) ->
                Text(dayLabel(items.first().manifest.createdAt), style = GrokType.section, color = GrokMuted)
                items.forEach { backup ->
                    val path = backup.file.absolutePath
                    val index = flat.indexOfFirst { it.file.absolutePath == path }
                    val previous = flat.getOrNull(index - 1)?.let { requester("backup:${it.file.absolutePath}") } ?: requester("create")
                    val nextBackup = flat.getOrNull(index + 1)?.let { requester("backup:${it.file.absolutePath}") }
                    val next = nextBackup ?: if (merging) requester("continue") else null
                    val summary = summaries[path].orEmpty()
                    val size = Formatter.formatFileSize(context, backup.bytes)
                    ActionRow(
                        title = backup.manifest.name.ifBlank { "Yedek" },
                        subtitle = if (summary.isBlank()) size else "$size · $summary",
                        checked = if (merging) path in picked else null,
                        left = leftFocus,
                        up = previous,
                        down = next,
                        focus = requester("backup:$path"),
                        onEnterDetails = onEnterDetails,
                        onClick = { if (merging) onToggle(backup) else onOpen(backup) },
                        onFocused = { onFocus("backup:$path") },
                    )
                }
            }
        }
        if (merging) {
            ActionRow(
                title = "Devam et",
                subtitle = if (picked.size >= 2) "${picked.size} yedek birleşecek" else "En az 2 yedek seç",
                checked = null,
                left = leftFocus,
                up = backups.lastOrNull()?.let { requester("backup:${it.file.absolutePath}") } ?: requester("merge"),
                down = null,
                focus = requester("continue"),
                onEnterDetails = onEnterDetails,
                onClick = onContinue,
                onFocused = { onFocus("continue") },
            )
        }
    }
}

@Composable
private fun NamePage(
    title: String,
    hint: String,
    name: String,
    busy: Boolean,
    saveLabel: String,
    leftFocus: FocusRequester,
    firstFocus: FocusRequester,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val save = requester("save")
    val cancel = requester("cancel")
    val field = requester("field")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = GrokType.cardTitle, color = GrokWhite)
        Text(hint, style = GrokType.cardMeta, color = GrokMuted)
        BasicTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = GrokType.cardTitle.copy(color = GrokWhite),
            cursorBrush = SolidColor(GrokYellow),
            modifier = Modifier
                .fillMaxWidth()
                .background(GrokSurface, RoundedCornerShape(8.dp))
                .padding(16.dp)
                .focusRequester(field)
                .chainKeys(leftFocus, up = null, down = save)
                .onFocusChanged { if (it.isFocused) onEnterDetails() },
        )
        ActionRow(saveLabel, null, null, leftFocus, field, cancel, save, onEnterDetails, also = firstFocus, onClick = onSave)
        ActionRow("Vazgeç", null, null, leftFocus, save, null, cancel, onEnterDetails, onClick = onCancel)
    }
}

@Composable
private fun DetailPage(
    backup: BackupStore.StoredBackup?,
    catalog: List<BackupPreview.Catalog>?,
    context: android.content.Context,
    leftFocus: FocusRequester,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    scroll: ScrollState,
    onFocus: (String) -> Unit,
    onOpen: (String) -> Unit,
    onReview: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(backup?.manifest?.name?.ifBlank { "Yedek" } ?: "Yedek", style = GrokType.cardTitle, color = GrokWhite)
        if (backup != null) {
            Text(backupLine(context, backup), style = GrokType.cardMeta, color = GrokMuted)
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (catalog == null) {
                Text("Yükleniyor…", style = GrokType.cardMeta, color = GrokMuted)
            } else {
                catalog.forEachIndexed { index, item ->
                    val previous = catalog.getOrNull(index - 1)?.let { requester("cat:${it.id}") }
                    val next = catalog.getOrNull(index + 1)?.let { requester("cat:${it.id}") } ?: requester("review")
                    ActionRow(
                        title = item.title,
                        subtitle = countLabel(item.count),
                        checked = null,
                        left = leftFocus,
                        up = previous,
                        down = next,
                        focus = requester("cat:${item.id}"),
                        onEnterDetails = onEnterDetails,
                        onClick = { onOpen(item.id) },
                        onFocused = { onFocus("cat:${item.id}") },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionRow(
                title = "Sil",
                subtitle = null,
                checked = null,
                left = leftFocus,
                up = catalog?.lastOrNull()?.let { requester("cat:${it.id}") },
                down = null,
                focus = requester("delete"),
                onEnterDetails = onEnterDetails,
                modifier = Modifier.weight(1f),
                right = requester("review"),
                onClick = onDelete,
                onFocused = { onFocus("delete") },
            )
            ActionRow(
                title = "Geri yüklemeyi incele",
                subtitle = null,
                checked = null,
                left = requester("delete"),
                up = catalog?.lastOrNull()?.let { requester("cat:${it.id}") },
                down = null,
                focus = requester("review"),
                onEnterDetails = onEnterDetails,
                modifier = Modifier.weight(1f),
                onClick = onReview,
                onFocused = { onFocus("review") },
            )
        }
    }
}

@Composable
private fun CategoryPage(
    title: String,
    rows: List<BackupPreview.Entry>,
    leftFocus: FocusRequester,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    scroll: ScrollState,
    onFocus: (String) -> Unit,
    onOpen: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = GrokType.cardTitle, color = GrokWhite)
        Text(countLabel(rows.size), style = GrokType.cardMeta, color = GrokMuted)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) {
                Text("Bu yedekte yok.", style = GrokType.cardMeta, color = GrokMuted)
            }
            rows.forEachIndexed { index, row ->
                ActionRow(
                    title = row.title,
                    subtitle = row.detail.ifBlank { null },
                    checked = null,
                    left = leftFocus,
                    up = if (index == 0) null else requester("entry:${index - 1}"),
                    down = if (index == rows.lastIndex) null else requester("entry:${index + 1}"),
                    focus = requester("entry:$index"),
                    onEnterDetails = onEnterDetails,
                    onClick = { onOpen(index) },
                    onFocused = { onFocus("entry:$index") },
                )
            }
        }
    }
}

@Composable
private fun LinesPage(
    title: String,
    rows: List<Pair<String, String>>,
    leftFocus: FocusRequester,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    scroll: ScrollState,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = GrokType.cardTitle, color = GrokWhite)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEachIndexed { index, row ->
                ActionRow(
                    title = row.first,
                    subtitle = row.second.ifBlank { null },
                    checked = null,
                    left = leftFocus,
                    up = if (index == 0) null else requester("line:${index - 1}"),
                    down = if (index == rows.lastIndex) null else requester("line:${index + 1}"),
                    focus = requester("line:$index"),
                    onEnterDetails = onEnterDetails,
                    onClick = {},
                )
            }
        }
    }
}

@Composable
private fun PreviewPage(
    backup: BackupStore.StoredBackup?,
    review: BackupPreview.Review?,
    leftFocus: FocusRequester,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    scroll: ScrollState,
    onMore: (String, List<BackupPreview.Line>, String) -> Unit,
    onCancel: () -> Unit,
    onRestore: () -> Unit,
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Geri yükleme", style = GrokType.cardTitle, color = GrokWhite)
        if (backup != null) {
            Text(backup.manifest.name, style = GrokType.cardMeta, color = GrokMuted)
        }
        if (review == null) {
            Text("Yükleniyor…", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.weight(1f))
        } else {
            Text(
                "${review.added} eklenecek · ${review.changed} değişecek · ${review.removed} silinecek",
                style = GrokType.cardTitle,
                color = GrokWhite,
            )
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review.items.isEmpty()) {
                    Text("Kayıtlarda fark yok. Video dosyaları silinmez.", style = GrokType.cardMeta, color = GrokMuted)
                }
                review.items.forEachIndexed { index, item ->
                    ActionRow(
                        title = item.title,
                        subtitle = item.detail,
                        checked = null,
                        left = leftFocus,
                        up = if (index == 0) null else requester("change:${index - 1}"),
                        down = if (index == review.items.lastIndex) requester("cancel") else requester("change:${index + 1}"),
                        focus = requester("change:$index"),
                        onEnterDetails = onEnterDetails,
                        mark = kindMark(item.kind),
                        markColor = kindColor(item.kind),
                        onClick = { if (item.more.isNotEmpty()) onMore(item.title, item.more, "change:$index") },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionRow(
                title = "Vazgeç",
                subtitle = null,
                checked = null,
                left = leftFocus,
                up = review?.items?.lastIndex?.let { requester("change:$it") },
                down = null,
                focus = requester("cancel"),
                onEnterDetails = onEnterDetails,
                modifier = Modifier.weight(1f),
                right = requester("restore"),
                onClick = onCancel,
            )
            ActionRow(
                title = "Geri yükle",
                subtitle = "Yeniden başlar",
                checked = null,
                left = requester("cancel"),
                up = review?.items?.lastIndex?.let { requester("change:$it") },
                down = null,
                focus = requester("restore"),
                onEnterDetails = onEnterDetails,
                modifier = Modifier.weight(1f),
                onClick = onRestore,
            )
        }
    }
}

@Composable
private fun MergeReviewPage(
    name: String,
    busy: Boolean,
    summary: String,
    kept: List<String>,
    leftFocus: FocusRequester,
    firstFocus: FocusRequester,
    onEnterDetails: () -> Unit,
    requester: (String) -> FocusRequester,
    scroll: ScrollState,
    onName: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val field = requester("field")
    val save = requester("save")
    val cancel = requester("cancel")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Birleştirme", style = GrokType.cardTitle, color = GrokWhite)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Eskiden yeniye birleştirilir. Aynı kayıtta yeni değer tutulur. Liste, koleksiyon ve indirme üyelikleri birleşir; yalnızca bir yedekte olan kayıt kalır. İzleme kaydında daha yeni olan tutulur. Beğeniler de bu kaydın içindedir. Video dosyaları değişmez.",
                style = GrokType.cardMeta,
                color = GrokMuted,
            )
            if (summary.isNotBlank()) {
                Text(summary, style = GrokType.cardTitle, color = GrokWhite)
            }
            if (kept.isEmpty()) {
                Text("Eski yedeklerde, yenisinde olmayan ayrı bir kayıt görünmüyor. Eksik üyelikler yine de birleşir.", style = GrokType.cardMeta, color = GrokMuted)
            } else {
                Text("Eski yedeklerden gelen", style = GrokType.cardTitle, color = GrokWhite)
                kept.forEach { Text(it, style = GrokType.cardMeta, color = GrokMuted) }
            }
            Text("Yedeğin adı", style = GrokType.cardTitle, color = GrokWhite)
            BasicTextField(
                value = name,
                onValueChange = onName,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                textStyle = GrokType.cardTitle.copy(color = GrokWhite),
                cursorBrush = SolidColor(GrokYellow),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GrokSurface, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .focusRequester(field)
                    .chainKeys(leftFocus, up = null, down = save)
                    .onFocusChanged { if (it.isFocused) onEnterDetails() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionRow(
                "Vazgeç",
                null,
                null,
                leftFocus,
                field,
                null,
                cancel,
                onEnterDetails,
                modifier = Modifier.weight(1f),
                right = save,
                onClick = onCancel,
            )
            ActionRow(
                if (busy) "Yazılıyor…" else "Kaydet",
                null,
                null,
                requester("cancel"),
                field,
                null,
                save,
                onEnterDetails,
                also = firstFocus,
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    checked: Boolean?,
    left: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    focus: FocusRequester,
    onEnterDetails: () -> Unit,
    also: FocusRequester? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
    right: FocusRequester? = null,
    mark: String? = null,
    markColor: Color = GrokYellow,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val shape = RoundedCornerShape(8.dp)
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    FocusableAction(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focus)
            .then(if (also != null) Modifier.focusRequester(also) else Modifier)
            .bringIntoViewRequester(bring)
            .chainKeys(left, up, down, right)
            .onFocusChanged {
                if (it.isFocused) {
                    onEnterDetails()
                    onFocused()
                    scope.launch { runCatching { bring.bringIntoView() } }
                }
            },
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(GrokSurface)
                .then(
                    when {
                        focused -> Modifier.border(2.dp, GrokYellow, shape)
                        checked == true -> Modifier.border(1.dp, GrokYellow.copy(alpha = 0.85f), shape)
                        else -> Modifier
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (mark != null) {
                Text(mark, style = GrokType.cardTitle, color = markColor)
            }
            if (checked != null) CheckMark(checked)
            Column(Modifier.weight(1f)) {
                Text(title, style = GrokType.cardTitle, color = GrokWhite)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun CheckMark(checked: Boolean) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .size(22.dp)
            .border(2.dp, GrokWhite, shape)
            .background(if (checked) GrokYellow else Color.Transparent, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = GrokInk, modifier = Modifier.size(16.dp))
        }
    }
}

private fun kindMark(kind: String): String = when (kind) {
    "Eklenecek" -> "+"
    "Silinecek" -> "−"
    else -> "→"
}

private fun kindColor(kind: String): Color = when (kind) {
    "Eklenecek" -> Color(0xFF7DDEA0)
    "Silinecek" -> GrokPink
    else -> GrokYellow
}

private fun countLabel(count: Int): String = if (count == 1) "1 kayıt" else "$count kayıt"

private fun dayKey(millis: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = millis
    return "%04d-%02d-%02d".format(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
}

private fun dayLabel(millis: Long): String {
    val today = dayKey(System.currentTimeMillis())
    val yesterday = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }.let { dayKey(it.timeInMillis) }
    return when (dayKey(millis)) {
        today -> "Bugün"
        yesterday -> "Dün"
        else -> {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = millis
            "%d/%d/%04d".format(
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.YEAR),
            )
        }
    }
}

private fun backupLine(context: android.content.Context, backup: BackupStore.StoredBackup): String {
    val whenText = SimpleDateFormat("d MMM yyyy HH:mm", Locale("tr")).format(Date(backup.manifest.createdAt))
    val size = Formatter.formatFileSize(context, backup.bytes)
    return "$whenText · $size · ${backup.manifest.appVersion}"
}

private fun mergeNotes(chosen: List<BackupStore.StoredBackup>): Pair<List<String>, String> {
    if (chosen.size < 2) return emptyList<String>() to ""
    val ordered = chosen.sortedBy { it.manifest.createdAt }
    val newest = BackupPreview.catalog(BackupArchive.readSections(ordered.last().file))
    val mergedSections = BackupArchive.merge(ordered.map { BackupArchive.readSections(it.file) })
    val merged = BackupPreview.catalog(mergedSections)
    val summary = BackupPreview.summaryText(BackupPreview.summary(mergedSections))
    val newestNames = newest.flatMap { catalog -> catalog.rows.map { it.title } }.toSet()
    val kept = merged.flatMap { catalog ->
        catalog.rows.map { row -> "${catalog.title}: ${row.title}" }
    }.filter { line ->
        val name = line.substringAfter(": ")
        name !in newestNames
    }.distinct().take(12)
    val head = if (summary.isBlank()) "Birleşik yedek hazır" else "Birleşik yedek · $summary"
    return kept to head
}

private fun restartProcess(context: Context) {
    val launch = Intent(context, com.grokplayer.tv.RestartActivity::class.java).apply {
        putExtra(com.grokplayer.tv.RestartActivity.EXTRA_PID, android.os.Process.myPid())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(launch) }
}

private fun FocusRequester.takeFocus(): Boolean =
    runCatching { requestFocus(FocusDirection.Enter) }.getOrDefault(false)

private fun Modifier.chainKeys(
    left: FocusRequester,
    up: FocusRequester?,
    down: FocusRequester?,
    right: FocusRequester? = null,
): Modifier = this
    .focusProperties {
        this.left = left
        this.right = right ?: FocusRequester.Cancel
        this.up = up ?: FocusRequester.Cancel
        this.down = down ?: FocusRequester.Cancel
    }
    .onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> {
                up?.takeFocus()
                true
            }
            Key.DirectionDown -> {
                down?.takeFocus()
                true
            }
            Key.DirectionLeft -> {
                left.takeFocus()
                true
            }
            Key.DirectionRight -> {
                right?.takeFocus()
                true
            }
            else -> false
        }
    }

private fun defaultBackupName(): String =
    "Yedek " + SimpleDateFormat("d MMM HH:mm", Locale("tr")).format(Date())

private fun requestSharedStorage(context: android.content.Context) {
    if (Build.VERSION.SDK_INT < 30) return
    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
        .setData(Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
