package com.google.ai.edge.gallery.ui.fieldscribe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.ModelDownloadStatusType
import com.google.ai.edge.gallery.ui.modelmanager.ModelManagerViewModel

@Composable
fun FieldScribeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  viewModel: FieldScribeViewModel = hiltViewModel(),
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val task = modelManagerViewModel.getTaskById(id = BuiltInTaskId.FIELD_SCRIBE)!!
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel

  val curDownloadStatus = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(curDownloadStatus, selectedModel.name) {
    if (curDownloadStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
      modelManagerViewModel.initializeModel(context, task = task, model = selectedModel)
    }
  }

  LaunchedEffect(uiState.transcript) {
    if (uiState.transcript.isNotEmpty()) {
      viewModel.generateStructuredReport(selectedModel, uiState.transcript)
    }
  }

  val recognizer = remember {
    if (SpeechRecognizer.isRecognitionAvailable(context)) {
      SpeechRecognizer.createSpeechRecognizer(context)
    } else {
      null
    }
  }

  DisposableEffect(recognizer) {
    recognizer?.setRecognitionListener(
      object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
          viewModel.setListening(false)
        }

        override fun onError(error: Int) {
          viewModel.setListening(false)
          viewModel.setError("Speech recognition error (code $error)")
        }

        override fun onResults(results: Bundle?) {
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val transcript = matches?.firstOrNull().orEmpty()
          if (transcript.isNotEmpty()) {
            viewModel.updateTranscript(transcript)
          }
          viewModel.setListening(false)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
      }
    )
    onDispose { recognizer?.destroy() }
  }

  val recognizerIntent = remember {
    android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
  }

  fun startListening() {
    viewModel.setError(null)
    viewModel.setListening(true)
    recognizer?.startListening(recognizerIntent)
  }

  val recordAudioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) {
        startListening()
      } else {
        viewModel.setError("Microphone permission is required to record an incident.")
      }
    }

  fun onMicClick() {
    if (recognizer == null) {
      viewModel.setError("Speech recognition is not available on this device.")
      return
    }
    if (uiState.isListening) {
      recognizer.stopListening()
      viewModel.setListening(false)
      return
    }
    val hasPermission =
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    if (hasPermission) {
      startListening()
    } else {
      recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(text = "FieldScribe", style = MaterialTheme.typography.titleLarge)

      Text(
        modifier = Modifier.padding(top = 24.dp),
        text =
          if (uiState.transcript.isEmpty()) {
            if (uiState.isListening) "Listening..." else "Tap the mic and describe the incident"
          } else {
            uiState.transcript
          },
        style = MaterialTheme.typography.bodyLarge,
      )

      if (uiState.inProgress) {
        CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
      }

      if (uiState.validatedReport.isNotEmpty()) {
        Text(
          modifier = Modifier.padding(top = 24.dp),
          text = "Validation:",
          style = MaterialTheme.typography.titleMedium,
        )
        Text(
          modifier = Modifier.fillMaxWidth(),
          text = uiState.validatedReport,
          style = MaterialTheme.typography.bodyMedium,
        )

        Text(
          modifier = Modifier.padding(top = 24.dp),
          text = "Edit report (edit the flagged values below):",
          style = MaterialTheme.typography.titleMedium,
        )
        OutlinedTextField(
          modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
          value = uiState.editedReport,
          onValueChange = { viewModel.updateEditedReport(it) },
        )
      }

      uiState.errorMessage?.let { error ->
        Text(
          modifier = Modifier.padding(top = 16.dp),
          text = error,
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    FloatingActionButton(
      onClick = { onMicClick() },
      modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
    ) {
      Icon(
        imageVector = if (uiState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
        contentDescription = if (uiState.isListening) "Stop recording" else "Start recording",
      )
    }
  }
}
