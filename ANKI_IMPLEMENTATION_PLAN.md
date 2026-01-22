# Anki Import/Export Implementation Plan

## Overview
Implement bidirectional Anki `.apkg` format compatibility for Flash Deck without modifying the database schema.

## User Requirements
- Import Anki decks (APKG format) → Flash Deck entities
- Export Flash Deck entities → Anki format (APKG)
- No database schema modifications
- Map data at application layer only

---

## Anki Documentation Research Summary

### Anki File Formats

| Format | Extension | Type | Content | Scheduling |
|--------|-----------|------|---------|-----------|
| Anki Deck Package | `.apkg` | ZIP | Notes, cards, media, decks, notetypes | Optional |
| Collection Package | `.colpkg` | ZIP | Full collection + media | Yes |
| Anki 2.0 Deck | `.anki2` | SQLite | Notes, cards, decks | Optional |
| Text Notes | `.txt`, `.csv` | Text | Note fields, tags | No |
| Text Cards | `.txt` | Text | Question/answer pairs | No |

### APKG File Structure

```
.apkg (ZIP archive)
├── collection.anki21        # SQLite database (main)
├── collection.anki2         # Dummy database (legacy compatibility)
├── media                    # JSON mapping file
└── 0, 1, 2, ...            # Media files with numeric names
```

**Media JSON format:**
```json
{
  "0": "image.jpg",
  "1": "audio.mp3",
  "2": "video.mp4"
}
```

### Database Schema (collection.anki21)

**Core Tables:**
- `notes` - Note records with GUID, fields, tags, modification time
- `cards` - Card records with scheduling data, due, interval, factor
- `decks` - Deck hierarchy (JSON stored in `col` table)
- `notetypes` (models) - Note type definitions (JSON stored in `col` table)
- `tags` - Tag registry
- `revlog` - Review history

**Critical Note Fields:**
- `guid` - Globally unique identifier (hex string, critical for deduplication)
- `mid` - Notetype ID reference
- `mod` - Modification timestamp (Unix time in milliseconds)
- `usn` - Update sequence number (for sync)
- `tags` - Space-separated tags
- `flds` - `\x1f` (ASCII 31) separated field values
- `sfld` - Sort field (first field for sorting)

**Critical Card Fields:**
- `nid` - Note ID reference
- `did` - Deck ID reference
- `ord` - Card ordinal (which template)
- `due` - Due date (days for new/review, timestamp for learning)
- `ivl` - Current interval in days
- `factor` - Ease factor (e.g., 2500 = 2.5)
- `reps` - Number of reviews
- `lapses` - Number of times lapsed
- `left` - Counts for remaining steps

### Import Requirements

**Field Separators:**
- Note fields: `\x1f` (ASCII 31) separator
- Tags: Space-separated
- TSV/CSV: Tab or comma delimiter, auto-detected

**Duplicate Detection:**
1. Primary: `guid` field match (exact string comparison)
2. Secondary: Field checksum (fast lookup in `csums` dict)
3. Tertiary: Full field content comparison

**Import Modes:**
- `UPDATE_MODE = 0` - Update existing if newer (compare `mod` timestamp)
- `IGNORE_MODE = 1` - Skip duplicates
- `ADD_MODE = 2` - Import as separate notes

**Deck Hierarchy:**
- Supports nested decks via `::` separator (e.g., "Japanese::Kanji")
- Optional deck prefix during import
- Deck IDs are auto-generated integers

**Notetype Schema Matching:**
- `mid` (notetype ID) must match exactly
- Field count must match
- Field names must match
- If schema differs, notes are added to `_ignoredGuids` list

**Scheduling Information:**
- Optional in .apkg exports (`includeSched` parameter)
- Preserves card due dates, intervals, ease factors
- Can be reset for new deck imports

### Export Capabilities

**Export Scopes:**
- Selected notes only
- Single deck (including subdecks)
- Entire collection (.colpkg)

**Export Options:**
- Include scheduling information (card states, due dates)
- Include media files
- HTML stripping (text exports)
- Scheduling reset (export without review data)

**Text Export Format:**
- TSV (tab-separated values)
- Cards: Question `<tab>` Answer
- Notes: Field1 `<tab>` Field2 `<tab>` ... `<tab>` Tags

