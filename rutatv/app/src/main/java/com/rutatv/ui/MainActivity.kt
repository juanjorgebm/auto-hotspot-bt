package com.rutatv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rutatv.RutaApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RutaTheme {
                Surface(Modifier.fillMaxSize(), color = Bg) { Root() }
            }
        }
    }
}

@Composable
private fun Root() {
    val repo = RutaApp.repo
    val profiles by repo.profiles.collectAsState()
    val active by repo.active.collectAsState()
    var screen by rememberSaveable { mutableStateOf("profiles") }

    when {
        profiles.isEmpty() || screen == "setup" -> SetupScreen(
            onDone = { screen = "profiles" },
            onCancel = if (profiles.isNotEmpty()) ({ screen = if (active != null) "home" else "profiles" }) else null,
        )
        active == null || screen == "profiles" -> ProfilePicker(
            profiles = profiles,
            last = repo.lastProfile(),
            onPick = { repo.selectProfile(it); screen = "home" },
            onAdd = { screen = "setup" },
        )
        else -> HomeScreen(
            onSwitchProfile = { screen = "profiles" },
            onSetup = { screen = "setup" },
        )
    }
}
