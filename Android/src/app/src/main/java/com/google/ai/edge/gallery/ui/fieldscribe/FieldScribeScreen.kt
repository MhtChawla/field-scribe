package com.google.ai.edge.gallery.ui.fieldscribe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

  val pulseTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by pulseTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
    label = "pulseScale",
  )

  val fabColor by animateColorAsState(
    targetValue =
      if (uiState.isListening) MaterialTheme.colorScheme.error
      else MaterialTheme.colorScheme.primary,
    animationSpec = tween(300),
    label = "fabColor",
  )

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .systemBarsPadding(),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "FieldScribe",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "Report is made by local LLM, on-device",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
      )

      Spacer(modifier = Modifier.height(24.dp))

      // --- Transcript card ---
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
          ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.RecordVoiceOver,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Transcript",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.primary,
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          if (uiState.transcript.isEmpty()) {
            Text(
              text =
                if (uiState.isListening) "Listening..."
                else "Tap the mic and describe the incident",
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Text(
              text = uiState.transcript,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // --- Progress ---
      AnimatedVisibility(visible = uiState.inProgress) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          LinearProgressIndicator(
            modifier =
              Modifier
                .fillMaxWidth()
                .height(4.dp)
                .padding(horizontal = 32.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeCap = StrokeCap.Round,
          )
          Text(
            text = "Analyzing report...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }

      // --- Validation card ---
      AnimatedVisibility(
        visible = uiState.validatedReport.isNotEmpty(),
        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 3 },
      ) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          shape = RoundedCornerShape(16.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface,
            ),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.CheckCircleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Validation",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
              text = uiState.validatedReport,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // --- Editable report card ---
      AnimatedVisibility(
        visible = uiState.validatedReport.isNotEmpty(),
        enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 },
      ) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          shape = RoundedCornerShape(16.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface,
            ),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Edit Report",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary,
              )
            }

            Text(
              text = "Review and correct flagged fields",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            OutlinedTextField(
              modifier = Modifier.fillMaxWidth(),
              value = uiState.editedReport,
              onValueChange = { viewModel.updateEditedReport(it) },
              shape = RoundedCornerShape(12.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                  focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                  unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
              minLines = 4,
            )
          }
        }
      }

      // --- Error ---
      uiState.errorMessage?.let { error ->
        Card(
          modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
          shape = RoundedCornerShape(12.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.ErrorOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = error,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      }
    }

    // --- Mic FAB ---
    Box(
      modifier =
        Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 28.dp),
    ) {
      if (uiState.isListening) {
        Box(
          modifier =
            Modifier
              .size(80.dp)
              .scale(pulseScale)
              .background(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = CircleShape,
              )
              .align(Alignment.Center),
        )
      }
      LargeFloatingActionButton(
        onClick = { onMicClick() },
        shape = CircleShape,
        containerColor = fabColor,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation =
          FloatingActionButtonDefaults.elevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp,
          ),
      ) {
        Icon(
          imageVector = if (uiState.isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
          contentDescription = if (uiState.isListening) "Stop recording" else "Start recording",
          modifier = Modifier.size(32.dp),
        )
      }
    }
  }
}
