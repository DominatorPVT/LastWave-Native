package com.lastwave.app.ui.tv

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ShuffleOn
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.lastwave.app.R
import com.lastwave.app.data.local.db.SongPlayStatsEntity
import com.lastwave.app.data.music.YouTubeMusicTrack
import com.lastwave.app.playback.PlayableTrack
import com.lastwave.app.ui.theme.ArtworkShape
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Leanback (Android TV) shell: a left icon rail (Home / Search) plus the
 * focused pane. D-pad modelled on a proven TV client:
 *
 *  - exactly one focus owner decides where focus starts (play button when a
 *    track is loaded, the rail's Home item otherwise) so the remote is never
 *    dead on launch;
 *  - LEFT from the content pane always lands on the rail, RIGHT from the rail
 *    re-enters the content, DOWN from the search field reaches the results
 *    (D-pad Up/Down are intercepted on the field itself);
 *  - horizontal shelves steer scroll so the focused card becomes the leading
 *    visible card, and default bring-into-view scrolling is disabled inside
 *    shelves to keep movement calm;
 *  - the remote's media keys (play/pause, next/previous, ff/rew) work from
 *    anywhere on the screen;
 *  - focused elements lift towards the viewer (spring scale + soft shadow,
 *    no outlines), like tvOS.
 *
 * All playback goes through the shared [com.lastwave.app.playback.MusicPlayer]
 * singleton, so the media session, queue and radio behave exactly as on phone.
 */

private val LiftScaleSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
private val LiftShadowSpring = spring<Dp>(dampingRatio = 1f, stiffness = 420f)
private const val SHELF_GAP_DP = 16

/** tvOS-style focus: the focused element grows, casts a soft shadow and gains a brand ring. */
@Composable
internal fun Modifier.tvLift(
    focused: Boolean,
    shape: Shape,
    focusedScale: Float = 1.06f,
    elevation: Dp = 12.dp,
    pressed: Boolean = false,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 1f + (focusedScale - 1f) * 0.35f
            focused -> focusedScale
            else -> 1f
        },
        animationSpec = LiftScaleSpring,
        label = "tvLiftScale",
    )
    val shadow by animateDpAsState(
        targetValue = if (focused && !pressed) elevation else 0.dp,
        animationSpec = LiftShadowSpring,
        label = "tvLiftShadow",
    )
    val ring by animateColorAsState(
        targetValue = if (focused && !pressed) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "tvLiftRing",
    )
    return this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = shadow.toPx()
            this.shape = shape
            clip = true
            ambientShadowColor = Color.Black
            spotShadowColor = Color.Black
        }
        // Visible focus line: users on a D-pad always know which element is active.
        .border(3.dp, ring, shape)
}

/**
 * A shelf must not let the framework scroll it per focus move: the steering
 * effect below is the only scroller, so movement stays calm and reversible.
 */
@OptIn(ExperimentalFoundationApi::class)
private object TvRowNoScrollSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
fun TvHomeRoot(viewModel: TvHomeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(TvTab.HOME) }
    val chrome by viewModel.musicPlayer.chromeState.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val railHomeFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hasTrack = chrome.current != null

    // The single owner of initial focus: play button when a track is loaded,
    // the rail's Home item otherwise. Without this the remote is dead —
    // Compose delivers no key events until some node holds focus.
    LaunchedEffect(tab, hasTrack) {
        if (tab != TvTab.HOME) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            if (hasTrack) playFocus.requestFocus() else railHomeFocus.requestFocus()
        }
    }

    // Remote BACK returns from any tab to Home instead of leaving the app.
    BackHandler(enabled = tab != TvTab.HOME) {
        keyboard?.hide()
        tab = TvTab.HOME
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    // Remote media keys work from anywhere on the screen.
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE,
                    -> {
                        if (hasTrack) viewModel.togglePlayPause()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_NEXT,
                    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    -> {
                        if (hasTrack) viewModel.next()
                        true
                    }
                    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    android.view.KeyEvent.KEYCODE_MEDIA_REWIND,
                    -> {
                        if (hasTrack) viewModel.previous()
                        true
                    }
                    else -> false
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            TvNavRail(
                selectedTab = tab,
                onSelect = { tab = it },
                isPlaying = chrome.isPlaying,
                homeItemFocus = railHomeFocus,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // LEFT from anywhere in the content that would leave the
                    // pane lands on the rail's Home item — the rail is always
                    // one press away, in every row and every scroll position.
                    .focusProperties { left = railHomeFocus },
            ) {
                when (tab) {
                    TvTab.HOME -> TvHomeTab(
                        viewModel = viewModel,
                        playFocus = playFocus,
                        onSelectSearch = { tab = TvTab.SEARCH },
                        onOpenLyrics = { tab = TvTab.LYRICS },
                    )
                    TvTab.QUEUE -> TvQueueTab(viewModel = viewModel, railHomeFocus = railHomeFocus)
                    TvTab.LYRICS -> TvLyricsTab(viewModel = viewModel, railHomeFocus = railHomeFocus)
                    TvTab.SEARCH -> TvSearchTab(viewModel)
                    TvTab.SETTINGS -> TvSettingsTab()
                }
            }
        }
    }
}

