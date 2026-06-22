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
import org.json.JSONArray
import org.json.JSONObject

data class FieldScribeUiState(
  /** Raw transcript produced by on-device speech recognition. */
  val transcript: String = "",

  val isListening: Boolean = false,

  /** Structured incident report JSON returned by inference call #1. */
  val structuredReport: String = "",

  /** Validated/flagged report returned by inference call #2. */
  val validatedReport: String = "",

  val editedReport: String = "",

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

  fun updateEditedReport(editedReport: String) {
    _uiState.update { it.copy(editedReport = editedReport) }
  }

  private fun buildEditedReport(structuredReport: String, validatedReport: String): String {
    return try {
      val jsonStart = structuredReport.indexOf('{')
      val jsonEnd = structuredReport.lastIndexOf('}')
      val reportJson = JSONObject(structuredReport.substring(jsonStart, jsonEnd + 1))

      val flagsMatch =
        Regex("FLAGS:\\s*(\\[.*?\\])", RegexOption.DOT_MATCHES_ALL).find(validatedReport)
      val flags = flagsMatch?.let { JSONArray(it.groupValues[1]) }

      if (flags != null) {
        for (i in 0 until flags.length()) {
          val field = flags.getString(i)
          if (reportJson.has(field)) {
            reportJson.put(field, "")
          }
        }
      }
      reportJson.toString(2)
    } catch (e: Exception) {
      structuredReport
    }
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
            validateReport(model, response)
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
        Below is a JSON incident report. Go through these fields one at a time:
        incident_type, location, time, impact, action_taken, injuries.

        For each field, write "FIELDNAME: OK" if it has a real value, or
        "FIELDNAME: MISSING" if its value is "unknown", empty, or missing.

        After checking all fields, on a final line write:
        FLAGS: [list the MISSING fields as a JSON array]

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
            _uiState.update {
              it.copy(
                inProgress = false,
                editedReport = buildEditedReport(structuredReport, response),
              )
            }
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
