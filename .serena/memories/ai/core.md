# :ai module core

Google Gemini deck generation. Depends on `:base` only. Package `m.co.rh.id.a_flash_deck.ai`.

- `service/GeminiService.java` — raw `HttpURLConnection` calls to the Gemini REST API (no HTTP client library); prompts request JSON output with Markdown + LaTeX.
- `security/ApiKeyManager.java` — AES/GCM key backed by AndroidKeyStore; stores encrypted API key + selected model in `ai_secure_prefs` SharedPreferences.
- `command/` — `GenerateDeckFromTopicCmd`, `GenerateDeckFromImageCmd`, `GenerateDeckFromExistingCmd`, `GenerateDeckFromCardCmd`.
- `workmanager/` — `GenerateDeck*Worker` + `BaseGenerateDeckWorker` (background generation jobs).
- `ui/page/` — `GenerateDeckFrom*Page` + `ApiKeyEntrySVDialog`; `model/` — `AiGeneratedDeck`, `AiGeneratedCard`, `AvailableModel`.
- Stored cards convert LaTeX to Anki-style `\(...\)` / `\[...\]` delimiters.