**Media Handling:**
- Files renamed sequentially (0, 1, 2...)
- `_`-prefixed files reserved for notetype templates
- SHA1-based deduplication in MediaManager
- Unicode normalization to NFC

### JSON Structures

**Notetype (Model) Schema:**
```python
{
  "id": 1234567890,
  "name": "Basic",
  "type": 0,  # 0=standard, 1=cloze
  "flds": [
    {"name": "Front", "ord": 0, "sticky": False},
    {"name": "Back", "ord": 1, "sticky": False}
  ],
  "tmpls": [
    {"name": "Card 1", "qfmt": "{{Front}}", "afmt": "{{BackSide}}"},
    {"name": "Card 2", "qfmt": "{{Back}}", "afmt": "{{FrontSide}}"}
  ],
  "css": "..."
}
```

**Deck Schema (JSON in `col.decks`):**
```python
{
  "1": {
    "name": "Default",
    "desc": "",
    "conf": 1,  # Deck config ID
    "children": []  # Subdeck IDs
  }
}
```

---

## Flash Deck Codebase Analysis

### Current Architecture

**Data Layer (Base Module):**
- **Room Database** (`AppDatabase`, v11) with entities:
  - `Deck` - id, name, created/updated timestamps
  - `Card` - id, deckId, ordinal, question, answer, question/answer images, question/answer voice files, reversibility flags
- **DAOs** - `DeckDao` handles CRUD operations, including `importDecks()` method
- **Serialization** - Both entities implement `toJson()`/`fromJson()` methods

**Business Logic Layer (App Module):**
- **Commands** - Encapsulated business logic including `ExportImportCmd`
- **Notifiers** - RxJava-based event publishers (`DeckChangeNotifier`)

**Presentation Layer (App Module):**
- `HomePage` - Main hub with Export/Import buttons
- `DeckListPage` / `CardListPage` - Lists for managing decks and cards

### Existing Import/Export Functionality

**Location:** `app/provider/command/ExportImportCmd.java`

**Export (Native Format):**
- Creates ZIP file containing:
  - `Decks.json` - JSON array of DeckModel objects
  - `media/image/question/` - Question images
  - `media/image/answer/` - Answer images
  - `media/voice/question/` - Question voice recordings
- Note: Answer voice is NOT exported in native format

**Import (Native Format):**
- Reads ZIP or JSON
- Extracts media files
- Imports to database via `DeckDao.importDecks()`
- Supports legacy JSON format

### FileHelper Capabilities

**Storage Paths:**
- `app/card/question/image/` + thumbnail
- `app/card/answer/image/` + thumbnail
- `app/card/question/voice/`
- `app/card/answer/voice/`

**Methods:**
- `createCardQuestionImage()`, `getCardQuestionImage()`, `deleteCardQuestionImage()`
- `createCardAnswerImage()`, `getCardAnswerImage()`, `deleteCardAnswerImage()`
- `createCardQuestionVoice()`, `getCardQuestionVoice()`, `deleteCardQuestionVoice()`
- `createCardAnswerVoice()`, `getCardAnswerVoice()`, `deleteCardAnswerVoice()`
- Image compression (1280x720 main, 320x180 thumbnail)
- EXIF rotation handling

---

## Mapping Strategy

### Import (Anki → Flash Deck)

| Anki Field | Flash Deck Field | Notes |
|-----------|-----------------|-------|
| Note field 1 | `Card.question` | Basic notetype only |
| Note field 2 | `Card.answer` | Basic notetype only |
| Note field 3+ | ❌ Skipped | More than 2 fields |
| Note tags | ❌ Skipped | No tag support |
| `<img src="...">` in field 1 | `Card.questionImage` | First image only |
| `<img src="...">` in field 2 | `Card.answerImage` | First image only |
| `[sound:...]` in field 1 | `Card.questionVoice` | Parse sound tags |
| `[sound:...]` in field 2 | `Card.answerVoice` | Parse sound tags |
| Deck name (e.g., "A::B") | `Deck.name` ("A - B") | Flatten hierarchy |
| Note GUID | ❌ Skipped | No GUID support |
| Scheduling data | ❌ Skipped | No scheduling |
| Multiple cards/note | Multiple cards | Each card → separate Flash Card |

