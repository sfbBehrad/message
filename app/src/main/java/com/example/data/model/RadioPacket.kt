package com.example.data.model

/**
 * Strict 32-byte packet structure for ESP32 + NRF24L01 radio bridge.
 *
 * Byte 0:    Message Type (0: UDP Text, 1: TCP Text, 2: File/Image Chunk, 3: ACK)
 * Byte 1:    Message ID (0 - 255)
 * Bytes 2-3: Current Chunk Index (uint16, big-endian)
 * Bytes 4-5: Total Chunks (uint16, big-endian)
 * Bytes 6-31: Data Payload (up to 26 bytes)
 */
data class RadioPacket(
    val type: Int,          // 0: UDP Text, 1: TCP Text, 2: File Chunk, 3: ACK
    val messageId: Int,     // 0-255
    val chunkIndex: Int,    // 0-65535
    val totalChunks: Int,   // 1-65535
    val payload: ByteArray  // up to 26 bytes
) {
    companion object {
        const val PACKET_SIZE = 32
        const val MAX_PAYLOAD_SIZE = 26

        const val TYPE_UDP_TEXT = 0
        const val TYPE_TCP_TEXT = 1
        const val TYPE_FILE_CHUNK = 2
        const val TYPE_ACK = 3

        /**
         * Encodes this packet into an exact 32-byte array.
         */
        fun toBytes(packet: RadioPacket): ByteArray {
            val buffer = ByteArray(PACKET_SIZE)
            buffer[0] = (packet.type and 0xFF).toByte()
            buffer[1] = (packet.messageId and 0xFF).toByte()
            buffer[2] = ((packet.chunkIndex shr 8) and 0xFF).toByte()
            buffer[3] = (packet.chunkIndex and 0xFF).toByte()
            buffer[4] = ((packet.totalChunks shr 8) and 0xFF).toByte()
            buffer[5] = (packet.totalChunks and 0xFF).toByte()

            val copyLen = minOf(packet.payload.size, MAX_PAYLOAD_SIZE)
            System.arraycopy(packet.payload, 0, buffer, 6, copyLen)
            // Bytes 6 + copyLen .. 31 remain 0 (zero padded)
            return buffer
        }

        /**
         * Parses a 32-byte array into a RadioPacket.
         */
        fun fromBytes(bytes: ByteArray): RadioPacket? {
            if (bytes.size < PACKET_SIZE) return null

            val type = bytes[0].toInt() and 0xFF
            val messageId = bytes[1].toInt() and 0xFF
            val chunkIndex = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            val totalChunks = ((bytes[4].toInt() and 0xFF) shl 8) or (bytes[5].toInt() and 0xFF)

            val payload = ByteArray(MAX_PAYLOAD_SIZE)
            System.arraycopy(bytes, 6, payload, 0, MAX_PAYLOAD_SIZE)

            return RadioPacket(
                type = type,
                messageId = messageId,
                chunkIndex = chunkIndex,
                totalChunks = maxOf(1, totalChunks),
                payload = payload
            )
        }

        /**
         * Builds an immediate Type 3 (ACK) packet for a received packet.
         */
        fun buildAck(targetPacket: RadioPacket): RadioPacket {
            return RadioPacket(
                type = TYPE_ACK,
                messageId = targetPacket.messageId,
                chunkIndex = targetPacket.chunkIndex,
                totalChunks = targetPacket.totalChunks,
                payload = ByteArray(MAX_PAYLOAD_SIZE) // zero payload
            )
        }

        fun typeName(type: Int): String = when (type) {
            TYPE_UDP_TEXT -> "UDP_TEXT"
            TYPE_TCP_TEXT -> "TCP_TEXT"
            TYPE_FILE_CHUNK -> "FILE_CHUNK"
            TYPE_ACK -> "ACK"
            else -> "UNKNOWN($type)"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RadioPacket) return false

        if (type != other.type) return false
        if (messageId != other.messageId) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + messageId
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + payload.contentHashCode()
        return result
    }

    fun toHexString(): String {
        val bytes = toBytes(this)
        return bytes.joinToString(" ") { String.format("%02X", it) }
    }
}
