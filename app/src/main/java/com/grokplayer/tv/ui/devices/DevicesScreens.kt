package com.grokplayer.tv.ui.devices

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import com.grokplayer.tv.ui.components.AbsorbOpeningOk
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.theme.RememberFocusLock
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.link.LinkController
import com.grokplayer.tv.data.link.LinkUi
import com.grokplayer.tv.data.link.LinkWatch
import com.grokplayer.tv.data.link.PairedPc
import com.grokplayer.tv.data.link.RemoteState
import com.grokplayer.tv.data.link.SendMode
import com.grokplayer.tv.data.link.SendWhen
import com.grokplayer.tv.data.link.ResumeOffer
import com.grokplayer.tv.data.link.TransferJob
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import org.json.JSONObject

@Composable
fun DevicesSection(
    link: LinkController,
    firstFocus: FocusRequester,
    leftFocus: FocusRequester,
    onOpen: (PairedPc) -> Unit,
    onDeviceMenu: (PairedPc) -> Unit,
    onOpenTransfers: () -> Unit,
    onEnterDetails: () -> Unit,
    focusDeviceId: String? = null,
    onFocusConsumed: () -> Unit = {},
    stayInDetails: Boolean = false,
    pageFocus: FocusRequester? = null,
) {
    val ui by link.ui.collectAsState()
    val buckets = LinkWatch.buckets(ui.nearby, ui.paired, ui.connectedId)
    val activeJobs = ui.jobs.count { it.status == "sending" || it.status == "streaming" }
    val deviceFocus = remember { mutableMapOf<String, FocusRequester>() }
    var lastDeviceId by remember { mutableStateOf<String?>(null) }
    fun deviceRequester(id: String) = deviceFocus.getOrPut(id) { FocusRequester() }
    fun rememberDevice(id: String?) {
        lastDeviceId = id
        onEnterDetails()
    }
    LaunchedEffect(stayInDetails, focusDeviceId, ui.connectedId, ui.connectingId, buckets.paired.size, buckets.connected.size) {
        if (!stayInDetails && focusDeviceId == null) return@LaunchedEffect
        onEnterDetails()
        val id = focusDeviceId ?: lastDeviceId
        repeat(14) {
            kotlinx.coroutines.delay(40)
            val ok = if (!id.isNullOrBlank() && (buckets.connected + buckets.paired).any { it.id == id }) {
                runCatching { deviceRequester(id).requestFocus() }.getOrDefault(false)
            } else {
                runCatching { firstFocus.requestFocus() }.getOrDefault(false)
            }
            if (ok) {
                if (focusDeviceId != null) onFocusConsumed()
                return@LaunchedEffect
            }
        }
        runCatching { firstFocus.requestFocus() }
        if (focusDeviceId != null) onFocusConsumed()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Yakındaki, eşleşmiş ve bağlı PC’ler ayrı listelenir. Bir satır seçip eşleş, bağlan veya yönet.",
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        DeviceRow(
            title = "Bu TV görünür",
            meta = if (ui.visible) "Keşif açık · PC’ler bu TV’yi görebilir" else "Keşif kapalı",
            value = if (ui.visible) "Açık" else "Kapalı",
            modifier = Modifier
                .focusRequester(firstFocus)
                .then(if (stayInDetails && pageFocus != null) Modifier.focusRequester(pageFocus) else Modifier)
                .focusProperties {
                    canFocus = focusDeviceId == null || lastDeviceId == null
                    left = leftFocus
                },
            onFocus = { rememberDevice(null); onEnterDetails() },
            onClick = { link.setVisible(!ui.visible) },
        )
        DeviceRow(
            title = "Yeni cihaz eşleştir",
            meta = when {
                !ui.visible -> "Önce görünürlüğü aç"
                buckets.nearby.isEmpty() -> "Açık GrokPlayer aranıyor…"
                else -> buckets.nearby.joinToString { it.name }
            },
            value = "PIN göster",
            enabled = ui.visible,
            onFocus = { rememberDevice(null); onEnterDetails() },
            onClick = { if (ui.visible) link.beginPair() },
            modifier = Modifier.focusProperties {
                canFocus = focusDeviceId == null
                left = leftFocus
            },
        )
        DeviceRow(
            title = "Aktarımlar",
            meta = if (activeJobs == 0) "Kopya ve yayınları yönet" else "$activeJobs aktif iş",
            value = "Aç",
            onFocus = { rememberDevice(null); onEnterDetails() },
            onClick = onOpenTransfers,
            modifier = Modifier.focusProperties {
                canFocus = focusDeviceId == null
                left = leftFocus
            },
        )
        if (buckets.connected.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Bağlı", style = GrokType.eyebrow, color = GrokSoft, modifier = Modifier.padding(bottom = 6.dp))
            buckets.connected.forEach { pc ->
                if (focusDeviceId == pc.id) {
                    LaunchedEffect(pc.id, focusDeviceId) {
                        repeat(12) {
                            kotlinx.coroutines.delay(40)
                            if (runCatching { deviceRequester(pc.id).requestFocus() }.getOrDefault(false)) {
                                onFocusConsumed()
                                return@LaunchedEffect
                            }
                        }
                    }
                }
                PairedDeviceRow(
                    pc = pc,
                    ui = ui,
                    leftFocus = leftFocus,
                    requester = deviceRequester(pc.id),
                    allowFocus = focusDeviceId == null || focusDeviceId == pc.id,
                    onEnterDetails = { rememberDevice(pc.id) },
                    onClick = { onOpen(pc) },
                    onLongClick = { onDeviceMenu(pc) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Eşleşmiş", style = GrokType.eyebrow, color = GrokSoft, modifier = Modifier.padding(bottom = 6.dp))
        if (ui.paired.isEmpty()) {
            Text("Henüz eşleşmiş cihaz yok.", style = GrokType.cardMeta, color = GrokMuted)
        } else if (buckets.paired.isEmpty()) {
            Text("Bağlı olmayan eşleşmiş PC yok.", style = GrokType.cardMeta, color = GrokMuted)
        } else {
            buckets.paired.forEach { pc ->
                if (focusDeviceId == pc.id) {
                    LaunchedEffect(pc.id, focusDeviceId) {
                        repeat(12) {
                            kotlinx.coroutines.delay(40)
                            if (runCatching { deviceRequester(pc.id).requestFocus() }.getOrDefault(false)) {
                                onFocusConsumed()
                                return@LaunchedEffect
                            }
                        }
                    }
                }
                PairedDeviceRow(
                    pc = pc,
                    ui = ui,
                    leftFocus = leftFocus,
                    requester = deviceRequester(pc.id),
                    allowFocus = focusDeviceId == null || focusDeviceId == pc.id,
                    onEnterDetails = { rememberDevice(pc.id) },
                    onClick = {
                        rememberDevice(pc.id)
                        link.connect(pc)
                    },
                    onLongClick = { onDeviceMenu(pc) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Yakında", style = GrokType.eyebrow, color = GrokSoft, modifier = Modifier.padding(bottom = 6.dp))
        if (buckets.nearby.isEmpty()) {
            Text(
                if (ui.visible) "Yeni açık GrokPlayer aranıyor…" else "Keşif kapalı",
                style = GrokType.cardMeta,
                color = GrokMuted,
            )
        } else {
            buckets.nearby.forEach { pc ->
                DeviceRow(
                    title = pc.name,
                    meta = "${pc.host} · eşleşmemiş · GrokPlayer açık",
                    value = "Eşleş",
                    online = true,
                    enabled = ui.visible,
                    onFocus = onEnterDetails,
                    onClick = { if (ui.visible) link.beginPair(pc) },
                    modifier = Modifier.focusProperties {
                        canFocus = focusDeviceId == null
                        left = leftFocus
                    },
                )
            }
        }
    }
}

@Composable
private fun PairedDeviceRow(
    pc: PairedPc,
    ui: LinkUi,
    leftFocus: FocusRequester,
    requester: FocusRequester,
    allowFocus: Boolean,
    onEnterDetails: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val jobs = ui.jobs.filter { it.pcId == pc.id }
    val live = ui.isLive(pc.id)
    val linked = ui.isConnected(pc.id)
    val linking = ui.isConnecting(pc.id)
    DeviceRow(
        title = pc.name,
        meta = buildString {
            append(
                when {
                    linked -> "Bağlı"
                    linking -> "Bağlanıyor"
                    live -> "Çevrimiçi"
                    else -> "Kapalı"
                },
            )
            append(" · ")
            append(pc.host)
            val playing = if (linked) ui.remote?.title else null
            if (playing != null) append(" · $playing")
            val active = jobs.firstOrNull { it.status == "sending" || it.status == "streaming" }
            if (active != null) append(" · ${active.status}")
        },
        value = when {
            linked -> "Yönet"
            linking -> "…"
            else -> "Bağlan"
        },
        online = live || linked,
        onFocus = onEnterDetails,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties {
                canFocus = allowFocus
                left = leftFocus
            },
    )
}

@Composable
fun PairPinOverlay(pin: String, targetName: String? = null, onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            Modifier
                .width(460.dp)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.28f), RoundedCornerShape(14.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("BU TV’Yİ EŞLEŞTİR", style = GrokType.eyebrow, color = GrokSoft)
            Text(
                if (targetName.isNullOrBlank()) "PC’de Devices penceresine bu kodu yaz"
                else "$targetName için bu kodu PC’ye yaz",
                style = GrokType.section,
                color = GrokWhite,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pin.forEach { ch ->
                    Box(
                        Modifier
                            .size(56.dp, 68.dp)
                            .background(GrokInk, RoundedCornerShape(10.dp))
                            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(ch.toString(), style = GrokType.heroTitle, color = GrokWhite)
                    }
                }
            }
            Text("Geri veya İptal ile kapat  ·  3 dakika", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 12.dp))
            FocusableAction(
                onClick = onCancel,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .focusRequester(cancelFocus),
            ) { focused ->
                Text(
                    "İptal",
                    style = GrokType.button,
                    color = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                )
            }
        }
        LaunchedEffect(pin) {
            repeat(10) {
                kotlinx.coroutines.delay(50)
                runCatching { cancelFocus.requestFocus() }
            }
        }
    }
}

@Composable
fun DeviceHub(
    link: LinkController,
    pc: PairedPc,
    onClose: () -> Unit,
    onBrowsePc: () -> Unit = {},
    onShareFolders: () -> Unit = {},
) {
    val ui by link.ui.collectAsState()
    val remote = ui.remote
    val jobs = ui.jobs.filter { it.pcId == pc.id }
    val first = remember { FocusRequester() }
    val sideFocus = remember { FocusRequester() }
    DisposableEffect(pc.id) {
        link.connect(pc)
        onDispose { }
    }
    RememberFocusLock()
    InterceptBack { onClose(); true }
    BackHandler(onBack = onClose)
    AbsorbOpeningOk {
    Column(
        Modifier
            .fillMaxSize()
            .background(GrokInk)
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 16.dp),
    ) {
        Text(pc.name, style = GrokType.pageTitle, color = GrokWhite)
        Text(
            when {
                remote != null && ui.isConnected(pc.id) -> "Bağlı · durum anlık"
                ui.isConnecting(pc.id) || ui.isConnected(pc.id) -> "Bağlanıyor…"
                else -> "Bağlantı koptu"
            },
            style = GrokType.heroMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            RemotePad(
                playing = remote?.playing == true,
                firstFocus = first,
                sideFocus = sideFocus,
                onPlay = { link.command(pc, if (remote?.playing == true) "pause" else "play") },
                onSeek = { link.command(pc, "seekBy", JSONObject().put("ms", it)) },
                onVolume = { delta ->
                    val next = ((remote?.volume ?: 100.0) + delta).coerceIn(0.0, 100.0)
                    link.command(pc, "volume", JSONObject().put("value", next))
                },
            )
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                NowPlayingCard(remote)
                Spacer(Modifier.height(12.dp))
                Text("Klasörler", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(bottom = 8.dp))
                DeviceRow(
                    "PC klasörleri",
                    "İzin verilen klasörleri VOD olarak aç",
                    "Aç",
                    modifier = Modifier
                        .focusRequester(sideFocus)
                        .focusProperties { left = first },
                    onClick = onBrowsePc,
                )
                DeviceRow("TV klasörlerini paylaş", "PC’nin buradan taraması için izin", "İzin", onClick = onShareFolders)
                Spacer(Modifier.height(12.dp))
                Text("Oynatma", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(bottom = 8.dp))
                CycleRow("Altyazı", remote?.subs?.firstOrNull { it.selected }?.label ?: "Kapalı") {
                    val next = nextIndex(remote?.subs)
                    if (next != null) link.command(pc, "sub", JSONObject().put("index", next))
                }
                CycleRow("Ses", remote?.audio?.firstOrNull { it.selected }?.label ?: "—") {
                    val next = nextIndex(remote?.audio)
                    if (next != null) link.command(pc, "audio", JSONObject().put("index", next))
                }
                CycleRow("Dublaj", dubLabel(remote?.dubbing)) {
                    val next = if (remote?.dubbing == "dub") "original" else "dub"
                    link.command(pc, "dub", JSONObject().put("lang", next))
                }
                CycleRow("Çözünürlük", remote?.resolution ?: "Kaynak") { }
                Spacer(Modifier.height(14.dp))
                Text("Liste", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(bottom = 8.dp))
                if (remote?.playlist.isNullOrEmpty()) {
                    Text("Liste boş.", style = GrokType.cardMeta, color = GrokMuted)
                } else {
                    remote!!.playlist.forEach { item ->
                        DeviceRow(
                            title = item.title,
                            meta = if (item.current) "Şimdi oynuyor" else "Sırada",
                            value = if (item.current) "●" else "Oynat",
                            onClick = { link.command(pc, "playIndex", JSONObject().put("index", item.index)) },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text("Aktarımlar", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(bottom = 8.dp))
                val allJobs = (jobs + (remote?.jobs ?: emptyList())).distinctBy { it.id + it.title }
                if (allJobs.isEmpty()) {
                    Text("Aktarım yok.", style = GrokType.cardMeta, color = GrokMuted)
                } else {
                    allJobs.forEach { JobRow(it) }
                }
            }
        }
        Text("Geri  ·  Yönetimi bırak", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 10.dp))
    }
    }
    LaunchedEffect(pc.id) {
        repeat(10) {
            kotlinx.coroutines.delay(40)
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
fun SendToPcSheet(
    video: LibraryVideo,
    pcs: List<PairedPc>,
    onSend: (PairedPc, SendMode, SendWhen, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var pc by remember { mutableStateOf(pcs.first()) }
    var mode by remember { mutableStateOf(if (video.isStream || !video.originUrl.isNullOrBlank()) SendMode.Stream else SendMode.Copy) }
    var whenPlay by remember { mutableStateOf(SendWhen.PlayNow) }
    var startOver by remember { mutableStateOf(true) }
    val first = remember { FocusRequester() }
    RememberFocusLock()
    InterceptBack { onDismiss(); true }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk.copy(0.62f))
                .focusProperties { canFocus = false },
        )
        Column(
            Modifier
                .width(520.dp)
                .fillMaxHeight(0.88f)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.25f), RoundedCornerShape(14.dp))
                .padding(18.dp)
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
        ) {
            Text("Gönder", style = GrokType.section, color = GrokWhite)
            Text(video.title, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Hedef", style = GrokType.eyebrow, color = GrokSoft)
                pcs.forEachIndexed { index, item ->
                    ChoiceRow(
                        item.name,
                        item.host,
                        selected = item.id == pc.id,
                        onClick = { pc = item },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                if (index == 0) up = first
                            },
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("Nasıl gitsin", style = GrokType.eyebrow, color = GrokSoft)
                ChoiceRow("Yayınla", "VOD olarak aç, kopyalama", selected = mode == SendMode.Stream, onClick = { mode = SendMode.Stream })
                ChoiceRow("Kopyala", "Dosyayı PC’ye yükle", selected = mode == SendMode.Copy, onClick = { mode = SendMode.Copy })
                Spacer(Modifier.height(8.dp))
                Text("Ne olsun", style = GrokType.eyebrow, color = GrokSoft)
                ChoiceRow("Hemen oynat", "PC şimdi açsın", selected = whenPlay == SendWhen.PlayNow, onClick = { whenPlay = SendWhen.PlayNow })
                ChoiceRow("Listeye ekle", "Sıraya koy", selected = whenPlay == SendWhen.Queue, onClick = { whenPlay = SendWhen.Queue })
                Spacer(Modifier.height(8.dp))
                Text("Kaldığın yer", style = GrokType.eyebrow, color = GrokSoft)
                ChoiceRow("Baştan oynat", "Sıfırdan başla", selected = startOver, onClick = { startOver = true })
                ChoiceRow("Kaldığın yerden", "PC’deki kaldığın saniyeden devam", selected = !startOver, onClick = { startOver = false })
            }
            Spacer(Modifier.height(10.dp))
            val dismissFocus = remember { FocusRequester() }
            FocusText("Gönder", onClick = { onSend(pc, mode, whenPlay, startOver) })
            FocusText(
                "Vazgeç",
                onClick = onDismiss,
                modifier = Modifier
                    .focusRequester(dismissFocus)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = dismissFocus
                    },
            )
        }
    }
    LaunchedEffect(Unit) {
        repeat(8) {
            kotlinx.coroutines.delay(40)
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
fun TransferOverlay(
    title: String,
    done: Long,
    total: Long,
    onBackground: () -> Unit,
    onCancel: () -> Unit,
) {
    val pct = if (total > 0) (done * 100 / total).toInt() else 0
    val first = remember { FocusRequester() }
    RememberFocusLock()
    InterceptBack { onCancel(); true }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk.copy(0.62f))
                .focusProperties { canFocus = false },
        )
        Column(
            Modifier
                .width(480.dp)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.25f), RoundedCornerShape(14.dp))
                .padding(18.dp)
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                }
                .focusRequester(first),
        ) {
            Text("GÖNDERİLİYOR", style = GrokType.eyebrow, color = GrokSoft)
            Text(title, style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(GrokPink))
            }
            Text("$pct%  ·  ${done / 1024 / 1024} / ${total / 1024 / 1024} MB", style = GrokType.cardMeta, color = GrokSoft, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            FocusText("Arka planda sürdür", onBackground)
            FocusText("Durdur", onCancel)
        }
    }
    LaunchedEffect(title) {
        repeat(8) {
            kotlinx.coroutines.delay(40)
            runCatching { first.requestFocus() }
        }
    }
}

@Composable
fun PcResumeOverlay(
    offer: ResumeOffer,
    onContinue: () -> Unit,
    onStartOver: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onStartOver,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            Modifier
                .width(460.dp)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.28f), RoundedCornerShape(14.dp))
                .padding(22.dp),
        ) {
            Text("KALDIĞIN YER", style = GrokType.eyebrow, color = GrokSoft)
            Text(offer.title.ifBlank { "Video" }, style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
            Text(
                "${(offer.seconds * 1000).toLong().formatClock()} / ${(offer.duration * 1000).toLong().formatClock()} noktasından devam et?",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
            )
            FocusableAction(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
            ) { focused ->
                Text(
                    "Devam et",
                    style = GrokType.button,
                    color = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                )
            }
            FocusableAction(
                onClick = onStartOver,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { focused ->
                Text(
                    "Baştan oynat",
                    style = GrokType.button,
                    color = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                )
            }
        }
        LaunchedEffect(offer.seconds) {
            repeat(8) {
                kotlinx.coroutines.delay(40)
                runCatching { first.requestFocus() }
            }
        }
    }
}

