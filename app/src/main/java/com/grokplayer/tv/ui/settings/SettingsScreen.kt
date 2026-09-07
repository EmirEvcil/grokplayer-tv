package com.grokplayer.tv.ui.settings

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SettingsInputHdmi
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.os.Build
import android.text.format.Formatter
import androidx.compose.ui.platform.LocalContext
import com.grokplayer.tv.R
import com.grokplayer.tv.data.DownloadPaths
import com.grokplayer.tv.data.PlaybackSettings
import com.grokplayer.tv.data.link.LinkController
import com.grokplayer.tv.data.link.PairedPc
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.devices.DevicesSection
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

enum class SettingsCategory(val labelRes: Int, val icon: ImageVector) {
    Playback(R.string.cat_playback, Icons.Outlined.PlayCircle),
    Picture(R.string.cat_picture, Icons.Outlined.Tv),
    Audio(R.string.cat_audio, Icons.AutoMirrored.Outlined.VolumeUp),
    Captions(R.string.cat_captions, Icons.Outlined.ClosedCaption),
    Downloads(R.string.cat_downloads, Icons.Outlined.Download),
    Devices(R.string.cat_devices, Icons.Outlined.SettingsInputHdmi),
    About(R.string.cat_about, Icons.Outlined.Info),
}

private enum class SettingsZone { Categories, Details }