private enum class TvTab { HOME, QUEUE, LYRICS, SEARCH, SETTINGS }

/** Brand mark: lime disc with the four-bar equalizer, drawn in place (no bitmap). */
@Composable
private fun TvLogoBadge(modifier: Modifier = Modifier) {
    // The original LastWave mark, exactly as the launcher draws it: the true
    // 4-bar logo vector on the brand lime disc (no canvas approximation).
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFFFC9FB00)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_logo),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.72f),
        )
    }
}

@Composable
private fun TvNavRail(
    selectedTab: TvTab,
    onSelect: (TvTab) -> Unit,
    isPlaying: Boolean,
    homeItemFocus: FocusRequester,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(112.dp)
            .padding(vertical = 40.dp)
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TvLogoBadge(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.weight(1f))
        TvRailItem(
            icon = Icons.Rounded.Home,
            label = stringResource(R.string.tv_tab_home),
            selected = selectedTab == TvTab.HOME,
            modifier = Modifier.focusRequester(homeItemFocus),
            onClick = { onSelect(TvTab.HOME) },
        )
        TvRailItem(
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            label = stringResource(R.string.tv_tab_queue),
            selected = selectedTab == TvTab.QUEUE,
            onClick = { onSelect(TvTab.QUEUE) },
        )
        TvRailItem(
            icon = Icons.Rounded.Search,
            label = stringResource(R.string.tv_tab_search),
            selected = selectedTab == TvTab.SEARCH,
            onClick = { onSelect(TvTab.SEARCH) },
        )
        TvRailItem(
            icon = Icons.Rounded.Settings,
            label = stringResource(R.string.tv_tab_settings),
            selected = selectedTab == TvTab.SETTINGS,
            onClick = { onSelect(TvTab.SETTINGS) },
        )
        Spacer(modifier = Modifier.weight(1f))
        if (isPlaying) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp).alpha(0.9f),
            )
        }
    }
}

