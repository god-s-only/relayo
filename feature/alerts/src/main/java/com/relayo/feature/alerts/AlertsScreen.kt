package com.relayo.feature.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relayo.domain.model.AlertSeverity
import com.relayo.domain.model.EmergencyAlert

@Composable
fun AlertsScreen(
    viewModel:AlertsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Emergency Alerts",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(20.dp)
            )

            AnimatedVisibility(visible = uiState.isComposerOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.selectedSeverity == AlertSeverity.WARNING,
                            onClick = { viewModel.onSeveritySelected(AlertSeverity.WARNING) },
                            label = { Text("Warning") }
                        )
                        FilterChip(
                            selected = uiState.selectedSeverity == AlertSeverity.CRITICAL,
                            onClick = { viewModel.onSeveritySelected(AlertSeverity.CRITICAL) },
                            label = { Text("Critical") }
                        )
                    }
                    OutlinedTextField(
                        value = uiState.composerText,
                        onValueChange = viewModel::onComposerTextChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        placeholder = { Text("Describe the emergency") }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { viewModel.onSendClicked() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
                            Text(" Send Alert", modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            if(uiState.alerts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No active alerts",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.alerts, key = { it.id }) { alert ->
                        AlertCard(alert = alert)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { viewModel.onComposerToggled() },
            containerColor = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(
                imageVector = if(uiState.isComposerOpen) Icons.Filled.Close else Icons.Filled.Warning,
                contentDescription = if(uiState.isComposerOpen) "Close composer" else "New alert"
            )
        }
    }
}

@Composable
private fun AlertCard(alert:EmergencyAlert) {
    val isCritical = alert.severity == AlertSeverity.CRITICAL
    val baseColor = if(isCritical) MaterialTheme.colorScheme.error else Color(0xFFF2B84B)

    val infiniteTransition = rememberInfiniteTransition(label = "alert-pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if(isCritical) 0.6f else 1f,
        targetValue = if(isCritical) 1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alert-pulse-alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(baseColor.copy(alpha = 0.15f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(baseColor.copy(alpha = if(isCritical) pulseAlpha else 1f))
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Row {
                Text(
                    text = if(isCritical) "CRITICAL" else "WARNING",
                    style = MaterialTheme.typography.labelSmall,
                    color = baseColor
                )
                Text(
                    text = "  •  ${alert.authorDisplayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if(alert.hopCount > 0) {
                    Text(
                        text = "  •  ${alert.hopCount} hops",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
            Text(
                text = alert.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}