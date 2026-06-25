# FieldScribe

**Voice-to-structured-report for field workers, entirely on-device.**

FieldScribe is a feature extension built on top of [Google AI Edge Gallery](https://github.com/google-ai-edge/gallery) that lets field workers in utilities, logistics, and disaster relief dictate an incident verbally and receive a clean, structured report — no internet required.

## How It Works

1. **Capture** — Worker presses a button and speaks naturally.
2. **Transcribe** — Audio is converted to raw text on-device via Android’s SpeechRecognizer.
3. **Structure** — Raw transcription is passed to an on-device SLM (Qwen 1B Q4), which produces a JSON incident report with fixed fields: `incident_type`, `location`, `time`, `impact`, `action_taken`, `injuries`.
4. **Validate** — A second, separate inference pass reviews the structured report. Incomplete or ambiguous fields are flagged inline so the worker can correct them before finalizing.

All four steps run fully offline after the initial model download.

## Key Design Decisions

- **Two-pass inference with session reset** — `resetConversation()` is called before each `runInference` call. The underlying LiteRT session is stateful; without resetting, structuring and validation calls would inherit stale context and risk memory leaks.
- **Strictly sequential chaining** — `generateStructuredReport()` completes first, then `validateReport()` runs on its output. Both use the same loaded model instance to keep memory flat.
- **Short, field-constrained prompts** — Small on-device SLMs get worse with longer prompts, not better. Tightly scoped prompts with an exact field list produce more accurate and consistent results than verbose instructions.
- **JSON output format** — Plain-text structuring was unreliable; switching to JSON gave stricter, more accurate reports.
- **Qwen over Gemma** — Switched to Qwen to bypass HuggingFace OAuth requirements, keeping the offline-first constraint clean.

## Build & Run

Requires Android 12+ and a physical device (not emulator).

```bash
cd Android/src
./gradlew assembleDebug
```

Install the APK, download the Qwen model on first launch (requires Wi-Fi), then go fully offline. The FieldScribe tile appears on the home screen.

See [DEVELOPMENT.md](DEVELOPMENT.md) for detailed build instructions.

## Project Structure

```
Android/src/app/src/main/java/com/google/ai/edge/gallery/
  ui/fieldscribe/
    FieldScribeScreen.kt       # UI — record button, report display, editable fields
    FieldScribeViewModel.kt    # Orchestrates capture → transcribe → structure → validate
    FieldScribeTaskModule.kt   # Task registration and model config
```

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
