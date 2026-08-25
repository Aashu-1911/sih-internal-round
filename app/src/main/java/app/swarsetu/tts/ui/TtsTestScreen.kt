package app.swarsetu.tts.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.swarsetu.tts.TtsLanguage
import app.swarsetu.tts.TtsLanguageCapability
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsTestScreen(
    onBack: () -> Unit,
    viewModel: TtsTestViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val metrics by viewModel.metricsCollector.latestMetrics.collectAsState()
    val voiceState by viewModel.voiceController.state.collectAsState()
    var inputText by remember { mutableStateOf("नमस्ते, यह एक परीक्षण संदेश है।") }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Phase 1 Test") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("<-") // In a real app, use an Icon
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Initialization State
            Text(
                text = "Engine Status: ${if (uiState.isInitialized) "Ready" else "Initializing..."}",
                style = MaterialTheme.typography.titleMedium
            )

            // Language Selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = uiState.selectedLanguage.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language") },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    TtsLanguage.values().forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language.displayName) },
                            onClick = {
                                viewModel.selectLanguage(language)
                                expanded = false
                                // Pre-fill sample text based on language if desired
                            }
                        )
                    }
                }
            }

            // Capability Display
            val capText = when (val cap = uiState.capability) {
                is TtsLanguageCapability.Supported -> "Supported (${cap.engineName}) - Voice: ${cap.voiceName ?: "Default"}"
                is TtsLanguageCapability.MissingData -> "Missing Data (${cap.engineName})"
                is TtsLanguageCapability.Unsupported -> "Unsupported: ${cap.reason}"
                is TtsLanguageCapability.Error -> "Error: ${cap.errorMessage}"
                null -> "Checking..."
            }
            Text("Capability: $capText", style = MaterialTheme.typography.bodyMedium)

            // Input Text
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Text to Speak") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            // Action Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.speakNormal(inputText) }, modifier = Modifier.weight(1f)) {
                    Text("Queue Normal")
                }
                Button(
                    onClick = { viewModel.speakAlert(inputText) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Play Alert")
                }
            }
            Button(onClick = { viewModel.stop() }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop Playback")
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // VOICE PIPELINE (Phase 3)
            Text("Voice Pipeline (Phase 3)", style = MaterialTheme.typography.titleMedium)
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = viewModel.voiceController.isLoopEnabled,
                    onClick = { viewModel.toggleLoop(!viewModel.voiceController.isLoopEnabled) },
                    label = { Text("LOCAL Loop") }
                )
                FilterChip(
                    selected = viewModel.voiceController.isMeshEnabled,
                    onClick = { viewModel.toggleMesh(!viewModel.voiceController.isMeshEnabled) },
                    label = { Text("MESH Mode") }
                )
            }
            Text("Voice State: $voiceState", style = MaterialTheme.typography.bodyMedium)
            
            val vm by viewModel.voiceController.voiceMetrics.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Message ID: ${vm.messageId?.take(8) ?: "N/A"}")
                Text("Voice payload: ${vm.payloadSizeBytes} bytes")
                
                val sttLat = if (vm.t1SttFinal > 0 && vm.t0SpeechDetected > 0) "${vm.t1SttFinal - vm.t0SpeechDetected}ms" else "N/A"
                val prepLat = if (vm.t2MessageConstructed > 0 && vm.t1SttFinal > 0) "${vm.t2MessageConstructed - vm.t1SttFinal}ms" else "N/A"
                val sendLat = if (vm.t3MessageSendRequested > 0 && vm.t2MessageConstructed > 0) "${vm.t3MessageSendRequested - vm.t2MessageConstructed}ms" else "N/A"
                val netLat = if (vm.t4RemoteMessageReceived > 0 && vm.t3MessageSendRequested > 0) "${vm.t4RemoteMessageReceived - vm.t3MessageSendRequested}ms (Uncalibrated clock)" else "N/A"
                val ttsLat = if (vm.t5TtsRequest > 0 && vm.t4RemoteMessageReceived > 0) "${vm.t5TtsRequest - vm.t4RemoteMessageReceived}ms" else "N/A"
                val e2eLat = if (vm.t5TtsRequest > 0 && vm.t0SpeechDetected > 0) "${vm.t5TtsRequest - vm.t0SpeechDetected}ms (approx)" else "N/A"
                
                Text("STT Latency (t1-t0): $sttLat", style = MaterialTheme.typography.bodySmall)
                Text("Message Prep (t2-t1): $prepLat", style = MaterialTheme.typography.bodySmall)
                Text("Local Send (t3-t2): $sendLat", style = MaterialTheme.typography.bodySmall)
                Text("Network Delivery (t4-t3): $netLat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text("TTS Dispatch (t5-t4): $ttsLat", style = MaterialTheme.typography.bodySmall)
                Text("End-to-End Latency: $e2eLat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Metrics Display
            Text("Latest Metrics", style = MaterialTheme.typography.titleMedium)
            metrics?.let { m ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Request ID: ${m.requestId.take(8)}...")
                    Text("TTFA (Time to First Audio): ${m.ttfaMs?.let { "${it}ms" } ?: "..."}")
                    Text("Playback Latency: ${m.playbackStartLatencyMs?.let { "${it}ms" } ?: "..."}")
                    Text("RTF: ${m.rtf?.let { String.format("%.2f", it) } ?: "..."}")
                    if (m.interrupted) Text("Status: INTERRUPTED", color = MaterialTheme.colorScheme.error)
                    if (m.error) Text("Status: ERROR (${m.errorMessage})", color = MaterialTheme.colorScheme.error)
                }
            } ?: Text("No metrics collected yet.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