/** Rail entry: focus lifts the disc; no outlines. */
@Composable
private fun TvRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .tvLift(focused = isFocused, shape = CircleShape, focusedScale = 1.12f, elevation = 10.dp)
                .clip(CircleShape)
                .background(
                    when {
                        selected -> MaterialTheme.colorScheme.primaryContainer
                        isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
                        else -> Color.Transparent
                    }
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    isFocused -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TvHomeTab(
    viewModel: TvHomeViewModel,
    playFocus: FocusRequester,
    onSelectSearch: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        item(key = "greeting") { TvGreeting() }
        item(key = "hero") {
            TvNowPlayingHero(
                viewModel = viewModel,
                playFocus = playFocus,
                onSelectSearch = onSelectSearch,
                onOpenLyrics = onOpenLyrics,
            )
        }
        if (viewModel.homeSuggestions.isNotEmpty()) {
            item(key = "suggested-header") {
                TvSectionTitle(stringResource(R.string.tv_suggested_for_you))
            }
            item(key = "suggested-row") {
                TvSuggestionShelf(tracks = viewModel.homeSuggestions, onPlay = viewModel::playTrack)
            }
        }
        if (viewModel.topTracks.isNotEmpty()) {
            item(key = "top-header") { TvSectionTitle(stringResource(R.string.tv_top_tracks)) }
            item(key = "top-row") {
                TvShelfRow(rows = viewModel.topTracks, onPlay = viewModel::playHistoryRow)
            }
        }
        if (viewModel.recentTracks.isNotEmpty()) {
            item(key = "recent-header") { TvSectionTitle(stringResource(R.string.tv_recently_played)) }
            item(key = "recent-row") {
                TvShelfRow(rows = viewModel.recentTracks, onPlay = viewModel::playHistoryRow)
            }
        }
        if (viewModel.topTracks.isEmpty() && viewModel.recentTracks.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.tv_empty_history),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TvGreeting() {
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.tv_greeting_morning)
        in 12..16 -> stringResource(R.string.tv_greeting_afternoon)
        in 17..21 -> stringResource(R.string.tv_greeting_evening)
        else -> stringResource(R.string.tv_greeting_night)
    }
    Column {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val formatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }
        Text(
            text = remember { LocalDate.now().format(formatter) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun TvSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/** Now-playing hero: the screen's single focal point — art, words, transport. */
@Composable
private fun TvNowPlayingHero(
    viewModel: TvHomeViewModel,
    playFocus: FocusRequester,
    onSelectSearch: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val chrome by viewModel.musicPlayer.chromeState.collectAsStateWithLifecycle()
    val progress by viewModel.musicPlayer.progressState.collectAsStateWithLifecycle()
    val shuffle by viewModel.shuffleEnabled.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val seekBarFocus = remember { FocusRequester() }
    var seekBarFocused by remember { mutableStateOf(false) }
    val track: PlayableTrack? = chrome.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        TvArtwork(artworkUrl = track?.artworkUrl, size = 220.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (track == null) {
                // Nothing playing: this panel is itself a focus target so the
                // content pane is always reachable with the remote. Activating
                // it opens Search.
                val interaction = remember { MutableInteractionSource() }
                var promptFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvLift(focused = promptFocused, shape = RoundedCornerShape(20.dp), focusedScale = 1.02f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (promptFocused) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        )
                        .onFocusChanged { promptFocused = it.isFocused }
                        .clickable(interactionSource = interaction, indication = null, onClick = onSelectSearch)
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(R.string.tv_nothing_playing),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.tv_nothing_playing_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                // D-pad scrub row: focus it and press LEFT/RIGHT for ±10 s.
                // The playhead animates smoothly, the bar wears a brand
                // gradient, and a thumb dot blooms when focused (BitChord feel).
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .focusRequester(seekBarFocus)
                        .focusable()
                        .onFocusChanged { seekBarFocused = it.isFocused }
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val delta = when (event.key) {
                                Key.DirectionRight -> 10_000L
                                Key.DirectionLeft -> -10_000L
                                else -> return@onPreviewKeyEvent false
                            }
                            viewModel.seekTo(
                                (progress.positionMs + delta).coerceIn(0L, progress.durationMs),
                            )
                            true
                        },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val fraction = if (progress.durationMs > 0) {
                        (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f)
                    } else 0f
                    val smoothFraction by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
                        label = "tvSeekFraction",
                    )
                    val barHeight by animateDpAsState(
                        targetValue = if (seekBarFocused) 10.dp else 6.dp,
                        animationSpec = LiftShadowSpring,
                        label = "tvSeekHeight",
                    )
                    val thumbSize by animateDpAsState(
                        targetValue = if (seekBarFocused) 22.dp else 14.dp,
                        animationSpec = LiftScaleSpring,
                        label = "tvSeekThumb",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(smoothFraction)
                            .height(barHeight)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    ),
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = (maxWidth - thumbSize) * smoothFraction)
                            .size(thumbSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = DateUtils.formatElapsedTime(progress.positionMs / 1000),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = DateUtils.formatElapsedTime(progress.durationMs / 1000),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvTransportButton(
                        icon = if (shuffle) Icons.Rounded.ShuffleOn else Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.tv_shuffle),
                        active = shuffle,
                        onClick = viewModel::toggleShuffle,
                    )
                    TvTransportButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.tv_previous),
                        onClick = viewModel::previous,
                    )
                    TvTransportButton(
                        icon = if (chrome.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (chrome.isPlaying) {
                            stringResource(R.string.tv_pause)
                        } else {
                            stringResource(R.string.tv_play)
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        iconTint = MaterialTheme.colorScheme.onPrimary,
                        iconSize = 40.dp,
                        buttonSize = 72.dp,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = viewModel::togglePlayPause,
                    )
                    TvTransportButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.tv_next),
                        onClick = viewModel::next,
                    )
                    TvTransportButton(
                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = stringResource(R.string.tv_repeat),
                        active = repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = viewModel::cycleRepeatMode,
                    )
                }
            }
        }
        if (track != null) {
            TvHeroLyricsButton(onClick = onOpenLyrics)
        }
    }
}