@Composable
fun TransferManager(
    link: LinkController,
    onClose: () -> Unit,
) {
    val ui by link.ui.collectAsState()
    val first = remember { FocusRequester() }
    InterceptBack { onClose(); true }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(GrokInk)
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 16.dp),
    ) {
        Text("Aktarımlar", style = GrokType.pageTitle, color = GrokWhite)
        Text("Kopya ve yayınları durdur", style = GrokType.heroMeta, color = GrokMuted, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
        if (ui.jobs.isEmpty()) {
            Text("Aktif veya geçmiş iş yok.", style = GrokType.cardMeta, color = GrokMuted)
        } else {
            ui.jobs.forEachIndexed { index, job ->
                val pcName = ui.paired.firstOrNull { it.id == job.pcId }?.name ?: job.pcId
                val running = job.status == "sending" || job.status == "streaming" || job.status == "starting"
                DeviceRow(
                    title = job.title,
                    meta = "$pcName · ${job.kind} · ${job.status}",
                    value = if (running) "Durdur" else job.status,
                    modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                    onClick = { if (running) link.cancelJob(job.id) },
                )
            }
        }
        Text("Geri  ·  Kapat", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 16.dp))
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun ChoiceRow(
    title: String,
    meta: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(
                when {
                    focused -> GrokYellow
                    selected -> GrokYellow.copy(alpha = 0.18f)
                    else -> GrokInk
                },
                RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GrokType.button, color = if (focused) GrokInk else GrokWhite)
            Text(meta, style = GrokType.cardMeta, color = if (focused) GrokInk.copy(alpha = 0.7f) else GrokMuted)
        }
        Text(if (selected) "●" else "○", style = GrokType.heroMeta, color = if (focused) GrokInk else GrokSoft)
    }
}

