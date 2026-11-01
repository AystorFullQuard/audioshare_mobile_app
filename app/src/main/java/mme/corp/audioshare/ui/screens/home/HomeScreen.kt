package mme.corp.audioshare.ui.screens.home

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mme.corp.audioshare.AudioShareApplication
import mme.corp.audioshare.BuildConfig
import mme.corp.audioshare.data.model.room.Room
import mme.corp.audioshare.startup.BootstrapStartupRequest
import mme.corp.audioshare.ui.screens.home.uiBlocks.RoomsEntryButton
import java.util.Locale
import java.util.TimeZone

private enum class HostChoice { ONLINE, LOCAL }
private enum class MediaChoice { AUDIO, VIDEO }
private enum class HomeIcon { WAVE, CLOUD, HOME, MIC, VIDEO, PERSON, NEARBY, ARROW, HISTORY }

private val LocalPalette = staticCompositionLocalOf { HomeAppearance.DARK }

/** Reuses bootstrap coordinator and real room state; Home is visible before authentication. */
@Composable
fun HomeScreen(
    onLogin: () -> Unit = {},
    onNavigateRooms: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val app = LocalContext.current.applicationContext as AudioShareApplication
    val token by app.container.accessTokenState.collectAsStateWithLifecycle()
    val presence by app.container.presenceHeartbeatCoordinator.confirmedPresence.collectAsStateWithLifecycle()
    val roomsState by app.container.roomSessionCoordinator.state.collectAsStateWithLifecycle()
    val user by app.container.currentUser.collectAsStateWithLifecycle()
    val signedIn = !token.isNullOrBlank()
    val ready = signedIn && presence != null
    var retryNumber by remember { mutableIntStateOf(0) }
    var isConnecting by remember { mutableStateOf(false) }
    var connectionError by remember { mutableStateOf<String?>(null) }

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
                .onFailure {
                    connectionError = "Unable to load rooms. Check your server connection."
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            connectionError = "Unable to load rooms. Check your server connection."
        } finally {
            isConnecting = false
        }
    }
    SvReHome(
        signedIn = signedIn,
        avatarInitial = user?.displayName?.firstOrNull()?.uppercaseChar()?.toString(),
        rooms = if (ready) roomsState.rooms else emptyList(),
        roomsReady = ready,
        isConnecting = isConnecting && !ready,
        connectionError = if (ready) null else connectionError,
        onRetry = { retryNumber++ },
        onAvatarClick = { if (signedIn) onOpenProfile() else onLogin() },
        onNavigateRooms = onNavigateRooms,
        appearance = HomeAppearance.from(
            user?.settings,
            HomeThemeOverrides(
                style = user?.settings?.theme ?: if (isSystemInDarkTheme()) "DARK" else "LIGHT"
            )
        )
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

    CompositionLocalProvider(LocalPalette provides appearance) {
        Box(Modifier
            .fillMaxSize()
            .background(appearance.background)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                BrandHeader(signedIn, avatarInitial, onAvatarClick)
                if (isConnecting) Text(
                    "Connecting to ServerRelay…",
                    color = appearance.muted,
                    fontSize = 13.sp
                )
                if (connectionError != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            connectionError,
                            color = appearance.muted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRetry) { Text("Retry") }
                    }
                }
                HostPanel(
                    host = host, media = media,
                    onHost = { choice ->
                        host = choice
                        when {
                            media == MediaChoice.VIDEO -> notice("Video sharing is not available yet")
                            choice == HostChoice.LOCAL -> notice("Secure nearby hosting is not available yet")
                            roomsReady && !isConnecting && connectionError == null -> onNavigateRooms()
                            else -> notice("Sign in and connect to manage rooms")
                        }
                    },
                    onMedia = { media = it }
                )
                JoinPanel(
                    onCode = {
                        if (roomsReady && !isConnecting && connectionError == null) onNavigateRooms() else notice(
                            "Sign in to join a server room"
                        )
                    },
                    onNearby = { notice("Nearby discovery is not available yet") }
                )
                MyRoomsPanel(rooms) {
                    if (roomsReady && !isConnecting && connectionError == null) onNavigateRooms()
                    else notice("Sign in and connect to view your rooms")
                }
                RecentPanel()
            }
            FooterWaves(Modifier
                .fillMaxWidth()
                .height(72.dp)
                .align(Alignment.BottomCenter))
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp)
            )
        }
    }
}

