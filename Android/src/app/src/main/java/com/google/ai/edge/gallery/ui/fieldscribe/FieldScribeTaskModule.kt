package com.google.ai.edge.gallery.ui.fieldscribe

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.runtime.Composable
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.customtasks.common.CustomTask
import com.google.ai.edge.gallery.customtasks.common.CustomTaskDataForBuiltinTask
import com.google.ai.edge.gallery.data.BuiltInTaskId
import com.google.ai.edge.gallery.data.Category
import com.google.ai.edge.gallery.data.Model
import com.google.ai.edge.gallery.data.Task
import com.google.ai.edge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.ai.edge.litertlm.Contents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

class FieldScribeTask @Inject constructor() : CustomTask {
  override val task: Task =
    Task(
      id = BuiltInTaskId.FIELD_SCRIBE,
      label = "FieldScribe",
      category = Category.LLM,
      icon = Icons.Outlined.Widgets,
      models = mutableListOf(),
      description = "Dictate a field incident and get a structured, validated report on-device",
      shortDescription = "Voice incident reports",
      modelNames = listOf("Qwen2.5-1.5B-Instruct"),
      docUrl = "https://github.com/google-ai-edge/LiteRT-LM/blob/main/kotlin/README.md",
      sourceCodeUrl =
        "https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatModelHelper.kt",
      textInputPlaceHolderRes = R.string.text_input_placeholder_llm_chat,
    )

  override fun initializeModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    systemInstruction: Contents?,
    onDone: (String) -> Unit,
  ) {
    LlmChatModelHelper.initialize(
      context = context,
      model = model,
      taskId = task.id,
      supportImage = false,
      supportAudio = false,
      onDone = onDone,
      systemInstruction = systemInstruction,
    )
  }

  override fun cleanUpModelFn(
    context: Context,
    coroutineScope: CoroutineScope,
    model: Model,
    onDone: () -> Unit,
  ) {
    LlmChatModelHelper.cleanUp(model = model, onDone = onDone)
  }

  @Composable
  override fun MainScreen(data: Any) {
    val myData = data as CustomTaskDataForBuiltinTask
    FieldScribeScreen(
      modelManagerViewModel = myData.modelManagerViewModel,
      navigateUp = myData.onNavUp,
    )
  }
}

@Module
@InstallIn(SingletonComponent::class) // Or another component that fits your scope
internal object FieldScribeTaskModule {
  @Provides
  @IntoSet
  fun provideTask(): CustomTask {
    return FieldScribeTask()
  }
}
