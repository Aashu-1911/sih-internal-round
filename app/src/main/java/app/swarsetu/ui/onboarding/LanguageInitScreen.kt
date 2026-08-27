package app.swarsetu.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.swarsetu.data.settings.SettingsStore
import app.swarsetu.translation.TranslatorEngine
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun LanguageInitScreen(
    onInitializationComplete: () -> Unit,
    settingsStore: SettingsStore = koinInject(),
    translatorEngine: TranslatorEngine = koinInject()
) {
    var completedModels by remember { mutableIntStateOf(0) }
    var totalModels by remember { mutableIntStateOf(8) }
    var currentDownloading by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val downloadModels = {
        isError = false
        isDownloading = true
        scope.launch {
            try {
                translatorEngine.downloadAllRequiredModels { completed, total, currentLang ->
                    currentDownloading = currentLang
                    completedModels = completed
                    totalModels = total
                }
                settingsStore.setLanguageInitComplete(true)
                onInitializationComplete()
            } catch (e: Exception) {
                isError = true
                isDownloading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Initializing Offline Translation",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Downloading native language packs to ensure 100% offline reliability during distress scenarios (~250MB).",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        if (!isDownloading && !isError) {
            Button(onClick = { downloadModels() }) {
                Text("Install Language Models (~250MB)")
            }
        } else if (isError) {
            Text(
                text = "Download failed. Please check your internet connection and try again.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { downloadModels() }) {
                Text("Retry")
            }
        } else {
            val progress = if (totalModels > 0) completedModels.toFloat() / totalModels.toFloat() else 0f
            LinearProgressIndicator(progress = { progress })
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Downloaded $completedModels of $totalModels languages...",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (completedModels < totalModels && currentDownloading.isNotBlank()) {
                Text(
                    text = "Currently fetching: $currentDownloading",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
