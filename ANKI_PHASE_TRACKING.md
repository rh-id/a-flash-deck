# Anki Import/Export Feature - Phase Tracking

## Overview
Implement Anki `.apkg` format import/export compatibility for Flash Deck without modifying the database schema.

## User Requirements
- Import Anki decks (APKG format) → Flash Deck entities
- Export Flash Deck entities → Anki format (APKG)
- No database schema modifications
- Map data at application layer only

## Phases

### Phase 1: Create Anki Package Classes
**Status:** ⬜ Not Started
**Files to Create:**
- [ ] `app/anki/AnkiModels.java` - DTOs for Anki data structures
- [ ] `app/anki/ApkgParser.java` - Parse .apkg files
- [ ] `app/anki/ApkgGenerator.java` - Generate .apkg files

**Tasks:**
- [ ] Define AnkiNote, AnkiCard, AnkiDeck, AnkiNotetype classes
- [ ] Implement ZIP extraction and SQLite database reading
- [ ] Implement media file extraction and JSON parsing
- [ ] Implement in-memory database creation with Anki schema
- [ ] Implement database population methods
- [ ] Implement ZIP packaging for .apkg generation

**Completion Criteria:** All parser/generator classes created and tested with unit tests

---

### Phase 2: Implement AnkiImporter
**Status:** ⬜ Not Started
**Files to Create:**
- [ ] `app/provider/command/AnkiImporter.java`

**Files to Modify:**
- [ ] `app/provider/command/ExportImportCmd.java`

**Tasks:**
- [ ] Implement importApkg() main method
- [ ] Implement field parsing (text, image, voice from HTML)
- [ ] Implement deck name flattening (:: → - )
- [ ] Implement media file copying to FileHelper paths
- [ ] Implement Basic notetype detection
- [ ] Implement error handling and validation
- [ ] Add format detection in ExportImportCmd

**Completion Criteria:** Can import simple Anki deck and cards display correctly

---

### Phase 3: Implement AnkiExporter
**Status:** ⬜ Not Started
**Files to Create:**
- [ ] `app/provider/command/AnkiExporter.java`

**Files to Modify:**
- [ ] `app/provider/command/ExportImportCmd.java`

**Tasks:**
- [ ] Implement exportApkg() main method
- [ ] Implement media scanning and sequential numbering
- [ ] Implement note field construction (text + img + sound tags)
- [ ] Implement GUID generation
- [ ] Implement database population
- [ ] Implement ZIP packaging
- [ ] Add export format parameter to ExportImportCmd

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
- [ ] Test import of multiple images per field (first used)
- [ ] Test import of Cloze cards (skipped with warning)
- [ ] Test import of >2 field notetypes (skipped with warning)
- [ ] Test export simple deck
- [ ] Test export deck with media
- [ ] Test export deck with reversible cards
- [ ] Test round-trip: Anki → Flash Deck → Anki
- [ ] Test round-trip: Flash Deck → Anki → Flash Deck
- [ ] Fix any discovered bugs

**Completion Criteria:** All test cases pass

---

### Phase 6: Documentation & Cleanup
**Status:** ⬜ Not Started
**Tasks:**
- [ ] Add inline code comments for complex logic
- [ ] Update README with Anki compatibility notes
- [ ] Clean up temporary files in code
- [ ] Final code review

**Completion Criteria:** Code is production-ready

---

## Progress Summary

| Phase | Status | % Complete |
|-------|--------|------------|
| Phase 1 | ⬜ Not Started | 0% |
| Phase 2 | ⬜ Not Started | 0% |
| Phase 3 | ⬜ Not Started | 0% |
| Phase 4 | ⬜ Not Started | 0% |
| Phase 5 | ⬜ Not Started | 0% |
| Phase 6 | ⬜ Not Started | 0% |
| **Overall** | **⬜ Not Started** | **0%** |

---

## Notes

- No database schema changes required
- All mapping happens in memory
- Compatible with existing Room database (v11)
- Using existing FileHelper for media storage
- Using RxJava for async operations
