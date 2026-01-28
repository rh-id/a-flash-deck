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
  public static SQLiteDatabase createTempDatabase(File dbFile)  // FILE-based, NOT in-memory
  public static void createTables(SQLiteDatabase db)
  public static void insertBasicNotetype(SQLiteDatabase db)
  public static long insertDeck(SQLiteDatabase db, String deckName)
  public static long insertNote(SQLiteDatabase db, String guid, long deckId, long notetypeId, String field1, String field2)
  public static long insertCard(SQLiteDatabase db, long noteId, long deckId, int ordinal)
  public static void insertColData(SQLiteDatabase db, String modelsJson, String decksJson)  // For col table
  public static String createMediaJson(Map<String, Integer> mediaMap)  // JSON string for separate file
  public static File generateApkg(File dbFile, Map<String, File> mediaFiles, String mediaJson, String outputFileName)
  ```

**CRITICAL CHANGES:**
- Use `createTempDatabase(File dbFile)` to create a file-based database, not in-memory
- `insertMediaMapping()` removed - media mapping is a separate ZIP file, not in database
- `createMediaJson()` creates JSON string for the `media` file entry
- `generateApkg()` takes `File dbFile` path and `String mediaJson` separately

---

### Phase 2: Implement AnkiImporter

**2.1 Create `AnkiImporter.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/provider/component/AnkiImporter.java`
- Constructor: Takes `Provider` dependency

**Main Method:**
```java
public List<DeckModel> importApkg(File apkgFile)
```
**Note:** Method returns `List<DeckModel>` directly (not wrapped in RxJava `Single`). The `ExportImportCmd.importFile()` method wraps the call with `Single.fromFuture()` for async execution.

**Algorithm:**
```
1. Validate file is .apkg
2. Parse APKG structure:
   - Extract SQLite database to temp file
   - Extract media files to temp directory
   - Parse media JSON mapping
3. Read Anki data:
   - Read all notetypes, filter for Basic (2 fields)
   - Read all notes that match Basic notetype
   - Read all cards for those notes
   - Read all decks
4. Resolve deck name conflicts:
   - Check each deck name against existing Flash Deck names
   - Auto-rename with suffix: "My Deck" → "My Deck (2)" → "My Deck (3)"
5. Build Flash Deck models:
   For each Anki note:
   a. Parse flds by \x1f → field1, field2
   b. Parse HTML/media from fields:
       - Use androidx.core.text.HtmlCompat.fromHtml() for parsing
      - Decode HTML entities (&nbsp;, &amp;, etc.)
      - Convert <br> tags to \n
      - Extract first <img src="...">
      - Extract [sound:...]
      - Strip HTML tags for plain text
      - Apply Unicode NFC normalization
   c. For each card referencing this note:
      - Create Card entity
      - Map media files
      - Assign to deck (flatten name, use resolved name)
6. Copy media files from temp to FileHelper paths:
   - questionImage → mCardQuestionImageParent
   - answerImage → mCardAnswerImageParent
   - questionVoice → mCardQuestionVoiceParent
   - answerVoice → mCardAnswerVoiceParent
   - Generate thumbnails using FileHelper methods
7. Clean up temp files (try-finally)
8. Return List<DeckModel>
```

**Helper Methods:**
```java
private String parseFieldText(String htmlField)
private String parseFieldImage(String htmlField)
private String parseFieldVoice(String htmlField)
private String flattenDeckName(String ankiDeckName)
private boolean isBasicNotetype(AnkiNotetype notetype)
private String resolveDeckNameConflict(String ankiDeckName)
private void copyMediaToAppPaths(Map<String, File> mediaFiles, List<Card> cards)
private void generateThumbnailsForMedia(List<Card> cards)
```

**HTML Parsing Logic (using android.text.Html):**
```java
// Decode HTML entities and convert to text
Spanned spanned = HtmlCompat.fromHtml(htmlField, HtmlCompat.FROM_HTML_MODE_LEGACY);
String text = spanned.toString();

// Convert line breaks
text = text.replace("<br>", "\n")
           .replace("<br/>", "\n")
           .replace("<br />", "\n");

// Extract first image using regex (more robust pattern)
Pattern imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
Matcher matcher = imgPattern.matcher(htmlField);
String imageName = null;
if (matcher.find()) {
    imageName = matcher.group(1);
}

// Extract sound
Pattern soundPattern = Pattern.compile("\\[sound:([^\\]]+)\\]");
matcher = soundPattern.matcher(htmlField);
String voiceName = null;
if (matcher.find()) {
    voiceName = matcher.group(1);
}

