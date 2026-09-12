package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LanguageOption
import com.example.ui.AuraViewModel
import com.example.ui.components.AuraGlassCard
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkBackground
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OnboardingScreen(
    viewModel: AuraViewModel,
    onOnboardingComplete: () -> Unit
) {
    val step by viewModel.onboardingStep.collectAsState()
    val userName by viewModel.tempUserName.collectAsState()
    val selectedLanguage by viewModel.tempLanguage.collectAsState()
    val licenseKey by viewModel.tempLicenseKey.collectAsState()
    val licenseError by viewModel.licenseError.collectAsState()
    val apiKey by viewModel.tempApiKey.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AuraDarkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 1) {
                IconButton(onClick = { viewModel.prevOnboardingStep() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }

            // Step Indicator
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..3) {
                    val isActive = i == step
                    val isDone = i < step
                    Box(
                        modifier = Modifier
                            .size(width = if (isActive) 24.dp else 10.dp, height = 10.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isActive -> AuraCyanPrimary
                                    isDone -> AuraVioletSecondary
                                    else -> AuraCardBorder
                                }
                            )
                    )
                }
            }

            Text(
                text = "Step $step of 3",
                color = TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Animated Content for Steps
        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboarding_step_content"
            ) { targetStep ->
                when (targetStep) {
                    1 -> StepName(userName = userName, onNameChange = { viewModel.setTempUserName(it) })
                    2 -> StepLanguage(
                        selectedCode = selectedLanguage,
                        onSelect = { viewModel.setTempLanguage(it) }
                    )
                    3 -> StepApiKey(
                        apiKey = apiKey,
                        onKeyChange = { viewModel.setTempApiKey(it) }
                    )
                }
            }
        }

        // Action Button
        Button(
            onClick = {
                val success = viewModel.nextOnboardingStep()
                if (success && step == 3) {
                    onOnboardingComplete()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuraCyanPrimary,
                contentColor = Color(0xFF070B13)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (step == 3) "Start Aura Assistant" else "Next Step",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StepName(userName: String, onNameChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(AuraCyanPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Welcome to Aura",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "What is your name? Aura will address you personally and calibrate your profile.",
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        OutlinedTextField(
            value = userName,
            onValueChange = onNameChange,
            placeholder = { Text("e.g. Alex, Maya, Dev...", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyanPrimary,
                unfocusedBorderColor = AuraCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = AuraDarkSurface,
                unfocusedContainerColor = AuraDarkSurface
            ),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AuraGlassCard {
            Text(
                text = "Preview: \"Good morning, ${if (userName.isBlank()) "Alex" else userName}. Voice-lock is active and standing by.\"",
                color = AuraCyanPrimary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StepLanguage(selectedCode: String, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(AuraVioletSecondary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Language, contentDescription = null, tint = AuraVioletSecondary, modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Response Language",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Choose the language Aura speaks back to you. Multilingual and regional languages supported.",
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LanguageOption.SUPPORTED_LANGUAGES) { lang ->
                val isSelected = lang.code == selectedCode
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AuraVioletSecondary.copy(alpha = 0.2f) else AuraDarkSurface)
                        .border(
                            1.dp,
                            if (isSelected) AuraVioletSecondary else AuraCardBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSelect(lang.code) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = lang.displayName,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = lang.nativeName,
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuraCyanPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepLicense(
    licenseKey: String,
    error: String?,
    onKeyChange: (String) -> Unit,
    onUseDemo: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(AuraCyanPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.VpnKey, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Access & License Key",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Enter your Aura product license key. You can also generate an instant Evaluation Pass.",
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = licenseKey,
            onValueChange = onKeyChange,
            placeholder = { Text("AURA-PRO-XXXX-XXXX", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            isError = error != null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyanPrimary,
                unfocusedBorderColor = AuraCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = AuraDarkSurface,
                unfocusedContainerColor = AuraDarkSurface
            )
        )

        if (error != null) {
            Text(
                text = error,
                color = AuraError,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onUseDemo,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Auto-Fill Demo Evaluation Pass", color = AuraCyanPrimary)
        }

        Spacer(modifier = Modifier.height(20.dp))

        AuraGlassCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Security, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(20.dp))
                Text(
                    text = "On-device license validation active. Grants full offline access to voiceprint biometric engine.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun StepApiKey(
    apiKey: String,
    onKeyChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(AuraVioletSecondary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = AuraVioletSecondary, modifier = Modifier.size(28.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "LLM API Key",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Enter your Gemini or Claude API key. Stored in hardware Android Keystore with AES-256 encryption.",
            color = TextSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        OutlinedTextField(
            value = apiKey,
            onValueChange = onKeyChange,
            placeholder = { Text("AIzaSy... (or Claude Key)", color = TextMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuraCyanPrimary,
                unfocusedBorderColor = AuraCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = AuraDarkSurface,
                unfocusedContainerColor = AuraDarkSurface
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        AuraGlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AuraSuccess, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Hardware Encryption Guarantee",
                        color = AuraSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "Aura never transmits or writes your API key in plaintext. If left blank, the app will use environment configured Gemini credentials or local offline smart commands.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
