package com.cloneLocal.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloneLocal.app.Engine
import com.cloneLocal.app.Gen
import com.cloneLocal.app.LibItem
import com.cloneLocal.app.Pack
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val GapSection = 24.dp

@Composable
private fun Page(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(GapSection),
    ) {
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun TopBar(engine: Engine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Logo()
        IconBtn(Ic.Globe) { engine.changeArabic(!engine.arabic) }
    }
}

@Composable
private fun IconSquare(ic: Ic, tint: Color = C.Cyan, filled: Boolean = false) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        Modifier.size(44.dp).clip(shape)
            .background(if (filled) Brush.linearGradient(listOf(C.Blue, C.Cyan)) else Brush.linearGradient(listOf(C.Cyan.copy(alpha = .12f), C.Cyan.copy(alpha = .12f))))
            .border(1.dp, C.Cyan.copy(alpha = .4f), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(ic, if (filled) Color.White else tint, 22.dp) }
}

@Composable
private fun ErrorPanel(msg: String, tip: String? = null) {
    Panel(accent = C.Danger, strong = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Ic.Alert, C.Danger, 22.dp)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(msg, style = LocalType.current.bodyRegular(C.Text, 14))
                if (tip != null) BasicText(tip, style = LocalType.current.body(C.Muted, 13))
            }
        }
    }
}

// ------------------------------------------------------------------ Home

@Composable
fun HomeScreen(engine: Engine, s: Strings, onPickRef: () -> Unit, onOpenSettings: () -> Unit) {
    val type = LocalType.current
    Page {
        Reveal(0) { TopBar(engine) }
        Reveal(1) { SystemTag(s.appTag) }
        Reveal(2) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(s.heroTop.upperIfEn(), style = type.hero())
                BasicText(s.heroGlow.upperIfEn(), style = type.heroGlow())
                BasicText(s.heroSub, style = type.body(C.Muted, 15))
            }
        }

        if (!engine.arPack.ready && !engine.zipPack.ready) {
            Reveal(3) {
                Panel(strong = true, accent = C.Warn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Ic.Shield, C.Warn, 22.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicText(s.engineOffline.upperIfEn(), style = type.section(C.Warn).copy(fontSize = 18.sp))
                            BasicText(s.engineOfflineMsg, style = type.body(C.Muted, 14))
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GhostButton(s.openSettings.upperIfEn(), color = C.Warn, icon = Ic.Sliders, onClick = onOpenSettings)
                }
            }
        }

        Reveal(4) {
            Panel(strong = true) {
                Label(s.voiceLabel, color = C.Cyan)
                Spacer(Modifier.height(10.dp))
                val shape = RoundedCornerShape(6.dp)
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(C.SurfaceHi).border(1.dp, C.Line, shape)
                        .clickable(onClick = onPickRef).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Ic.Mic, if (engine.activeVoice != null) C.Cyan else C.Muted, 22.dp)
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            engine.activeVoice?.name ?: s.refHint,
                            style = type.bodyRegular(if (engine.activeVoice != null) C.Text else C.Faint, 14),
                            maxLines = 1,
                        )
                        engine.activeVoice?.let { v ->
                            BasicText("%.1f s".format(v.seconds), style = type.label(C.Cyan))
                        }
                    }
                    Icon(Ic.Folder, C.Cyan, 20.dp)
                }
                Spacer(Modifier.height(6.dp))
                BasicText(s.refTip, style = type.body(C.Muted, 12))

                Spacer(Modifier.height(16.dp))
                Label(s.refTextLabel, color = C.Cyan)
                Spacer(Modifier.height(10.dp))
                Field(engine.refText, { engine.refText = it }, s.refTextHint, minLines = 2)

                Spacer(Modifier.height(16.dp))
                Label(s.textLabel, color = C.Cyan)
                Spacer(Modifier.height(10.dp))
                Field(engine.inputText, { engine.inputText = it }, s.textHint, minLines = 3)

                Spacer(Modifier.height(18.dp))
                val running = engine.gen is Gen.Running
                PrimaryButton(
                    text = (if (running) s.generating else s.generate).upperIfEn(),
                    icon = Ic.Bolt,
                    loading = running,
                    onClick = { engine.generate() },
                )
            }
        }

        val g = engine.gen
        if (g is Gen.Running) {
            Reveal(0) { RunningBlock(engine, s) }
        }
        if (g is Gen.Error) {
            Reveal(0) { ErrorPanel(g.msg, s.errTip) }
        }
        if (g is Gen.Done) {
            Reveal(0) {
                Panel(strong = true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SystemTag(s.complete, C.Success)
                        BasicText("%.1f s".format(engine.resultSeconds), style = type.stat(C.Cyan, 26))
                    }
                    Spacer(Modifier.height(14.dp))
                    GlowBar(1f, SolidColorBrush(C.Success))
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        (if (engine.playing) s.pause else s.play).upperIfEn(),
                        icon = if (engine.playing) Ic.Pause else Ic.Play,
                        onClick = { engine.togglePlayResult() },
                    )
                    Spacer(Modifier.height(10.dp))
                    engine.library.firstOrNull()?.let { latest ->
                        GhostButton(s.saveMusic.upperIfEn(), icon = Ic.Download, onClick = { engine.exportToMusic(latest) })
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Reveal(5) { Label(s.whatItDoes) }
            Reveal(6) { Feature(Ic.Shield, s.f1t, s.f1d) }
            Reveal(7) { Feature(Ic.Layers, s.f2t, s.f2d) }
            Reveal(8) { Feature(Ic.Archive, s.f3t, s.f3d) }
        }
        BasicText(s.legal, style = type.body(C.Muted, 12))
    }
}