@Composable
private fun NowPlayingCard(remote: RemoteState?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GrokSurface, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text("ŞİMDİ PC’DE", style = GrokType.eyebrow, color = GrokSoft)
        Text(remote?.title ?: "Boş", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(top = 6.dp))
        val pos = (remote?.positionMs ?: 0L).formatClock()
        val dur = (remote?.durationMs ?: 0L).formatClock()
        Text("$pos / $dur  ·  ses ${remote?.volume?.toInt() ?: 0}", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 4.dp))
        val frac = if ((remote?.durationMs ?: 0) > 0) (remote!!.positionMs.toFloat() / remote.durationMs).coerceIn(0f, 1f) else 0f
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.12f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(GrokPink))
        }
    }
}

@Composable
private fun RemotePad(
    playing: Boolean,
    firstFocus: FocusRequester,
    sideFocus: FocusRequester,
    onPlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Double) -> Unit,
) {
    val seekBack = remember { FocusRequester() }
    val playKey = remember { FocusRequester() }
    val seekFwd = remember { FocusRequester() }
    val volDown = remember { FocusRequester() }
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(GrokSurface, RoundedCornerShape(16.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("KUMANDA", style = GrokType.eyebrow, color = GrokSoft)
        Spacer(Modifier.height(18.dp))
        PadKey(
            "VOL +",
            modifier = Modifier
                .focusRequester(firstFocus)
                .focusProperties {
                    up = firstFocus
                    down = playKey
                    left = firstFocus
                    right = sideFocus
                },
            onClick = { onVolume(5.0) },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PadKey(
                "−10",
                modifier = Modifier
                    .focusRequester(seekBack)
                    .focusProperties {
                        left = seekBack
                        right = playKey
                        up = firstFocus
                        down = volDown
                    },
                onClick = { onSeek(-10_000) },
            )
            PadKey(
                if (playing) "❚❚" else "▶",
                accent = true,
                modifier = Modifier
                    .focusRequester(playKey)
                    .focusProperties {
                        left = seekBack
                        right = seekFwd
                        up = firstFocus
                        down = volDown
                    },
                onClick = onPlay,
            )
            PadKey(
                "+10",
                modifier = Modifier
                    .focusRequester(seekFwd)
                    .focusProperties {
                        left = playKey
                        right = sideFocus
                        up = firstFocus
                        down = volDown
                    },
                onClick = { onSeek(10_000) },
            )
        }
        Spacer(Modifier.height(10.dp))
        PadKey(
            "VOL −",
            modifier = Modifier
                .focusRequester(volDown)
                .focusProperties {
                    up = playKey
                    down = volDown
                    left = volDown
                    right = sideFocus
                },
            onClick = { onVolume(-5.0) },
        )
        Text("OK oynatır · sol/sağ sarar", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun PadKey(
    label: String,
    accent: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val fill = when {
        focused -> GrokYellow
        accent -> GrokYellow.copy(alpha = 0.92f)
        else -> GrokInk
    }
    Box(
        modifier
            .size(if (accent) 74.dp else 64.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (focused || accent) Modifier else Modifier.border(1.dp, Color.White.copy(0.12f), CircleShape))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (focused || accent) GrokInk else GrokWhite, fontSize = 13.sp)
    }
}

@Composable
private fun JobRow(job: TransferJob) {
    val pct = if (job.total > 0) (job.done * 100 / job.total).toInt() else 0
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(GrokSurface, RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Text(job.title, style = GrokType.cardTitle, color = GrokWhite)
        Text("${job.kind} · ${job.status} · $pct%", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 3.dp))
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.1f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(GrokPink))
        }
    }
}