/** Round Lyrics shortcut on the hero's right edge — OK opens the Lyrics tab. */
@Composable
private fun TvHeroLyricsButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .tvLift(focused = isFocused, shape = CircleShape, focusedScale = 1.12f, elevation = 12.dp)
                .clip(CircleShape)
                .background(
                    if (isFocused) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = stringResource(R.string.tv_tab_lyrics),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(R.string.tv_tab_lyrics),
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private val TransportShape = CircleShape

@Composable
private fun TvTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    containerColor: Color = Color.Transparent,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconSize: Dp = 28.dp,
    buttonSize: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(buttonSize)
            .tvLift(
                focused = isFocused,
                shape = TransportShape,
                focusedScale = 1.14f,
                elevation = 10.dp,
            )
            .clip(TransportShape)
            .background(
                when {
                    isFocused && containerColor == Color.Transparent -> MaterialTheme.colorScheme.surfaceContainerHighest
                    else -> containerColor
                }
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused && containerColor == Color.Transparent) {
                MaterialTheme.colorScheme.onSurface
            } else {
                if (active && containerColor == Color.Transparent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    iconTint
                }
            },
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Horizontal shelf with BitChord-style focus steering: entering the row lands
 * on the leading visible card, the focused card is scrolled to the lead
 * position, and the framework's per-focus bring-into-view scroll is disabled
 * inside the shelf so nothing double-scrolls.
 */
@OptIn(ExperimentalFoundationApi::class)
/**
 * BitChord-style suggestion shelf: a horizontal run of [TvTrackCard]s fed by the
 * YouTube Music home feed (chart fallback), placed at the very top of the home
 * tab so the screen opens with concrete music, not an empty shell.
 */
@Composable
private fun TvSuggestionShelf(tracks: List<YouTubeMusicTrack>, onPlay: (YouTubeMusicTrack) -> Unit) {
    val state = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var leadIndex by remember { mutableIntStateOf(0) }
    val entry = remember { FocusRequester() }
    val density = LocalDensity.current

    LaunchedEffect(focusedIndex) {
        if (focusedIndex < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: return@LaunchedEffect
        val gap = with(density) { SHELF_GAP_DP.dp.toPx() }
        val slots = ((info.viewportSize.width + gap) / (itemSize + gap)).toInt().coerceAtLeast(1)
        val next = when {
            focusedIndex < leadIndex -> focusedIndex
            focusedIndex > leadIndex + slots - 1 -> focusedIndex - slots + 1
            else -> leadIndex
        }
        if (next != leadIndex || state.firstVisibleItemIndex != next || state.firstVisibleItemScrollOffset != 0) {
            leadIndex = next
            state.animateScrollToItem(next)
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvRowNoScrollSpec) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(SHELF_GAP_DP.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { onEnter = { runCatching { entry.requestFocus() } } }
                .focusGroup(),
        ) {
            itemsIndexed(tracks, key = { index, track -> "$index-${track.videoId}" }) { index, track ->
                Box(
                    modifier = Modifier
                        .then(if (index == leadIndex) Modifier.focusRequester(entry) else Modifier)
                        .onFocusChanged { if (it.hasFocus) focusedIndex = index },
                ) {
                    TvTrackCard(
                        title = track.title,
                        artist = track.artist,
                        artworkUrl = track.artworkUrl,
                        onClick = { onPlay(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvShelfRow(rows: List<SongPlayStatsEntity>, onPlay: (SongPlayStatsEntity) -> Unit) {
    val state = rememberLazyListState()
    var focusedIndex by remember { mutableIntStateOf(-1) }
    var leadIndex by remember { mutableIntStateOf(0) }
    val entry = remember { FocusRequester() }
    val density = LocalDensity.current

    LaunchedEffect(focusedIndex) {
        if (focusedIndex < 0) return@LaunchedEffect
        val info = state.layoutInfo
        val itemSize = info.visibleItemsInfo.firstOrNull()?.size ?: return@LaunchedEffect
        val gap = with(density) { SHELF_GAP_DP.dp.toPx() }
        val slots = ((info.viewportSize.width + gap) / (itemSize + gap)).toInt().coerceAtLeast(1)
        val next = when {
            focusedIndex < leadIndex -> focusedIndex
            focusedIndex > leadIndex + slots - 1 -> focusedIndex - slots + 1
            else -> leadIndex
        }
        if (next != leadIndex || state.firstVisibleItemIndex != next || state.firstVisibleItemScrollOffset != 0) {
            leadIndex = next
            state.animateScrollToItem(next)
        }
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides TvRowNoScrollSpec) {
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(SHELF_GAP_DP.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusProperties { onEnter = { runCatching { entry.requestFocus() } } }
                .focusGroup(),
        ) {
            itemsIndexed(rows, key = { index, row -> "$index-${row.trackKey}" }) { index, row ->
                Box(
                    modifier = Modifier
                        .then(if (index == leadIndex) Modifier.focusRequester(entry) else Modifier)
                        .onFocusChanged { if (it.hasFocus) focusedIndex = index },
                ) {
                    TvTrackCard(
                        title = row.title,
                        artist = row.artist,
                        artworkUrl = row.artworkUrl,
                        onClick = { onPlay(row) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTrackCard(
    title: String,
    artist: String,
    artworkUrl: String?,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(184.dp)
            .tvLift(focused = isFocused, shape = ArtworkShape, focusedScale = 1.07f, elevation = 14.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(184.dp)
                .clip(ArtworkShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvArtwork(artworkUrl: String?, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(ArtworkShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

/** Remote-keyboard search: results land as a vertical list under the field. */
@Composable
private fun TvSearchTab(viewModel: TvHomeViewModel) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        fieldFocus.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, end = 48.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_search_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                // One line, so Up and Down have nothing to do inside the
                // field: they always move focus. Without this a D-pad gets
                // trapped in the field and the results are unreachable.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Up)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                            true
                        }
                        else -> false
                    }
                },
            placeholder = { Text(stringResource(R.string.tv_search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        )
        when {
            viewModel.isSearching -> Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            viewModel.searchResults.isEmpty() && query.isNotBlank() -> Text(
                text = stringResource(R.string.tv_search_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(viewModel.searchResults, key = { index, track -> "$index-${track.videoId}" }) { _, track ->
                    TvSearchResultRow(track = track, onPlay = { viewModel.playTrack(track) })
                }
            }
        }
    }
}

@Composable
private fun TvSearchResultRow(track: YouTubeMusicTrack, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvLift(
                focused = isFocused,
                shape = RoundedCornerShape(16.dp),
                focusedScale = 1.015f,
                elevation = 8.dp,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TvArtwork(artworkUrl = track.artworkUrl, size = 56.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Rounded.PlayArrow,
            contentDescription = stringResource(R.string.tv_play),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
    }
}
