# Anki Import/Export Feature - Phase Tracking

## Overview
Implement Anki `.apkg` format import/export compatibility for Flash Deck without modifying the database schema.

## User Requirements
- Import Anki decks (APKG format) → Flash Deck entities
- Export Flash Deck entities → Anki format (APKG)
- No database schema modifications
- Map data at application layer only

## User Decisions

### Deck Name Conflicts
**Decision:** Auto-rename with suffix
- If deck name "My Deck" already exists, create "My Deck (2)"
- If "My Deck (2)" exists, create "My Deck (3)"
- Check against all existing deck names before import

### HTML Parsing
**Decision:** Use built-in `android.text.Html`
- Use `Html.fromHtml()` for HTML stripping
- Use `Html.toHtml()` for basic encoding if needed
- No external libraries

### Missing Media Files
**Decision:** Import without media
- Import card even if referenced media doesn't exist in APKG
- Log warning for missing media
- Continue with null media fields

### Progress Indication
**Decision:** Show simple loading spinner
- Use existing loading UI patterns
- No detailed progress bar (e.g., "Card 500 of 2000")
- User sees spinner during operation

### Thumbnail Generation
**Decision:** Generate thumbnails using FileHelper
- After copying images, call `createCardQuestionImageThumbnail()`
- After copying images, call `createCardAnswerImageThumbnail()`
- Use existing compression (320x180)

## Phases

### Phase 1: Create Anki Package Classes
**Status:** ✅ Completed (with critical fixes)
**Files Created:**
- [x] `app/anki/model/AnkiNote.java` - DTOs for AnkiNote
- [x] `app/anki/model/AnkiCard.java` - DTOs for AnkiCard
- [x] `app/anki/model/AnkiDeck.java` - DTOs for AnkiDeck
- [x] `app/anki/model/AnkiField.java` - DTOs for AnkiField
- [x] `app/anki/model/AnkiNotetype.java` - DTOs for AnkiNotetype
- [x] `app/anki/model/AnkiTemplate.java` - DTOs for AnkiTemplate
- [x] `app/anki/ApkgParser.java` - Parse .apkg files
- [x] `app/anki/ApkgGenerator.java` - Generate .apkg files

**Tasks Completed:**
- [x] Define all Anki model classes with proper fields
- [x] Add missing fields to AnkiNote (csum), AnkiCard (mod, usn, type, queue, left, odue, odid, flags, data), AnkiDeck (usn, common, kind)
- [x] Implement ZIP extraction and SQLite database reading
- [x] Add UTF-8 encoding to JSON parsing
- [x] Fix regex to exclude subdirectories in media extraction
- [x] Implement media file extraction and JSON parsing
- [x] Implement file-based database creation (not in-memory)
- [x] Fix ID generation to avoid collisions (add random offset)
- [x] Add Card 2 template to Basic notetype
- [x] Add latexPre/latexPost fields to Basic notetype
- [x] Implement database population methods with transactions
- [x] Implement ZIP packaging for .apkg generation

**Critical Fixes Applied:**
1. Added missing `csum` column to readNotes() - prevents crash
2. Added Card 2 template to insertBasicNotetype() - required by Anki spec
3. Added latexPre/latexPost fields - required by Anki spec
4. Fixed UTF-8 encoding in JSON parsing - prevents corruption
5. Fixed media file extraction regex - prevents directory traversal
6. Fixed all missing fields in model classes - prevents data loss
7. Fixed ID generation - prevents collisions on rapid operations

**Completion Criteria:** All parser/generator classes created, reviewed, and fixed

---

### Phase 2: Implement AnkiImporter
**Status:** ⬜ Not Started
**Files to Create:**
- [ ] `app/provider/command/AnkiImporter.java`

**Files to Modify:**
- [ ] `app/provider/command/ExportImportCmd.java`
- [ ] `app/provider/CommandProviderModule.java`

**Tasks:**
- [ ] Implement importApkg() main method (RxJava Single)
- [ ] Implement field parsing using android.text.Html
- [ ] Implement HTML entity decoding
- [ ] Implement line break conversion (<br> → \n)
- [ ] Implement Unicode NFC normalization
- [ ] Implement deck name flattening (:: → - )
- [ ] Implement deck name conflict resolution with suffix
- [ ] Implement media file copying to FileHelper paths
- [ ] Implement thumbnail generation for imported images
- [ ] Implement Basic notetype detection
- [ ] Implement error handling and validation
- [ ] Add format detection in ExportImportCmd
- [ ] Register AnkiImporter in CommandProviderModule

