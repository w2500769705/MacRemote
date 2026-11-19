package com.macremote.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF121418)
private val Card = Color(0xFF22262E)
private val CardHi = Color(0xFF2E3440)
private val Accent = Color(0xFF4FC3F7)
private val TextMain = Color(0xFFECEFF4)
private val TextDim = Color(0xFF8A93A3)

class MainActivity : ComponentActivity() {
    private val client = RemoteClient()
    private var hasLocalNetworkAccess by mutableStateOf(false)
    private val requestLocalNetworkAccess = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocalNetworkAccess = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        client.host = prefs.getString("host", "") ?: ""
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                if (hasLocalNetworkAccess) {
                    App(client, prefs)
                } else {
                    LocalNetworkPermissionScreen(
                        onRequest = {
                            requestLocalNetworkAccess.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                        },
                        onOpenSettings = {
                            startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasLocalNetworkAccess = Build.VERSION.SDK_INT < 37 ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }

    // 手机音量键直接调 Mac 的音量
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!hasLocalNetworkAccess) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { client.send("v up"); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { client.send("v down"); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        client.close()
    }
}

@Composable
private fun LocalNetworkPermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Bg).systemBarsPadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("需要局域网权限", color = TextMain, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("授权后才能发现并遥控同一局域网里的 Mac。", color = TextDim)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("授权局域网访问") }
        TextButton(onClick = onOpenSettings) { Text("打开应用设置") }
    }
}

@Composable
private fun App(client: RemoteClient, prefs: android.content.SharedPreferences) {
    var host by remember { mutableStateOf(client.host) }
    var macName by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf(profileById(prefs.getString("profile", null))) }
    var tab by remember { mutableIntStateOf(prefs.getInt("tab", 0)) }
    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("sens", 1.5f)) }
    var reverseScroll by remember { mutableStateOf(prefs.getBoolean("revScroll", false)) }
    var showSettings by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current

    fun setHost(h: String) {
        host = h.trim()
        client.host = host
        prefs.edit().putString("host", host).apply()
    }

    // 首次打开：自动搜一次
    LaunchedEffect(Unit) {
        if (host.isBlank()) {
            val found = withContext(Dispatchers.IO) { RemoteClient.discover() }
            if (found.size == 1) setHost(found[0].ip) else showSettings = true
        }
    }
    // 前台时每 3 秒探测一次在线状态
    LaunchedEffect(host) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                macName = withContext(Dispatchers.IO) { client.ping() }
                delay(3000)
            }
        }
    }

    // 屏幕矮（小折叠外屏、横屏）时用紧凑布局
    BoxWithConstraints(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        val compact = maxHeight < 560.dp
        val narrow = maxWidth < 330.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 10.dp else 16.dp)) {
            val setTab = { i: Int -> tab = i; prefs.edit().putInt("tab", i).apply() }
            val setProfile = { p: Profile -> profile = p; prefs.edit().putString("profile", p.id).apply() }
            if (compact && narrow) {
                // 外屏应用区很窄（约 249dp）：两行顶栏
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (macName != null) Color(0xFF66BB6A) else Color(0xFFEF5350)))
                    PROFILES.forEach { p -> Chip(p.label, p == profile, Modifier.weight(1f), small = true) { setProfile(p) } }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Chip(if (tab == 0) "切到触控板" else "切到遥控器", false, Modifier.weight(1f), small = true) { setTab(1 - tab) }
                    Chip("设置", false, Modifier.width(52.dp), small = true) { showSettings = true }
                }
            } else if (compact) {
                // 一行：状态点 + 网站切换 + 遥控/触控板切换 + 设置
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(if (macName != null) Color(0xFF66BB6A) else Color(0xFFEF5350))
                        .clickable { showSettings = true })
                    PROFILES.forEach { p -> Chip(p.label, p == profile, Modifier.weight(1f), small = true) { setProfile(p) } }
                    Chip(if (tab == 0) "触控板" else "遥控器", false, Modifier.weight(1f), small = true) { setTab(1 - tab) }
                    Chip("设置", false, Modifier.width(44.dp), small = true) { showSettings = true }
                }
            } else {
                // 顶栏：连接状态 + 设置
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (macName != null) Color(0xFF66BB6A) else Color(0xFFEF5350)))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(macName ?: if (host.isBlank()) "未设置 Mac" else "连不上", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        if (host.isNotBlank()) Text(host, color = TextDim, fontSize = 12.sp)
                    }
                    TextButton(onClick = { showSettings = true }) { Text("设置", color = Accent) }
                }
                // 网站/播放器切换
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFILES.forEach { p -> Chip(p.label, selected = p == profile, Modifier.weight(1f)) { setProfile(p) } }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("遥控器", "触控板").forEachIndexed { i, label ->
                        Chip(label, selected = tab == i, Modifier.weight(1f)) { setTab(i) }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val onText = { showText = true }
                when {
                    tab == 0 && compact && narrow -> NarrowRemotePanel(client, profile, onText)
                    tab == 0 && compact -> CompactRemotePanel(client, profile, onText)
                    tab == 0 -> RemotePanel(client, profile, onText)
                    else -> TouchpadPanel(client, profile, sensitivity, reverseScroll, compact, onText)
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            host = host,
            sensitivity = sensitivity,
            reverseScroll = reverseScroll,
            onDismiss = { showSettings = false },
            onSave = { h, s, r ->
                setHost(h)
                sensitivity = s; reverseScroll = r
                prefs.edit().putFloat("sens", s).putBoolean("revScroll", r).apply()
                showSettings = false
            },
        )
    }
    if (showText) TextDialog(client) { showText = false }
}

