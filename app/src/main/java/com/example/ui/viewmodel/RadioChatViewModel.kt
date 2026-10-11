package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.bluetooth.BluetoothService
import com.example.data.bluetooth.ConnectionStatus
import com.example.data.bluetooth.DiscoveredDevice
import com.example.data.bluetooth.TransportMode
import com.example.data.crypto.AesSecurity
import com.example.data.model.ChatMessage
import com.example.data.model.PacketLogEntry
import com.example.data.model.RadioPacket
import com.example.data.protocol.RadioProtocolManager
import com.example.util.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MainTab {
    CHAT,
    TERMINAL,
    BLUETOOTH
}

class RadioChatViewModel(application: Application) : AndroidViewModel(application) {

    val bluetoothService = BluetoothService(application.applicationContext)
    val protocolManager = RadioProtocolManager(application.applicationContext, bluetoothService)

    private val _selectedTab = MutableStateFlow(MainTab.CHAT)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _isTcpMode = MutableStateFlow(true) // Default TCP reliable ACK loop
    val isTcpMode: StateFlow<Boolean> = _isTcpMode.asStateFlow()

    private val _passphrase = MutableStateFlow(AesSecurity.currentPassphrase)
    val passphrase: StateFlow<String> = _passphrase.asStateFlow()

    private val _simulatedPacketLoss = MutableStateFlow(0)
    val simulatedPacketLoss: StateFlow<Int> = _simulatedPacketLoss.asStateFlow()

    val connectionStatus: StateFlow<ConnectionStatus> = bluetoothService.connectionStatus
    val connectedDeviceName: StateFlow<String?> = bluetoothService.connectedDeviceName
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = bluetoothService.discoveredDevices
    val transportMode: StateFlow<TransportMode> = bluetoothService.transportMode

    val messages: StateFlow<List<ChatMessage>> = protocolManager.messages
    val logs: StateFlow<List<PacketLogEntry>> = protocolManager.logs

    init {
        NotificationHelper.createNotificationChannel(application.applicationContext)

        // Listen for incoming messages to trigger background notification
        viewModelScope.launch {
            protocolManager.incomingMessageEvent.collect { message ->
                val preview = message.textContent ?: if (message.isImage) "Received an image" else "Received a file"
                NotificationHelper.showMessageNotification(
                    context = application.applicationContext,
                    title = "RadioBridge: ESP32 [Node 2]",
                    content = preview
                )
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun setTcpMode(enabled: Boolean) {
        _isTcpMode.value = enabled
    }

    fun setTransportMode(mode: TransportMode) {
        bluetoothService.setTransportMode(mode)
    }

    fun setSimulatedPacketLoss(percent: Int) {
        _simulatedPacketLoss.value = percent
        bluetoothService.simulatedPacketDropRatePercent = percent
    }

    fun updatePassphrase(newPhrase: String) {
        _passphrase.value = newPhrase
        AesSecurity.currentPassphrase = newPhrase
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        protocolManager.sendTextMessage(text.trim(), isTcp = _isTcpMode.value)
    }

    fun sendFile(fileBytes: ByteArray, fileName: String, isImage: Boolean) {
        protocolManager.sendFile(fileBytes, fileName, isImage)
    }

    fun connectToDevice(address: String, name: String) {
        bluetoothService.connectToDevice(address, name)
    }

    fun connectQuickNode(nodeName: String) {
        bluetoothService.connectToNodeByName(nodeName)
    }

    fun disconnect() {
        bluetoothService.disconnect()
    }

    fun refreshDevices() {
        bluetoothService.refreshPairedDevices()
    }

    fun clearLogs() {
        protocolManager.clearLogs()
    }

    /**
     * Injects a simulated incoming message from remote ESP_Node_2 (useful for demo & emulator testing)
     */
    fun simulateIncomingPeerMessage(customText: String? = null) {
        viewModelScope.launch {
            val text = customText ?: "Hello from Node 2! NRF24 signal RSSI optimal on Channel 115."
            val plainBytes = text.toByteArray(Charsets.UTF_8)
            val encryptedBytes = AesSecurity.encrypt(plainBytes)

            val totalEncryptedLen = encryptedBytes.size
            val stream = ByteArray(4 + totalEncryptedLen)
            stream[0] = ((totalEncryptedLen shr 24) and 0xFF).toByte()
            stream[1] = ((totalEncryptedLen shr 16) and 0xFF).toByte()
            stream[2] = ((totalEncryptedLen shr 8) and 0xFF).toByte()
            stream[3] = (totalEncryptedLen and 0xFF).toByte()
            System.arraycopy(encryptedBytes, 0, stream, 4, totalEncryptedLen)

            val totalChunks = (stream.size + RadioPacket.MAX_PAYLOAD_SIZE - 1) / RadioPacket.MAX_PAYLOAD_SIZE
            val msgId = (10..240).random()

            for (i in 0 until totalChunks) {
                val start = i * RadioPacket.MAX_PAYLOAD_SIZE
                val end = minOf(start + RadioPacket.MAX_PAYLOAD_SIZE, stream.size)
                val chunkData = stream.sliceArray(start until end)

                val packet = RadioPacket(
                    type = RadioPacket.TYPE_TCP_TEXT,
                    messageId = msgId,
                    chunkIndex = i,
                    totalChunks = totalChunks,
                    payload = chunkData
                )
                bluetoothService.injectSimulatedPacket(packet)
            }
        }
    }
}
