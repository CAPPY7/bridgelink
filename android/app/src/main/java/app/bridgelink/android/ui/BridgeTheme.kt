package app.bridgelink.android.ui

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF0E6B66)
private val TealSoft = Color(0xFF8ED9D2)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EFEB),
    onPrimaryContainer = Color(0xFF062E2C),
    background = Color(0xFFF4F7F6),
    surface = Color.White,
    onBackground = Color(0xFF141618),
    onSurface = Color(0xFF141618),
    onSurfaceVariant = Color(0xFF4E5B58),
)

private val DarkColors = darkColorScheme(
    primary = TealSoft,
    onPrimary = Color(0xFF062E2C),
    primaryContainer = Color(0xFF0E4A46),
    onPrimaryContainer = Color(0xFFD3EFEB),
    background = Color(0xFF101416),
    surface = Color(0xFF1A2124),
    onBackground = Color(0xFFF4F6F8),
    onSurface = Color(0xFFF4F6F8),
    onSurfaceVariant = Color(0xFFB7C2BE),
)

@Composable
fun BridgeApp(model: PhoneModel, actions: PhoneActions) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PhoneShell(model, actions)
        }
    }
}

@Composable
private fun PhoneShell(model: PhoneModel, actions: PhoneActions) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNav(model) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            AnimatedContent(
                targetState = model.page,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "page",
            ) { page ->
                when (page) {
                    Page.Home -> if (model.setupDone) HomePage(model, actions) else SetupPage(model, actions)
                    Page.Send -> SendPage(model, actions)
                    Page.Notifications -> NotificationsPage(model, actions)
                    Page.Messages -> MessagesPage(model, actions)
                    Page.Files -> FilesPage(model, actions)
                    Page.History -> HistoryPage(model, actions)
                    Page.Remote -> RemotePage(model, actions)
                    Page.Mirror -> MirrorPage(model, actions)
                    Page.Settings -> SettingsPage(model, actions)
                }
            }
            if (model.askPair) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(model.pairTitle, style = MaterialTheme.typography.headlineMedium)
                        Text(model.pairDetail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BigButton(model.pairButton, "pair-accept", actions::acceptPair)
                        BigButton("Cancel", "pair-cancel", actions::cancelPair, tonal = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(model: PhoneModel) {
    val primary = listOf(Page.Home, Page.Send, Page.Notifications, Page.Messages, Page.Files)
    val extra = listOf(Page.Remote, Page.Mirror, Page.History, Page.Settings)
    var moreOpen by remember { mutableStateOf(false) }
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            primary.forEach { page ->
                NavPill(page, selected = model.page == page, onClick = { model.page = page })
            }
            Box {
                MorePill(selected = extra.contains(model.page), onClick = { moreOpen = true })
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    extra.forEach { page ->
                        DropdownMenuItem(
                            text = { Text(page.label) },
                            onClick = {
                                model.page = page
                                moreOpen = false
                            },
                            modifier = Modifier.testTag("nav-" + page.name.lowercase()),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavPill(page: Page, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = Modifier.testTag("nav-" + page.name.lowercase()),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(page.icon(), contentDescription = page.title, tint = content)
            Text(page.label, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorePill(selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = background,
        modifier = Modifier.testTag("nav-more"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = content)
            Text("More", color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun Page.icon(): ImageVector = when (this) {
    Page.Home -> Icons.Filled.Home
    Page.Send -> Icons.Filled.Send
    Page.Notifications -> Icons.Filled.Notifications
    Page.Messages -> Icons.Filled.Email
    Page.Files -> Icons.Filled.Folder
    Page.History -> Icons.Filled.History
    Page.Remote -> Icons.Filled.PhoneAndroid
    Page.Mirror -> Icons.Filled.Cast
    Page.Settings -> Icons.Filled.Settings
}
