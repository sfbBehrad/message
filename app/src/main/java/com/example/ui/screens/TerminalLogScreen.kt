package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import com.example.data.model.PacketLogEntry
import com.example.ui.theme.*
import com.example.ui.viewmodel.RadioChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalLogScreen(
    viewModel: RadioChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()

    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "TX" -> logs.filter { it.direction == PacketLogEntry.Direction.TX && !it.isAck }
            "RX" -> logs.filter { it.direction == PacketLogEntry.Direction.RX && !it.isAck }
            "ACK" -> logs.filter { it.isAck }
            else -> logs
        }
    }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LogTerminalBg)
    ) {
        // Top Toolbar
        Surface(
            color = CardNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "32-BYTE RF PACKET LOGS",
                        style = MaterialTheme.typography.titleSmall,
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredLogs.size} packets recorded (ESP32 <-> NRF24)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row {
                    // Copy to clipboard
                    IconButton(
                        onClick = {
                            val textToCopy = filteredLogs.joinToString("\n") {
                                "[${it.direction}] TYPE:${it.typeName} ID:${it.messageId} CHK:${it.chunkIndex}/${it.totalChunks}\nHEX: ${it.hexDump}"
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("RF Packet Log", textToCopy))
                            Toast.makeText(context, "Copied logs to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = TextMuted
                        )
                    }

                    // Clear logs
                    IconButton(
                        onClick = { viewModel.clearLogs() },
                        modifier = Modifier.testTag("clear_logs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = LogErrColor
                        )
                    }
                }
            }
        }

        // Filter chips bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == "ALL",
                onClick = { selectedFilter = "ALL" },
                label = { Text("ALL (${logs.size})", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedFilter == "TX",
                onClick = { selectedFilter = "TX" },
                label = { Text("TX Out", fontSize = 11.sp, color = LogTxColor) }
            )
            FilterChip(
                selected = selectedFilter == "RX",
                onClick = { selectedFilter = "RX" },
                label = { Text("RX In", fontSize = 11.sp, color = LogRxColor) }
            )
            FilterChip(
                selected = selectedFilter == "ACK",
                onClick = { selectedFilter = "ACK" },
                label = { Text("ACK (Type 3)", fontSize = 11.sp, color = LogAckColor) }
            )
        }

        Divider(color = BorderNavy, thickness = 1.dp)

        // Monospace Terminal Log Feed
        Box(modifier = Modifier.fillMaxSize()) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No packets captured yet.\nSend a message to see 32-byte hex streams.",
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        TerminalPacketCard(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalPacketCard(log: PacketLogEntry) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { timeFormat.format(Date(log.timestamp)) }

    val dirColor = when {
        log.isAck -> LogAckColor
        log.direction == PacketLogEntry.Direction.TX -> LogTxColor
        log.direction == PacketLogEntry.Direction.RX -> LogRxColor
        else -> LogErrColor
    }

    val dirBadge = when {
        log.isAck && log.direction == PacketLogEntry.Direction.TX -> "TX ACK"
        log.isAck && log.direction == PacketLogEntry.Direction.RX -> "RX ACK"
        log.direction == PacketLogEntry.Direction.TX -> "TX DATA"
        log.direction == PacketLogEntry.Direction.RX -> "RX DATA"
        else -> "SYS"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF0A0F1D))
            .border(1.dp, BorderNavy, RoundedCornerShape(6.dp))
            .padding(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = dirColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = dirBadge,
                            color = dirColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ID: 0x${String.format("%02X", log.messageId)} (${log.messageId})",
                        color = TextBright,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CHK: ${log.chunkIndex}/${log.totalChunks}",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Text(
                    text = formattedTime,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Raw Hex Dump with highlight on header bytes (Byte 0-5) vs payload bytes (Byte 6-31)
            Text(
                text = log.hexDump,
                color = if (log.isAck) LogAckColor else TextBright,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 14.sp
            )
        }
    }
}
