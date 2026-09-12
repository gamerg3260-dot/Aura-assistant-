package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuraViewModel
import com.example.ui.components.AuraGlassCard
import com.example.ui.theme.AuraCardBorder
import com.example.ui.theme.AuraCyanBright
import com.example.ui.theme.AuraCyanPrimary
import com.example.ui.theme.AuraDarkSurface
import com.example.ui.theme.AuraError
import com.example.ui.theme.AuraSuccess
import com.example.ui.theme.AuraVioletSecondary
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: AuraViewModel,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversationHistory.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.History, contentDescription = null, tint = AuraCyanPrimary, modifier = Modifier.size(24.dp))
                    Text(
                        text = "Interaction Log",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (conversations.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearAllHistory() }) {
                        Text("Clear All", color = AuraError, fontSize = 13.sp)
                    }
                }
            }
            Text(
                text = "Chronological record of commands, verified speaker confidence, and executed device tools.",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        if (conversations.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AuraDarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = "No recorded interactions yet",
                            color = TextMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Speak to Aura using the Home orb or quick shortcuts to start.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(conversations, key = { it.id }) { item ->
                val timeStr = SimpleDateFormat("h:mm a • MMM d", Locale.getDefault()).format(Date(item.timestamp))
                AuraGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (item.isOwnerVerified) AuraSuccess.copy(alpha = 0.15f) else AuraError.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (item.isOwnerVerified) "Verified Voice ${(item.speakerConfidence * 100).toInt()}%" else "Unverified",
                                        color = if (item.isOwnerVerified) AuraSuccess else AuraError,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = timeStr,
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteConversation(item.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Query
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "User: ",
                                color = AuraCyanPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = item.userQuery,
                                color = TextPrimary,
                                fontSize = 13.sp
                            )
                        }

                        // Reply
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "Aura: ",
                                color = AuraVioletSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = item.assistantReply,
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }

                        // Tool tag
                        if (!item.toolUsed.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = AuraCyanBright, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Tool Executed: ${item.toolUsed}",
                                    color = AuraCyanBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