### Export (Flash Deck → Anki)

| Flash Deck Field | Anki Field | Notes |
|-----------------|-----------|-------|
| `Card.question` | Note field 1 | Plain text |
| `Card.questionImage` | `<img src="...">` in field 1 | Insert media tag |
| `Card.questionVoice` | `[sound:...]` in field 1 | Insert sound tag |
| `Card.answer` | Note field 2 | Plain text |
| `Card.answerImage` | `<img src="...">` in field 2 | Insert media tag |
| `Card.answerVoice` | `[sound:...]` in field 2 | Insert sound tag |
| `Card.isReversibleQA` | ❌ Ignored | Export as single card |
| `Deck.name` | Deck name | Single-level |
| `Card.isReversed` | ❌ Ignored | N/A for export |

---

## Implementation Plan

### Phase 1: Create Anki Package Classes

**1.1 Create `AnkiModels.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/anki/AnkiModels.java`
- Purpose: DTOs for Anki data structures
- Classes:
  ```java
  class AnkiNote {
      long id;
      long guid;
      long mid; // Notetype ID
      String mod;
      String usn;
      String tags;
      String flds; // \x1f-separated fields
      String sfld; // Sort field
      long flags;
      String data;
  }

  class AnkiCard {
      long id;
      long nid; // Note ID
      long did; // Deck ID
      int ord;
      int due;
      int ivl;
      int factor;
      int reps;
      int lapses;
  }

  class AnkiDeck {
      long id;
      String name;
      long mtimeStamp;
      String conf;
      List<Long> children;
  }

  class AnkiNotetype {
      long id;
      String name;
      int type; // 0=standard, 1=cloze
      List<AnkiField> flds;
      List<AnkiTemplate> tmpls;
      String css;
  }
  ```

**1.2 Create `ApkgParser.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/anki/ApkgParser.java`
- Purpose: Parse `.apkg` files
- Methods:
  ```java
  public static SQLiteDatabase extractAndReadDatabase(File apkgFile)
  public static Map<String, File> extractMediaFiles(File apkgFile)
  public static String parseMediaJson(ZipFile zipFile)
  public static List<AnkiNote> readNotes(SQLiteDatabase db, long notetypeId)
  public static List<AnkiCard> readCards(SQLiteDatabase db, List<Long> noteIds)
  public static List<AnkiDeck> readDecks(SQLiteDatabase db)
  public static List<AnkiNotetype> readNotetypes(SQLiteDatabase db)
  public static boolean isBasicNotetype(AnkiNotetype notetype)
  ```

**1.3 Create `ApkgGenerator.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/anki/ApkgGenerator.java`
- Purpose: Generate `.apkg` files
- Methods:
  ```java
  public static SQLiteDatabase createInMemoryDatabase()
  public static void createTables(SQLiteDatabase db)
  public static void insertBasicNotetype(SQLiteDatabase db)
  public static long insertDeck(SQLiteDatabase db, String deckName)
  public static long insertNote(SQLiteDatabase db, String guid, long deckId, long notetypeId, String field1, String field2)
  public static long insertCard(SQLiteDatabase db, long noteId, long deckId, int ordinal)
  public static void insertMediaMapping(SQLiteDatabase db, Map<String, Integer> mediaMap)
  public static File generateApkg(SQLiteDatabase db, Map<String, File> mediaFiles, String outputFileName)
  ```

---

### Phase 2: Implement AnkiImporter

**2.1 Create `AnkiImporter.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/provider/command/AnkiImporter.java`
- Constructor: Takes `Provider` dependency

**Main Method:**
```java
public Single<List<DeckModel>> importApkg(File apkgFile)
```

**Algorithm:**
```
1. Validate file is .apkg
2. Parse APKG structure:
   - Extract SQLite database
   - Extract media files to temp
   - Parse media JSON mapping
3. Read Anki data:
   - Read all notetypes, filter for Basic (2 fields)
   - Read all notes that match Basic notetype
   - Read all cards for those notes
   - Read all decks
4. Build Flash Deck models:
   For each Anki note:
   a. Parse flds by \x1f → field1, field2
   b. Parse HTML/media from fields:
      - Extract first <img src="...">
      - Extract [sound:...]
      - Strip HTML tags for plain text
   c. For each card referencing this note:
      - Create Card entity
      - Map media files
      - Assign to deck (flatten name)
5. Copy media files from temp to FileHelper paths:
   - questionImage → mCardQuestionImageParent
   - answerImage → mCardAnswerImageParent
   - questionVoice → mCardQuestionVoiceParent
   - answerVoice → mCardAnswerVoiceParent
6. Clean up temp files
7. Return List<DeckModel>
```

