package br.com.barnswallow.levanta.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LevantaTheme { LevantaNav() }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Edit(val alarmId: Int?) : Screen
    data object Settings : Screen
}

@Composable
private fun LevantaNav() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it) },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Edit -> EditAlarmScreen(
            alarmId = current.alarmId,
            onDone = { screen = Screen.Home },
            onOpenSettings = { screen = Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
    }
}