@Composable
fun SettingsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
    initialCategory: SettingsCategory = SettingsCategory.Playback,
    onCategoryChanged: (SettingsCategory) -> Unit = {},
    settings: PlaybackSettings,
    link: LinkController,
    onOpenDevice: (PairedPc) -> Unit = {},
    onDeviceMenu: (PairedPc) -> Unit = {},
    onOpenTransfers: () -> Unit = {},
    focusSettingKey: String? = null,
    onFocusConsumed: () -> Unit = {},
) {
    var category by remember { mutableStateOf(initialCategory) }
    var zone by remember { mutableStateOf(SettingsZone.Categories) }
    val categoryFocus = remember {
        SettingsCategory.entries.associateWith { FocusRequester() }
    }
    val firstDetailFocus = remember { FocusRequester() }
    val settingKeys = remember {
        mapOf(
            "resume" to FocusRequester(),
            "autonext" to FocusRequester(),
            "seek" to FocusRequester(),
            "speed" to FocusRequester(),
            "hide" to FocusRequester(),
            "start" to FocusRequester(),
            "fit" to FocusRequester(),
            "maxh" to FocusRequester(),
            "alang" to FocusRequester(),
            "stereo" to FocusRequester(),
            "capon" to FocusRequester(),
            "caplang" to FocusRequester(),
            "capsize" to FocusRequester(),
            "dlq" to FocusRequester(),
            "dlpath" to FocusRequester(),
            "about" to FocusRequester(),
        )
    }
    fun categoryRequester(item: SettingsCategory): FocusRequester = categoryFocus.getValue(item)

    androidx.compose.runtime.LaunchedEffect(focusSettingKey, initialCategory) {
        category = initialCategory
        val key = focusSettingKey ?: return@LaunchedEffect
        kotlinx.coroutines.delay(50)
        if (key == "category") {
            runCatching { categoryRequester(category).requestFocus() }
        } else {
            settingKeys[key]?.let { runCatching { it.requestFocus() } }
        }
        onFocusConsumed()
    }

    BackHandler(enabled = zone == SettingsZone.Details) {
        zone = SettingsZone.Categories
        runCatching { categoryRequester(category).requestFocus() }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 14.dp),
    ) {
        Text(stringResource(R.string.nav_settings), style = GrokType.pageTitle, color = GrokWhite)
        Text(
            text = stringResource(R.string.settings_subtitle),
            style = GrokType.heroMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        Row(Modifier.weight(1f)) {
            Column(
                modifier = Modifier.width(168.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SettingsCategory.entries.forEachIndexed { index, item ->
                    val previous = SettingsCategory.entries.getOrNull(index - 1)
                    val next = SettingsCategory.entries.getOrNull(index + 1)
                    CategoryRow(
                        item = item,
                        selected = item == category,
                        modifier = Modifier
                            .focusRequester(categoryRequester(item))
                            .then(
                                if (item == category) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                            )
                            .focusProperties {
                                left = railFocus
                                right = FocusRequester.Cancel
                                up = previous?.let { categoryRequester(it) } ?: FocusRequester.Default
                                down = next?.let { categoryRequester(it) } ?: FocusRequester.Default
                            }
                            .onPreviewKeyEvent { event ->
                                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                                if (ok) return@onPreviewKeyEvent true
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                    zone = SettingsZone.Details
                                    runCatching { firstDetailFocus.requestFocus() }
                                    true
                                } else {
                                    false
                                }
                            }
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    category = item
                                    zone = SettingsZone.Categories
                                    onCategoryChanged(item)
                                }
                            },
                    )
                }
            }
            Box(
                Modifier
                    .padding(horizontal = 16.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.08f)),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(category.labelRes),
                    style = GrokType.section,
                    color = GrokWhite,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                key(category) {
                    CategoryDetails(
                        category = category,
                        settings = settings,
                        link = link,
                        onOpenDevice = onOpenDevice,
                        onDeviceMenu = onDeviceMenu,
                        onOpenTransfers = onOpenTransfers,
                        firstDetailFocus = firstDetailFocus,
                        settingKeys = settingKeys,
                        categoryFocus = categoryRequester(category),
                        onEnterDetails = { zone = SettingsZone.Details },
                    )
                }
            }
        }

        HintBar(
            parts = if (zone == SettingsZone.Details) {
                listOf(
                    stringResource(R.string.hint_change),
                    stringResource(R.string.hint_back_categories),
                )
            } else {
                listOf(
                    stringResource(R.string.hint_open_details),
                    stringResource(R.string.hint_back_menu),
                )
            },
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun CategoryDetails(
    category: SettingsCategory,
    settings: PlaybackSettings,
    link: LinkController,
    onOpenDevice: (PairedPc) -> Unit,
    onDeviceMenu: (PairedPc) -> Unit,
    onOpenTransfers: () -> Unit,
    firstDetailFocus: FocusRequester,
    settingKeys: Map<String, FocusRequester>,
    categoryFocus: FocusRequester,
    onEnterDetails: () -> Unit,
) {
    val context = LocalContext.current
    fun row(key: String, first: Boolean = false): Modifier {
        return Modifier
            .then(if (first) Modifier.focusRequester(firstDetailFocus) else Modifier)
            .focusRequester(settingKeys.getValue(key))
            .focusProperties { left = categoryFocus }
            .onFocusChanged { if (it.isFocused) onEnterDetails() }
    }
    when (category) {
        SettingsCategory.Playback -> {
            ToggleRow("Kaldığın yerden devam et", "Videoları bıraktığın noktadan aç.", settings.resumeEnabled, { settings.toggleResume() }, row("resume", true))
            ToggleRow("Sonraki videoyu otomatik oynat", null, settings.autoNext, { settings.toggleAutoNext() }, row("autonext"))
            ValueRow("İleri / geri sarma adımı", settings.seekStepLabel, { settings.cycleSeekStep() }, row("seek"))
            ValueRow("Varsayılan oynatma hızı", settings.speedLabel, { settings.cycleSpeed() }, row("speed"))
            ValueRow("Kontrolleri gizleme süresi", settings.hideControlsLabel, { settings.cycleHideControls() }, row("hide"))
            ValueRow("Açılış ekranı", settings.startScreenLabel, { settings.cycleStartScreen() }, row("start"))
        }
        SettingsCategory.Picture -> {
            ValueRow("Görüntü sığdırma", settings.fitModeLabel, { settings.cycleFitMode() }, row("fit", true))
            ValueRow("Üst çözünürlük", settings.maxHeightLabel, { settings.cycleMaxHeight() }, row("maxh"))
        }
        SettingsCategory.Audio -> {
            ValueRow("Tercih edilen ses dili", settings.audioLangLabel, { settings.cycleAudioLang() }, row("alang", true))
            ToggleRow("Yalnızca stereo", "Çok kanallı izleri iki kanala düşür.", settings.stereoOnly, { settings.toggleStereoOnly() }, row("stereo"))
        }
        SettingsCategory.Captions -> {
            ToggleRow("Altyazıyı varsayılan aç", "Uygun iz varsa oynatmada otomatik seç.", settings.captionsOn, { settings.toggleCaptionsOn() }, row("capon", true))
            ValueRow("Tercih edilen altyazı dili", settings.captionLangLabel, { settings.cycleCaptionLang() }, row("caplang"))
            ValueRow("Altyazı boyutu", settings.captionSizeLabel, { settings.cycleCaptionSize() }, row("capsize"))
        }
        SettingsCategory.Downloads -> {
            ValueRow("İndirme kalitesi", settings.downloadHeightLabel, { settings.cycleDownloadHeight() }, row("dlq", true))
            InfoRow(
                title = "Kayıt klasörü",
                value = DownloadPaths.dir(context).absolutePath.substringAfter("/files/"),
                modifier = row("dlpath"),
            )
            InfoRow(
                title = "Boş alan",
                value = Formatter.formatFileSize(context, DownloadPaths.freeBytes(context)),
                modifier = Modifier
                    .focusProperties { left = categoryFocus }
                    .onFocusChanged { if (it.isFocused) onEnterDetails() },
            )
        }
        SettingsCategory.Devices -> {
            DevicesSection(
                link = link,
                firstFocus = firstDetailFocus,
                leftFocus = categoryFocus,
                onOpen = onOpenDevice,
                onDeviceMenu = onDeviceMenu,
                onOpenTransfers = onOpenTransfers,
                onEnterDetails = onEnterDetails,
            )
        }
        SettingsCategory.About -> {
            val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
            InfoRow("Sürüm", info?.versionName ?: "0.1.0", row("about", true))
            InfoRow("Paket", context.packageName, Modifier.focusProperties { left = categoryFocus }.onFocusChanged { if (it.isFocused) onEnterDetails() })
            InfoRow("Cihaz", Build.MODEL, Modifier.focusProperties { left = categoryFocus }.onFocusChanged { if (it.isFocused) onEnterDetails() })
            InfoRow("Android", Build.VERSION.RELEASE, Modifier.focusProperties { left = categoryFocus }.onFocusChanged { if (it.isFocused) onEnterDetails() })
        }
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    SettingCard(onClick = { }, modifier = modifier) {
        Text(title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.weight(1f))
        Text(value, style = GrokType.heroMeta, color = GrokSoft, maxLines = 2)
    }
}

@Composable
private fun CategoryRow(
    item: SettingsCategory,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) GrokSurface else Color.Transparent)
            .then(if (focused) Modifier.border(1.5.dp, GrokYellow, shape) else Modifier)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) GrokPink else Color.Transparent),
        )
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = if (selected || focused) GrokWhite else GrokMuted,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(18.dp),
        )
        Text(
            text = stringResource(item.labelRes),
            style = GrokType.nav,
            color = if (selected || focused) GrokWhite else GrokMuted,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingCard(onClick = onClick, modifier = modifier) { focused ->
        Column(Modifier.weight(1f)) {
            Text(title, style = GrokType.cardTitle, color = GrokWhite)
            if (subtitle != null) {
                Text(subtitle, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Box(
            Modifier
                .width(40.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (checked) GrokPink else GrokMuted.copy(alpha = 0.4f)),
        ) {
            Box(
                Modifier
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(2.dp)
                    .size(18.dp)
                    .background(if (focused) GrokYellow else GrokWhite, CircleShape),
            )
        }
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingCard(onClick = onClick, modifier = modifier) {
        Text(title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.weight(1f))
        Text(value, style = GrokType.heroMeta, color = GrokSoft)
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = GrokMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(shape)
            .background(GrokSurface)
            .then(if (focused) Modifier.border(2.dp, GrokYellow, shape) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = { content(focused) },
    )
}
