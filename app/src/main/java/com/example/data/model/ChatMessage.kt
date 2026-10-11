package com.example.data.model

enum class SenderType {
    ME,
    PEER
}

enum class MessageStatus {
    PENDING,
    SENDING,
    SENT_UNACKED,   // for UDP (single tick)
    DELIVERED,      // for TCP/File when all ACKs received (double tick)
    FAILED
}

data class ChatMessage(
    val id: String,
    val messageIdByte: Int,
    val sender: SenderType,
    val type: Int,
    val textContent: String? = null,
    val fileBytes: ByteArray? = null,
    val fileName: String? = null,
    val isImage: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val totalChunks: Int = 1,
    val currentChunk: Int = 0,
    val retriesCurrentChunk: Int = 0,
    val status: MessageStatus = MessageStatus.PENDING
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessage) return false
        return id == other.id &&
                messageIdByte == other.messageIdByte &&
                sender == other.sender &&
                type == other.type &&
                textContent == other.textContent &&
                fileName == other.fileName &&
                isImage == other.isImage &&
                timestamp == other.timestamp &&
                totalChunks == other.totalChunks &&
                currentChunk == other.currentChunk &&
                retriesCurrentChunk == other.retriesCurrentChunk &&
                status == other.status
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class PacketLogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val direction: Direction,
    val typeName: String,
    val messageId: Int,
    val chunkIndex: Int,
    val totalChunks: Int,
    val hexDump: String,
    val isAck: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Direction { TX, RX, SYS }
}
