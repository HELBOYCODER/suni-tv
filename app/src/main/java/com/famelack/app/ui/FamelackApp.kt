package com.famelack.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famelack.app.data.Channel
import com.famelack.app.data.MediaKind
import com.famelack.app.player.PlayerHolder
import com.famelack.app.ui.components.MiniPlayerBar
import com.famelack.app.ui.screens.BrowseScreen
import com.famelack.app.ui.screens.FavoritesScreen
import com.famelack.app.ui.screens.PlayerScreen
import com.famelack.app.ui.screens.PlayerTabScreen

enum class TopTab(val kind: MediaKind?, val label: String) {
    TV(MediaKind.TV, "TV"),
    RADIO(MediaKind.RADIO, "Radio"),
    WEBCAM(MediaKind.WEBCAM, "Webcam"),
    FAVORITES(null, "Favorites"),
    PLAYER(null, "Player")
}

@Composable
fun FamelackApp() {
    val isTv = LocalIsTv.current
    var currentTab by remember { mutableStateOf(TopTab.TV) }
    var playerChannel by remember { mutableStateOf<Channel?>(null) }
    val activeChannel by PlayerHolder.currentChannelFlow.collectAsState()

    val selectTab: (TopTab) -> Unit = { selected ->
        currentTab = selected
        if (selected == TopTab.PLAYER && playerChannel != null) {
            playerChannel = null // Seamlessly transition to embedded PlayerTab
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // TV: focusable tab row on top so the D-pad flows tabs -> content
            if (isTv) TvTopTabBar(currentTab, selectTab)
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Show floating mini-player bar when stream is playing and not on full player
                if (activeChannel != null && playerChannel == null && currentTab != TopTab.PLAYER) {
                    MiniPlayerBar(
                        channel = activeChannel!!,
                        onClick = { playerChannel = activeChannel },
                        onClose = { PlayerHolder.stop() }
                    )
                }

                // Phone/touch keeps the Material bottom bar; TV uses the top tab row above
                if (!isTv) BottomBar(currentTab, selectTab)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                TopTab.FAVORITES -> FavoritesScreen(onPlay = {
                    PlayerHolder.setCurrentChannel(it)
                    playerChannel = it
                })
                TopTab.PLAYER -> PlayerTabScreen(onPlayChannel = {
                    PlayerHolder.setCurrentChannel(it)
                    playerChannel = it
                })
                else -> {
                    val kind = currentTab.kind!!
                    BrowseScreen(
                        kind = kind,
                        onPlay = {
                            PlayerHolder.setCurrentChannel(it)
                            playerChannel = it
                        }
                    )
                }
            }
        }
    }

    // Modal overlay for full screen experience
    playerChannel?.let { ch ->
        PlayerScreen(channel = ch, onClose = { playerChannel = null })
    }

    // System back navigation handling
    // TV: player overlay -> browse list -> TV tab -> exit app (never stuck:
    // on the TV tab no handler is enabled, so Back exits predictably)
    BackHandler(enabled = playerChannel != null) {
        playerChannel = null
    }

    BackHandler(enabled = playerChannel == null && currentTab != TopTab.TV) {
        currentTab = TopTab.TV
    }
}

/** TV-mode tab strip: every tab is D-pad focusable, centre button selects. */
@Composable
private fun TvTopTabBar(current: TopTab, onSelect: (TopTab) -> Unit) {
    val firstTabFocus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TopTab.entries.forEachIndexed { index, tab ->
            TvTabItem(
                tab = tab,
                selected = current == tab,
                modifier = if (index == 0) Modifier.focusRequester(firstTabFocus) else Modifier,
                onSelect = { onSelect(tab) }
            )
        }
    }
    // Guarantee the remote has a visible focus target on the very first frame
    LaunchedEffect(Unit) {
        runCatching { firstTabFocus.requestFocus() }
    }
}

@Composable
private fun TvTabItem(
    tab: TopTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .scale(if (focused) 1.06f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    focused -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            )
            .border(
                width = 2.dp,
                color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusEvent { focused = it.isFocused }
            // clickable() already confirms selection on DPAD_CENTER / Enter
            .clickable { onSelect() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = tabIcon(tab), contentDescription = tab.label)
        Spacer(Modifier.width(6.dp))
        Text(
            tab.label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun tabIcon(tab: TopTab): ImageVector = when (tab) {
    TopTab.TV -> Icons.Filled.LiveTv
    TopTab.RADIO -> Icons.Filled.Radio
    TopTab.WEBCAM -> Icons.Filled.Videocam
    TopTab.FAVORITES -> Icons.Filled.Favorite
    TopTab.PLAYER -> Icons.Filled.PlayCircle
}

@Composable
private fun BottomBar(current: TopTab, onSelect: (TopTab) -> Unit) {
    NavigationBar {
        TopTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = current == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = tabIcon(tab),
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors()
            )
        }
    }
}