@Composable
private fun SolidColorBrush(c: Color) = Brush.horizontalGradient(listOf(c, c))

@Composable
private fun RunningBlock(engine: Engine, s: Strings) {
    val type = LocalType.current
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) { delay(4000); step = (step + 1).coerceAtMost(s.statusSteps.lastIndex) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SystemTag(s.extracting)
        BasicText(s.statusSteps[step], style = type.body(C.Muted, 14))
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(46.dp)) }
        GhostButton(s.cancel.upperIfEn(), color = C.Muted, onClick = { engine.cancelGenerate() })
    }
}

@Composable
private fun Feature(ic: Ic, title: String, desc: String) {
    val type = LocalType.current
    Panel(pad = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconSquare(ic)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(title, style = type.section().copy(fontSize = 17.sp))
                BasicText(desc, style = type.body(C.Muted, 13))
            }
        }
    }
}

// ------------------------------------------------------------------ Library

@Composable
fun LibraryScreen(engine: Engine, s: Strings, onHome: () -> Unit) {
    val type = LocalType.current
    Page {
        Reveal(0) { TopBar(engine) }
        Reveal(1) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SystemTag("${engine.library.size} ${s.items}")
                BasicText(s.libTitle.upperIfEn(), style = type.section().copy(fontSize = 30.sp))
            }
        }
        if (engine.library.isEmpty()) {
            Reveal(2) {
                Panel(strong = true) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Ic.Folder, C.Cyan, 56.dp)
                        BasicText(s.libEmpty, style = type.bodyRegular(C.Text, 15))
                        GhostButton(s.libEmptyBtn.upperIfEn(), icon = Ic.Home, onClick = onHome)
                    }
                }
            }
        } else {
            engine.library.forEachIndexed { i, item -> Reveal(minOf(i + 2, 8)) { LibRow(engine, s, item) } }
        }
    }
}

@Composable
private fun LibRow(engine: Engine, s: Strings, item: LibItem) {
    val type = LocalType.current
    Panel(pad = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconSquare(Ic.Activity, filled = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText("%.1f s · WAV".format(item.seconds), style = type.section().copy(fontSize = 18.sp))
                BasicText(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.modified)),
                    style = type.body(C.Muted, 12),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Ic.Play) { engine.playFile(item) }
            IconBtn(Ic.Download) { engine.exportToMusic(item) }
            IconBtn(Ic.Trash, C.Danger) { engine.delete(item) }
        }
    }
}

// ------------------------------------------------------------------ Settings