@Composable
private fun BrandHeader(signedIn: Boolean, avatarInitial: String?, onAvatarClick: () -> Unit) {
    val colors = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LineIcon(HomeIcon.WAVE, Modifier.size(32.dp), colors.accent)
        Spacer(Modifier.width(12.dp))
        Text("SvRe", color = colors.text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.dp, colors.border, CircleShape)
                .background(colors.raised)
                .clickable(onClick = onAvatarClick)
                .semantics { contentDescription = if (signedIn) "Open profile" else "Sign in" }
                .testTag("home_profile_avatar"),
            contentAlignment = Alignment.Center
        ) {
            if (signedIn && !avatarInitial.isNullOrBlank()) {
                Text(
                    avatarInitial,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.text
                )
            } else LineIcon(HomeIcon.PERSON, Modifier.size(23.dp), colors.text)
            if (signedIn) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.success)
                        .border(2.dp, colors.raised, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.panel)
            .border(1.dp, colors.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun HostPanel(
    host: HostChoice, media: MediaChoice,
    onHost: (HostChoice) -> Unit, onMedia: (MediaChoice) -> Unit
) {
    val colors = LocalPalette.current
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Start sharing",
                color = colors.text,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Choose where to host", color = colors.muted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HostTile(
                "Online host", "Manage a server room", HomeIcon.CLOUD,
                host == HostChoice.ONLINE, Modifier.weight(1f)
            ) { onHost(HostChoice.ONLINE) }
            HostTile(
                "Local host", "Nearby devices · coming later", HomeIcon.HOME,
                host == HostChoice.LOCAL, Modifier.weight(1f)
            ) { onHost(HostChoice.LOCAL) }
        }
        Text("Media", color = colors.muted, fontSize = 13.sp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.raised)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MediaOption(
                "Audio",
                HomeIcon.MIC,
                media == MediaChoice.AUDIO,
                Modifier.weight(1f)
            ) { onMedia(MediaChoice.AUDIO) }
            MediaOption(
                "Video",
                HomeIcon.VIDEO,
                media == MediaChoice.VIDEO,
                Modifier.weight(1f)
            ) { onMedia(MediaChoice.VIDEO) }
        }
    }
}

