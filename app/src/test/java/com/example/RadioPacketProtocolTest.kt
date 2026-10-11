package com.example

import com.example.data.crypto.AesSecurity
import com.example.data.model.RadioPacket
import org.junit.Assert.*
import org.junit.Test

class RadioPacketProtocolTest {

    @Test
    fun testRadioPacket32ByteStrictSerialization() {
        val payload = "Hello NRF24 Radio!".toByteArray(Charsets.UTF_8)
        val packet = RadioPacket(
            type = RadioPacket.TYPE_TCP_TEXT,
            messageId = 42,
            chunkIndex = 1,
            totalChunks = 5,
            payload = payload
        )

        val bytes = RadioPacket.toBytes(packet)
        assertEquals("Packet must be exactly 32 bytes", 32, bytes.size)

        // Verify header layout
        assertEquals("Byte 0 must be Type (1 for TCP)", 1.toByte(), bytes[0])
        assertEquals("Byte 1 must be Message ID 42", 42.toByte(), bytes[1])
        assertEquals("Byte 2 must be Chunk Index High Byte", 0.toByte(), bytes[2])
        assertEquals("Byte 3 must be Chunk Index Low Byte", 1.toByte(), bytes[3])
        assertEquals("Byte 4 must be Total Chunks High Byte", 0.toByte(), bytes[4])
        assertEquals("Byte 5 must be Total Chunks Low Byte", 5.toByte(), bytes[5])

        val parsed = RadioPacket.fromBytes(bytes)
        assertNotNull(parsed)
        assertEquals(RadioPacket.TYPE_TCP_TEXT, parsed!!.type)
        assertEquals(42, parsed.messageId)
        assertEquals(1, parsed.chunkIndex)
        assertEquals(5, parsed.totalChunks)
    }

    @Test
    fun testAckPacketCreation() {
        val original = RadioPacket(
            type = RadioPacket.TYPE_TCP_TEXT,
            messageId = 88,
            chunkIndex = 3,
            totalChunks = 7,
            payload = ByteArray(26)
        )

        val ack = RadioPacket.buildAck(original)
        assertEquals(RadioPacket.TYPE_ACK, ack.type)
        assertEquals(88, ack.messageId)
        assertEquals(3, ack.chunkIndex)
        assertEquals(7, ack.totalChunks)

        val ackBytes = RadioPacket.toBytes(ack)
        assertEquals(32, ackBytes.size)
        assertEquals(RadioPacket.TYPE_ACK.toByte(), ackBytes[0])
        assertEquals(88.toByte(), ackBytes[1])
    }

    @Test
    fun testAes256EncryptionAndDecryption() {
        val secretMessage = "Top Secret Off-Grid Radio Transmission 2026!"
        val plainBytes = secretMessage.toByteArray(Charsets.UTF_8)

        val encrypted = AesSecurity.encrypt(plainBytes)
        assertFalse("Encrypted bytes must not match plaintext", plainBytes.contentEquals(encrypted))
        assertTrue("Encrypted data must include 16-byte IV", encrypted.size > 16)

        val decrypted = AesSecurity.decrypt(encrypted)
        assertNotNull(decrypted)
        val decryptedMessage = String(decrypted!!, Charsets.UTF_8)
        assertEquals(secretMessage, decryptedMessage)
    }
}
