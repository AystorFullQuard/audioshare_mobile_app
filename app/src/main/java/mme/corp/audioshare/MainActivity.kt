package mme.corp.audioshare

import android.Manifest
import android.app.Activity
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
import mme.corp.audioshare.network.discovery.DiscoveredReceiver
import mme.corp.audioshare.network.discovery.UdpDiscoveryClient
import mme.corp.audioshare.network.udp.UdpAudioSender
import mme.corp.audioshare.service.AudioStreamingService
import mme.corp.audioshare.util.NetworkUtils

class MainActivity : ComponentActivity() {

    private val port = 50005
    private val frameSize = MicrophoneCapture.FRAME_SIZE_BYTES

    private var streamManager: AudioStreamManager? = null
    private var testToneStreamManager: TestToneStreamManager? = null
    private var discoveryClient: UdpDiscoveryClient? = null

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
        val packets: PacketUiState,
        val discoveredReceivers: List<DiscoveredReceiver>,
        val selectedReceiver: DiscoveredReceiver?
    )

    private data class InputActions(
        val onReceiverIpChange: (String) -> Unit,
        val onCopyIpClick: () -> Unit,
        val onSelectReceiverClick: (DiscoveredReceiver) -> Unit
    )

    private data class ReceiverActions(
        val onStartReceiverServiceClick: () -> Unit,
        val onFindReceiversClick: () -> Unit
    )

    private data class SenderActions(
        val onStartSystemAudioSenderClick: () -> Unit,
        val onStartMicSenderClick: () -> Unit,
        val onStartTestToneSenderClick: () -> Unit
    )

    private data class CommonActions(
        val onRequestPermissionsAgainClick: () -> Unit,
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

    private data class DiscoveryCallbacks(
        val onReceiversFound: (List<DiscoveredReceiver>) -> Unit,
        val onStatusChange: (String) -> Unit
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AudioShareScreen()
        }
    }

    @Composable
    private fun AudioShareScreen() {
        val activity = this
        val context = LocalContext.current
        val localIp = remember { NetworkUtils.getLocalIpAddress() }

        var receiverIp by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Ready. Start Receive Audio on the playback phone.") }
        var packetsSent by remember { mutableIntStateOf(0) }
        var packetsReceived by remember { mutableIntStateOf(0) }
        var lastPacketSize by remember { mutableIntStateOf(0) }
        var pendingSystemAudioReceiverIp by remember { mutableStateOf("") }
        var discoveredReceivers by remember { mutableStateOf(emptyList<DiscoveredReceiver>()) }
        var selectedReceiver by remember { mutableStateOf<DiscoveredReceiver?>(null) }

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
                "Microphone permission granted."
            } else {
                "Microphone permission is missing. Microphone and system audio capture cannot start."
            }
        }

        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasNotificationPermission = granted
            status = if (granted) {
                "Notification permission granted."
            } else {
                "Notification permission is missing. Background streaming may not show a foreground notification."
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
            packets = packets,
            discoveredReceivers = discoveredReceivers,
            selectedReceiver = selectedReceiver
        )

        val launchers = ActivityLaunchers(
            audioPermissionLauncher = audioPermissionLauncher,
            notificationPermissionLauncher = notificationPermissionLauncher,
            mediaProjectionLauncher = mediaProjectionLauncher
        )

        val inputActions = InputActions(
            onReceiverIpChange = {
                receiverIp = it
                selectedReceiver = null
            },
            onCopyIpClick = {
                copyTextToClipboard(context, localIp)
                status = "Copied receiver IP: $localIp"
            },
            onSelectReceiverClick = { receiver ->
                selectedReceiver = receiver
                receiverIp = receiver.ip
                status = "Selected receiver: ${receiver.displayName}"
            }
        )

        val receiverActions = ReceiverActions(
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
            onFindReceiversClick = {
                onFindReceiversClicked(
                    callbacks = DiscoveryCallbacks(
                        onReceiversFound = { receivers ->
                            discoveredReceivers = receivers
                            selectedReceiver = null
                            status = if (receivers.isEmpty()) {
                                "No receivers found. Make sure both phones are on the same Wi-Fi or hotspot, then tap Receive Audio on the receiver."
                            } else {
                                "Found ${receivers.size} receiver(s). Select one below."
                            }
                        },
                        onStatusChange = { status = it }
                    )
                )
            }
        )

        val senderActions = SenderActions(
            onStartSystemAudioSenderClick = {
                onStartSystemAudioSenderClicked(
                    request = StartSystemAudioRequest(
                        receiverIp = receiverIp,
                        hasAudioPermission = hasAudioPermission,
                        hasNotificationPermission = hasNotificationPermission
                    ),
                    launchers = launchers,
                    callbacks = SystemAudioCallbacks(
                        onPendingReceiverIpChange = { pendingSystemAudioReceiverIp = it },
                        onStatusChange = { status = it }
                    )
                )
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
                            status = "Sending microphone audio to ${receiverIp.trim()}:$port"
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
                            status = "Sending 220 Hz test tone to ${receiverIp.trim()}:$port"
                        }
                    }
                )
            }
        )

        val commonActions = CommonActions(
            onRequestPermissionsAgainClick = {
                onRequestPermissionsAgainClicked(
                    hasAudioPermission = hasAudioPermission,
                    hasNotificationPermission = hasNotificationPermission,
                    launchers = launchers,
                    onStatusChange = { status = it }
                )
            },
            onStopClick = {
                stopLocalStreams()
                AudioStreamingService.stop(this)
                status = "Stopped."
            }
        )

        AudioShareContent(
            uiState = uiState,
            inputActions = inputActions,
            receiverActions = receiverActions,
            senderActions = senderActions,
            commonActions = commonActions
        )
    }

    @Composable
    private fun AudioShareContent(
        uiState: DebugUiState,
        inputActions: InputActions,
        receiverActions: ReceiverActions,
        senderActions: SenderActions,
        commonActions: CommonActions
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HeaderSection()
            ReceiveAudioSection(uiState, inputActions, receiverActions)
            SendLocalHotspotSection(uiState, inputActions, receiverActions)
            AudioSourceSection(senderActions)
            PermissionsSection(uiState.permissions, commonActions)
            StatusSection(uiState.status, uiState.packets)
        }
    }

    @Composable
    private fun HeaderSection() {
        Text(
            text = "AudioShare",
            style = MaterialTheme.typography.headlineSmall
        )
        Text("Share audio locally over Wi-Fi or hotspot. No internet is required.")
    }

    @Composable
    private fun ReceiveAudioSection(
        uiState: DebugUiState,
        inputActions: InputActions,
        receiverActions: ReceiverActions
    ) {
        SectionTitle("1. Receive Audio")
        Text("Start this on the phone that will play sound. Use the shown IP on the sender.")
        Text("Use this IP on sender: ${uiState.localIp}")
        Text("Audio port: $port")

        Button(
            onClick = receiverActions.onStartReceiverServiceClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Receive Audio")
        }

        Button(
            onClick = inputActions.onCopyIpClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy Receiver IP")
        }

        HorizontalDivider()
    }

    @Composable
    private fun SendLocalHotspotSection(
        uiState: DebugUiState,
        inputActions: InputActions,
        receiverActions: ReceiverActions
    ) {
        SectionTitle("2. Send Local / Hotspot")
        Text("Use when both phones are on the same Wi-Fi or hotspot. No internet required.")

        OutlinedTextField(
            value = uiState.receiverIp,
            onValueChange = inputActions.onReceiverIpChange,
            label = { Text("Receiver IP") },
            placeholder = { Text("Example: 192.168.1.42") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        uiState.selectedReceiver?.let { receiver ->
            Text("Selected receiver: ${receiver.displayName}")
        }

        Button(
            onClick = receiverActions.onFindReceiversClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Find Receivers")
        }

        if (uiState.discoveredReceivers.isNotEmpty()) {
            Text("Receivers found on this Wi-Fi/hotspot:")
            uiState.discoveredReceivers.forEach { receiver ->
                Button(
                    onClick = { inputActions.onSelectReceiverClick(receiver) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(receiver.displayName)
                }
            }
        }

        HorizontalDivider()
    }

    @Composable
    private fun AudioSourceSection(senderActions: SenderActions) {
        SectionTitle("3. Audio Source")

        Text("System Audio")
        Text("Captures app/system playback using Android screen/audio permission.")
        Button(
            onClick = senderActions.onStartSystemAudioSenderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start System Audio")
        }

        Text("Microphone")
        Text("Streams your microphone to the receiver.")
        Button(
            onClick = senderActions.onStartMicSenderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Microphone")
        }

        Text("Test Tone")
        Text("Sends a 220 Hz test tone to check the receiver and network.")
        Button(
            onClick = senderActions.onStartTestToneSenderClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Test Tone")
        }

        HorizontalDivider()
    }

    @Composable
    private fun PermissionsSection(
        permissions: PermissionUiState,
        commonActions: CommonActions
    ) {
        SectionTitle("4. Permissions")
        Text("Microphone: ${permissionLabel(permissions.hasAudioPermission)}")
        Text("Notifications: ${permissionLabel(permissions.hasNotificationPermission)}")

        Button(
            onClick = commonActions.onRequestPermissionsAgainClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Request Permissions Again")
        }

        Button(
            onClick = commonActions.onStopClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop")
        }

        HorizontalDivider()
    }

    @Composable
    private fun StatusSection(
        status: String,
        packets: PacketUiState
    ) {
        SectionTitle("5. Status")
        Text(status)
        Text("Packets sent: ${packets.packetsSent}")
        Text("Packets received: ${packets.packetsReceived}")
        Text("Last packet size: ${packets.lastPacketSize} bytes")
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium
        )
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
            onStatusChange("Microphone permission required.")
            return
        }

        if (!isValidIpv4(ip)) {
            onStatusChange("Invalid receiver IP. Enter a local IP such as 192.168.x.x, 172.x.x.x, or 10.x.x.x.")
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
                        onStatusChange("Microphone sender error: ${error.message}")
                    }
                }
            )

            streamManager?.start()
            onStatusChange("Microphone sender started. Streaming directly to $ip:$port over local UDP.")
        } catch (error: Exception) {
            onStatusChange("Microphone sender failed: ${error.message}")
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
            onStatusChange("Invalid receiver IP. Enter the receiver phone local IP first.")
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
            onStatusChange("Test tone started. If the receiver plays a 220 Hz tone, local network audio works.")
        } catch (error: Exception) {
            onStatusChange("Test tone failed: ${error.message}")
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
            callbacks.onStatusChange("System audio capture requires Android 10 or newer.")
            return
        }

        if (!request.hasAudioPermission) {
            launchers.audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            callbacks.onStatusChange("Audio capture permission required.")
            return
        }

        if (requiresNotificationPermission(request.hasNotificationPermission)) {
            launchers.notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            callbacks.onStatusChange("Notification permission required for background streaming.")
            return
        }

        if (!isValidIpv4(ip)) {
            callbacks.onStatusChange("Invalid receiver IP. Enter a receiver IP or select a discovered receiver.")
            return
        }

        callbacks.onPendingReceiverIpChange(ip)

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        launchers.mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        callbacks.onStatusChange("Waiting for Android screen/audio capture permission.")
    }

    private fun onStartReceiverServiceClicked(
        hasNotificationPermission: Boolean,
        notificationPermissionLauncher: ActivityResultLauncher<String>,
        onStatusChange: (String) -> Unit,
        onCountersReset: () -> Unit
    ) {
        if (requiresNotificationPermission(hasNotificationPermission)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            onStatusChange("Notification permission required for background receiver playback.")
            return
        }

        try {
            stopLocalStreams()
            onCountersReset()

            AudioStreamingService.startReceiver(
                context = this,
                port = port
            )

            onStatusChange("Receive Audio started. Use this device IP on the sender. Discovery is also active.")
        } catch (error: Exception) {
            onStatusChange("Receiver failed to start: ${error.message}")
            stopLocalStreams()
        }
    }

    private fun onFindReceiversClicked(callbacks: DiscoveryCallbacks) {
        discoveryClient?.stop()
        callbacks.onStatusChange("Searching for receivers on the same Wi-Fi or hotspot...")

        discoveryClient = UdpDiscoveryClient(
            onReceiversFound = { receivers ->
                runOnUiThread {
                    callbacks.onReceiversFound(receivers)
                }
            },
            onError = { error ->
                runOnUiThread {
                    callbacks.onStatusChange("Receiver discovery failed: ${error.message}")
                }
            }
        )

        discoveryClient?.findReceivers()
    }

    private fun onRequestPermissionsAgainClicked(
        hasAudioPermission: Boolean,
        hasNotificationPermission: Boolean,
        launchers: ActivityLaunchers,
        onStatusChange: (String) -> Unit
    ) {
        if (!hasAudioPermission) {
            launchers.audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            onStatusChange("Requesting microphone permission...")
            return
        }

        if (requiresNotificationPermission(hasNotificationPermission)) {
            launchers.notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            onStatusChange("Requesting notification permission...")
            return
        }

        onStatusChange("Required normal permissions are already granted.")
    }

    private fun handleMediaProjectionResult(
        resultCode: Int,
        data: Intent?,
        receiverIp: String
    ): String {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return "System audio permission denied. Nothing was started."
        }

        AudioStreamingService.startSystemAudio(
            context = this,
            receiverIp = receiverIp,
            port = port,
            resultCode = resultCode,
            projectionData = data
        )

        return "System audio capture started. If the selected app or browser blocks capture, try non-protected audio or another app."
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

    private fun permissionLabel(granted: Boolean): String {
        return if (granted) "Granted" else "Missing"
    }

    private fun copyTextToClipboard(
        context: Context,
        text: String
    ) {
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Receiver IP", text))
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
        discoveryClient?.stop()
        stopLocalStreams()
    }
}
