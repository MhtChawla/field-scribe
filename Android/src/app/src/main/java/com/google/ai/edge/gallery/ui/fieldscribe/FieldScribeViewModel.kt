package com.google.ai.edge.gallery.ui.fieldscribe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.common.processLlmResponse
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.runtime.runtimeHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FieldScribeUiState(
  /** Raw transcript produced by on-device speech recognition. */
  val transcript: String = "",

  val isListening: Boolean = false,

  /** Structured incident report JSON returned by inference call #1. */
  val structuredReport: String = "",

  /** Validated/flagged report returned by inference call #2. */
  val validatedReport: String = "",

  /** Whether any inference pass is currently running. */
  val inProgress: Boolean = false,

  val errorMessage: String? = null,
)

@HiltViewModel
class FieldScribeViewModel @Inject constructor() : ViewModel() {
  private val _uiState = MutableStateFlow(FieldScribeUiState())
  val uiState = _uiState.asStateFlow()

  fun updateTranscript(transcript: String) {
    _uiState.update { it.copy(transcript = transcript) }
  }

  fun setListening(isListening: Boolean) {
    _uiState.update { it.copy(isListening = isListening) }
  }

  fun setError(message: String?) {
    _uiState.update { it.copy(errorMessage = message) }
  }

  fun generateStructuredReport(model: Model, transcript: String) {
    _uiState.update { it.copy(structuredReport = "", inProgress = true, errorMessage = null) }

    viewModelScope.launch(Dispatchers.Default) {
      while (model.instance == null) {
        delay(100)
      }

      model.runtimeHelper.resetConversation(model = model, supportImage = false, supportAudio = false)

      val prompt =
        """
        You are an assistant that converts a worker's spoken incident description into a structured JSON report.
        Return ONLY valid JSON, with exactly these fields: incident_type, location, time, impact, action_taken, injuries.
        If a field is not mentioned, use "unknown" as the value.

        Transcript:
        $transcript
        """
          .trimIndent()

      var response = ""
      model.runtimeHelper.runInference(
        model = model,
        input = prompt,
        resultListener = { partialResult, done, _ ->
          response = processLlmResponse(response = "$response$partialResult")
          _uiState.update { it.copy(structuredReport = response) }
          if (done) {
            _uiState.update { it.copy(inProgress = false) }
          }
        },
        cleanUpListener = { _uiState.update { it.copy(inProgress = false) } },
        onError = { message ->
          _uiState.update { it.copy(inProgress = false, errorMessage = message) }
        },
        coroutineScope = viewModelScope,
      )
    }
  }

  fun validateReport(model: Model, structuredReport: String) {
    _uiState.update { it.copy(validatedReport = "", inProgress = true, errorMessage = null) }

    viewModelScope.launch(Dispatchers.Default) {
      while (model.instance == null) {
        delay(100)
      }

      model.runtimeHelper.resetConversation(model = model, supportImage = false, supportAudio = false)

      val prompt =
        """
        You are a safety reviewer checking an incident report JSON for completeness and accuracy.
        Given the JSON below, return the SAME JSON but add a "flags" field, which is a list of
        strings naming any field that is "unknown", missing, vague, or needs human review
        (for example, injuries reported but no action_taken). If nothing needs review, return
        an empty list for "flags".
        Return ONLY valid JSON.

        Report:
        $structuredReport
        """
          .trimIndent()

      var response = ""
      model.runtimeHelper.runInference(
        model = model,
        input = prompt,
        resultListener = { partialResult, done, _ ->
          response = processLlmResponse(response = "$response$partialResult")
          _uiState.update { it.copy(validatedReport = response) }
          if (done) {
            _uiState.update { it.copy(inProgress = false) }
          }
        },
        cleanUpListener = { _uiState.update { it.copy(inProgress = false) } },
        onError = { message ->
          _uiState.update { it.copy(inProgress = false, errorMessage = message) }
        },
        coroutineScope = viewModelScope,
      )
    }
  }
}
