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
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.SettingsInputHdmi
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

private enum class SettingsCategory(val labelRes: Int, val icon: ImageVector) {
    Playback(R.string.cat_playback, Icons.Outlined.PlayCircle),
    Picture(R.string.cat_picture, Icons.Outlined.Tv),
    Audio(R.string.cat_audio, Icons.AutoMirrored.Outlined.VolumeUp),
    Captions(R.string.cat_captions, Icons.Outlined.ClosedCaption),
    Downloads(R.string.cat_downloads, Icons.Outlined.Download),
    Devices(R.string.cat_devices, Icons.Outlined.SettingsInputHdmi),
    About(R.string.cat_about, Icons.Outlined.Info),
}

@Composable
fun SettingsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var category by remember { mutableStateOf(SettingsCategory.Playback) }
    var inDetails by remember { mutableStateOf(true) }
    val categoryFocus = remember { FocusRequester() }
    val detailFocus = firstFocus

    var resumeFrom by remember { mutableStateOf(true) }
    var autoNext by remember { mutableStateOf(true) }
    var seekStep by remember { mutableStateOf("10 saniye") }
    var speed by remember { mutableStateOf("1×") }
    var hideControls by remember { mutableStateOf("2 saniye") }
    var startScreen by remember { mutableStateOf("Ana sayfa") }

    BackHandler(enabled = inDetails) {
        inDetails = false
        categoryFocus.requestFocus()
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
                    CategoryRow(
                        item = item,
                        selected = item == category,
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(categoryFocus) else Modifier)
                            .focusProperties {
                                left = railFocus
                                right = detailFocus
                            },
                        onClick = {
                            category = item
                            inDetails = true
                            detailFocus.requestFocus()
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
                if (category == SettingsCategory.Playback) {
                    ToggleRow(
                        title = "Kaldığın yerden devam et",
                        subtitle = "Videoları bıraktığın noktadan aç.",
                        checked = resumeFrom,
                        onClick = { resumeFrom = !resumeFrom },
                        modifier = Modifier.focusProperties { left = categoryFocus },
                    )
                    ToggleRow(
                        title = "Sonraki videoyu otomatik oynat",
                        subtitle = null,
                        checked = autoNext,
                        onClick = { autoNext = !autoNext },
                        modifier = Modifier.focusProperties { left = categoryFocus },
                    )
                    ValueRow(
                        title = "İleri / geri sarma adımı",
                        value = seekStep,
                        onClick = {
                            seekStep = if (seekStep == "10 saniye") "5 saniye" else "10 saniye"
                        },
                        modifier = Modifier
                            .focusRequester(detailFocus)
                            .focusProperties { left = categoryFocus },
                    )
                    ValueRow(
                        title = "Varsayılan oynatma hızı",
                        value = speed,
                        onClick = { speed = if (speed == "1×") "1,25×" else "1×" },
                        modifier = Modifier.focusProperties { left = categoryFocus },
                    )
                    ValueRow(
                        title = "Kontrolleri gizleme süresi",
                        value = hideControls,
                        onClick = { hideControls = if (hideControls == "2 saniye") "5 saniye" else "2 saniye" },
                        modifier = Modifier.focusProperties { left = categoryFocus },
                    )
                    ValueRow(
                        title = "Açılış ekranı",
                        value = startScreen,
                        onClick = {
                            startScreen = if (startScreen == "Ana sayfa") "Videolar" else "Ana sayfa"
                        },
                        modifier = Modifier.focusProperties { left = categoryFocus },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.coming_soon_body),
                        style = GrokType.comingBody,
                        color = GrokMuted,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .focusRequester(detailFocus)
                            .clickable { },
                    )
                }
            }
        }

        HintBar(
            parts = listOf(
                stringResource(R.string.hint_change),
                stringResource(R.string.hint_back_categories),
            ),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun CategoryRow(
    item: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
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
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
    content: @Composable androidx.compose.foundation.layout.RowScope.(Boolean) -> Unit,
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