// ---------------- 遥控器 ----------------

@Composable
private fun RemotePanel(client: RemoteClient, p: Profile, onText: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("上一集", Modifier.weight(1f).fillMaxHeight(), enabled = p.prev != null) { p.prev?.let { client.send("k $it") } }
            Btn("播放 / 暂停", Modifier.weight(1.4f).fillMaxHeight(), primary = true, big = true) { client.send("k ${p.playPause}") }
            Btn("下一集", Modifier.weight(1f).fillMaxHeight(), enabled = p.next != null) { p.next?.let { client.send("k $it") } }
        }
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("« 后退", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k ${p.back}") }
            Btn("全屏", Modifier.weight(1f).fillMaxHeight()) { client.send("k ${p.fullscreen}") }
            Btn("前进 »", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k ${p.forward}") }
        }

        // 方向键：在 Jellyfin 里选片、网页里滚动
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("▲", Modifier.size(72.dp), repeat = true) { client.send("k up") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("◀", Modifier.size(72.dp), repeat = true) { client.send("k left") }
                    Btn("确定", Modifier.size(72.dp), primary = true) { client.send("k return") }
                    Btn("▶", Modifier.size(72.dp), repeat = true) { client.send("k right") }
                }
                Btn("▼", Modifier.size(72.dp), repeat = true) { client.send("k down") }
            }
        }

        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("音量 −", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("v down") }
            Btn("静音", Modifier.weight(1f).fillMaxHeight()) { client.send("v mute") }
            Btn("音量 +", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("v up") }
        }
        Row(Modifier.fillMaxWidth().height(56.dp).padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("返回 Esc", Modifier.weight(1f).fillMaxHeight()) { client.send("k escape") }
            Btn("打字", Modifier.weight(1f).fillMaxHeight()) { onText() }
            Btn("网页后退", Modifier.weight(1f).fillMaxHeight()) { client.send("k cmd+bracketleft") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CompactRemotePanel(client: RemoteClient, p: Profile, onText: () -> Unit) {
    val gap = 6.dp
    Row(Modifier.fillMaxSize().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Btn("播放 / 暂停", Modifier.fillMaxWidth().weight(1.4f), primary = true, big = true) { client.send("k ${p.playPause}") }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Btn("上一集", Modifier.weight(1f).fillMaxHeight(), enabled = p.prev != null, small = true) { p.prev?.let { client.send("k $it") } }
                Btn("下一集", Modifier.weight(1f).fillMaxHeight(), enabled = p.next != null, small = true) { p.next?.let { client.send("k $it") } }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Btn("«", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k ${p.back}") }
                Btn("全屏", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("k ${p.fullscreen}") }
                Btn("»", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k ${p.forward}") }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Btn("音−", Modifier.weight(1f).fillMaxHeight(), repeat = true, small = true) { client.send("v down") }
                Btn("静音", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("v mute") }
                Btn("音+", Modifier.weight(1f).fillMaxHeight(), repeat = true, small = true) { client.send("v up") }
            }
        }
        // 方向键
        Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Btn("Esc", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("k escape") }
                Btn("▲", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k up") }
                Btn("打字", Modifier.weight(1f).fillMaxHeight(), small = true) { onText() }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Btn("◀", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k left") }
                Btn("OK", Modifier.weight(1f).fillMaxHeight(), primary = true) { client.send("k return") }
                Btn("▶", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k right") }
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Spacer(Modifier.weight(1f))
                Btn("▼", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k down") }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 外屏窄条：全部按钮竖着排成 6 行。 */
@Composable
private fun NarrowRemotePanel(client: RemoteClient, p: Profile, onText: () -> Unit) {
    val gap = 5.dp
    @Composable
    fun RowScope3(content: @Composable RowScope.() -> Unit, weight: Float = 1f, col: ColumnScope) = with(col) {
        Row(Modifier.fillMaxWidth().weight(weight), horizontalArrangement = Arrangement.spacedBy(gap), content = content)
    }
    Column(Modifier.fillMaxSize().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        val c = this
        RowScope3({ Btn("播放 / 暂停", Modifier.weight(1f).fillMaxHeight(), primary = true, big = true) { client.send("k ${p.playPause}") } }, 1.3f, c)
        RowScope3({
            Btn("上一集", Modifier.weight(1f).fillMaxHeight(), enabled = p.prev != null, small = true) { p.prev?.let { client.send("k $it") } }
            Btn("下一集", Modifier.weight(1f).fillMaxHeight(), enabled = p.next != null, small = true) { p.next?.let { client.send("k $it") } }
        }, col = c)
        RowScope3({
            Btn("«", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k ${p.back}") }
            Btn("全屏", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("k ${p.fullscreen}") }
            Btn("»", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k ${p.forward}") }
        }, col = c)
        RowScope3({
            Btn("Esc", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("k escape") }
            Btn("▲", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k up") }
            Btn("打字", Modifier.weight(1f).fillMaxHeight(), small = true) { onText() }
        }, col = c)
        RowScope3({
            Btn("◀", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k left") }
            Btn("OK", Modifier.weight(1f).fillMaxHeight(), primary = true) { client.send("k return") }
            Btn("▶", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k right") }
        }, col = c)
        RowScope3({
            Btn("音−", Modifier.weight(1f).fillMaxHeight(), repeat = true, small = true) { client.send("v down") }
            Btn("▼", Modifier.weight(1f).fillMaxHeight(), repeat = true) { client.send("k down") }
            Btn("音+", Modifier.weight(1f).fillMaxHeight(), repeat = true, small = true) { client.send("v up") }
        }, col = c)
    }
}

// ---------------- 触控板 ----------------

@Composable
private fun TouchpadPanel(client: RemoteClient, p: Profile, sensitivity: Float, reverseScroll: Boolean, compact: Boolean, onText: () -> Unit) {
    if (compact) {
        Column(Modifier.fillMaxSize().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(Card)
                    .border(1.dp, CardHi, RoundedCornerShape(14.dp))
                    .touchpad(client, sensitivity, reverseScroll),
                contentAlignment = Alignment.Center,
            ) { Text("滑动移动 · 点按左键 · 两指右键/滚动", color = TextDim, fontSize = 11.sp) }
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("右键", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("c r") }
                Btn("播放", Modifier.weight(1f).fillMaxHeight(), primary = true, small = true) { client.send("k ${p.playPause}") }
                Btn("全屏", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("k ${p.fullscreen}") }
                Btn("Esc", Modifier.weight(1f).fillMaxHeight(), small = true) { client.send("k escape") }
                Btn("打字", Modifier.weight(1f).fillMaxHeight(), small = true) { onText() }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(Card)
                .border(1.dp, CardHi, RoundedCornerShape(18.dp))
                .touchpad(client, sensitivity, reverseScroll),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "单指滑动 移动鼠标\n轻点 左键 · 两指轻点 右键\n两指滑动 滚动 · 长按后拖动 拖拽",
                color = TextDim, fontSize = 13.sp, lineHeight = 22.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Row(Modifier.fillMaxWidth().height(60.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("左键", Modifier.weight(1f).fillMaxHeight()) { client.send("c l") }
            Btn("右键", Modifier.weight(1f).fillMaxHeight()) { client.send("c r") }
        }
        Row(Modifier.fillMaxWidth().height(56.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("播放/暂停", Modifier.weight(1f).fillMaxHeight(), primary = true) { client.send("k ${p.playPause}") }
            Btn("全屏", Modifier.weight(1f).fillMaxHeight()) { client.send("k ${p.fullscreen}") }
            Btn("Esc", Modifier.weight(1f).fillMaxHeight()) { client.send("k escape") }
            Btn("打字", Modifier.weight(1f).fillMaxHeight()) { onText() }
        }
    }
}

// ---------------- 通用组件 ----------------

@Composable
private fun Chip(label: String, selected: Boolean, modifier: Modifier, small: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier.height(if (small) 30.dp else 38.dp).clip(RoundedCornerShape(if (small) 15.dp else 19.dp))
            .background(if (selected) Accent else Card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.Black else TextMain, fontSize = if (small) 12.sp else 14.sp, maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 按钮；repeat=true 时按住会连发（快进、音量）。 */
@Composable
private fun Btn(
    label: String,
    modifier: Modifier,
    primary: Boolean = false,
    big: Boolean = false,
    enabled: Boolean = true,
    repeat: Boolean = false,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(16.dp)
    val bg = when {
        !enabled -> Card.copy(alpha = 0.4f)
        primary -> Accent
        else -> CardHi
    }
    Box(
        modifier.clip(shape).background(bg)
            .then(
                if (!enabled) Modifier
                else Modifier.pressRepeat(repeat, scope) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color.Black else if (enabled) TextMain else TextDim,
            fontSize = if (big) 20.sp else if (small) 13.sp else 16.sp,
            maxLines = 1,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsDialog(
    host: String,
    sensitivity: Float,
    reverseScroll: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, Float, Boolean) -> Unit,
) {
    var h by remember { mutableStateOf(host) }
    var s by remember { mutableFloatStateOf(sensitivity) }
    var r by remember { mutableStateOf(reverseScroll) }
    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<FoundMac>?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(h, { h = it }, label = { Text("Mac 的 IP 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        searching = true
                        scope.launch {
                            found = withContext(Dispatchers.IO) { RemoteClient.discover() }
                            searching = false
                            found?.singleOrNull()?.let { h = it.ip }
                        }
                    },
                    enabled = !searching,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (searching) "搜索中…" else "在局域网里搜索") }
                found?.let { list ->
                    if (list.isEmpty()) Text("没找到。确认 Mac 开着、和手机在同一个 Wi-Fi。", color = TextDim, fontSize = 13.sp)
                    list.forEach { m ->
                        Text("${m.name}  ${m.ip}", color = if (m.ip == h) Accent else TextMain,
                            modifier = Modifier.fillMaxWidth().clickable { h = m.ip }.padding(vertical = 6.dp))
                    }
                }
                Text("触控板灵敏度 ${"%.1f".format(s)}", color = TextMain, fontSize = 14.sp)
                Slider(s, { s = it }, valueRange = 0.5f..4f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("反转两指滚动方向", color = TextMain, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(r, { r = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(h, s, r) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun TextDialog(client: RemoteClient, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("输入到 Mac") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it }, label = { Text("比如搜索片名") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { client.send("k backspace") }, modifier = Modifier.weight(1f)) { Text("删一个字") }
                    OutlinedButton(onClick = { client.send("k cmd+a"); client.send("k backspace") }, modifier = Modifier.weight(1f)) { Text("清空") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (t.isNotEmpty()) client.send("t $t")
                client.send("k return")
                onDismiss()
            }) { Text("发送并回车") }
        },
        dismissButton = {
            TextButton(onClick = { if (t.isNotEmpty()) client.send("t $t"); onDismiss() }) { Text("只发送") }
        },
    )
}