**Helper Methods:**
```java
private String parseFieldText(String htmlField)
private String parseFieldImage(String htmlField)
private String parseFieldVoice(String htmlField)
private String flattenDeckName(String ankiDeckName)
private boolean isBasicNotetype(AnkiNotetype notetype)
private void copyMediaToAppPaths(Map<String, File> mediaFiles, List<Card> cards)
```

**HTML Parsing Logic:**
```java
// Extract first image
Pattern imgPattern = Pattern.compile("<img[^>]+src=\"([^\"]+)\"");
Matcher matcher = imgPattern.matcher(htmlField);
if (matcher.find()) {
    return matcher.group(1);
}

// Extract sound
Pattern soundPattern = Pattern.compile("\\[sound:([^\\]]+)\\]");
matcher = soundPattern.matcher(htmlField);
if (matcher.find()) {
    return matcher.group(1);
}

// Strip HTML for plain text
return htmlField.replaceAll("<[^>]+>", "").trim();
```

**Deck Flattening:**
```java
private String flattenDeckName(String ankiDeckName) {
    return ankiDeckName.replace("::", " - ");
}
```

**Error Handling:**
- Log warnings for:
  - Non-Basic notetypes (skip)
  - Notes with >2 fields (skip)
  - Cards with missing media files (log, skip media)
- Throw exceptions for:
  - Invalid APKG format
  - Corrupt database
  - IO errors

---

### Phase 3: Implement AnkiExporter

**3.1 Create `AnkiExporter.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/provider/command/AnkiExporter.java`
- Constructor: Takes `Provider` dependency

**Main Method:**
```java
public Single<File> exportApkg(List<Deck> deckList)
```

**Algorithm:**
```
1. Gather all cards from selected decks
2. Scan for unique media references:
   For each card:
   - If questionImage exists → add to media map
   - If answerImage exists → add to media map
   - If questionVoice exists → add to media map
   - If answerVoice exists → add to media map
3. Assign sequential numbers to media files (0, 1, 2, ...)
4. Create SQLite database via ApkgGenerator:
   a. Insert Basic notetype
   b. Insert deck records
   c. For each card:
      - Generate GUID using UUID4().hex()
      - Construct field strings:
        field1 = question + <img> tag + [sound] tag
        field2 = answer + <img> tag + [sound] tag
      - Insert note with flds = field1 + \x1f + field2
      - Insert card record
   d. Insert media JSON mapping into col table
5. Create ZIP archive:
   - Add collection.anki21 (SQLite database)
   - Add collection.anki2 (dummy legacy file)
   - Add media (JSON mapping file)
   - Add media files with numeric names
6. Return ZIP file
```

**Helper Methods:**
```java
private String generateGuid()
private String constructNoteField(String text, String image, String voice)
private Map<String, Integer> buildMediaMap(List<DeckModel> deckModels)
private void packageMediaFiles(ZipOutputStream zos, Map<String, Integer> mediaMap)
```

**Note Field Construction:**
```java
private String constructNoteField(String text, String image, String voice) {
    StringBuilder sb = new StringBuilder();
    if (text != null) sb.append(text);
    if (image != null) {
        sb.append("<img src=\"").append(image).append("\">");
    }
    if (voice != null) {
        sb.append("[sound:").append(voice).append("]");
    }
    return sb.toString();
}
```

**Media Mapping Example:**
```java
// Input media references:
//   question: "photo1.jpg"
//   answer: "audio.mp3", "icon.png"

// Media map after numbering:
//   "photo1.jpg" → 0
//   "audio.mp3" → 1
//   "icon.png" → 2

// ZIP structure:
//   collection.anki21
//   collection.anki2
//   media → {"0": "photo1.jpg", "1": "audio.mp3", "2": "icon.png"}
//   0 → photo1.jpg file
//   1 → audio.mp3 file
//   2 → icon.png file
```

