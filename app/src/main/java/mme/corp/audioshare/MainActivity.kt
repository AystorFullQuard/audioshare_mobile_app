package mme.corp.audioshare

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import mme.corp.audioshare.audio.AudioStreamManager
import mme.corp.audioshare.audio.TestToneStreamManager
import mme.corp.audioshare.audio.capture.MicrophoneCapture
import mme.corp.audioshare.network.udp.UdpAudioSender
import mme.corp.audioshare.service.AudioStreamingService
import mme.corp.audioshare.util.NetworkUtils

class MainActivity : ComponentActivity() {

    private val port = 50005
    private val frameSize = MicrophoneCapture.FRAME_SIZE_BYTES

    private var streamManager: AudioStreamManager? = null
    private var testToneStreamManager: TestToneStreamManager? = null

    private data class PermissionUiState(
        val hasAudioPermission: Boolean,
        val hasNotificationPermission: Boolean
    )

    private data class PacketUiState(
        val packetsSent: Int,
        val packetsReceived: Int,
        val lastPacketSize: Int
    )

    private data class DebugUiState(
        val localIp: String,
        val receiverIp: String,
        val status: String,
        val permissions: PermissionUiState,
        val packets: PacketUiState
    )

    private data class DebugActions(
        val onReceiverIpChange: (String) -> Unit,
        val onCopyIpClick: () -> Unit,
        val onStartMicSenderClick: () -> Unit,
        val onStartTestToneSenderClick: () -> Unit,
        val onStartSystemAudioSenderClick: () -> Unit,
        val onStartReceiverServiceClick: () -> Unit,
        val onStopClick: () -> Unit
    )

    private data class ActivityLaunchers(
        val audioPermissionLauncher: ActivityResultLauncher<String>,
        val notificationPermissionLauncher: ActivityResultLauncher<String>,
        val mediaProjectionLauncher: ActivityResultLauncher<Intent>
    )

    private data class StartSystemAudioRequest(
        val receiverIp: String,
        val hasAudioPermission: Boolean,
        val hasNotificationPermission: Boolean
    )

