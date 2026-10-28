package br.com.barnswallow.levanta.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.barnswallow.levanta.alarm.AlarmRingingService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LevantaTheme { LevantaNav() }
        }
    }

    override fun onResume() {
        super.onResume()
        // Se um alarme está tocando, abrir o app leva direto para a missão.
        if (AlarmRingingService.state.value != null) startActivity(AlarmActivity.intent(this))
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

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            val forward = targetState != Screen.Home
            (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { if (forward) it / 6 else -it / 6 }) togetherWith
                fadeOut(tween(160))
        },
        label = "screen",
    ) { current -> ScreenContent(current, onNavigate = { screen = it }) }
}

@Composable
private fun ScreenContent(current: Screen, onNavigate: (Screen) -> Unit) {
    when (current) {
        Screen.Home -> HomeScreen(
            onAdd = { onNavigate(Screen.Edit(null)) },
            onEdit = { onNavigate(Screen.Edit(it)) },
            onSettings = { onNavigate(Screen.Settings) },
        )
        is Screen.Edit -> EditAlarmScreen(
            alarmId = current.alarmId,
            onDone = { onNavigate(Screen.Home) },
            onOpenSettings = { onNavigate(Screen.Settings) },
        )
        Screen.Settings -> SettingsScreen(onBack = { onNavigate(Screen.Home) })
    }
}
