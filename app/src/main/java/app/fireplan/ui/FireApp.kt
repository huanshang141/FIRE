package app.fireplan.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.fireplan.ui.chat.ChatScreen
import app.fireplan.ui.goal.GoalEditorScreen
import app.fireplan.ui.progress.ProgressScreen
import app.fireplan.ui.settings.SettingsScreen

val LocalViewModelFactory = compositionLocalOf<ViewModelProvider.Factory> {
    error("ViewModel 工厂还没有准备好")
}

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab("progress", "进度"),
    Tab("chat", "聊天"),
    Tab("settings", "设置"),
)

@Composable
fun FireApp(openChatToken: Int, openConversationId: Long, factory: ViewModelProvider.Factory) {
    CompositionLocalProvider(LocalViewModelFactory provides factory) {
        val context = LocalContext.current
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        LaunchedEffect(openChatToken) {
            if (openChatToken > 0) {
                nav.navigate("chat") {
                    launchSingleTop = true
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    restoreState = true
                }
            }
        }
        Scaffold(
            bottomBar = {
                if (route != "editor") {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = when (tab.route) {
                                            "chat" -> Icons.AutoMirrored.Filled.Chat
                                            "settings" -> Icons.Filled.Settings
                                            else -> Icons.AutoMirrored.Filled.ShowChart
                                        },
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "progress",
                modifier = Modifier.padding(padding),
            ) {
                composable("progress") { ProgressScreen(onEdit = { nav.navigate("editor") }) }
                composable("editor") { GoalEditorScreen(onBack = { nav.popBackStack() }) }
                composable("chat") {
                    ChatScreen(
                        openToken = openChatToken,
                        openConversationId = openConversationId,
                        onOpenProgress = {
                            nav.navigate("progress") {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable("settings") { SettingsScreen() }
            }
        }
    }
}
