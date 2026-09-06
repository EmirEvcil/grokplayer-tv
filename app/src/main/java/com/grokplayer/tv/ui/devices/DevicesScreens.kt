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
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.link.LinkController
import com.grokplayer.tv.data.link.PairedPc
import com.grokplayer.tv.data.link.RemoteState
import com.grokplayer.tv.data.link.SendMode
import com.grokplayer.tv.data.link.SendWhen
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
    onEnterDetails: () -> Unit,
) {
    val ui by link.ui.collectAsState()
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Aynı ev ağındaki GrokPlayer PC ile eşleş. Video gönder, kopyala veya yayınla; kumanda ile PC’yi yönet.",
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        DeviceRow(
            title = "Bu TV görünür",
            meta = if (ui.visible) "Diğer cihazlar bu TV’yi görebilir" else "Eşleşme ve keşif kapalı",
            value = if (ui.visible) "Açık" else "Kapalı",
            modifier = Modifier
                .focusRequester(firstFocus)
                .focusProperties { left = leftFocus },
            onFocus = onEnterDetails,
            onClick = { link.setVisible(!ui.visible) },
        )
        DeviceRow(
            title = "Yeni cihaz eşleştir",
            meta = when {
                !ui.visible -> "Önce görünürlüğü aç"
                ui.nearby.isEmpty() -> "PC’de GrokPlayer açık olsun"
                else -> ui.nearby.joinToString { it.name }
            },
            value = "PIN göster",
            enabled = ui.visible,
            onFocus = onEnterDetails,
            onClick = { if (ui.visible) link.beginPair() },
            modifier = Modifier.focusProperties { left = leftFocus },
        )
        Spacer(Modifier.height(10.dp))
        if (ui.paired.isEmpty()) {
            Text("Henüz eşleşmiş cihaz yok.", style = GrokType.cardMeta, color = GrokMuted)
        } else {
            ui.paired.forEach { pc ->
                val jobs = ui.jobs.filter { it.pcId == pc.id }
                val live = ui.nearby.any { it.id == pc.id }
                DeviceRow(
                    title = pc.name,
                    meta = buildString {
                        append(if (live) "Çevrimiçi" else "Bekleniyor")
                        append(" · ")
                        append(pc.host)
                        val playing = ui.remote?.title
                        if (playing != null) append(" · $playing")
                        val active = jobs.firstOrNull { it.status == "sending" || it.status == "streaming" }
                        if (active != null) append(" · ${active.status}")
                    },
                    value = if (live) "Yönet" else "Bağlan",
                    online = live,
                    onFocus = {
                        onEnterDetails()
                        if (ui.visible) link.watch(pc)
                    },
                    onClick = { onOpen(pc) },
                    onLongClick = { onDeviceMenu(pc) },
                    modifier = Modifier.focusProperties { left = leftFocus },
                )
            }
        }
    }
}

@Composable
fun PairPinOverlay(pin: String, onCancel: () -> Unit) {
    RememberFocusLock()
    InterceptBack { onCancel(); true }
    BackHandler(onBack = onCancel)
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(pin) {
        repeat(4) {
            kotlinx.coroutines.delay(40)
            runCatching { cancelFocus.requestFocus() }
        }
    }
    AbsorbOpeningOk {
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.62f))
            .onFocusChanged { state ->
                if (!state.hasFocus && !state.isFocused) {
                    runCatching { cancelFocus.requestFocus() }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(460.dp)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.28f), RoundedCornerShape(14.dp))
                .padding(22.dp)
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                    up = FocusRequester.Cancel
                    down = FocusRequester.Cancel
                    previous = FocusRequester.Cancel
                    next = FocusRequester.Cancel
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("BU TV’Yİ EŞLEŞTİR", style = GrokType.eyebrow, color = GrokSoft)
            Text("PC’de Devices penceresine bu kodu yaz", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
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
            Text("Aynı Wi‑Fi gerekli · 3 dakika", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 12.dp))
            FocusText(
                "İptal",
                onClick = onCancel,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .focusRequester(cancelFocus)
                    .focusProperties {
                        left = cancelFocus
                        right = cancelFocus
                        up = cancelFocus
                        down = cancelFocus
                    },
            )
        }
    }
    }
}