@Composable
private fun HostTile(
    title: String, subtitle: String, icon: HomeIcon,
    selected: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    val colors = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .height(168.dp)
            .clip(shape)
            .background(if (selected) colors.selected else colors.raised)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LineIcon(icon, Modifier.size(26.dp), if (selected) colors.accent else colors.muted)
        Spacer(Modifier.weight(1f))
        Text(
            title, color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle, color = colors.muted, fontSize = 12.sp, lineHeight = 16.sp,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaOption(
    label: String, icon: HomeIcon, selected: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val colors = LocalPalette.current
    Row(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.panel else Color.Transparent)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        LineIcon(icon, Modifier.size(18.dp), if (selected) colors.accent else colors.muted)
        Spacer(Modifier.width(8.dp))
        Text(
            label, color = if (selected) colors.text else colors.muted,
            fontSize = 14.sp, fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun JoinPanel(onCode: () -> Unit, onNearby: () -> Unit) {
    val colors = LocalPalette.current
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Join a session",
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text("Use a room ID or connect nearby", color = colors.muted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionTile("Room ID", HomeIcon.HOME, Modifier.weight(1f), onCode)
            ActionTile("Nearby", HomeIcon.NEARBY, Modifier.weight(1f), onNearby)
        }
    }
}

@Composable
private fun ActionTile(label: String, icon: HomeIcon, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalPalette.current
    Row(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LineIcon(icon, Modifier.size(18.dp), colors.muted)
        Text(
            label,
            color = colors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        LineIcon(HomeIcon.ARROW, Modifier.size(14.dp), colors.muted)
    }
}

@Composable
private fun MyRoomsPanel(rooms: List<Room>, onNavigateRooms: () -> Unit) {
    val colors = LocalPalette.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "My Rooms", color = colors.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            RoomsEntryButton(onNavigateRooms, compact = true)
        }
        if (rooms.isEmpty()) {
            Text(
                "No rooms yet. Create your first room to get started.",
                color = colors.muted,
                fontSize = 13.sp
            )
            OutlinedButton(onClick = onNavigateRooms) { Text("Create or browse rooms") }
        } else {
            rooms.take(3).forEach { room ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.raised)
                        .clickable(onClick = onNavigateRooms)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.selected), contentAlignment = Alignment.Center
                    ) {
                        LineIcon(HomeIcon.WAVE, Modifier.size(20.dp), colors.accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(room.name?.takeIf { it.isNotBlank() } ?: "Untitled room",
                            color = colors.text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text(
                            room.status.name.lowercase().replaceFirstChar { it.uppercase() },
                            color = colors.muted, fontSize = 12.sp
                        )
                    }
                    LineIcon(HomeIcon.ARROW, Modifier.size(16.dp), colors.muted)
                }
            }
        }
    }
}

@Composable
private fun RecentPanel() {
    val colors = LocalPalette.current
    Panel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LineIcon(HomeIcon.HISTORY, Modifier.size(19.dp), colors.muted)
            Text(
                "Recent Sessions",
                color = colors.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Text("Your recent sessions will appear here.", color = colors.muted, fontSize = 13.sp)
    }
}

