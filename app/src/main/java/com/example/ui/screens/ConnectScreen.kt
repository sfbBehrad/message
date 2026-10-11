package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.bluetooth.ConnectionStatus
import com.example.data.bluetooth.DiscoveredDevice
import com.example.data.bluetooth.TransportMode
import com.example.ui.theme.*
import com.example.ui.viewmodel.RadioChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    viewModel: RadioChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val connectedDeviceName by viewModel.connectedDeviceName.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val transportMode by viewModel.transportMode.collectAsState()
    val simulatedPacketLoss by viewModel.simulatedPacketLoss.collectAsState()
    val passphrase by viewModel.passphrase.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf(passphrase) }

    // Bluetooth permission launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            viewModel.refreshDevices()
        }
    }

    fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        } else {
            viewModel.refreshDevices()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDevices()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyDark)
            .padding(16.dp)
    ) {
        // Mode Switcher Card: Hardware vs Simulator
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TRANSMISSION MEDIUM",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            viewModel.setTransportMode(TransportMode.HARDWARE_BLUETOOTH)
                            checkAndRequestPermissions()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (transportMode == TransportMode.HARDWARE_BLUETOOTH) CyberCyanDark else CardNavyHover,
                            contentColor = if (transportMode == TransportMode.HARDWARE_BLUETOOTH) CyberCyan else TextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hardware_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = "Bluetooth Hardware",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ESP32 Bluetooth", fontSize = 12.sp)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.setTransportMode(TransportMode.VIRTUAL_SIMULATOR) },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (transportMode == TransportMode.VIRTUAL_SIMULATOR) CyberCyanDark else CardNavyHover,
                            contentColor = if (transportMode == TransportMode.VIRTUAL_SIMULATOR) CyberCyan else TextMuted
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simulator_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Virtual Simulator",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("NRF24 Simulator", fontSize = 12.sp)
                    }
                }

                // If in simulator mode: packet loss slider to demo ACK retry loop!
                if (transportMode == TransportMode.VIRTUAL_SIMULATOR) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Simulated RF Packet Loss: $simulatedPacketLoss% (Tests 500ms ACK Retries)",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberAmber,
                        fontSize = 11.sp
                    )
                    Slider(
                        value = simulatedPacketLoss.toFloat(),
                        onValueChange = { viewModel.setSimulatedPacketLoss(it.toInt()) },
                        valueRange = 0f..60f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberAmber,
                            activeTrackColor = CyberAmber
                        ),
                        modifier = Modifier.testTag("loss_slider")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick ESP Node Connect Buttons
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TARGET ESP32 BRIDGE NODES",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (transportMode == TransportMode.HARDWARE_BLUETOOTH) {
                                checkAndRequestPermissions()
                            }
                            viewModel.connectQuickNode("ESP_Node_1")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_node_1_button"),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CyberCyan)
                        )
                    ) {
                        Text("Connect Node 1", color = CyberCyan, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (transportMode == TransportMode.HARDWARE_BLUETOOTH) {
                                checkAndRequestPermissions()
                            }
                            viewModel.connectQuickNode("ESP_Node_2")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("connect_node_2_button"),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(CyberAmber)
                        )
                    ) {
                        Text("Connect Node 2", color = CyberAmber, fontSize = 12.sp)
                    }
                }

                if (connectionStatus == ConnectionStatus.CONNECTED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.disconnect() },
                        colors = ButtonDefaults.buttonColors(containerColor = LogErrColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .testTag("disconnect_button")
                    ) {
                        Text("Disconnect ($connectedDeviceName)", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AES-256 Pre-Shared Key Card
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AES-256 SECURITY KEY",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Key: ${passphrase.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextBright,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }

                FilledTonalButton(
                    onClick = {
                        keyInput = passphrase
                        showKeyDialog = true
                    },
                    modifier = Modifier.testTag("configure_key_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Edit Key",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Change", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Paired Devices Header & Refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PAIRED BLUETOOTH DEVICES",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontFamily = FontFamily.Monospace
            )

            IconButton(
                onClick = { checkAndRequestPermissions() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // List of Paired Bluetooth Devices
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (discoveredDevices.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No paired devices found.\nPair ESP_Node_1 or ESP_Node_2 in Android Bluetooth Settings,\nor use Virtual Simulator mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(discoveredDevices) { device ->
                    DeviceRow(
                        device = device,
                        isConnected = device.name == connectedDeviceName,
                        onConnect = {
                            viewModel.connectToDevice(device.address, device.name)
                        }
                    )
                }
            }
        }
    }

    // Passphrase Dialog
    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("AES-256 Pre-Shared Key") },
            text = {
                Column {
                    Text(
                        "Enter the shared passphrase used by all nodes for end-to-end encryption:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (keyInput.isNotBlank()) {
                            viewModel.updatePassphrase(keyInput.trim())
                        }
                        showKeyDialog = false
                    }
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DeviceRow(
    device: DiscoveredDevice,
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = if (isConnected) CyberGreen else CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextBright
                    )
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Button(
                onClick = onConnect,
                enabled = !isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) CyberGreen else CyberCyanDark,
                    contentColor = if (isConnected) Color.Black else CyberCyan
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = if (isConnected) "Connected" else "Connect",
                    fontSize = 11.sp
                )
            }
        }
    }
}