// Unicode NFC normalization
import java.text.Normalizer;
text = Normalizer.normalize(text, Normalizer.Form.NFC);
```

**Deck Flattening:**
```java
private String flattenDeckName(String ankiDeckName) {
    return ankiDeckName.replace("::", " - ");
}
```

**Deck Name Conflict Resolution:**
```java
private String resolveDeckNameConflict(String ankiDeckName) {
    String baseName = flattenDeckName(ankiDeckName);
    String resolvedName = baseName;
    int suffix = 2;
    List<Deck> existingDecks = mDeckDao.getAllDecks();
    Set<String> existingNames = new HashSet<>();
    for (Deck deck : existingDecks) {
        existingNames.add(deck.name);
    }
    while (existingNames.contains(resolvedName)) {
        resolvedName = baseName + " (" + suffix + ")";
        suffix++;
    }
    return resolvedName;
}
```

**Error Handling:**
- Log warnings for:
  - Non-Basic notetypes (skip)
  - Notes with >2 fields (skip)
  - Notes with only 1 field (use empty string for field 2)
  - Cards with missing media files (log, import card without media)
  - Multiple images in one field (use first, log warning)
- Throw exceptions for:
  - Invalid APKG format (not a ZIP)
  - Missing collection.anki21 in ZIP
  - Corrupt database
  - IO errors
- Temporary file cleanup in try-finally block
- Database transactions for bulk operations

---

### Phase 3: Implement AnkiExporter

**3.1 Create `AnkiExporter.java`**
- Location: `app/src/main/java/m/co/rh/id/a_flash_deck/app/provider/component/AnkiExporter.java`
- Constructor: Takes `Provider` dependency

**Main Method:**
```java
public File exportApkg(List<Deck> deckList)
```
**Note:** Method returns `File` directly (not wrapped in RxJava `Single`). The `ExportImportCmd.exportFile()` method wraps the call with `Single.fromFuture()` for async execution, matching the pattern used for AnkiImporter.

**Algorithm:**
```
1. Gather all cards from selected decks using DeckDao
2. Scan for unique media references:
   For each card:
   - If questionImage exists → add to media map with original extension
   - If answerImage exists → add to media map with original extension
   - If questionVoice exists → add to media map
   - If answerVoice exists → add to media map
3. Assign sequential numbers to media files (0, 1, 2, ...)
4. Create SQLite database via ApkgGenerator:
   a. Create temp database file using SQLiteDatabase.openOrCreateDatabase()
   b. Use transaction for bulk operations:
      db.beginTransaction()
      try {
         Insert Basic notetype
         Insert deck records
         For each card:
            - Generate GUID using UUID4().hex()
            - Construct field strings with proper HTML
            - Insert note with flds = field1 + \x1f + field2
            - Insert card record
         Insert col data (models, decks JSON)
         db.setTransactionSuccessful()
      } finally {
         db.endTransaction()
      }
5. Create media JSON string (separate from database):
   - Convert media map to JSON: {"0": "file.jpg", "1": "audio.mp3"}
6. Create ZIP archive:
   - Add collection.anki21 (SQLite database file)
   - Add collection.anki2 (empty/minimal database file for legacy)
   - Add media (JSON mapping string as separate entry at root)
   - Add media files with numeric names at root