/** Subtle two-tone waves are the only decorative flourish, using the neutral ramp. */
@Composable
private fun FooterWaves(modifier: Modifier) {
    val colors = LocalPalette.current
    Canvas(modifier) {
        val back = Path().apply {
            moveTo(0f, size.height * .55f)
            cubicTo(
                size.width * .22f, size.height * .38f, size.width * .32f, size.height * .87f,
                size.width * .53f, size.height * .64f
            )
            cubicTo(
                size.width * .75f, size.height * .42f, size.width * .85f, size.height * .71f,
                size.width, size.height * .49f
            )
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(back, colors.waveSecondary)
        val front = Path().apply {
            moveTo(0f, size.height * .70f)
            cubicTo(
                size.width * .20f, size.height * .57f, size.width * .28f, size.height * .97f,
                size.width * .50f, size.height * .80f
            )
            cubicTo(
                size.width * .75f, size.height * .63f, size.width * .82f, size.height * .88f,
                size.width, size.height * .72f
            )
            lineTo(size.width, size.height); lineTo(0f, size.height); close()
        }
        drawPath(front, colors.wave)
    }
}

/** One consistently stroked icon set; avoids platform-dependent symbol fonts. */
@Composable
private fun LineIcon(icon: HomeIcon, modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width;
        val h = size.height
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(
                color,
                Offset(x1 * w, y1 * h),
                Offset(x2 * w, y2 * h),
                strokeWidth = w * .07f,
                cap = StrokeCap.Round
            )
        when (icon) {
            HomeIcon.WAVE -> listOf(.3f, .7f, 1f, .65f, .4f).forEachIndexed { index, height ->
                val x = .12f + index * .19f
                line(x, (1f - height) / 2f, x, (1f + height) / 2f)
            }

            HomeIcon.CLOUD -> {
                val p = Path().apply {
                    moveTo(w * .23f, h * .75f)
                    cubicTo(w * .03f, h * .75f, w * .05f, h * .48f, w * .27f, h * .47f)
                    cubicTo(w * .3f, h * .13f, w * .74f, h * .13f, w * .77f, h * .49f)
                    cubicTo(w * .98f, h * .50f, w * .99f, h * .75f, w * .75f, h * .75f)
                    close()
                }
                drawPath(p, color, style = Stroke(width = w * .065f, cap = StrokeCap.Round))
            }

            HomeIcon.HOME -> {
                line(.15f, .48f, .50f, .15f); line(.50f, .15f, .85f, .48f)
                line(.25f, .44f, .25f, .86f); line(.75f, .44f, .75f, .86f); line(
                    .25f,
                    .86f,
                    .75f,
                    .86f
                )
                line(.43f, .86f, .43f, .64f); line(.43f, .64f, .57f, .64f); line(
                    .57f,
                    .64f,
                    .57f,
                    .86f
                )
            }

            HomeIcon.MIC -> {
                drawRoundRect(
                    color,
                    Offset(w * .38f, h * .11f),
                    androidx.compose.ui.geometry.Size(w * .24f, h * .48f),
                    androidx.compose.ui.geometry.CornerRadius(w * .12f),
                    style = Stroke(width = w * .07f)
                )
                val p = Path().apply {
                    moveTo(w * .24f, h * .48f); cubicTo(
                    w * .24f,
                    h * .91f,
                    w * .76f,
                    h * .91f,
                    w * .76f,
                    h * .48f
                )
                }
                drawPath(p, color, style = Stroke(width = w * .07f, cap = StrokeCap.Round))
                line(.5f, .78f, .5f, .92f); line(.35f, .92f, .65f, .92f)
            }

            HomeIcon.VIDEO -> {
                drawRoundRect(
                    color,
                    Offset(w * .13f, h * .26f),
                    androidx.compose.ui.geometry.Size(w * .5f, h * .48f),
                    androidx.compose.ui.geometry.CornerRadius(w * .08f),
                    style = Stroke(width = w * .07f)
                )
                val p = Path().apply {
                    moveTo(w * .66f, h * .39f); lineTo(
                    w * .88f,
                    h * .27f
                ); lineTo(w * .88f, h * .73f); lineTo(w * .66f, h * .61f)
                }
                drawPath(p, color, style = Stroke(width = w * .07f))
            }

            HomeIcon.PERSON -> {
                drawCircle(
                    color,
                    w * .15f,
                    Offset(w * .5f, h * .30f),
                    style = Stroke(width = w * .065f)
                )
                val p = Path().apply {
                    moveTo(w * .19f, h * .87f); cubicTo(
                    w * .19f,
                    h * .52f,
                    w * .81f,
                    h * .52f,
                    w * .81f,
                    h * .87f
                )
                }
                drawPath(p, color, style = Stroke(width = w * .065f, cap = StrokeCap.Round))
            }

            HomeIcon.NEARBY -> {
                drawCircle(color, w * .07f, Offset(w * .50f, h * .5f))
                drawCircle(
                    color,
                    w * .26f,
                    Offset(w * .50f, h * .5f),
                    style = Stroke(width = w * .06f)
                )
                drawCircle(
                    color,
                    w * .43f,
                    Offset(w * .50f, h * .5f),
                    style = Stroke(width = w * .05f)
                )
            }

            HomeIcon.ARROW -> {
                line(.2f, .5f, .8f, .5f); line(.55f, .25f, .8f, .5f); line(.8f, .5f, .55f, .75f)
            }

            HomeIcon.HISTORY -> {
                drawCircle(
                    color,
                    w * .39f,
                    Offset(w * .5f, h * .5f),
                    style = Stroke(width = w * .065f)
                )
                line(.5f, .26f, .5f, .52f); line(.5f, .52f, .67f, .63f)
            }
        }
    }
}
