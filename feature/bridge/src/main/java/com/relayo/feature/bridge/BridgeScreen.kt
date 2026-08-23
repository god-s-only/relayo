package com.relayo.feature.bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relayo.domain.model.BridgeRequestType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BridgeScreen(
    viewModel:BridgeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Text(
                text = "Internet Bridge",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = if(uiState.hasInternet) "You have internet — you will answer others" else "No internet — asking via mesh",
                style = MaterialTheme.typography.labelMedium,
                color = if(uiState.hasInternet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.size(16.dp))

            // Composer
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = when(uiState.selectedType) {
                                BridgeRequestType.EXCHANGE_RATE -> "Exchange Rate"
                                BridgeRequestType.WEATHER -> "Weather"
                                BridgeRequestType.WEB_FETCH -> "Web Fetch"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Request type") },
                            leadingIcon = {
                                when(uiState.selectedType) {
                                    BridgeRequestType.EXCHANGE_RATE -> androidx.compose.material3.Icon(Icons.Filled.CurrencyExchange, null)
                                    BridgeRequestType.WEATHER -> androidx.compose.material3.Icon(Icons.Filled.Cloud, null)
                                    BridgeRequestType.WEB_FETCH -> androidx.compose.material3.Icon(Icons.Filled.Language, null)
                                }
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Exchange Rate — e.g. USD/EUR") }, onClick = { viewModel.onTypeSelected(BridgeRequestType.EXCHANGE_RATE); expanded = false })
                            DropdownMenuItem(text = { Text("Weather — e.g. London") }, onClick = { viewModel.onTypeSelected(BridgeRequestType.WEATHER); expanded = false })
                            DropdownMenuItem(text = { Text("Web Fetch — e.g. example.com") }, onClick = { viewModel.onTypeSelected(BridgeRequestType.WEB_FETCH); expanded = false })
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                when(uiState.selectedType) {
                                    BridgeRequestType.EXCHANGE_RATE -> "USD/EUR"
                                    BridgeRequestType.WEATHER -> "City name"
                                    BridgeRequestType.WEB_FETCH -> "example.com or https://..."
                                }
                            )
                        },
                        label = { Text("Query") },
                        isError = uiState.error != null
                    )
                    uiState.error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Button(
                        onClick = viewModel::onSendClicked,
                        enabled = !uiState.isSending,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.Send, contentDescription = null)
                        Text(" Ask mesh", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.size(16.dp))
            if(uiState.requests.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No bridge requests yet", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(uiState.requests.reversed(), key = { it.id }) { req ->
                        val resp = uiState.responses.find { it.requestId == req.id }
                        BridgeRequestCard(req, resp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeRequestCard(
    req:com.relayo.domain.model.BridgeRequest,
    resp:com.relayo.domain.model.BridgeResponse?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = when(req.type) {
                        BridgeRequestType.EXCHANGE_RATE -> "Exchange"
                        BridgeRequestType.WEATHER -> "Weather"
                        BridgeRequestType.WEB_FETCH -> "Web"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = req.query,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            if(resp == null) {
                Text(
                    text = "Waiting for a bridge peer with internet...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else if(resp.error != null) {
                Text(
                    text = "Error: ${resp.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = resp.result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "Answered by ${resp.responderId.takeLast(6)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
