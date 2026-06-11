package com.google.ai.edge.gallery.ui.fieldscribe

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FieldScribeUiState(
  /** Raw transcript produced by on-device speech recognition. */
  val transcript: String = "",

  /** Structured incident report JSON returned by inference call #1. */
  val structuredReport: String = "",

  /** Validated/flagged report returned by inference call #2. */
  val validatedReport: String = "",

  /** Whether any inference pass is currently running. */
  val inProgress: Boolean = false,
)

@HiltViewModel
class FieldScribeViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(FieldScribeUiState())
  val uiState = _uiState.asStateFlow()

  fun updateTranscript(transcript: String) {
    _uiState.update { it.copy(transcript = transcript) }
  }
}
