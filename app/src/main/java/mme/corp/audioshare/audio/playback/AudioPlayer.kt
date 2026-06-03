package mme.corp.audioshare.audio.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

class AudioPlayer {
    private val sampleRate = 48000

    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val audioTrack = AudioTrack(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build(),

        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),

        bufferSize,
        AudioTrack.MODE_STREAM,
        AudioTrack.WRITE_BLOCKING
    )

    fun start() {
        audioTrack.play()
    }

    fun write(buffer: ByteArray, size: Int) {
        audioTrack.write(buffer, 0, size)
    }

    fun stop() {
        audioTrack.stop()
    }

    fun release() {
        audioTrack.release()
    }
}