@Composable
fun DeviceOptions(
    pc: PairedPc,
    connected: Boolean,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.grokplayer.tv.ui.components.ModalMenu(
        title = pc.name,
        meta = if (connected) "${pc.host} · bağlı" else "${pc.host} · eşleşmiş",
        onDismiss = onDismiss,
        width = 400.dp,
        actions = buildList {
            if (connected) {
                add(com.grokplayer.tv.ui.components.ModalAction("Yönet", onManage))
                add(com.grokplayer.tv.ui.components.ModalAction("Bağlantıyı kes", onDisconnect))
            } else {
                add(com.grokplayer.tv.ui.components.ModalAction("Bağlan", onConnect))
            }
            add(com.grokplayer.tv.ui.components.ModalAction("Eşleşmeyi unut", onForget))
        },
    )
}

@Composable
private fun DeviceRow(
    title: String,
    meta: String,
    value: String,
    modifier: Modifier = Modifier,
    online: Boolean = false,
    enabled: Boolean = true,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    FocusableAction(
        onClick = { if (enabled) onClick() },
        onLongClick = onLongClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = shape,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(GrokSurface, shape)
                .then(if (focused) Modifier.border(1.5.dp, GrokYellow, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(8.dp)
                    .background(
                        when {
                            !enabled -> Color(0xFF3A3A42)
                            online -> Color(0xFF3DCE6A)
                            else -> Color(0xFF5A5A62)
                        },
                        CircleShape,
                    ),
            )
            Column(Modifier.weight(1f)) {
                Text(title, style = GrokType.cardTitle, color = if (enabled) GrokWhite else GrokMuted)
                Text(meta, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
            }
            Text(value, style = GrokType.heroMeta, color = if (enabled) GrokSoft else GrokMuted)
        }
    }
}

@Composable
private fun CycleRow(title: String, value: String, onClick: () -> Unit) {
    DeviceRow(title = title, meta = "OK ile değiştir", value = value, onClick = onClick)
}

@Composable
private fun FocusText(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Text(
        label,
        style = GrokType.button,
        color = if (focused) GrokInk else GrokWhite,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    )
}

private fun nextIndex(tracks: List<com.grokplayer.tv.data.link.RemoteTrack>?): Int? {
    if (tracks.isNullOrEmpty()) return null
    val cur = tracks.indexOfFirst { it.selected }.let { if (it < 0) 0 else it }
    return tracks[(cur + 1) % tracks.size].index
}

private fun dubLabel(value: String?) = when (value) {
    "dub" -> "Dublaj"
    "original" -> "Orijinal"
    else -> "Alıcının ayarı"
}