7. Clean up temp database file
8. Return ZIP file
```

**Helper Methods:**
```java
private String generateGuid()
private String constructNoteField(String text, String image, String voice)
private Map<String, Integer> buildMediaMap(List<DeckModel> deckModels)
private String createMediaJson(Map<String, Integer> mediaMap)
private void packageMediaFiles(ZipOutputStream zos, Map<String, Integer> mediaMap)
private void copyDatabaseToZip(File dbFile, ZipOutputStream zos)
```

**Note Field Construction:**
```java
private String constructNoteField(String text, String image, String voice) {
    StringBuilder sb = new StringBuilder();
    if (text != null) {
        // Apply Unicode NFC normalization
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        sb.append(text);
    }
    if (image != null) {
        // Preserve original extension (.jpg, .png, .gif, etc.)
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

// Create media JSON string:
//   String mediaJson = "{\"0\":\"photo1.jpg\",\"1\":\"audio.mp3\",\"2\":\"icon.png\"}";

// ZIP structure (EXACT):
//   collection.anki21 (SQLite database file - main)
//   collection.anki2 (empty/minimal database - legacy compat)
//   media (JSON mapping file - SEPARATE entry at root)
//   0 (photo1.jpg file)
//   1 (audio.mp3 file)
//   2 (icon.png file)
```

**Error Handling:**
- Log warnings for:
  - Missing media files (skip, continue, import card without media)
- Throw exceptions for:
  - Failed to create temp database file
  - Failed to populate database
  - Failed to copy database to ZIP
  - Failed to create media JSON
  - Failed to copy media files
  - IO errors
- Use try-finally to clean up temp database file
- Use database transactions for bulk inserts
- AnkiImporter/Exporter methods return values directly, ExportImportCmd wraps with Single.fromFuture() for async execution

---

### Phase 4: Integrate into ExportImportCmd

**4.1 Modify `ExportImportCmd.java`**

**Add new import logic:**
```java
public Single<List<DeckModel>> importFile(File file) {
    // Check for Anki format
    if (file.getName().toLowerCase().endsWith(".apkg")) {
        // AnkiImporter returns List<DeckModel> directly
        // Wrap in Single.fromFuture for async execution
        return Single.fromFuture(mExecutorService.submit(() -> mAnkiImporter.importApkg(file)));
    }

    // Existing logic for native format
    // ... (current implementation)
}
```

**Constructor changes:**
```java
protected AnkiImporter mAnkiImporter;
protected AnkiExporter mAnkiExporter;

public ExportImportCmd(Provider provider) {
    mAppContext = provider.getContext().getApplicationContext();
    mExecutorService = provider.get(ExecutorService.class);
    mLogger = provider.get(ILogger.class);
    mDeckDao = provider.get(DeckDao.class);
    mFileHelper = provider.get(FileHelper.class);
    mAnkiImporter = provider.get(AnkiImporter.class); // Get AnkiImporter from provider
    mAnkiExporter = provider.get(AnkiExporter.class); // Get AnkiExporter from provider
}
```

**Add new export method:**
```java
public Single<File> exportFile(List<Deck> deckList, String format) {
    if ("anki".equals(format)) {
        // AnkiExporter returns File directly
        // Wrap in Single.fromFuture for async execution
        return Single.fromFuture(
                mExecutorService.submit(() -> mAnkiExporter.exportApkg(deckList))
        );
    }
    return exportNativeFile(deckList); // default/native
}

// Rename existing exportFile to exportNativeFile
private Single<File> exportNativeFile(List<Deck> deckList) {
    // ... existing export implementation
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

Add to `app/src/main/res/values/strings.xml` and `app/src/main/res/values-in/strings.xml`:
```xml
<string name="error_invalid_apkg">Not a valid Anki deck file (.apkg)</string>
```

**Note:** The Indonesian translation in `values-in/strings.xml` says "Anki deck file tidak valid (.apkg)".

Additional planned strings (not yet added):
```xml
<string name="msg_importing_anki">Importing Anki deck...</string>
<string name="msg_exporting_anki">Exporting to Anki format...</string>
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
│   ├── model/                                # Anki DTOs (AnkiNote, AnkiCard, AnkiDeck, AnkiNotetype)
│   ├── ApkgParser.java                        # APKG parsing utilities
│   └── ApkgGenerator.java                     # APKG generation utilities
│
├── provider/
│   ├── AppProviderModule.java                 # MODIFIED - register AnkiImporter/Exporter
│   ├── CommandProviderModule.java               # (No changes)
│   └── component/
│       ├── AnkiImporter.java                  # NEW - import logic
│       └── AnkiExporter.java                  # NEW - export logic (Phase 3)
│   └── command/
│       └── ExportImportCmd.java               # MODIFIED - add format detection
│
└── ui/page/
    └── HomePage.java                          # MODIFIED - accept .apkg files (Phase 4)

app/src/main/res/
├── values/strings.xml                         # MODIFIED - add new strings
└── values-in/strings.xml                     # MODIFIED - Indonesian translations

app/src/androidTest/java/m/co/rh/id/a_flash_deck/app/provider/component/
├── AnkiTestDataHelper.java                    # Test data generation utilities
├── AnkiImporterTest.java                      # Import tests (6 scenarios)
├── AnkiExporterTest.java                      # Export tests (3 scenarios)
├── AnkiRoundTripTest.java                     # Round-trip tests (4 scenarios)
└── ApkgParserTest.java                        # Parser tests (12 methods)
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
- [x] Import simple Basic deck (no media)
- [x] Import deck with question images only
- [x] Import deck with answer images only
- [x] Import deck with voice recordings (question)
- [x] Import deck with voice recordings (answer)
- [x] Import deck with all media types
- [x] Import deck with nested decks (verify flattening)
- [ ] Import deck with multiple images in one field (verify first used)
- [ ] Import deck with Cloze cards (verify skipped/warned)
- [ ] Import deck with >2 field notetype (verify skipped/warned)
- [x] Import with duplicate deck names (verify auto-rename with suffix)
- [ ] Import deck with empty name (verify default handling)
- [ ] Import deck with special characters (emoji, unicode)
- [ ] Import deck with very long names
- [ ] Import card with only media (no text)
- [x] Import missing media files (verify card imported, media null)
- [ ] Import HTML with entities (&nbsp;, &amp;, etc.) (verify decoded)
- [ ] Import HTML with line breaks (<br>) (verify converted to \n)
- [x] Verify thumbnails generated for imported images

**Export Tests:**
- [x] Export simple deck to .apkg
- [ ] Import exported .apkg into Anki Desktop
- [ ] Verify cards display correctly in Anki
- [ ] Verify images display in Anki
- [ ] Verify audio plays in Anki
- [ ] Export deck with reversible cards (verify single card exported)
- [ ] Export deck with no media
- [x] Export deck with all media types
- [ ] Export deck with special characters in names
- [ ] Export large deck (1000+ cards) - verify no memory issues
- [x] Verify ZIP structure matches Anki format (collection.anki21, media file at root)
- [x] Verify media JSON is correct format
- [x] Verify database transactions improve performance
- [x] Verify Unicode NFC normalization applied
- [x] Verify image file extensions preserved (.jpg, .png, etc.)

**Round-trip Tests:**
- [x] Anki → Flash Deck → Anki (compare)
- [x] Flash Deck → Anki → Flash Deck (compare)

**Note:** Tests marked [x] are implemented and compile successfully. Instrumented tests require Android device/emulator to run using `./gradlew :app:connectedAndroidTest`. Tests marked [ ] are deferred to user testing.

---

## Dependencies

**Required (likely already available):**
- `java.util.zip.*` - ZIP handling
- `android.database.sqlite.*` - SQLite support
- `androidx.core.text.HtmlCompat` - HTML parsing (AndroidX)
- `java.text.Normalizer` - Unicode normalization (built-in)
- `java.util.UUID` - GUID generation
- `org.json.*` - JSON creation (built-in)

**No new external dependencies needed** ✓

---

## Important Implementation Notes

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
- **Media JSON is separate ZIP file, NOT in database** (CRITICAL)
- **Use file-based SQLite for export, NOT in-memory** (CRITICAL)
- **Use database transactions for bulk operations**
- **Use androidx.core.text.HtmlCompat for HTML parsing, NOT simple regex**
- **Generate thumbnails for imported images using FileHelper**
- **Unicode NFC normalization for all text**
- **Auto-rename deck names with suffix on conflicts**
- **Missing media: import card without media, log warning**
- **Progress: simple loading spinner**
- **Register AnkiImporter/Exporter in AppProviderModule**
- **Clean up temp files with try-finally**
- **Preserve image file extensions** (.jpg, .png, .gif, etc.)

---

## Critical Decisions Summary

| Decision | Choice |
|----------|---------|
| Deck name conflicts | Auto-rename with suffix ("Deck" → "Deck (2)") |
| HTML parsing | Use built-in androidx.core.text.HtmlCompat |
| Missing media | Import card without media, log warning |
| Progress indication | Simple loading spinner |
| Thumbnail generation | Yes, using FileHelper methods |
| Registration location | AppProviderModule.java (not CommandProviderModule) |

---

## Implementation Gotchas

1. **Media JSON Location**: Must be separate file entry in ZIP, NOT in `col` table
2. **SQLite Creation**: Must use file-based database for export, NOT in-memory
3. **HTML Parsing**: Use `androidx.core.text.HtmlCompat.fromHtml()` for proper entity decoding
4. **Image Extensions**: Preserve original extensions (.jpg, .png, etc.), don't force .jpg
5. **Line Breaks**: Convert `<br>`, `<br/>`, `<br />` to `\n`
6. **Unicode**: Always normalize to NFC format
7. **Transactions**: Wrap bulk database operations in transactions
8. **RxJava**: Don't use `blockingGet()`, chain Singles properly
9. **Temp Files**: Clean up with try-finally, not try-catch
10. **Provider Registration**: Must register new commands in CommandProviderModule.java
