package com.google.ai.edge.gallery.ui.fieldscribe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun FieldScribeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  viewModel: FieldScribeViewModel = hiltViewModel(),
) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = "FieldScribe", style = MaterialTheme.typography.titleLarge)
  }
}
