package com.lastwave.app.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lastwave.app.R
import com.lastwave.app.data.lyrics.LyricLine
import com.lastwave.app.ui.player.LyricsUiState

/**
 * TV lyrics tab, BitChord-style: the current line is large and brand-colored
 * while the rest stays quiet, and the list auto-scrolls with playback. Lines
 * are deliberately not focusable — the remote keeps the rail, BACK returns
 * Home, and the words simply follow the song.
 */
@Composable
fun TvLyricsTab(
    viewModel: TvHomeViewModel,
    railHomeFocus: androidx.compose.ui.focus.FocusRequester,
) {
    val lyrics by viewModel.lyricsState
    val chrome by viewModel.musicPlayer.chromeState.collectAsStateWithLifecycle()
    val track = chrome.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        when (lyrics) {
            LyricsUiState.Idle, is LyricsUiState.Error, LyricsUiState.Empty ->
                TvLyricsPrompt(
                    headline = when (lyrics) {
                        is LyricsUiState.Error -> (lyrics as LyricsUiState.Error).message
                        LyricsUiState.Empty -> stringResource(R.string.tv_lyrics_not_found)
                        else -> stringResource(R.string.tv_lyrics_empty)
                    },
                    showRetry = lyrics is LyricsUiState.Error,
                    onRetry = viewModel::reloadLyrics,
                )
            LyricsUiState.Loading -> Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(R.string.tv_lyrics_loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            is LyricsUiState.Success -> {
                val success = lyrics as LyricsUiState.Success
                if (track != null) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sourceLabel = success.source
                    val subtitle = if (sourceLabel.isNullOrBlank()) {
                        track.artist
                    } else {
                        stringResource(R.string.tv_lyrics_source, sourceLabel)
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (success.isSynced) {
                    TvSyncedLyrics(lines = success.lines, progressState = viewModel.musicPlayer.progressState)
                } else {
                    val plain = success.plainLyrics?.takeIf { it.isNotBlank() }
                        ?: success.lines.joinToString(separator = "\n") { it.text }
                    Text(
                        text = plain,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSyncedLyrics(
    lines: List<LyricLine>,
    progressState: kotlinx.coroutines.flow.StateFlow<com.lastwave.app.playback.PlaybackProgressState>,
) {
    val listState = rememberLazyListState()
    val progress by progressState.collectAsStateWithLifecycle()

    // Deriving the active index from the position flow keeps per-tick work to a
    // single int comparison — recomposition only happens when the line changes.
    val activeLine by remember(lines) {
        derivedStateOf {
            val positionMs = progress.positionMs
            var index = -1
            for (i in lines.indices) {
                if (lines[i].timeMs <= positionMs) index = i else break
            }
            index
        }
    }

    LaunchedEffect(activeLine) {
        if (activeLine >= 0) {
            runCatching { listState.animateScrollToItem(activeLine) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 140.dp, bottom = 420.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
            TvLyricLineRow(line = line, active = index == activeLine)
        }
    }
}

@Composable
private fun TvLyricLineRow(line: LyricLine, active: Boolean) {
    Text(
        text = line.text,
        style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (active) 1f else 0.8f },
    )
}

@Composable
private fun TvLyricsPrompt(headline: String, showRetry: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (showRetry) {
            Spacer(modifier = Modifier.height(20.dp))
            val interaction = remember { MutableInteractionSource() }
            var focused by remember { mutableStateOf(false) }
            Text(
                text = stringResource(R.string.tv_lyrics_retry),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .tvLift(focused = focused, shape = RoundedCornerShape(14.dp), focusedScale = 1.05f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (focused) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(interactionSource = interaction, indication = null, onClick = onRetry)
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
    }
}
