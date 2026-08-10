package com.relayo.feature.qrboards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.relayo.domain.model.Board
import com.relayo.domain.model.BoardPost

@Composable
fun QrBoardsScreen(
    viewModel:QrBoardsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isScannerOpen -> {
            QrScannerScreen(
                onScanned = { viewModel.onQrScanned(it) },
                onClose = { viewModel.onScannerToggled() }
            )
        }
        uiState.selectedBoardId != null -> {
            val board = uiState.boards.find { it.id == uiState.selectedBoardId }
            BoardDetailScreen(
                board = board,
                boardId = uiState.selectedBoardId!!,
                posts = uiState.selectedBoardPosts,
                draft = uiState.postDraft,
                onDraftChanged = viewModel::onPostDraftChanged,
                onSend = { viewModel.onPostSubmitted() },
                onClose = { viewModel.onBoardDetailClosed() }
            )
        }
        else -> {
            BoardsListContent(
                boards = uiState.boards,
                isCreateDialogOpen = uiState.isCreateDialogOpen,
                newBoardName = uiState.newBoardName,
                onCreateToggled = { viewModel.onCreateDialogToggled() },
                onNameChanged = viewModel::onNewBoardNameChanged,
                onCreateConfirmed = { viewModel.onCreateBoardConfirmed() },
                onScanClicked = { viewModel.onScannerToggled() },
                onBoardClicked = { viewModel.onBoardSelected(it) }
            )
        }
    }
}

@Composable
private fun BoardsListContent(
    boards:List<Board>,
    isCreateDialogOpen:Boolean,
    newBoardName:String,
    onCreateToggled:() -> Unit,
    onNameChanged:(String) -> Unit,
    onCreateConfirmed:() -> Unit,
    onScanClicked:() -> Unit,
    onBoardClicked:(String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "QR Boards",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(20.dp)
            )

            if(boards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No boards yet — create one or scan a QR code",
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
                    items(boards, key = { it.id }) { board ->
                        BoardRow(board = board, onClick = { onBoardClicked(board.id) })
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(onClick = onScanClicked) {
                Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = "Scan QR")
            }
            FloatingActionButton(
                onClick = onCreateToggled,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Create board")
            }
        }
    }

    if(isCreateDialogOpen) {
        AlertDialog(
            onDismissRequest = onCreateToggled,
            title = { Text("New Board") },
            text = {
                OutlinedTextField(
                    value = newBoardName,
                    onValueChange = onNameChanged,
                    placeholder = { Text("Board name") }
                )
            },
            confirmButton = {
                Button(onClick = onCreateConfirmed) { Text("Create") }
            }
        )
    }
}

@Composable
private fun BoardRow(board:Board, onClick:() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = board.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Created by ${board.createdByDisplayName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}