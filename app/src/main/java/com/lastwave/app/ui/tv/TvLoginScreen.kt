package com.lastwave.app.ui.tv

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lastwave.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import kotlin.math.roundToInt

/** Which service the TV login WebView is signing into. */
enum class TvLoginTarget {
    YOUTUBE,
    SPOTIFY,
}

private const val YT_LOGIN_URL = "https://music.youtube.com/"
private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/en/login"

private const val COOKIE_POLL_MS = 1_200L

/**
 * Full-screen login for the TV: a WebView driven by a **virtual mouse
 * cursor**, with a polling loop that waits for the session cookie and hands
 * it to the shared stores.
 *
 * Why a cursor: login pages (accounts.google.com, accounts.spotify.com) have
 * almost no natively focusable elements, so forwarding D-pad key events into
 * the WebView moves nothing — the remote looks dead. Instead the D-pad moves
 * a visible pointer, OK synthesizes a real touch at its position, and
 * PageUp/PageDown scrolls. This works on every page regardless of focus.
 *
 * - YouTube: once music.youtube.com has SAPISID/__Secure-3PSID the raw cookie
 *   header is passed to [YtMusicAuthManager.connect] via [onComplete].
 * - Spotify: once accounts.spotify.com issues sp_dc (the post-auth marker is
 *   [com.lastwave.app.data.spotify.SpotifyLoginUrls.isPostAuth]) the value is
 *   saved through [onComplete], invalidating the stale personal token.
 *
 * The user agent drops the WebView marker ("; wv)") so Google does not refuse
 * sign-in with "this browser or app may not be secure".
 *
 * BACK goes one page back inside the flow; on the first page it cancels
 * without saving anything.
 */