    private data class SystemAudioCallbacks(
        val onPendingReceiverIpChange: (String) -> Unit,
        val onStatusChange: (String) -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AudioShareDebugScreen()
        }
    }

    @Composable
    private fun AudioShareDebugScreen() {
        val activity = this
        val context = LocalContext.current
        val localIp = remember { NetworkUtils.getLocalIpAddress() }

        var receiverIp by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Idle") }
        var packetsSent by remember { mutableIntStateOf(0) }
        var packetsReceived by remember { mutableIntStateOf(0) }
        var lastPacketSize by remember { mutableIntStateOf(0) }
        var pendingSystemAudioReceiverIp by remember { mutableStateOf("") }

        var hasAudioPermission by remember {
            mutableStateOf(hasPermission(Manifest.permission.RECORD_AUDIO))
        }

        var hasNotificationPermission by remember {
            mutableStateOf(hasNotificationPermission())
        }

        val audioPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasAudioPermission = granted
            status = if (granted) {
                "Microphone permission granted"
            } else {
                "Microphone permission denied"
            }
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasNotificationPermission = granted
            status = if (granted) {
                "Notification permission granted"
            } else {
                "Notification permission denied"
            }
        }

        val mediaProjectionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            status = handleMediaProjectionResult(
                resultCode = result.resultCode,
                data = result.data,
                receiverIp = pendingSystemAudioReceiverIp
            )
        }

        val permissions = PermissionUiState(
            hasAudioPermission = hasAudioPermission,
            hasNotificationPermission = hasNotificationPermission
        )

        val packets = PacketUiState(
            packetsSent = packetsSent,
            packetsReceived = packetsReceived,
            lastPacketSize = lastPacketSize
        )

        val uiState = DebugUiState(
            localIp = localIp,
            receiverIp = receiverIp,
            status = status,
            permissions = permissions,
            packets = packets
        )

        val launchers = ActivityLaunchers(
            audioPermissionLauncher = audioPermissionLauncher,
            notificationPermissionLauncher = notificationPermissionLauncher,
            mediaProjectionLauncher = mediaProjectionLauncher
        )

        val actions = DebugActions(
            onReceiverIpChange = { receiverIp = it },
            onCopyIpClick = {
                copyTextToClipboard(context, localIp)
                status = "Copied IP: $localIp"
            },
            onStartMicSenderClick = {
                onStartMicSenderClicked(
                    receiverIp = receiverIp,
                    hasAudioPermission = hasAudioPermission,
                    audioPermissionLauncher = audioPermissionLauncher,
                    onStatusChange = { status = it },
                    onCountersReset = {
                        packetsSent = 0
                        packetsReceived = 0
                        lastPacketSize = 0
                    },
                    onPacketSent = { size ->
                        activity.runOnUiThread {
                            packetsSent++
                            lastPacketSize = size
                            status = "Sending mic to ${receiverIp.trim()}:$port"
                        }
                    }
                )
            },
            onStartTestToneSenderClick = {
                onStartTestToneSenderClicked(
                    receiverIp = receiverIp,
                    onStatusChange = { status = it },
                    onCountersReset = {
                        packetsSent = 0
                        packetsReceived = 0
                        lastPacketSize = 0
                    },
                    onPacketSent = { size ->
                        activity.runOnUiThread {
                            packetsSent++
                            lastPacketSize = size
                            status = "Sending test tone to ${receiverIp.trim()}:$port"
                        }
                    }
                )
            },
            onStartSystemAudioSenderClick = {
                onStartSystemAudioSenderClicked(
                    request = StartSystemAudioRequest(
                        receiverIp = receiverIp,
                        hasAudioPermission = hasAudioPermission,
                        hasNotificationPermission = hasNotificationPermission
                    ),
                    launchers = launchers,
                    callbacks = SystemAudioCallbacks(
                        onPendingReceiverIpChange = {
                            pendingSystemAudioReceiverIp = it
                        },
                        onStatusChange = {
                            status = it
                        }
                    )
                )
            },
            onStartReceiverServiceClick = {
                onStartReceiverServiceClicked(
                    hasNotificationPermission = hasNotificationPermission,
                    notificationPermissionLauncher = notificationPermissionLauncher,
                    onStatusChange = { status = it },
                    onCountersReset = {
                        packetsSent = 0
                        packetsReceived = 0
                        lastPacketSize = 0
                    }
                )
            },
            onStopClick = {
                stopLocalStreams()
                AudioStreamingService.stop(this)
                status = "Stopped"
            }
        )

        AudioShareDebugContent(
            uiState = uiState,
            actions = actions
        )
    }

    @Composable
    private fun AudioShareDebugContent(
        uiState: DebugUiState,
        actions: DebugActions
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderSection(uiState.localIp)

            Button(
                onClick = actions.onCopyIpClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy This Device IP")
            }

            ReceiverIpInput(
                receiverIp = uiState.receiverIp,
                onReceiverIpChange = actions.onReceiverIpChange
            )

            ActionButtons(actions)

            HorizontalDivider()

            DiagnosticsSection(
                status = uiState.status,
                permissions = uiState.permissions,
                packets = uiState.packets
            )
        }
    }

    @Composable
    private fun HeaderSection(localIp: String) {
        Text(
            text = "AudioShare Debug",
            style = MaterialTheme.typography.headlineSmall
        )

        Text("This device IP: $localIp")
        Text("If this device is Receiver, enter this IP on Sender.")
        Text("Port: $port")
        Text("Frame size: $frameSize bytes")
    }

    @Composable
    private fun ReceiverIpInput(
        receiverIp: String,
        onReceiverIpChange: (String) -> Unit
    ) {
        OutlinedTextField(
            value = receiverIp,
            onValueChange = onReceiverIpChange,
            label = { Text("Receiver IP") },
            placeholder = { Text("Example: 192.168.1.42") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }

    @Composable
    private fun ActionButtons(actions: DebugActions) {
        Button(
            onClick = actions.onStartMicSenderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Mic Sender")
        }

        Button(
            onClick = actions.onStartTestToneSenderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Test Tone Sender")
        }

        Button(
            onClick = actions.onStartSystemAudioSenderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start System Audio Sender")
        }

        Button(
            onClick = actions.onStartReceiverServiceClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Receiver Service")
        }

        Button(
            onClick = actions.onStopClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop")
        }
    }

    @Composable
    private fun DiagnosticsSection(
        status: String,
        permissions: PermissionUiState,
        packets: PacketUiState
    ) {
        Text("Status: $status")
        Text("Microphone permission: ${permissions.hasAudioPermission}")
        Text("Notification permission: ${permissions.hasNotificationPermission}")
        Text("Packets sent: ${packets.packetsSent}")
        Text("Packets received: ${packets.packetsReceived}")
        Text("Last packet size: ${packets.lastPacketSize} bytes")
    }

    private fun onStartMicSenderClicked(
        receiverIp: String,
        hasAudioPermission: Boolean,
        audioPermissionLauncher: ActivityResultLauncher<String>,
        onStatusChange: (String) -> Unit,
        onCountersReset: () -> Unit,
        onPacketSent: (Int) -> Unit
    ) {
        val ip = receiverIp.trim()

        if (!hasAudioPermission) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            onStatusChange("Microphone permission required")
            return
        }

        if (!isValidIpv4(ip)) {
            onStatusChange("Invalid Receiver IP: $ip")
            return
        }

        try {
            stopLocalStreams()
            onCountersReset()

            val mic = MicrophoneCapture(applicationContext)

            val sender = UdpAudioSender(
                hostIp = ip,
                port = port,
                onPacketSent = onPacketSent
            )

            streamManager = AudioStreamManager(
                mic = mic,
                sender = sender,
                frameSize = frameSize,
                onError = { error ->
                    runOnUiThread {
                        onStatusChange("Sender error: ${error.message}")
                    }
                }
            )

            streamManager?.start()
            onStatusChange("Mic sender started")
        } catch (e: Exception) {
            onStatusChange("Sender failed: ${e.message}")
            stopLocalStreams()
        }
    }

    private fun onStartTestToneSenderClicked(
        receiverIp: String,
        onStatusChange: (String) -> Unit,
        onCountersReset: () -> Unit,
        onPacketSent: (Int) -> Unit
    ) {
        val ip = receiverIp.trim()

        if (!isValidIpv4(ip)) {
            onStatusChange("Invalid Receiver IP: $ip")
            return
        }

        try {
            stopLocalStreams()
            onCountersReset()

            val sender = UdpAudioSender(
                hostIp = ip,
                port = port,
                onPacketSent = onPacketSent
            )

            testToneStreamManager = TestToneStreamManager(
                sender = sender,
                frameSize = frameSize,
                onError = { error ->
                    runOnUiThread {
                        onStatusChange("Test tone error: ${error.message}")
                    }
                }
            )

            testToneStreamManager?.start()
            onStatusChange("Test tone sender started")
        } catch (e: Exception) {
            onStatusChange("Test tone failed: ${e.message}")
            stopLocalStreams()
        }
    }

    private fun onStartSystemAudioSenderClicked(
        request: StartSystemAudioRequest,
        launchers: ActivityLaunchers,
        callbacks: SystemAudioCallbacks
    ) {
        val ip = request.receiverIp.trim()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            callbacks.onStatusChange("System audio capture requires Android 10+")
            return
        }

        if (!request.hasAudioPermission) {
            launchers.audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            callbacks.onStatusChange("Microphone/audio capture permission required")
            return
        }

        if (requiresNotificationPermission(request.hasNotificationPermission)) {
            launchers.notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
            callbacks.onStatusChange("Notification permission required")
            return
        }

        if (!isValidIpv4(ip)) {
            callbacks.onStatusChange("Invalid Receiver IP: $ip")
            return
        }

        callbacks.onPendingReceiverIpChange(ip)

        val projectionManager = getSystemService(
            MediaProjectionManager::class.java
        )

        launchers.mediaProjectionLauncher.launch(
            projectionManager.createScreenCaptureIntent()
        )

        callbacks.onStatusChange("Waiting for system audio permission")
    }

    private fun onStartReceiverServiceClicked(
        hasNotificationPermission: Boolean,
        notificationPermissionLauncher: ActivityResultLauncher<String>,
        onStatusChange: (String) -> Unit,
        onCountersReset: () -> Unit
    ) {
        if (requiresNotificationPermission(hasNotificationPermission)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            onStatusChange("Notification permission required")
            return
        }

        try {
            stopLocalStreams()
            onCountersReset()

            AudioStreamingService.startReceiver(
                context = this,
                port = port
            )

            onStatusChange("Receiver service started on port $port")
        } catch (e: Exception) {
            onStatusChange("Receiver service failed: ${e.message}")
            stopLocalStreams()
        }
    }

    private fun handleMediaProjectionResult(
        resultCode: Int,
        data: Intent?,
        receiverIp: String
    ): String {
        if (resultCode != RESULT_OK || data == null) {
            return "System audio permission denied"
        }

        AudioStreamingService.startSystemAudio(
            context = this,
            receiverIp = receiverIp,
            port = port,
            resultCode = resultCode,
            projectionData = data
        )

        return "System audio sender service started"
    }

    private fun stopLocalStreams() {
        streamManager?.stop()
        testToneStreamManager?.stop()

        streamManager = null
        testToneStreamManager = null
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true
        }
    }

    private fun requiresNotificationPermission(
        hasNotificationPermission: Boolean
    ): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasNotificationPermission
    }

    private fun copyTextToClipboard(
        context: Context,
        text: String
    ) {
        val clipboardManager = context.getSystemService(
            ClipboardManager::class.java
        )

        clipboardManager.setPrimaryClip(
            ClipData.newPlainText("Device IP", text)
        )
    }

    private fun isValidIpv4(value: String): Boolean {
        val parts = value.split(".")
        if (parts.size != 4) return false

        return parts.all { part ->
            val number = part.toIntOrNull() ?: return false
            number in 0..255
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Do not stop AudioStreamingService here.
        // The service must continue when the app goes to background.
        stopLocalStreams()
    }
}