@Composable
fun DeviceHub(
    link: LinkController,
    pc: PairedPc,
    onClose: () -> Unit,
) {
    val ui by link.ui.collectAsState()
    val remote = ui.remote
    val jobs = ui.jobs.filter { it.pcId == pc.id }
    DisposableEffect(pc.id) {
        link.watch(pc)
        onDispose { link.watch(null) }
    }
    InterceptBack { onClose(); true }
    BackHandler(onBack = onClose)
    Column(
        Modifier
            .fillMaxSize()
            .background(GrokInk)
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 16.dp),
    ) {
        Text(pc.name, style = GrokType.pageTitle, color = GrokWhite)
        Text(
            if (remote != null) "Bağlı · durum anlık" else "Bağlanıyor…",
            style = GrokType.heroMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            RemotePad(
                playing = remote?.playing == true,
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

@Composable
fun SendToPcSheet(
    video: LibraryVideo,
    pcs: List<PairedPc>,
    onSend: (PairedPc, SendMode, SendWhen) -> Unit,
    onDismiss: () -> Unit,
) {
    var pc by remember { mutableStateOf(pcs.first()) }
    var mode by remember { mutableStateOf(SendMode.Copy) }
    var whenPlay by remember { mutableStateOf(SendWhen.PlayNow) }
    InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize().background(GrokInk.copy(0.62f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(520.dp)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.25f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text("Gönder", style = GrokType.section, color = GrokWhite)
            Text("${video.title}  →  ${pc.name}", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            CycleRow("Hedef", pc.name) {
                val i = pcs.indexOf(pc)
                pc = pcs[(i + 1) % pcs.size]
            }
            CycleRow("Nasıl gitsin", if (mode == SendMode.Copy) "Kopyala ve oynat" else "Yayınla") {
                mode = if (mode == SendMode.Copy) SendMode.Stream else SendMode.Copy
            }
            CycleRow("Ne olsun", if (whenPlay == SendWhen.PlayNow) "Hemen oynat" else "Listeye ekle") {
                whenPlay = if (whenPlay == SendWhen.PlayNow) SendWhen.Queue else SendWhen.PlayNow
            }
            Text("İlk açılışta PC genel ayarı kullanılır.", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
            FocusText("Gönder", onClick = { onSend(pc, mode, whenPlay) })
            FocusText("Vazgeç", onClick = onDismiss)
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
    Box(Modifier.fillMaxSize().background(GrokInk.copy(0.62f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(480.dp)
                .background(GrokSurface, RoundedCornerShape(14.dp))
                .border(1.dp, GrokYellow.copy(0.25f), RoundedCornerShape(14.dp))
                .padding(18.dp),
        ) {
            Text("GÖNDERİLİYOR", style = GrokType.eyebrow, color = GrokSoft)
            Text(title, style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(0.12f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(pct / 100f).background(GrokPink))
            }
            Text("$pct%  ·  ${done / 1024 / 1024} / ${total / 1024 / 1024} MB", style = GrokType.cardMeta, color = GrokSoft, modifier = Modifier.padding(top = 8.dp, bottom = 14.dp))
            FocusText("Arka planda sürdür", onBackground)
            FocusText("İptal", onCancel)
        }
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
    onPlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Double) -> Unit,
) {
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
        PadKey("VOL +", onClick = { onVolume(5.0) })
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PadKey("−10", onClick = { onSeek(-10_000) })
            PadKey(if (playing) "❚❚" else "▶", accent = true, onClick = onPlay)
            PadKey("+10", onClick = { onSeek(10_000) })
        }
        Spacer(Modifier.height(10.dp))
        PadKey("VOL −", onClick = { onVolume(-5.0) })
        Text("OK oynatır · sol/sağ sarar", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun PadKey(label: String, accent: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val fill = when {
        focused -> GrokYellow
        accent -> GrokYellow.copy(alpha = 0.92f)
        else -> GrokInk
    }
    Box(
        Modifier
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
    onManage: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    RememberFocusLock()
    InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    val manageFocus = remember { FocusRequester() }
    val disconnectFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }
    AbsorbOpeningOk {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .width(400.dp)
                    .background(GrokSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(pc.name, style = GrokType.section, color = GrokWhite)
                Text(pc.host, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                DeviceOptionRow(
                    if (connected) "Yönet" else "Bağlan",
                    onClick = onManage,
                    modifier = Modifier
                        .focusRequester(manageFocus)
                        .focusProperties {
                            up = manageFocus
                            down = disconnectFocus
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                )
                DeviceOptionRow(
                    "Bağlantıyı kes",
                    enabled = connected,
                    onClick = onDisconnect,
                    modifier = Modifier
                        .focusRequester(disconnectFocus)
                        .focusProperties {
                            canFocus = true
                            up = manageFocus
                            down = removeFocus
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                )
                DeviceOptionRow(
                    "Cihazı kaldır",
                    onClick = onRemove,
                    modifier = Modifier
                        .focusRequester(removeFocus)
                        .focusProperties {
                            up = disconnectFocus
                            down = removeFocus
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        },
                )
            }
        }
    }
    LaunchedEffect(pc.id) {
        repeat(5) {
            kotlinx.coroutines.delay(40)
            runCatching { manageFocus.requestFocus() }
        }
    }
}

@Composable
private fun DeviceOptionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Text(
        text = label,
        style = GrokType.button,
        color = when {
            !enabled && focused -> GrokInk.copy(alpha = 0.45f)
            !enabled -> GrokMuted
            focused -> GrokInk
            else -> GrokWhite
        },
        modifier = modifier
            .fillMaxWidth()
            .background(
                when {
                    focused && enabled -> GrokYellow
                    focused -> GrokYellow.copy(alpha = 0.35f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(6.dp),
            )
            .clickable(
                enabled = true,
                interactionSource = interaction,
                indication = null,
                onClick = { if (enabled) onClick() },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