@Composable
fun TvLoginScreen(
    target: TvLoginTarget,
    onComplete: (credential: String) -> Unit,
    onCancel: () -> Unit,
) {
    val title = when (target) {
        TvLoginTarget.YOUTUBE -> stringResource(R.string.tv_login_youtube_title)
        TvLoginTarget.SPOTIFY -> stringResource(R.string.tv_login_spotify_title)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Virtual cursor position in WebView pixel coordinates; NaN = hidden
    // until the first D-pad press places it at the page center.
    var cursorX by remember { mutableStateOf(Float.NaN) }
    var cursorY by remember { mutableStateOf(Float.NaN) }
    val density = LocalDensity.current
    val cursorSize = 22.dp
    val cursorHalfPx = with(density) { cursorSize.toPx() / 2f }
    val moveStepPx = with(density) { 42.dp.toPx() }

    BackHandler {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else onCancel()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier
                        .tvLift(focused = false, shape = RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.tv_login_cancel_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.tv_login_instructions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // TV rendering: full desktop layout scaled to fit.
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            // Google refuses sign-in from WebView user agents
                            // ("this browser or app may not be secure"); the
                            // WebView marker is the "; wv)" token — drop it so
                            // the login looks like the device browser.
                            settings.userAgentString =
                                settings.userAgentString.replace("; wv)", "")
                            // The cursor tap needs the WebView to hold focus
                            // (that is also what summons the on-screen
                            // keyboard when an input field is clicked).
                            isFocusable = true
                            isFocusableInTouchMode = true
                            val cm = CookieManager.getInstance()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            loadUrl(
                                when (target) {
                                    TvLoginTarget.YOUTUBE -> YT_LOGIN_URL
                                    TvLoginTarget.SPOTIFY -> SPOTIFY_LOGIN_URL
                                },
                            )
                        }.also { webView = it }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            val ke = event.nativeKeyEvent
                            if (ke.action != android.view.KeyEvent.ACTION_DOWN) {
                                // Swallow the matching ACTION_UP of the keys we
                                // handle so they never leak into Compose focus.
                                return@onPreviewKeyEvent when (ke.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                    android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                    android.view.KeyEvent.KEYCODE_ENTER,
                                    android.view.KeyEvent.KEYCODE_PAGE_UP,
                                    android.view.KeyEvent.KEYCODE_PAGE_DOWN,
                                    -> true
                                    else -> false
                                }
                            }
                            val wv = webView ?: return@onPreviewKeyEvent false
                            val code = ke.keyCode
                            when (code) {
                                android.view.KeyEvent.KEYCODE_DPAD_UP,
                                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                                -> {
                                    // First press materializes the pointer at
                                    // the center; afterwards arrows move it.
                                    // Key repeats arrive as more ACTION_DOWNs,
                                    // so holding a direction keeps moving.
                                    val centerX =
                                        if (wv.width > 0) wv.width / 2f else 400f
                                    val centerY =
                                        if (wv.height > 0) wv.height / 2f else 300f
                                    val x = if (cursorX.isNaN()) centerX else cursorX
                                    val y = if (cursorY.isNaN()) centerY else cursorY
                                    val nx = when (code) {
                                        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> x - moveStepPx
                                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> x + moveStepPx
                                        else -> x
                                    }
                                    val ny = when (code) {
                                        android.view.KeyEvent.KEYCODE_DPAD_UP -> y - moveStepPx
                                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> y + moveStepPx
                                        else -> y
                                    }
                                    cursorX = nx.coerceIn(0f, (wv.width - 1).coerceAtLeast(0).toFloat())
                                    cursorY = ny.coerceIn(0f, (wv.height - 1).coerceAtLeast(0).toFloat())
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                                android.view.KeyEvent.KEYCODE_ENTER,
                                -> {
                                    // OK = a real touch tap at the pointer.
                                    val centerX =
                                        if (wv.width > 0) wv.width / 2f else 400f
                                    val centerY =
                                        if (wv.height > 0) wv.height / 2f else 300f
                                    val x = if (cursorX.isNaN()) centerX else cursorX
                                    val y = if (cursorY.isNaN()) centerY else cursorY
                                    cursorX = x
                                    cursorY = y
                                    wv.requestFocus()
                                    val now = SystemClock.uptimeMillis()
                                    val down = MotionEvent.obtain(
                                        now, now, MotionEvent.ACTION_DOWN, x, y,
                                        1f, 1f, 0, 1f, 1f, 0, 0,
                                    )
                                    wv.dispatchTouchEvent(down)
                                    down.recycle()
                                    // Lift after a short beat so the page sees
                                    // a proper press-release gesture.
                                    wv.post {
                                        val up = MotionEvent.obtain(
                                            now, now + 60, MotionEvent.ACTION_UP, x, y,
                                            1f, 1f, 0, 1f, 1f, 0, 0,
                                        )
                                        wv.dispatchTouchEvent(up)
                                        up.recycle()
                                    }
                                    true
                                }
                                android.view.KeyEvent.KEYCODE_PAGE_UP,
                                android.view.KeyEvent.KEYCODE_PAGE_DOWN,
                                -> {
                                    wv.requestFocus()
                                    wv.dispatchKeyEvent(
                                        android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, code),
                                    )
                                    wv.dispatchKeyEvent(
                                        android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, code),
                                    )
                                    true
                                }
                                else -> false
                            }
                        },
                )
                if (!cursorX.isNaN() && !cursorY.isNaN()) {
                    // The pointer dot, drawn above the page.
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (cursorX - cursorHalfPx).roundToInt(),
                                    (cursorY - cursorHalfPx).roundToInt(),
                                )
                            }
                            .size(cursorSize)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    )
                }
            }
        }
    }

    LaunchedEffect(webView) {
        // Give the WebView focus once it is attached and laid out, so the
        // first tap already lands on the page.
        withFrameNanos { }
        webView?.requestFocus()
    }

    LaunchedEffect(target) {
        while (isActive) {
            delay(COOKIE_POLL_MS)
            val credential = withContext(Dispatchers.IO) {
                val cm = CookieManager.getInstance()
                when (target) {
                    TvLoginTarget.YOUTUBE -> {
                        val cookie = cm.getCookie("https://music.youtube.com").orEmpty()
                        val signedIn = cookie.contains("SAPISID=") ||
                            cookie.contains("__Secure-3PSID=") ||
                            cookie.contains("__Secure-3PAPISID=")
                        if (signedIn) cookie else null
                    }
                    TvLoginTarget.SPOTIFY -> {
                        val cookie = cm.getCookie("https://accounts.spotify.com").orEmpty()
                        cookie.split(";")
                            .map { it.trim() }
                            .firstOrNull { it.startsWith("sp_dc=") }
                            ?.substringAfter("sp_dc=")
                            ?.takeIf { it.isNotBlank() }
                    }
                }
            }
            if (credential != null) {
                withContext(Dispatchers.IO) { CookieManager.getInstance().flush() }
                onComplete(credential)
                return@LaunchedEffect
            }
        }
    }
}