**Error Handling:**
- Log warnings for:
  - Missing media files (skip, continue)
- Throw exceptions for:
  - Failed to create database
  - Failed to copy media files
  - IO errors

---

### Phase 4: Integrate into ExportImportCmd

**4.1 Modify `ExportImportCmd.java`**

**Add new import logic:**
```java
public Single<List<DeckModel>> importFile(File file) {
    return Single.fromFuture(mExecutorService.submit(() -> {
        // Check for Anki format
        if (file.getName().toLowerCase().endsWith(".apkg")) {
            return new AnkiImporter(mProvider)
                    .importApkg(file)
                    .blockingGet();
        }

        // Existing logic for native format
        // ... (current implementation)
    }));
}
```

**Add new export method:**
```java
public Single<File> exportFile(List<Deck> deckList, String format) {
    if ("anki".equals(format)) {
        return new AnkiExporter(mProvider)
                .exportApkg(deckList);
    }
    return exportFile(deckList); // default/native
}
```

---

### Phase 5: Update HomePage

**5.1 Modify `HomePage.java`**

**Update file picker for import:**
```java
intent.setType("*/*");
String[] mimeTypes = {"application/zip", "application/octet-stream", "application/vnd.anki.apkg"};
intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
```

**Handle import result:**
- No changes needed, same flow works for both formats

**Export flow:**
- Keep existing export button for native format
- Optionally add "Export to Anki" button (can be added later)

---

### Phase 6: Add String Resources

**6.1 Modify `strings.xml`**

Add:
```xml
<string name="msg_importing_anki">Importing Anki deck...</string>
<string name="msg_exporting_anki">Exporting to Anki format...</string>
<string name="error_invalid_apkg">Not a valid Anki deck file (.apkg)</string>
<string name="warning_only_basic_cards">Warning: Only Basic (2-field) cards will be imported. Other card types will be skipped.</string>
<string name="warning_multiple_images">Warning: Multiple images in one field detected. Using first image only.</string>
<string name="warning_missing_media">Warning: Media file not found: %s</string>
<string name="info_cards_imported">Imported %d cards from %d deck(s)</string>
<string name="info_cards_skipped">Skipped %d unsupported cards</string>
```

---

## File Structure

```
app/src/main/java/m/co/rh/id/a_flash_deck/app/
├── anki/                                       # NEW PACKAGE
│   ├── AnkiModels.java                        # DTOs (AnkiNote, AnkiCard, AnkiDeck, AnkiNotetype)
│   ├── ApkgParser.java                        # APKG parsing utilities
│   └── ApkgGenerator.java                     # APKG generation utilities
│
├── provider/command/
│   ├── ExportImportCmd.java                   # MODIFIED - add format detection
│   ├── AnkiImporter.java                      # NEW - import logic
│   └── AnkiExporter.java                      # NEW - export logic
│
└── ui/page/
    └── HomePage.java                          # MODIFIED - accept .apkg files

app/src/main/res/
└── values/strings.xml                         # MODIFIED - add new strings
```

---

## Key Implementation Details

### SQLite Database Schema (Anki 2.1)

