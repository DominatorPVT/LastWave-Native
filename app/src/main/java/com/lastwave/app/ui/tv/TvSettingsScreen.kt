package com.lastwave.app.ui.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.lastwave.app.BuildConfig
import com.lastwave.app.R
import com.lastwave.app.data.local.SettingsPreferences
import com.lastwave.app.data.local.ThemeMode
import com.lastwave.app.data.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The settings that matter on a TV, all backed by the shared stores. */
data class TvSettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val crossfadeEnabled: Boolean = false,
    val crossfadeSeconds: Int = 5,
)

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    private val settingsPreferences: SettingsPreferences,
) : ViewModel() {

    val uiState: StateFlow<TvSettingsUiState> = combine(
        themeRepository.uiState,
        settingsPreferences.settings,
    ) { theme, misc ->
        TvSettingsUiState(
            themeMode = theme.themeMode,
            amoled = theme.amoled,
            crossfadeEnabled = misc.crossfadeEnabled,
            crossfadeSeconds = misc.crossfadeSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TvSettingsUiState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        themeRepository.setThemeMode(mode)
    }

    fun setAmoled(enabled: Boolean) = viewModelScope.launch {
        themeRepository.setAmoled(enabled)
    }

    fun setCrossfade(enabled: Boolean) = viewModelScope.launch {
        settingsPreferences.setCrossfadeEnabled(enabled)
    }

    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch {
        settingsPreferences.setCrossfadeSeconds(seconds.coerceIn(1, 12))
    }
}

@Composable
fun TvSettingsTab(viewModel: TvHomeViewModel = hiltViewModel()) {
    val settingsViewModel: TvSettingsViewModel = hiltViewModel()
    val ui by settingsViewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.tv_tab_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        item(key = "appearance-header") {
            TvSettingsSectionTitle(stringResource(R.string.tv_settings_appearance), Icons.Rounded.Palette)
        }
        item(key = "theme") {
            TvThemeModeRow(selected = ui.themeMode, onSelect = settingsViewModel::setThemeMode)
        }
        item(key = "amoled") {
            TvSettingsRow(
                icon = Icons.Rounded.Contrast,
                title = stringResource(R.string.tv_settings_amoled),
                subtitle = stringResource(R.string.tv_settings_amoled_desc),
                checked = ui.amoled,
                onToggle = { settingsViewModel.setAmoled(!ui.amoled) },
            )
        }

        item(key = "playback-header") {
            TvSettingsSectionTitle(stringResource(R.string.tv_settings_playback), Icons.Rounded.GraphicEq)
        }
        item(key = "crossfade") {
            TvSettingsRow(
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.tv_settings_crossfade),
                subtitle = stringResource(R.string.tv_settings_crossfade_desc),
                checked = ui.crossfadeEnabled,
                onToggle = { settingsViewModel.setCrossfade(!ui.crossfadeEnabled) },
            )
        }
        if (ui.crossfadeEnabled) {
            item(key = "crossfade-seconds") {
                TvStepperRow(
                    title = stringResource(R.string.tv_settings_crossfade_seconds, ui.crossfadeSeconds),
                    value = ui.crossfadeSeconds,
                    min = 1,
                    max = 12,
                    onChange = settingsViewModel::setCrossfadeSeconds,
                )
            }
        }

        item(key = "about-header") {
            TvSettingsSectionTitle(stringResource(R.string.tv_settings_about), Icons.Rounded.Info)
        }
        item(key = "about-card") {
            TvAboutCard()
        }
        item(key = "remote-help") {
            TvRemoteTipsCard()
        }
    }
}

@Composable
private fun TvSettingsSectionTitle(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Theme mode: three chips in a focus group — D-pad LEFT/RIGHT walks them. */
@Composable
private fun TvThemeModeRow(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val modes = remember { listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        modes.forEach { mode ->
            val interaction = remember { MutableInteractionSource() }
            var isFocused by remember { mutableStateOf(false) }
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .tvLift(focused = isFocused, shape = RoundedCornerShape(14.dp), focusedScale = 1.05f, elevation = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .clickable(interactionSource = interaction, indication = null) { onSelect(mode) }
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) {
                Text(
                    text = when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.tv_theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.tv_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.tv_theme_dark)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** A settings line: whole row is the D-pad target; OK activates the toggle. */
@Composable
private fun TvSettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvLift(focused = isFocused, shape = RoundedCornerShape(16.dp), focusedScale = 1.01f, elevation = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TvToggle(checked = checked)
    }
}

/** Visual-only switch (the row is the click target, so OK never double-fires). */
@Composable
private fun TvToggle(checked: Boolean) {
    val thumbStart by animateDpAsState(
        targetValue = if (checked) 33.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "tvToggleThumb",
    )
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (checked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                }
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbStart)
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (checked) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                .align(Alignment.CenterStart),
        )
    }
}

/** - value + stepper row for small numbers (crossfade seconds). */
@Composable
private fun TvStepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TvRoundKeyButton(
            icon = Icons.Rounded.Remove,
            contentDescription = "-",
            enabled = value > min,
            onClick = { onChange(value - 1) },
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(40.dp),
        )
        TvRoundKeyButton(
            icon = Icons.Rounded.Add,
            contentDescription = "+",
            enabled = value < max,
            onClick = { onChange(value + 1) },
        )
    }
}

@Composable
private fun TvRoundKeyButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(48.dp)
            .tvLift(
                focused = isFocused,
                shape = CircleShape,
                focusedScale = 1.1f,
                elevation = 8.dp,
                pressed = false,
            )
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceContainerLow
                    isFocused -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun TvAboutCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.tv_settings_about_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.tv_settings_about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TvRemoteTipsCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_settings_remote_tips),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.tv_settings_remote_tips_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
