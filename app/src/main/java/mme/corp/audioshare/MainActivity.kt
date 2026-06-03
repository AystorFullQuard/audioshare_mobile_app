package mme.corp.audioshare

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import mme.corp.audioshare.audio.AudioStreamManager
import mme.corp.audioshare.audio.capture.MicrophoneCapture
import mme.corp.audioshare.audio.playback.AudioPlayer
import mme.corp.audioshare.network.udp.UdpAudioReceiver
import mme.corp.audioshare.network.udp.UdpAudioSender
import mme.corp.audioshare.util.NetworkUtils

class MainActivity : ComponentActivity() {

    private val port = 50005
    private val frameSize = 1920

    private var streamManager: AudioStreamManager? = null
    private var receiver: UdpAudioReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestAudioPermission()

        setContent {
            AudioShareDebugScreen()
        }
    }

    private fun requestAudioPermission() {
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        }
    }

    @Composable
    private fun AudioShareDebugScreen() {
        val localIp = remember { NetworkUtils.getLocalIpAddress() }
        val clipboardManager = LocalClipboardManager.current

        var receiverIp by remember { mutableStateOf("192.168.224.") }

        var status by remember { mutableStateOf("Idle") }
        var packetsSent by remember { mutableStateOf(0) }
        var packetsReceived by remember { mutableStateOf(0) }
        var lastPacketSize by remember { mutableStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AudioShare Debug", style = MaterialTheme.typography.headlineSmall)

            Text("This device IP: $localIp")
            Text("If this phone is Receiver, enter this IP on Sender.")

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(localIp))
                    status = "Copied IP: $localIp"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy This Device IP")
            }

            OutlinedTextField(
                value = receiverIp,
                onValueChange = { receiverIp = it },
                label = { Text("Receiver IP") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    stopAll()

                    packetsSent = 0
                    packetsReceived = 0
                    lastPacketSize = 0

                    val mic = MicrophoneCapture()

                    val sender = UdpAudioSender(
                        hostIp = receiverIp.trim(),
                        port = port,
                        onPacketSent = { size ->
                            packetsSent++
                            lastPacketSize = size
                            status = "Sending to ${receiverIp.trim()}:$port"
                        }
                    )

                    streamManager = AudioStreamManager(
                        mic = mic,
                        sender = sender,
                        frameSize = frameSize
                    )

                    streamManager?.start()
                    status = "Sender started"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Sender")
            }

            Button(
                onClick = {
                    stopAll()

                    packetsSent = 0
                    packetsReceived = 0
                    lastPacketSize = 0

                    val player = AudioPlayer()

                    receiver = UdpAudioReceiver(
                        port = port,
                        audioPlayer = player,
                        onPacketReceived = { size ->
                            packetsReceived++
                            lastPacketSize = size
                            status = "Receiving on port $port"
                        }
                    )

                    receiver?.start()
                    status = "Receiver started on port $port"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Receiver")
            }

            Button(
                onClick = {
                    stopAll()
                    status = "Stopped"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop")
            }

            Divider()

            Text("Status: $status")
            Text("Port: $port")
            Text("Packets sent: $packetsSent")
            Text("Packets received: $packetsReceived")
            Text("Last packet size: $lastPacketSize bytes")
        }
    }

    private fun stopAll() {
        streamManager?.stop()
        receiver?.stop()
        streamManager = null
        receiver = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAll()
    }
}