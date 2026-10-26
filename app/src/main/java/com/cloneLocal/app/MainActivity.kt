package com.cloneLocal.app

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.cloneLocal.app.ui.ArFonts
import com.cloneLocal.app.ui.Atmosphere
import com.cloneLocal.app.ui.Dock
import com.cloneLocal.app.ui.EnFonts
import com.cloneLocal.app.ui.HomeScreen
import com.cloneLocal.app.ui.LibraryScreen
import com.cloneLocal.app.ui.LocalArabic
import com.cloneLocal.app.ui.LocalFonts
import com.cloneLocal.app.ui.LocalType
import com.cloneLocal.app.ui.SettingsScreen
import com.cloneLocal.app.ui.Strings
import com.cloneLocal.app.ui.Type

class MainActivity : ComponentActivity() {
    private lateinit var engine: Engine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        engine = Engine(applicationContext)
        setContent { VoxApp(engine) }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }
}

@Composable
private fun VoxApp(engine: Engine) {
    val ar = engine.arabic
    val fonts = if (ar) ArFonts else EnFonts
    val s = Strings(ar)
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            } ?: "reference"
            engine.loadReference(uri, name)
        }
    }

    LaunchedEffect(engine.toast) {
        engine.toast?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show(); engine.toast = null }
    }

    CompositionLocalProvider(
        LocalFonts provides fonts,
        LocalArabic provides ar,
        LocalType provides Type(fonts, ar),
        LocalLayoutDirection provides if (ar) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        Box(Modifier.fillMaxSize()) {
            Atmosphere()
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.statusBarsPadding(),
                transitionSpec = {
                    (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 14 }) togetherWith fadeOut(tween(200))
                },
                label = "tabs",
            ) { t ->
                when (t) {
                    0 -> HomeScreen(
                        engine, s,
                        onPickRef = { picker.launch(arrayOf("audio/*", "video/*")) },
                        onOpenSettings = { tab = 2 },
                    )
                    1 -> LibraryScreen(engine, s, onHome = { tab = 0 })
                    else -> SettingsScreen(engine, s)
                }
            }
            Dock(
                selected = tab,
                labels = listOf(s.navHome, s.navLibrary, s.navSettings),
                onSelect = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            )
        }
    }
}
