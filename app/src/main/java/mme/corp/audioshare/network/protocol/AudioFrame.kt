package mme.corp.audioshare.network.protocol

class AudioFrame(
    val sequence: Int,
    val timestamp: Long,
    val data: ByteArray,
    val size: Int) {

}