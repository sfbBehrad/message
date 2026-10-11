package com.example.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.bluetooth.ConnectionStatus
import com.example.data.bluetooth.TransportMode
import com.example.data.model.ChatMessage
import com.example.data.model.MessageStatus
import com.example.data.model.RadioPacket
import com.example.data.model.SenderType
import com.example.ui.theme.*
import com.example.ui.viewmodel.RadioChatViewModel
import kotlinx.coroutines.launch
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: RadioChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by viewModel.messages.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val deviceName by viewModel.connectedDeviceName.collectAsState()
    val isTcpMode by viewModel.isTcpMode.collectAsState()
    val transportMode by viewModel.transportMode.collectAsState()

    var textInput by remember { mutableStateOf("") }

    // File picker launcher for images/files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes() ?: return@launch
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    val isImage = mimeType.startsWith("image/") || bytes.size < 500000
                    val fileName = "payload_${System.currentTimeMillis()}.${if (isImage) "jpg" else "bin"}"
                    viewModel.sendFile(bytes, fileName, isImage)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepNavyDark)
    ) {
        // Top Bridge Status Bar
        Surface(
            color = CardNavy,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Pulsing LED indicator
                    val ledColor = when (connectionStatus) {
                        ConnectionStatus.CONNECTED -> CyberGreen
                        ConnectionStatus.CONNECTING -> CyberAmber
                        ConnectionStatus.SCANNING -> CyberCyan
                        ConnectionStatus.DISCONNECTED -> Color.Red
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ledColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (connectionStatus) {
                                ConnectionStatus.CONNECTED -> deviceName ?: "ESP_Node_Bridge"
                                ConnectionStatus.CONNECTING -> "Connecting..."
                                ConnectionStatus.SCANNING -> "Scanning for ESP32..."
                                ConnectionStatus.DISCONNECTED -> "Bridge Offline"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = TextBright,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (transportMode == TransportMode.VIRTUAL_SIMULATOR)
                                "Virtual NRF24 Mesh • Ch 115 (1Mbps)"
                            else
                                "Bluetooth SPP • NRF24 Ch 115",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Quick Simulation Action / Info button
                if (transportMode == TransportMode.VIRTUAL_SIMULATOR) {
                    FilledTonalButton(
                        onClick = { viewModel.simulateIncomingPeerMessage() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CardNavyHover,
                            contentColor = CyberCyan
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("simulate_peer_msg_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Simulate Peer",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Simulate Node 2",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Encrypted Mesh",
                        tint = CyberCyanDark,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "End-to-End Encrypted Radio Mesh",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextBright,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Packets are AES-256 encrypted, sliced into strict 32-byte arrays, and bridged through ESP32 to NRF24L01 transceivers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SuggestionChip(
                        onClick = { viewModel.simulateIncomingPeerMessage("Hello! NRF24 Node 2 connected and listening on Channel 115.") },
                        label = { Text("Send Test Packet from Node 2", color = CyberCyan) }
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(message = message)
                    }
                }
            }
        }

        // Bottom Input & Controls Bar
        Surface(
            color = CardNavy,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Protocol Mode Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = isTcpMode,
                            onClick = { viewModel.setTcpMode(true) },
                            label = { Text("TCP (500ms ACK Loop)", fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "TCP ACK",
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyanDark,
                                selectedLabelColor = CyberCyan
                            ),
                            modifier = Modifier.testTag("tcp_mode_chip")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = !isTcpMode,
                            onClick = { viewModel.setTcpMode(false) },
                            label = { Text("UDP (Fast)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CardNavyHover,
                                selectedLabelColor = CyberAmber
                            ),
                            modifier = Modifier.testTag("udp_mode_chip")
                        )
                    }

                    Text(
                        text = "AES-256 • 32B",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Input Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attach File/Image button
                    IconButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("attach_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach File or Image",
                            tint = CyberCyan
                        )
                    }

                    // Text Input
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("message_input_field"),
                        placeholder = {
                            Text(
                                "Type secure radio message...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextBright,
                            unfocusedTextColor = TextBright,
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = BorderNavy,
                            focusedContainerColor = DeepNavyDark,
                            unfocusedContainerColor = DeepNavyDark
                        ),
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Send Button
                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendTextMessage(textInput)
                                textInput = ""
                            }
                        },
                        containerColor = CyberCyan,
                        contentColor = Color(0xFF00363D),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Radio Packet",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isMe = message.sender == SenderType.ME
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    val bubbleColor = if (isMe) CardNavyHover else CardNavy
    val borderColor = if (isMe) BorderNavy else Color(0xFF1E293B)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isMe) "You (Tx Node)" else "ESP32 (Rx Node)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isMe) CyberCyan else CyberAmber,
                        fontSize = 11.sp
                    )

                    Text(
                        text = if (message.type == RadioPacket.TYPE_UDP_TEXT) "UDP" else "TCP",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Image / File Content
                if (message.fileBytes != null && message.isImage) {
                    val bitmap = remember(message.fileBytes) {
                        try {
                            BitmapFactory.decodeByteArray(message.fileBytes, 0, message.fileBytes.size)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Decrypted Radio Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Text Content
                if (!message.textContent.isNullOrBlank()) {
                    Text(
                        text = message.textContent,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextBright
                    )
                }

                // Multi-chunk progress bar if currently sending
                if (message.totalChunks > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column {
                        val progress = if (message.totalChunks > 0)
                            message.currentChunk.toFloat() / message.totalChunks.toFloat()
                        else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = CyberCyan,
                            trackColor = BorderNavy,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chunk ${message.currentChunk}/${message.totalChunks} (32B each)",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            if (message.retriesCurrentChunk > 0) {
                                Text(
                                    text = "↻ retry #${message.retriesCurrentChunk}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = CyberAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Footer: Timestamp and Delivery Ticks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 10.sp
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            MessageStatus.DELIVERED -> {
                                // Double tick for ACK received (Type 3 match!)
                                Row {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "ACK Received (Double Tick)",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            MessageStatus.SENT_UNACKED -> {
                                // Single tick for UDP sent
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent (Single Tick)",
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.SENDING, MessageStatus.PENDING -> {
                                // In-flight single tick with subtle amber hue
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Transmitting...",
                                    tint = CyberAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            MessageStatus.FAILED -> {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Failed",
                                    tint = LogErrColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
