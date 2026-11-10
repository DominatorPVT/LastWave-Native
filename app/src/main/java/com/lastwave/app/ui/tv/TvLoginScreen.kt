package com.lastwave.app.ui.tv

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lastwave.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Which service the TV login WebView is signing into. */
enum class TvLoginTarget {
    YOUTUBE,
    SPOTIFY,
}

private const val YT_LOGIN_URL = "https://music.youtube.com/"
private const val SPOTIFY_LOGIN_URL = "https://accounts.spotify.com/en/login"

private const val COOKIE_POLL_MS = 1_200L

/**
 * Full-screen login for the TV: a WebView the remote can drive, with a polling
 * loop that waits for the session cookie and hands it to the shared stores.
 *
 * - YouTube: once music.youtube.com has SAPISID/__Secure-3PSID the raw cookie
 *   header is passed to [YtMusicAuthManager.connect] via [onComplete].
 * - Spotify: once accounts.spotify.com issues sp_dc (the post-auth marker is
 *   [com.lastwave.app.data.spotify.SpotifyLoginUrls.isPostAuth]) the value is
 *   saved through [onComplete], invalidating the stale personal token.
 *
 * BACK cancels the flow without saving anything.
 */
@Composable
fun TvLoginScreen(
    target: TvLoginTarget,
    onComplete: (credential: String) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler { onCancel() }
    val title = when (target) {
        TvLoginTarget.YOUTUBE -> stringResource(R.string.tv_login_youtube_title)
        TvLoginTarget.SPOTIFY -> stringResource(R.string.tv_login_spotify_title)
    }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val closeFocus = remember { FocusRequester() }

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
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
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
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
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
                onComplete(credential)
                return@LaunchedEffect
            }
        }
    }
}
