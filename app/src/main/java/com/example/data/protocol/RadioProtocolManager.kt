package com.example.data.protocol

import android.content.Context
import com.example.data.bluetooth.BluetoothService
import com.example.data.crypto.AesSecurity
import com.example.data.model.ChatMessage
import com.example.data.model.MessageStatus
import com.example.data.model.PacketLogEntry
import com.example.data.model.RadioPacket
import com.example.data.model.SenderType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class RadioProtocolManager(
    private val context: Context,
    private val bluetoothService: BluetoothService
) {
    companion object {
        const val ACK_TIMEOUT_MS = 500L
        const val MAX_RETRIES = 12
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val nextMessageId = AtomicInteger(1)

    // Log buffer for terminal screen
    private val _logs = MutableStateFlow<List<PacketLogEntry>>(emptyList())
    val logs: StateFlow<List<PacketLogEntry>> = _logs

    // Completed chat messages
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // Incoming completed message event (for triggering notifications)
    private val _incomingMessageEvent = MutableSharedFlow<ChatMessage>()
    val incomingMessageEvent: SharedFlow<ChatMessage> = _incomingMessageEvent

    // Pending ACK waiter: key = (messageId shl 16) or chunkIndex -> CompletableDeferred<Unit>
    private val pendingAcks = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()

    // In-progress reassembly for incoming messages: messageId -> Map<chunkIndex, payloadBytes>
    private val incomingChunks = ConcurrentHashMap<Int, ConcurrentHashMap<Int, ByteArray>>()
    private val incomingMeta = ConcurrentHashMap<Int, Pair<Int, Int>>() // messageId -> Pair(type, totalChunks)

    init {
        // Collect incoming packets from Bluetooth
        scope.launch {
            bluetoothService.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }

        // Collect outgoing packets for terminal logging
        scope.launch {
            bluetoothService.outgoingPackets.collect { packet ->
                addLog(
                    direction = PacketLogEntry.Direction.TX,
                    typeName = RadioPacket.typeName(packet.type),
                    messageId = packet.messageId,
                    chunkIndex = packet.chunkIndex,
                    totalChunks = packet.totalChunks,
                    hexDump = packet.toHexString(),
                    isAck = packet.type == RadioPacket.TYPE_ACK
                )
            }
        }
    }

    private fun addLog(
        direction: PacketLogEntry.Direction,
        typeName: String,
        messageId: Int,
        chunkIndex: Int,
        totalChunks: Int,
        hexDump: String,
        isAck: Boolean
    ) {
        val entry = PacketLogEntry(
            direction = direction,
            typeName = typeName,
            messageId = messageId,
            chunkIndex = chunkIndex,
            totalChunks = totalChunks,
            hexDump = hexDump,
            isAck = isAck
        )
        val current = _logs.value.toMutableList()
        current.add(entry)
        if (current.size > 200) {
            current.removeAt(0)
        }
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private suspend fun handleIncomingPacket(packet: RadioPacket) {
        addLog(
            direction = PacketLogEntry.Direction.RX,
            typeName = RadioPacket.typeName(packet.type),
            messageId = packet.messageId,
            chunkIndex = packet.chunkIndex,
            totalChunks = packet.totalChunks,
            hexDump = packet.toHexString(),
            isAck = packet.type == RadioPacket.TYPE_ACK
        )

        // 1. If this is an ACK packet (Type 3), satisfy the waiting sender loop
        if (packet.type == RadioPacket.TYPE_ACK) {
            val key = ackKey(packet.messageId, packet.chunkIndex)
            val deferred = pendingAcks[key]
            deferred?.complete(Unit)
            return
        }

        // 2. If this is a Type 1 (TCP text) or Type 2 (File chunk), IMMEDIATELY send ACK (Type 3) back!
        if (packet.type == RadioPacket.TYPE_TCP_TEXT || packet.type == RadioPacket.TYPE_FILE_CHUNK) {
            val ackPacket = RadioPacket.buildAck(packet)
            bluetoothService.sendPacket(ackPacket)
        }

        // 3. Assemble chunk into incoming message stream
        val msgId = packet.messageId
        val chunksMap = incomingChunks.getOrPut(msgId) { ConcurrentHashMap() }
        chunksMap[packet.chunkIndex] = packet.payload
        incomingMeta[msgId] = Pair(packet.type, packet.totalChunks)

        // Check if all chunks received
        if (chunksMap.size == packet.totalChunks) {
            reassembleAndProcessMessage(msgId, packet.type, packet.totalChunks, chunksMap)
            incomingChunks.remove(msgId)
            incomingMeta.remove(msgId)
        }
    }

    private fun reassembleAndProcessMessage(
        messageId: Int,
        type: Int,
        totalChunks: Int,
        chunksMap: Map<Int, ByteArray>
    ) {
        try {
            // Concatenate all payloads in index order
            val totalBytes = totalChunks * RadioPacket.MAX_PAYLOAD_SIZE
            val combined = ByteArray(totalBytes)
            var offset = 0
            for (i in 0 until totalChunks) {
                val chunk = chunksMap[i] ?: ByteArray(RadioPacket.MAX_PAYLOAD_SIZE)
                System.arraycopy(chunk, 0, combined, offset, RadioPacket.MAX_PAYLOAD_SIZE)
                offset += RadioPacket.MAX_PAYLOAD_SIZE
            }

            // Read 4-byte big-endian payload length header
            if (combined.size < 4) return
            val totalLen = ((combined[0].toInt() and 0xFF) shl 24) or
                    ((combined[1].toInt() and 0xFF) shl 16) or
                    ((combined[2].toInt() and 0xFF) shl 8) or
                    (combined[3].toInt() and 0xFF)

            if (totalLen <= 0 || totalLen > combined.size - 4) return
            val encryptedBytes = combined.sliceArray(4 until (4 + totalLen))

            // AES-256 Decrypt
            val decryptedBytes = AesSecurity.decrypt(encryptedBytes)
            if (decryptedBytes == null) {
                addLog(
                    direction = PacketLogEntry.Direction.SYS,
                    typeName = "DECRYPT_ERROR",
                    messageId = messageId,
                    chunkIndex = 0,
                    totalChunks = totalChunks,
                    hexDump = "AES Decryption failed! Key mismatch?",
                    isAck = false
                )
                return
            }

            // Create received chat message
            val chatMsg = if (type == RadioPacket.TYPE_FILE_CHUNK) {
                val isImg = isImageBytes(decryptedBytes)
                ChatMessage(
                    id = "rx_${messageId}_${System.currentTimeMillis()}",
                    messageIdByte = messageId,
                    sender = SenderType.PEER,
                    type = type,
                    textContent = if (isImg) "Received Image (${decryptedBytes.size} bytes)" else "Received File (${decryptedBytes.size} bytes)",
                    fileBytes = decryptedBytes,
                    fileName = if (isImg) "image_rx_$messageId.jpg" else "file_rx_$messageId.bin",
                    isImage = isImg,
                    totalChunks = totalChunks,
                    currentChunk = totalChunks,
                    status = MessageStatus.DELIVERED
                )
            } else {
                val text = String(decryptedBytes, Charsets.UTF_8)
                ChatMessage(
                    id = "rx_${messageId}_${System.currentTimeMillis()}",
                    messageIdByte = messageId,
                    sender = SenderType.PEER,
                    type = type,
                    textContent = text,
                    totalChunks = totalChunks,
                    currentChunk = totalChunks,
                    status = MessageStatus.DELIVERED
                )
            }

            val list = _messages.value.toMutableList()
            list.add(chatMsg)
            _messages.value = list

            scope.launch {
                _incomingMessageEvent.emit(chatMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Sends a text message (Type 0: UDP, or Type 1: TCP Reliable with 500ms ACK loop).
     */
    fun sendTextMessage(text: String, isTcp: Boolean) {
        val type = if (isTcp) RadioPacket.TYPE_TCP_TEXT else RadioPacket.TYPE_UDP_TEXT
        val plainBytes = text.toByteArray(Charsets.UTF_8)
        sendMessageInternal(
            plainBytes = plainBytes,
            type = type,
            textContent = text,
            isImage = false,
            fileName = null
        )
    }

    /**
     * Sends a file/image (Type 2: File Chunk with 500ms ACK loop per chunk).
     */
    fun sendFile(fileBytes: ByteArray, fileName: String, isImage: Boolean) {
        sendMessageInternal(
            plainBytes = fileBytes,
            type = RadioPacket.TYPE_FILE_CHUNK,
            textContent = if (isImage) "Image: $fileName (${fileBytes.size} bytes)" else "File: $fileName (${fileBytes.size} bytes)",
            isImage = isImage,
            fileName = fileName
        )
    }

    private fun sendMessageInternal(
        plainBytes: ByteArray,
        type: Int,
        textContent: String?,
        isImage: Boolean,
        fileName: String?
    ) {
        scope.launch {
            // 1. AES-256 Encrypt first
            val encryptedBytes = AesSecurity.encrypt(plainBytes)

            // 2. Framing: [4-byte big-endian length] + [encryptedBytes]
            val totalEncryptedLen = encryptedBytes.size
            val stream = ByteArray(4 + totalEncryptedLen)
            stream[0] = ((totalEncryptedLen shr 24) and 0xFF).toByte()
            stream[1] = ((totalEncryptedLen shr 16) and 0xFF).toByte()
            stream[2] = ((totalEncryptedLen shr 8) and 0xFF).toByte()
            stream[3] = (totalEncryptedLen and 0xFF).toByte()
            System.arraycopy(encryptedBytes, 0, stream, 4, totalEncryptedLen)

            // 3. Chunk into 26-byte payloads
            val totalChunks = (stream.size + RadioPacket.MAX_PAYLOAD_SIZE - 1) / RadioPacket.MAX_PAYLOAD_SIZE
            val msgIdByte = nextMessageId.getAndIncrement() and 0xFF

            val messageIdStr = "tx_${msgIdByte}_${System.currentTimeMillis()}"
            val initialStatus = if (type == RadioPacket.TYPE_UDP_TEXT) MessageStatus.SENDING else MessageStatus.SENDING

            val chatMsg = ChatMessage(
                id = messageIdStr,
                messageIdByte = msgIdByte,
                sender = SenderType.ME,
                type = type,
                textContent = textContent,
                fileBytes = plainBytes,
                fileName = fileName,
                isImage = isImage,
                totalChunks = totalChunks,
                currentChunk = 0,
                retriesCurrentChunk = 0,
                status = initialStatus
            )

            val currentMessages = _messages.value.toMutableList()
            currentMessages.add(chatMsg)
            _messages.value = currentMessages

            // 4. Send each chunk with ACK loop or UDP flow
            var allSucceeded = true

            for (chunkIdx in 0 until totalChunks) {
                val start = chunkIdx * RadioPacket.MAX_PAYLOAD_SIZE
                val end = minOf(start + RadioPacket.MAX_PAYLOAD_SIZE, stream.size)
                val chunkPayload = stream.sliceArray(start until end)

                val packet = RadioPacket(
                    type = type,
                    messageId = msgIdByte,
                    chunkIndex = chunkIdx,
                    totalChunks = totalChunks,
                    payload = chunkPayload
                )

                if (type == RadioPacket.TYPE_UDP_TEXT) {
                    // UDP Mode: send once without ACK loop
                    bluetoothService.sendPacket(packet)
                    delay(30) // Inter-packet radio pacing
                    updateMessageProgress(messageIdStr, chunkIdx + 1, 0, MessageStatus.SENT_UNACKED)
                } else {
                    // TCP Mode (Type 1 or Type 2): 500ms loop until ACK received
                    val key = ackKey(msgIdByte, chunkIdx)
                    var chunkAcked = false
                    var retries = 0

                    while (!chunkAcked && retries <= MAX_RETRIES && isActive) {
                        val deferred = CompletableDeferred<Unit>()
                        pendingAcks[key] = deferred

                        bluetoothService.sendPacket(packet)

                        try {
                            withTimeout(ACK_TIMEOUT_MS) {
                                deferred.await()
                            }
                            chunkAcked = true
                            pendingAcks.remove(key)
                        } catch (e: TimeoutCancellationException) {
                            retries++
                            pendingAcks.remove(key)
                            updateMessageProgress(messageIdStr, chunkIdx, retries, MessageStatus.SENDING)
                        }
                    }

                    if (!chunkAcked) {
                        allSucceeded = false
                        break
                    } else {
                        updateMessageProgress(messageIdStr, chunkIdx + 1, 0, MessageStatus.SENDING)
                    }
                }
            }

            // Final status update
            val finalStatus = when {
                !allSucceeded -> MessageStatus.FAILED
                type == RadioPacket.TYPE_UDP_TEXT -> MessageStatus.SENT_UNACKED // Single tick ✓
                else -> MessageStatus.DELIVERED // Double tick ✓✓
            }
            updateMessageProgress(messageIdStr, totalChunks, 0, finalStatus)
        }
    }

    private fun updateMessageProgress(
        id: String,
        chunk: Int,
        retries: Int,
        status: MessageStatus
    ) {
        val updated = _messages.value.map { msg ->
            if (msg.id == id) {
                msg.copy(
                    currentChunk = chunk,
                    retriesCurrentChunk = retries,
                    status = status
                )
            } else {
                msg
            }
        }
        _messages.value = updated
    }

    private fun ackKey(messageId: Int, chunkIndex: Int): Int {
        return (messageId shl 16) or (chunkIndex and 0xFFFF)
    }

    private fun isImageBytes(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // PNG magic: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        // JPEG magic: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        // GIF magic: 47 49 46 38
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return true
        return false
    }
}