**Required Tables:**
```sql
CREATE TABLE notes (
    id INTEGER PRIMARY KEY,
    guid TEXT NOT NULL,
    mid INTEGER NOT NULL,           -- notetype ID
    mod INTEGER NOT NULL,          -- modification time (ms)
    usn INTEGER NOT NULL,           -- update sequence number
    tags TEXT NOT NULL,
    flds TEXT NOT NULL,             -- \x1f separated fields
    sfld TEXT NOT NULL,            -- sort field
    csum INTEGER NOT NULL,
    flags INTEGER NOT NULL,
    data TEXT NOT NULL
);

CREATE TABLE cards (
    id INTEGER PRIMARY KEY,
    nid INTEGER NOT NULL,           -- note ID
    did INTEGER NOT NULL,           -- deck ID
    ord INTEGER NOT NULL,           -- card ordinal
    mod INTEGER NOT NULL,
    usn INTEGER NOT NULL,
    type INTEGER NOT NULL,          -- 0=new, 1=learning, 2=review, 3=relearning
    queue INTEGER NOT NULL,
    due INTEGER NOT NULL,
    ivl INTEGER NOT NULL,          -- interval
    factor INTEGER NOT NULL,        -- ease factor
    reps INTEGER NOT NULL,
    lapses INTEGER NOT NULL,
    left INTEGER NOT NULL,
    odue INTEGER NOT NULL,
    odid INTEGER NOT NULL,
    flags INTEGER NOT NULL,
    data TEXT NOT NULL
);

CREATE TABLE decks (
    id INTEGER PRIMARY KEY,
    conf INTEGER NOT NULL,
    name TEXT NOT NULL,
    mtime_secs INTEGER NOT NULL,
    usn INTEGER NOT NULL,
    common TEXT NOT NULL,
    kind TEXT NOT NULL
);

CREATE TABLE col (
    id INTEGER PRIMARY KEY,
    crt INTEGER NOT NULL,
    mod INTEGER NOT NULL,
    scm INTEGER NOT NULL,
    ver INTEGER NOT NULL,
    dty INTEGER NOT NULL,
    usn INTEGER NOT NULL,
    ls INTEGER NOT NULL,
    conf TEXT NOT NULL,
    models TEXT NOT NULL,           -- JSON with notetype definitions
    decks TEXT NOT NULL,            -- JSON with deck definitions
    dconf TEXT NOT NULL,
    tags TEXT NOT NULL
);
```

### Basic Notetype Structure (JSON in `col.models`)

```json
{
  "1527489230890": {
    "id": 1527489230890,
    "name": "Basic",
    "type": 0,
    "flds": [
      {"name": "Front", "ord": 0, "sticky": false},
      {"name": "Back", "ord": 1, "sticky": false}
    ],
    "tmpls": [
      {"name": "Card 1", "qfmt": "{{Front}}", "afmt": "{{FrontSide}}<hr id=answer>{{Back}}", "ord": 0},
      {"name": "Card 2", "qfmt": "{{Back}}", "afmt": "{{FrontSide}}<hr id=answer>{{Front}}", "ord": 1}
    ],
    "css": ".card { font-family: arial; font-size: 20px; text-align: center; color: black; background-color: white; }",
    "latexPre": "[latex]",
    "latexPost": "[/latex]"
  }
}
```

### Media JSON Format

```json
{
  "0": "question_img.jpg",
  "1": "answer_img.png",
  "2": "question_audio.mp3",
  "3": "answer_audio.mp3"
}
```

---

## Testing Checklist

**Import Tests:**
- [ ] Import simple Basic deck (no media)
- [ ] Import deck with question images only
- [ ] Import deck with answer images only
- [ ] Import deck with voice recordings (question)
- [ ] Import deck with voice recordings (answer)
- [ ] Import deck with all media types
- [ ] Import deck with nested decks (verify flattening)
- [ ] Import deck with multiple images in one field (verify first used)
- [ ] Import deck with Cloze cards (verify skipped/warned)
- [ ] Import deck with >2 field notetype (verify skipped/warned)

**Export Tests:**
- [ ] Export simple deck to .apkg
- [ ] Import exported .apkg into Anki Desktop
- [ ] Verify cards display correctly in Anki
- [ ] Verify images display in Anki
- [ ] Verify audio plays in Anki
- [ ] Export deck with reversible cards (verify single card exported)
- [ ] Export deck with no media
- [ ] Export deck with all media types

**Round-trip Tests:**
- [ ] Anki → Flash Deck → Anki (compare)
- [ ] Flash Deck → Anki → Flash Deck (compare)

---

## Dependencies

**Required (likely already available):**
- `java.util.zip.*` - ZIP handling
- `android.database.sqlite.*` - SQLite support
- `java.util.regex.*` - HTML parsing
- `java.util.UUID` - GUID generation

**No new external dependencies needed**

---

## Important Notes

- No database schema changes required ✓
- All mapping happens in memory
- Compatible with existing Room database (v11)
- Using existing FileHelper for media storage
- Using RxJava for async operations
- Answer voice IS supported in Flash Deck (FileHelper lines 232-292)
- Multiple images per field: use first image only, log warning
- Reversible cards: export as single card, not two notes
- Deck hierarchy: flatten with " - " separator
- Format selection: simple format parameter in exportFile() method
