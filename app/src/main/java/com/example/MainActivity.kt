package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.TerminalLogScreen
import com.example.ui.theme.CardNavy
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepNavyDark
import com.example.ui.theme.RadioBridgeTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.RadioChatViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: RadioChatViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RadioBridgeTheme {
                val selectedTab by viewModel.selectedTab.collectAsState()
                var showWiringDialog by remember { mutableStateOf(false) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Text(
                                            text = "RadioBridge",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberCyan
                                        )
                                        Text(
                                            text = "ESP32 + NRF24L01 • Secure Bridge",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = { showWiringDialog = true },
                                    modifier = Modifier.testTag("wiring_info_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Hardware Wiring Guide",
                                        tint = CyberCyan
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CardNavy,
                                titleContentColor = CyberCyan
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = CardNavy,
                            contentColor = CyberCyan
                        ) {
                            NavigationBarItem(
                                selected = selectedTab == MainTab.CHAT,
                                onClick = { viewModel.selectTab(MainTab.CHAT) },
                                icon = {
                                    Icon(Icons.Default.Chat, contentDescription = "Chat")
                                },
                                label = { Text("Mesh Chat") },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color(0xFF004F59),
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("nav_chat_tab")
                            )

                            NavigationBarItem(
                                selected = selectedTab == MainTab.TERMINAL,
                                onClick = { viewModel.selectTab(MainTab.TERMINAL) },
                                icon = {
                                    Icon(Icons.Default.Code, contentDescription = "Terminal")
                                },
                                label = { Text("32B Hex Log") },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color(0xFF004F59),
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("nav_terminal_tab")
                            )

                            NavigationBarItem(
                                selected = selectedTab == MainTab.BLUETOOTH,
                                onClick = { viewModel.selectTab(MainTab.BLUETOOTH) },
                                icon = {
                                    Icon(Icons.Default.Bluetooth, contentDescription = "Connect")
                                },
                                label = { Text("Hardware/RF") },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Color(0xFF004F59),
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan
                                ),
                                modifier = Modifier.testTag("nav_bluetooth_tab")
                            )
                        }
                    },
                    containerColor = DeepNavyDark,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (selectedTab) {
                            MainTab.CHAT -> ChatScreen(viewModel = viewModel)
                            MainTab.TERMINAL -> TerminalLogScreen(viewModel = viewModel)
                            MainTab.BLUETOOTH -> ConnectScreen(viewModel = viewModel)
                        }
                    }

                    if (showWiringDialog) {
                        HardwareWiringDialog(onDismiss = { showWiringDialog = false })
                    }
                }
            }
        }
    }
}

@Composable
fun HardwareWiringDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "ESP32 + NRF24L01 Hardware Setup",
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Pinout for RF24 radio(22, 21):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• CE: GPIO 22\n• CSN: GPIO 21\n• SCK: GPIO 18\n• MISO: GPIO 19\n• MOSI: GPIO 23\n• VCC: 3.3V (NOT 5V!)\n• GND: GND",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(0xFFE2E8F0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Hardware Tip: Solder a 10µF to 100µF capacitor between VCC and GND on the NRF24L01 module to prevent brownouts during RF transmission bursts.",
                    fontSize = 11.sp,
                    color = Color(0xFFFFB74D)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Understood", color = CyberCyan)
            }
        }
    )
}
