# Flash Deck native import/export format (`Decks.json`)

This document describes the app's native deck file format so you can author
decks on a computer and import them via the app's **Import** feature.
`scripts/build_deck.py` (standard-library-only Python 3.9+) builds and fixes
these files for you — see [Usage](#usage) at the bottom.

## Container

A native export is a ZIP file containing:

```
Decks.zip
├── Decks.json                      <- required, exact name (case-sensitive)
└── media/                          <- optional, only when media is attached
    ├── image/question/<fileName>   <- card question images
    ├── image/answer/<fileName>     <- card answer images
    ├── voice/question/<fileName>   <- card question voice recordings
    └── voice/answer/<fileName>     <- card answer voice recordings
```

- The JSON entry must be named exactly `Decks.json` — the importer looks it up
  with a case-sensitive equality check (`ExportImportCmd.java`, line 226,
  constant defined at line 70). A different casing such as `decks.json` is
  **not** found, and a ZIP without this entry silently imports 0 decks.
- The ZIP entries are plain (stored or deflated) ZIP bytes — **not** gzip. A
  gzip-compressed `Decks.json` renamed to `.zip` fails with a parse error.
- A plain (non-ZIP) `.json` file in this same format is also accepted: if the
  import file is not a valid ZIP, the importer falls back to parsing the file
  directly as the JSON below.

## `Decks.json` content

A JSON **array of deck objects**, written as UTF-8.

> **Critical gotcha:** the JSON must be on a **single line** (a single trailing
> newline after `]` is fine). The current importer reads only the first line of
> the entry: `getDeckModelsFromJson` uses
> `new BufferedReader(...).readLine()` (`ExportImportCmd.java`, line 323), so
> any pretty-printing / newline inside the JSON breaks the import with
> `Unterminated array at character 1 of [`. This will no longer be required
> once that bug is fixed.

### Deck object

| Field             | Type   | Required            | Notes |
|-------------------|--------|---------------------|-------|
| `serialVersionUID`| number | No                  | Written on export as the constant `-8121772616636312403` but **never read on import** — purely informational. (The value exceeds JavaScript's safe integer range; a JS tool warning about that is harmless.) |
| `deck`            | object | Yes                 | The deck itself, see below. |
| `cardList`        | array  | No                  | Array of card objects. The exporter **omits the key entirely** when a deck has no cards; the importer accepts both an absent and an empty `cardList`. |

### `deck` object

| Field             | Type   | Required | Notes |
|-------------------|--------|----------|-------|
| `id`              | number | Yes      | Any positive number works — **all ids are discarded and reassigned on import**. Cards reference decks by this value (matching `card.deckId` is conventional, not enforced). |
| `name`            | string | Yes      | Deck display name. |
| `createdDateTime` | string | Yes      | A **string** containing epoch **milliseconds**, e.g. `"1789240551008"` (the key must exist; an empty string is tolerated). |
| `updatedDateTime` | string | Yes      | Same format as `createdDateTime`. |

### Card object (`cardList` items)

| Field             | Type    | Required | Notes |
|-------------------|---------|----------|-------|
| `id`              | number  | Yes      | Discarded and reassigned on import. |
| `deckId`          | number  | Yes      | Required number; the value is discarded on import (cards are re-linked to the newly assigned deck id), but matching the enclosing `deck.id` keeps files self-consistent. |
| `ordinal`         | number  | Yes      | 0-based card position within the deck. |
| `question`        | string  | Yes      | Card front (supports Markdown/LaTeX). |
| `questionImage`   | string  | No       | File name of the question image inside `media/image/question/` in the same ZIP. Empty string `""` = none. |
| `questionVoice`   | string  | No       | File name inside `media/voice/question/`. Empty string `""` = none. |
| `answer`          | string  | Yes      | Card back. |
| `answerImage`     | string  | No       | File name inside `media/image/answer/`. Empty string `""` = none. |
| `answerVoice`     | string  | No       | File name inside `media/voice/answer/`. Empty string `""` = none. |
| `isReversibleQA`  | boolean | No       | `true` = the card can be tested in both directions. Defaults to `false`. |
| `isReversed`      | boolean | No       | Runtime-only state; the exporter always writes `false`. Harmless to include or omit. |

Media fields hold only the **file name** — the actual bytes must be present as
sibling ZIP entries under the matching `media/...` directory listed above.

## Complete minimal example

Readable form (for humans — **do not** import it pretty-printed, see the
single-line gotcha above):

```json
[
  {
    "serialVersionUID": -8121772616636312403,
    "deck": {
      "id": 1,
      "name": "test deck",
      "createdDateTime": "1789240551008",
      "updatedDateTime": "1789240551008"
    },
    "cardList": [
      {
        "id": 1,
        "deckId": 1,
        "ordinal": 0,
        "question": "What is the capital of France?",
        "questionImage": "",
        "questionVoice": "",
        "answer": "Paris",
        "answerImage": "",
        "answerVoice": "",
        "isReversibleQA": false,
        "isReversed": false
      }
    ]
  }
]
```

What actually goes into the ZIP entry — exactly one line:

```
[{"serialVersionUID":-8121772616636312403,"deck":{"id":1,"name":"test deck","createdDateTime":"1789240551008","updatedDateTime":"1789240551008"},"cardList":[{"id":1,"deckId":1,"ordinal":0,"question":"What is the capital of France?","questionImage":"","questionVoice":"","answer":"Paris","answerImage":"","answerVoice":"","isReversibleQA":false,"isReversed":false}]}]
```

## Gotchas checklist

1. **One line only.** The importer parses only the first line of `Decks.json`
   (`ExportImportCmd.java`, `getDeckModelsFromJson`, `readLine()` at line 323).
   Pretty-printed JSON fails with `Unterminated array at character 1 of [`.
   Use `scripts/build_deck.py fix` to collapse a pretty-printed file.
2. **Entry name is exact and case-sensitive.** `Decks.json`, at the ZIP root.
   If it is missing (or mis-cased), the importer does not error — it silently
   imports 0 decks.
3. **Plain ZIP bytes, not gzip.** Deflate compression is fine; a gzip stream is
   not.
4. **Ids don't survive import.** The app reassigns deck/card ids and re-links
   cards to the newly assigned deck id (`DeckCardRepository.importDecks`
   discards both values), so any positive unique numbers are fine. Matching
   `card.deckId` to the enclosing `deck.id` is not enforced by the importer
   but keeps files self-consistent.
5. **Timestamps are strings of epoch milliseconds**, not JSON numbers and not
   ISO dates.
6. **Media fields are file names, not paths**, and `""` means "no media". The
   referenced files must exist inside the ZIP under `media/image|voice/question|answer/`.

## Usage

`scripts/build_deck.py` needs only Python 3.9+ (no third-party packages).

Build from a CSV (header row with `question`, `answer`; optional `reversible`
(true/false) and media path columns `questionImage`, `questionVoice`,
`answerImage`, `answerVoice`):

```bash
python scripts/build_deck.py build --input cards.csv --name "Spanish vocab" --output Decks.zip
```

Build from an authoring JSON file (one or more decks):

```bash
python scripts/build_deck.py build --input my-decks.json --output Decks.zip
```

Authoring JSON format:

```json
[
  {
    "name": "Deck name",
    "cards": [
      {
        "question": "front",
        "answer": "back",
        "isReversibleQA": false,
        "questionImage": "C:/pictures/local-image.png"
      }
    ]
  }
]
```

Media fields in the authoring format are **local file paths**; `build` bundles
them into the ZIP and rewrites the fields to the stored file names. Already
deck-format JSON (objects with a `deck` key) is detected automatically — it is
just minified and packaged unchanged.

Fix a pretty-printed deck file (also works for a ZIP whose `Decks.json` is
pretty-printed; media entries are carried over to a `.zip` output):

```bash
python scripts/build_deck.py fix --input Decks-pretty.json --output Decks.json
python scripts/build_deck.py fix --input Decks-broken.zip --output Decks.zip --verbose
```

Both subcommands accept `--verbose` to print a summary (decks, cards, media
count, output path, output sha256).
