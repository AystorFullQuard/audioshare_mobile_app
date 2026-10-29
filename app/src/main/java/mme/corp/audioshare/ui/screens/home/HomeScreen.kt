package mme.corp.audioshare.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import mme.corp.audioshare.BuildConfig
import mme.corp.audioshare.startup.BootstrapStartupRequest
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import mme.corp.audioshare.AudioShareApplication
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.ui.screens.home.uiBlocks.RoomsEntryButton

private val LocalHomeAppearance = staticCompositionLocalOf { HomeAppearance.DARK }
private val Background @Composable get() = LocalHomeAppearance.current.background
private val Panel @Composable get() = LocalHomeAppearance.current.panel
private val PanelBorder @Composable get() = LocalHomeAppearance.current.panelBorder
private val TextMain @Composable get() = LocalHomeAppearance.current.text
private val TextMuted @Composable get() = LocalHomeAppearance.current.muted
private val Accent @Composable get() = LocalHomeAppearance.current.accent
private val AccentBlue @Composable get() = LocalHomeAppearance.current.accentSecondary
private val AccentGradient @Composable get() = LocalHomeAppearance.current.accentGradient

private enum class MediaChoice { AUDIO, VIDEO }
private enum class HostChoice { ONLINE, LOCAL }

/** A lightweight home shell: reuses the bootstrap-restored room state, not a second poller. */
@Composable
fun HomeScreen(onLogin: () -> Unit = {}, onNavigateRooms: () -> Unit = {}) {
    val app = LocalContext.current.applicationContext as AudioShareApplication
    val token by app.container.accessTokenState.collectAsStateWithLifecycle()
    val presence by app.container.presenceHeartbeatCoordinator.confirmedPresence.collectAsStateWithLifecycle()
    val roomsState by app.container.roomSessionCoordinator.state.collectAsStateWithLifecycle()
    val currentUser by app.container.currentUser.collectAsStateWithLifecycle()
    val signedIn = !token.isNullOrBlank()
    val ready = signedIn && presence != null
    var retryNumber by remember { mutableIntStateOf(0) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

    // Login is optional at launch. Existing sessions reconnect from Home without an auth screen.
    // One attempt per signed-in session, plus explicit retries; never loop while offline.
    LaunchedEffect(signedIn, retryNumber) {
        if (!signedIn) {
            app.container.currentUser.value = null
            connectionError = null
            isConnecting = false
            return@LaunchedEffect
        }
        if (app.container.presenceHeartbeatCoordinator.confirmedPresence.value != null) return@LaunchedEffect
        isConnecting = true
        connectionError = null
        try {
            val request = BootstrapStartupRequest(
                deviceName = Build.MODEL.takeIf { it.isNotBlank() },
                manufacturer = Build.MANUFACTURER.takeIf { it.isNotBlank() },
                model = Build.MODEL.takeIf { it.isNotBlank() },
                platformVersion = Build.VERSION.RELEASE.takeIf { it.isNotBlank() },
                appVersion = BuildConfig.VERSION_NAME,
                locale = Locale.getDefault().toLanguageTag(),
                timezone = TimeZone.getDefault().id
            )
            app.container.bootstrapStartupCoordinator.initialize(request)
                .onSuccess { app.container.currentUser.value = it.sessionBootstrap.user }
                .onFailure { connectionError = "Cannot load rooms. Check ServerRelay connection." }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            connectionError = "Cannot load rooms. Check ServerRelay connection."
        } finally {
            isConnecting = false
        }
    }

    SvReHome(
        signedIn = signedIn,
        avatarInitial = currentUser?.displayName?.firstOrNull()?.uppercaseChar()?.toString(),
        rooms = if (ready) roomsState.rooms else emptyList(),
        roomsReady = ready,
        isConnecting = isConnecting && !ready,
        connectionError = if (ready) null else connectionError,
        onRetry = { retryNumber++ },
        onAvatarClick = onLogin,
        onNavigateRooms = onNavigateRooms,
        appearance = HomeAppearance.from(currentUser?.settings)
    )
}

@Composable
internal fun SvReHome(
    signedIn: Boolean,
    avatarInitial: String? = null,
    rooms: List<Room>,
    onNavigateRooms: () -> Unit,
    roomsReady: Boolean = signedIn,
    onAvatarClick: () -> Unit = {},
    isConnecting: Boolean = false,
    connectionError: String? = null,
    onRetry: () -> Unit = {},
    appearance: HomeAppearance = HomeAppearance.DARK
) {
    var media by remember { mutableStateOf(MediaChoice.AUDIO) }
    var host by remember { mutableStateOf(HostChoice.ONLINE) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notice(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    CompositionLocalProvider(LocalHomeAppearance provides appearance) {
        val ambientColor = appearance.ambient
        Box(Modifier
            .fillMaxSize()
            .background(Background)) {
            // Subtle ambient gradient behind the fixed bottom waves.
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(ambientColor, Color.Transparent),
                        center = Offset(size.width * .52f, size.height * .25f),
                        radius = size.width * .9f
                    ),
                    radius = size.width * .9f,
                    center = Offset(size.width * .52f, size.height * .25f)
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                BrandHeader(signedIn, avatarInitial) {
                    if (!signedIn) onAvatarClick() else notice("You are signed in.")
                }
                if (isConnecting) {
                    Text(
                        "Connecting to ServerRelay and loading your rooms…",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
                if (connectionError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            connectionError,
                            modifier = Modifier.weight(1f),
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        TextButton(onClick = onRetry) { Text("Retry", color = Accent) }
                    }
                }
                StartSharePanel(
                    host = host, media = media,
                    onHost = { selected ->
                        host = selected
                        when {
                            media == MediaChoice.VIDEO -> notice("Video sharing is planned for a later release.")
                            selected == HostChoice.LOCAL -> notice("Secure nearby hosting is not yet available.")
                            roomsReady && !isConnecting && connectionError == null -> onNavigateRooms() // online room management
                            else -> notice("Sign in and connect to manage rooms.")
                        }
                    },
                    onMedia = { media = it }
                )
                JoinPanel(
                    onCode = {
                        if (roomsReady && !isConnecting && connectionError == null) onNavigateRooms() else notice(
                            "Connect your account to join server rooms."
                        )
                    },
                    onNearby = { notice("Secure nearby discovery is planned for the local sharing stage.") }
                )
                MyRoomsPanel(
                    rooms = rooms,
                    onNavigateRooms = {
                        if (roomsReady && !isConnecting && connectionError == null) onNavigateRooms() else notice(
                            "Sign in and connect to view your rooms."
                        )
                    }
                )
                RecentPanel()
            }
            FooterWaves(Modifier
                .fillMaxWidth()
                .height(88.dp)
                .align(Alignment.BottomCenter))
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
            )
        }
    }
}

@Composable
private fun BrandHeader(signedIn: Boolean, avatarInitial: String?, onAvatarClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WaveformLogo(Modifier.size(43.dp))
        Spacer(Modifier.width(10.dp))
        Text("SvRe", color = TextMain, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clickable(onClick = onAvatarClick)
                .testTag("home_profile_avatar"),
            contentAlignment = Alignment.BottomEnd
        ) {
            Box(
                Modifier
                    .size(49.dp)
                    .border(2.dp, if (signedIn) Accent else PanelBorder, CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(LocalHomeAppearance.current.avatarSurface),
                contentAlignment = Alignment.Center
            ) {
                if (signedIn && !avatarInitial.isNullOrBlank()) {
                    Text(
                        avatarInitial, color = TextMain, fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                } else {
                    LoginAvatarGlyph(Modifier.size(31.dp))
                }
            }
            if (signedIn) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(LocalHomeAppearance.current.online)
                        .border(2.dp, Background, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun LoginAvatarGlyph(modifier: Modifier = Modifier) {
    val iconColor = TextMain
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            iconColor, radius = size.width * .17f,
            center = Offset(center.x, size.height * .33f)
        )
        val shoulders = Path().apply {
            moveTo(size.width * .16f, size.height * .88f)
            cubicTo(
                size.width * .15f, size.height * .53f, size.width * .85f, size.height * .53f,
                size.width * .84f, size.height * .88f
            )
            close()
        }
        drawPath(shoulders, iconColor)
    }
}

@Composable
private fun WaveformLogo(modifier: Modifier = Modifier) {
    val secondary = AccentBlue
    val logoTop = LocalHomeAppearance.current.logoTop
    Canvas(modifier) {
        val heights = listOf(.24f, .55f, 1f, .6f, .3f)
        heights.forEachIndexed { index, height ->
            val x = size.width * (.13f + index * .18f)
            drawLine(
                brush = Brush.verticalGradient(listOf(logoTop, secondary)),
                start = Offset(x, (size.height * (1 - height)) / 2),
                end = Offset(x, (size.height * (1 + height)) / 2),
                strokeWidth = size.width * .105f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PanelContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PanelBorder, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(LocalHomeAppearance.current.panelTop, Panel)))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
private fun StartSharePanel(
    host: HostChoice,
    media: MediaChoice,
    onHost: (HostChoice) -> Unit,
    onMedia: (MediaChoice) -> Unit
) {
    PanelContent {
        Text(
            "Start a Share Session",
            color = TextMain,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )
        Text("Choose how you want to host.", color = TextMuted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HostTile(
                title = "Online Host", subtitle = "Manage a server room",
                symbol = "☁", selected = host == HostChoice.ONLINE,
                modifier = Modifier.weight(1f), onClick = { onHost(HostChoice.ONLINE) }
            )
            HostTile(
                title = "Local Host", subtitle = "Nearby devices · planned",
                symbol = "⌂", selected = host == HostChoice.LOCAL,
                modifier = Modifier.weight(1f), onClick = { onHost(HostChoice.LOCAL) }
            )
        }
        Text("Select what to share", color = TextMuted, fontSize = 14.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, PanelBorder, RoundedCornerShape(30.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MediaPill(
                "♪  Audio Share", media == MediaChoice.AUDIO,
                Modifier.weight(1f), { onMedia(MediaChoice.AUDIO) }
            )
            MediaPill(
                "▣  Video Share", media == MediaChoice.VIDEO,
                Modifier.weight(1f), { onMedia(MediaChoice.VIDEO) }
            )
        }
    }
}

@Composable
private fun HostTile(
    title: String,
    subtitle: String,
    symbol: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .height(190.dp)
            .clip(shape)
            .background(
                if (selected) Brush.verticalGradient(
                    listOf(
                        LocalHomeAppearance.current.activeTop,
                        LocalHomeAppearance.current.activeBottom
                    )
                )
                else Brush.verticalGradient(
                    listOf(
                        LocalHomeAppearance.current.inactiveTop,
                        LocalHomeAppearance.current.inactiveBottom
                    )
                )
            )
            .border(1.dp, if (selected) Accent else PanelBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            symbol,
            color = if (selected) LocalHomeAppearance.current.selectedIcon else TextMuted,
            fontSize = 36.sp,
            maxLines = 1
        )
        Text(
            title,
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            minLines = 2,
            lineHeight = 19.sp,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            color = if (selected) LocalHomeAppearance.current.selectedSubtitle else TextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .align(Alignment.End)
                .size(31.dp)
                .clip(CircleShape)
                .background(if (selected) LocalHomeAppearance.current.arrowLight else LocalHomeAppearance.current.arrowSurface),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "›", fontSize = 25.sp,
                color = if (selected) LocalHomeAppearance.current.arrowText else TextMain
            )
        }
    }
}

@Composable
private fun MediaPill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(
                if (selected) AccentGradient else Brush.linearGradient(
                    listOf(
                        Color.Transparent,
                        Color.Transparent
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else TextMuted,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun JoinPanel(onCode: () -> Unit, onNearby: () -> Unit) {
    PanelContent {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("◉", color = LocalHomeAppearance.current.sectionIcon, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Join Session",
                    color = TextMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Text(
                    "Use a room ID or discover nearby devices.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("#", "Join by Room ID", Modifier.weight(1f), onCode)
            ActionTile("◉", "Find Nearby", Modifier.weight(1f), onNearby)
        }
    }
}

@Composable
private fun ActionTile(symbol: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .heightIn(min = 66.dp)
            .border(1.dp, PanelBorder, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(LocalHomeAppearance.current.actionSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            symbol,
            color = LocalHomeAppearance.current.selectedIcon,
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp
        )
        Text(
            label, color = TextMain, fontSize = 12.sp, maxLines = 2,
            modifier = Modifier.weight(1f), lineHeight = 15.sp
        )
        Text("›", fontSize = 22.sp, color = TextMuted)
    }
}

@Composable
private fun MyRoomsPanel(rooms: List<Room>, onNavigateRooms: () -> Unit) {
    PanelContent {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⌂", color = LocalHomeAppearance.current.sectionIcon, fontSize = 29.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                "My Rooms", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 19.sp,
                modifier = Modifier.weight(1f)
            )
            RoomsEntryButton(onNavigateRooms, compact = true)
        }
        if (rooms.isEmpty()) {
            Text(
                "No rooms yet. Create your first room to get started.",
                color = TextMuted, fontSize = 13.sp
            )
            OutlinedButton(onClick = onNavigateRooms) { Text("Create or browse rooms") }
        } else {
            rooms.take(3).forEach { room ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(LocalHomeAppearance.current.roomSurface)
                        .clickable(onClick = onNavigateRooms)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(AccentGradient), contentAlignment = Alignment.Center
                    ) {
                        Text("⌂", color = TextMain, fontSize = 26.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(room.name?.takeIf { it.isNotBlank() } ?: "Untitled Room",
                            color = TextMain, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            room.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = TextMuted, fontSize = 12.sp
                        )
                    }
                    Text("›", color = TextMuted, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun RecentPanel() {
    PanelContent {
        Text(
            "◷   Recent Sessions",
            color = TextMain,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text("Your recent sessions will appear here.", color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun FooterWaves(modifier: Modifier) {
    val appearance = LocalHomeAppearance.current
    Canvas(modifier) {
        val back = Path().apply {
            moveTo(0f, size.height * .35f)
            cubicTo(
                size.width * .19f, size.height * .30f, size.width * .22f, size.height,
                size.width * .50f, size.height * .70f
            )
            cubicTo(
                size.width * .72f, size.height * .40f, size.width * .83f, size.height * .48f,
                size.width, size.height * .32f
            )
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(back, Brush.verticalGradient(listOf(appearance.waveTop, appearance.waveBottom)))
        val front = Path().apply {
            moveTo(0f, size.height * .58f)
            cubicTo(
                size.width * .23f, size.height * 1.12f, size.width * .39f, size.height * .62f,
                size.width * .59f, size.height * .54f
            )
            cubicTo(
                size.width * .75f, size.height * .44f, size.width * .84f, size.height * 1.1f,
                size.width, size.height * .58f
            )
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(
            front,
            Brush.verticalGradient(listOf(appearance.accentSecondary, appearance.waveBottom))
        )
    }
}
