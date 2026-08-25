package app.swarsetu.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.swarsetu.R
import app.swarsetu.ui.hasAllMeshPermissions
import app.swarsetu.ui.hasBleHardware
import app.swarsetu.ui.hasWifiAwareHardware
import app.swarsetu.ui.preview.SwarSetuPreview
import app.swarsetu.ui.requestIgnoreBatteryOptimizations
import app.swarsetu.ui.requiredMeshPermissions
import kotlinx.coroutines.launch

/**
 * Onboarding data class for each step
 */
private data class OnboardingStep(
    val icon: ImageVector,
    val titleRes: Int,
    val descriptionRes: Int,
    val iconTint: androidx.compose.ui.graphics.Color,
)

private val onboardingSteps = listOf(
    OnboardingStep(
        icon = Icons.Filled.Headphones,
        titleRes = R.string.onboarding_title,
        descriptionRes = R.string.onboarding_blurb,
        iconTint = androidx.compose.ui.graphics.Color(0xFF0D9488),
    ),
    OnboardingStep(
        icon = Icons.Filled.Groups,
        titleRes = R.string.onboarding_title,
        descriptionRes = R.string.onboarding_blurb,
        iconTint = androidx.compose.ui.graphics.Color(0xFF7C3AED),
    ),
    OnboardingStep(
        icon = Icons.Filled.Bluetooth,
        titleRes = R.string.onboarding_title,
        descriptionRes = R.string.onboarding_blurb,
        iconTint = androidx.compose.ui.graphics.Color(0xFF2563EB),
    ),
)

/**
 * First-run gate: explains why the mesh needs its nearby-Wi-Fi + Bluetooth + notification permissions
 * and (optionally) battery exemption, requests them, then hands off to the chat once granted.
 *
 * This version features a multi-step onboarding flow with visual illustrations.
 */
@Composable
fun OnboardingScreen(onReady: () -> Unit) {
    val context = LocalContext.current
    val meshSupported = remember { hasWifiAwareHardware(context) || hasBleHardware(context) }
    var granted by remember { mutableStateOf(hasAllMeshPermissions(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = hasAllMeshPermissions(context)
    }

    OnboardingScreenContent(
        meshSupported = meshSupported,
        granted = granted,
        onGrantPermissions = { launcher.launch(requiredMeshPermissions()) },
        onAllowBattery = { requestIgnoreBatteryOptimizations(context) },
        onReady = onReady,
    )
}

@Composable
internal fun OnboardingScreenContent(
    meshSupported: Boolean,
    granted: Boolean,
    onGrantPermissions: () -> Unit,
    onAllowBattery: () -> Unit,
    onReady: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { onboardingSteps.size })
    val scope = rememberCoroutineScope()
    val currentStep = pagerState.currentPage

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Brand header
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / onboardingSteps.size },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Pager with step illustrations
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                OnboardingStepPage(step = onboardingSteps[page])
            }

            // Page indicators
            Row(
                modifier = Modifier.padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(onboardingSteps.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentStep) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentStep) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Unsupported device warning
            if (!meshSupported) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_unsupported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            // Permission button
            Button(
                onClick = onGrantPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_grant"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = if (granted) Icons.Filled.Check else Icons.Filled.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (granted) {
                        stringResource(R.string.onboarding_permissions_granted)
                    } else {
                        stringResource(R.string.onboarding_grant_permissions)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Battery optimization button
            FilledTonalButton(
                onClick = onAllowBattery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.battery_allow_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start button
            OutlinedButton(
                onClick = onReady,
                enabled = granted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_start"),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_start),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Individual onboarding step page with icon illustration and text
 */
@Composable
private fun OnboardingStepPage(step: OnboardingStep) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon illustration with gradient background
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            step.iconTint.copy(alpha = 0.2f),
                            step.iconTint.copy(alpha = 0.05f),
                        ),
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = step.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = step.iconTint,
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(step.titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(step.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenPreview() = SwarSetuPreview {
    OnboardingScreenContent(
        meshSupported = true,
        granted = false,
        onGrantPermissions = {},
        onAllowBattery = {},
        onReady = {},
    )
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenGrantedPreview() = SwarSetuPreview {
    OnboardingScreenContent(
        meshSupported = true,
        granted = true,
        onGrantPermissions = {},
        onAllowBattery = {},
        onReady = {},
    )
}

@Preview(showBackground = true)
@Composable
fun OnboardingScreenUnsupportedPreview() = SwarSetuPreview {
    OnboardingScreenContent(
        meshSupported = false,
        granted = false,
        onGrantPermissions = {},
        onAllowBattery = {},
        onReady = {},
    )
}