**Completion Criteria:** Can import simple Anki deck and cards display correctly

---

### Phase 3: Implement AnkiExporter
**Status:** ⬜ Not Started
**Files to Create:**
- [ ] `app/provider/command/AnkiExporter.java`

**Files to Modify:**
- [ ] `app/provider/command/ExportImportCmd.java`
- [ ] `app/provider/CommandProviderModule.java`

**Tasks:**
- [ ] Implement exportApkg() main method (RxJava Single)
- [ ] Implement media scanning and sequential numbering
- [ ] Implement note field construction (text + img + sound tags)
- [ ] Implement GUID generation using UUID
- [ ] Implement database population with transactions
- [ ] Implement media JSON file creation (separate from DB)
- [ ] Implement ZIP packaging with proper structure
- [ ] Add export format parameter to ExportImportCmd
- [ ] Register AnkiExporter in CommandProviderModule

**Completion Criteria:** Can export Flash Deck to .apkg and import into Anki Desktop

---

### Phase 4: Update UI Components
**Status:** ⬜ Not Started
**Files to Modify:**
- [ ] `app/ui/page/HomePage.java`
- [ ] `app/res/values/strings.xml`

**Tasks:**
- [ ] Update file picker to accept .apkg files
- [ ] Add string resources for Anki operations
- [ ] Add warning messages for unsupported cards
- [ ] Add progress messages for import/export

**Completion Criteria:** User can import/export Anki decks from UI

---

### Phase 5: Testing & Bug Fixes
**Status:** ⬜ Not Started
**Tasks:**
- [ ] Test import of simple Basic deck (no media)
- [ ] Test import of deck with question images
- [ ] Test import of deck with answer images
- [ ] Test import of deck with question voice
- [ ] Test import of deck with answer voice
- [ ] Test import of deck with all media types
- [ ] Test import of nested decks (flattening)
- [ ] Test import of multiple images in one field (first used)
- [ ] Test import of Cloze cards (skipped with warning)
- [ ] Test import of >2 field notetype (skipped with warning)
- [ ] Test import with duplicate deck names (auto-rename)
- [ ] Test import of deck with empty name
- [ ] Test import of deck with special characters (emoji, unicode)
- [ ] Test import of deck with very long names
- [ ] Test import of card with only media (no text)
- [ ] Test import of missing media files (card without media)
- [ ] Test export simple deck
- [ ] Test export deck with media
- [ ] Test export deck with reversible cards
- [ ] Test export of deck with special characters
- [ ] Test export of large deck (1000+ cards) for memory
- [ ] Test round-trip: Anki → Flash Deck → Anki
- [ ] Test round-trip: Flash Deck → Anki → Flash Deck
- [ ] Verify card ordering preserved in round-trip
- [ ] Fix any discovered bugs

**Completion Criteria:** All test cases pass

---

### Phase 6: Documentation & Cleanup
**Status:** ⬜ Not Started
**Tasks:**
- [ ] Add inline code comments for complex logic
- [ ] Update README with Anki compatibility notes
- [ ] Clean up temporary files in code (try-finally)
- [ ] Final code review
- [ ] Verify no memory leaks in image processing

**Completion Criteria:** Code is production-ready

---

## Progress Summary

| Phase | Status | % Complete |
|-------|--------|------------|
| Phase 1 | ✅ Completed | 100% |
| Phase 2 | ⬜ Not Started | 0% |
| Phase 3 | ⬜ Not Started | 0% |
| Phase 4 | ⬜ Not Started | 0% |
| Phase 5 | ⬜ Not Started | 0% |
| Phase 6 | ⬜ Not Started | 0% |
| **Overall** | **🔄 In Progress** | **17%** |

---

## Notes

- No database schema changes required
- All mapping happens in memory
- Compatible with existing Room database (v11)
- Using existing FileHelper for media storage
- Using RxJava for async operations
- Media JSON is separate ZIP file, NOT in database
- Use file-based SQLite for export (not in-memory)
- Use database transactions for bulk operations
- Generate thumbnails for imported images
- Use Unicode NFC normalization for text
- Deck name conflicts auto-resolve with suffix
- Missing media: import card without media, log warning
- HTML parsing: use android.text.Html (built-in)
- Progress: simple loading spinner only