@Composable
fun SettingsScreen(engine: Engine, s: Strings) {
    val type = LocalType.current
    Page {
        Reveal(0) { TopBar(engine) }
        Reveal(1) { BasicText(s.setTitle.upperIfEn(), style = type.section().copy(fontSize = 30.sp)) }

        Reveal(2) {
            Panel {
                Label(s.language, color = C.Cyan)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf("English", "العربية"), if (engine.arabic) 1 else 0) { engine.changeArabic(it == 1) }
            }
        }
        Reveal(3) {
            Panel {
                Label(s.quality, color = C.Cyan)
                Spacer(Modifier.height(10.dp))
                Segmented(listOf(s.qFast, s.qBalanced, s.qBest), engine.quality) { engine.changeQuality(it) }
                Spacer(Modifier.height(8.dp))
                BasicText(s.qualityNote, style = type.body(C.Muted, 12))
            }
        }
        Reveal(4) {
            PackPanel(s.arPackTitle, s.arPackNote, engine.arPack, s, null, {}, { engine.downloadArabicPack() }, { engine.deleteArabicPack() })
        }
        Reveal(5) { VoicesPanel(engine, s) }
        Reveal(6) {
            PackPanel(
                s.enPackTitle, s.modelNote, engine.zipPack, s,
                engine.modelUrl, { engine.setModelUrlPref(it) },
                { engine.downloadZipPack() }, { engine.deleteZipPack() },
            )
        }
        Reveal(7) { ReaderPanel(s) }
        Reveal(8) { GhostButton(s.clearCache.upperIfEn(), icon = Ic.Refresh, onClick = { engine.clearCache() }) }
        BasicText(s.about, style = type.body(C.Muted, 12))
    }
}


@Composable
private fun PackPanel(
    title: String,
    note: String,
    pack: Pack,
    s: Strings,
    url: String?,
    onUrl: (String) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val type = LocalType.current
    Panel(strong = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Label(title, color = C.Cyan)
            SystemTag(
                if (pack.busy) s.downloading else if (pack.ready) s.modelReady else s.modelMissing,
                if (pack.ready && !pack.busy) C.Success else C.Warn,
            )
        }
        Spacer(Modifier.height(10.dp))
        BasicText(note, style = type.body(C.Muted, 12))
        if (url != null) {
            Spacer(Modifier.height(14.dp))
            Label(s.modelUrl)
            Spacer(Modifier.height(8.dp))
            Field(url, onUrl, "https://…", ltr = true, small = true)
        }
        if (pack.busy) {
            Spacer(Modifier.height(14.dp))
            GlowBar(pack.progress)
            Spacer(Modifier.height(6.dp))
            BasicText(
                if (pack.extracting) s.extractingModel else "${(pack.progress * 100).toInt()}%",
                style = type.stat(C.Cyan, 22),
            )
        }
        pack.error?.let {
            Spacer(Modifier.height(12.dp))
            ErrorPanel("${s.dlFailed}: $it", s.noInternet)
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            (if (pack.ready) s.redownload else s.downloadModel).upperIfEn(),
            icon = Ic.Download, loading = pack.busy, onClick = onDownload,
        )
        if (pack.ready) {
            Spacer(Modifier.height(10.dp))
            GhostButton(s.deleteModel.upperIfEn(), color = C.Danger, icon = Ic.Trash, onClick = onDelete)
        }
    }
}

@Composable
private fun VoicesPanel(engine: Engine, s: Strings) {
    val type = LocalType.current
    Panel {
        Label(s.voicesTitle, color = C.Cyan)
        Spacer(Modifier.height(10.dp))
        if (engine.voices.isEmpty()) {
            BasicText(s.noVoices, style = type.body(C.Muted, 13))
        } else {
            engine.voices.forEach { v ->
                val active = v.id == engine.activeId
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        BasicText(v.name, style = type.bodyRegular(if (active) C.Cyan else C.Text, 14), maxLines = 1)
                        BasicText("%.1f s".format(v.seconds), style = type.label(C.Muted))
                    }
                    if (active) SystemTag(s.activeTag, C.Success, blink = false)
                    else GhostButton(s.useVoice.upperIfEn(), modifier = Modifier.width(96.dp).height(40.dp), onClick = { engine.selectVoice(v) })
                    IconBtn(Ic.Trash, C.Danger) { engine.deleteVoice(v) }
                }
            }
        }
    }
}

@Composable
private fun ReaderPanel(s: Strings) {
    val type = LocalType.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Panel(strong = true) {
        Label(s.readerTitle, color = C.Cyan)
        Spacer(Modifier.height(10.dp))
        BasicText(s.readerNote, style = type.body(C.Muted, 13))
        Spacer(Modifier.height(14.dp))
        GhostButton(s.openTts.upperIfEn(), icon = Ic.Sliders, onClick = {
            try {
                ctx.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
    }
}
