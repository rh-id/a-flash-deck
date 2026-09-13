#!/usr/bin/env python3
"""Build and fix importable deck files for the Flash Deck Android app.

Author flashcard decks on a computer (CSV/TSV or JSON) and package them into
the app's native import format: a ZIP containing an entry named exactly
``Decks.json`` (the app scans all entries; the name match is case-sensitive)
with a JSON array of deck objects on a single line (single line is only
required for app releases up to and including 2.0.0, which parse only the
first line; the script keeps producing single-line output for maximum
compatibility with all versions).

Subcommands:
  build  Create an importable deck file from a CSV/TSV file or a JSON file.
  fix    Rewrite a pretty-printed / multi-line deck JSON as a single line
         (or package it into a ZIP).

Python 3.9+, standard library only. Cross-platform.

Format documentation: docs/deck-json-schema.md
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import sys
import time
import zipfile
from pathlib import Path
from typing import Dict, List, NoReturn, Optional, Tuple

# Must match ExportImportCmd.java (app module).
DECKS_JSON_ENTRY = "Decks.json"  # exact, case-sensitive
SERIAL_VERSION_UID = -8121772616636312403  # informational; never read on import

# Media card field -> ZIP directory prefix (must match ExportImportCmd.java).
MEDIA_DIRS = {
    "questionImage": "media/image/question/",
    "answerImage": "media/image/answer/",
    "questionVoice": "media/voice/question/",
    "answerVoice": "media/voice/answer/",
}
MEDIA_FIELDS = tuple(MEDIA_DIRS)
MEDIA_PREFIXES = tuple(MEDIA_DIRS.values())

CSV_REQUIRED_COLUMNS = ("question", "answer")
CSV_OPTIONAL_COLUMNS = ("reversible",) + MEDIA_FIELDS
AUTHORING_CARD_KEYS = ("question", "answer", "isReversibleQA") + MEDIA_FIELDS

# Fixed ZIP entry timestamps so the container metadata is deterministic
# (the JSON payload still embeds the build time in createdDateTime/updatedDateTime).
ZIP_DATE_TIME = (2026, 1, 1, 0, 0, 0)


def warn(message: str) -> None:
    print(f"warning: {message}", file=sys.stderr)


def fail(message: str) -> NoReturn:
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def type_name(value: object) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if isinstance(value, list):
        return "array"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


# ---------------------------------------------------------------------------
# Input reading
# ---------------------------------------------------------------------------

def read_text(path: Path) -> str:
    """Read a text file as UTF-8, tolerating an Excel BOM."""
    try:
        return path.read_text(encoding="utf-8-sig")
    except UnicodeDecodeError as exc:
        fail(f"{path}: input is not valid UTF-8: {exc}")
    except OSError as exc:
        fail(f"{path}: cannot read input file: {exc}")


def parse_json(text: str, source: str) -> object:
    try:
        return json.loads(text)
    except ValueError as exc:
        fail(f"{source}: invalid JSON: {exc}")


def input_is_json(path: Path, text: str) -> bool:
    """Decide between JSON and CSV/TSV by extension, then by content."""
    suffix = path.suffix.lower()
    if suffix == ".json":
        return True
    if suffix in (".csv", ".tsv", ".txt"):
        return False
    return text.lstrip().startswith("[")


# ---------------------------------------------------------------------------
# CSV input
# ---------------------------------------------------------------------------

def sniff_delimiter(path: Path, text: str) -> str:
    sample = text[:4096]
    try:
        return csv.Sniffer().sniff(sample, delimiters=",\t;").delimiter
    except csv.Error:
        return "\t" if path.suffix.lower() == ".tsv" else ","


def csv_to_authoring(text: str, path: Path, deck_name: str) -> List[dict]:
    """Convert CSV/TSV text to authoring-format decks (one deck)."""
    if not text.strip():
        fail(f"{path}: input file is empty")
    delimiter = sniff_delimiter(path, text)
    reader = csv.reader(io.StringIO(text, newline=""), delimiter=delimiter)
    try:
        header = next(reader)
    except StopIteration:
        fail(f"{path}: input file is empty (a header row is required)")

    column_index: Dict[str, int] = {}
    unknown_columns: List[str] = []
    for index, column in enumerate(header):
        normalized = column.strip().lower()
        if normalized in CSV_REQUIRED_COLUMNS or normalized in CSV_OPTIONAL_COLUMNS:
            column_index[normalized] = index
        elif column.strip():
            unknown_columns.append(column.strip())
    if unknown_columns:
        warn(f"{path}: ignoring unknown column(s): {', '.join(unknown_columns)}")
    missing = [c for c in CSV_REQUIRED_COLUMNS if c not in column_index]
    if missing:
        fail(f"{path}: missing required column(s): {', '.join(missing)}. "
             f"The header row must contain 'question' and 'answer' "
             f"(optional: {', '.join(CSV_OPTIONAL_COLUMNS)}).")

    def cell(row: List[str], column: str) -> str:
        index = column_index.get(column)
        if index is None or index >= len(row):
            return ""
        return row[index].strip()

    cards: List[dict] = []
    for row in reader:
        if not row or all(not c.strip() for c in row):
            continue  # skip blank lines
        line = reader.line_num
        question = cell(row, "question")
        answer = cell(row, "answer")
        if not question or not answer:
            fail(f"{path}: line {line}: 'question' and 'answer' are required "
                 f"(got question={question!r}, answer={answer!r})")
        reversible_raw = cell(row, "reversible")
        if reversible_raw == "":
            reversible = False
        else:
            reversible_lower = reversible_raw.lower()
            if reversible_lower == "true":
                reversible = True
            elif reversible_lower == "false":
                reversible = False
            else:
                fail(f"{path}: line {line}: invalid reversible value "
                     f"{reversible_raw!r} (expected true or false)")
        card = {"question": question, "answer": answer, "isReversibleQA": reversible}
        for field in MEDIA_FIELDS:
            card[field] = cell(row, field)
        cards.append(card)
    return [{"name": deck_name, "cards": cards}]


# ---------------------------------------------------------------------------
# Authoring JSON input
# ---------------------------------------------------------------------------

def require_string(obj: dict, key: str, context: str, allow_empty: bool = False) -> str:
    if key not in obj:
        fail(f"{context}: '{key}' is required")
    value = obj[key]
    if not isinstance(value, str):
        fail(f"{context}: '{key}' must be a string, got {type_name(value)}")
    if not allow_empty and not value.strip():
        fail(f"{context}: '{key}' must not be empty")
    return value


def validate_authoring(data: object, source: str) -> List[dict]:
    """Validate the authoring JSON format and return normalized decks."""
    if not isinstance(data, list):
        fail(f"{source}: authoring format must be a JSON array of deck objects, "
             f"got {type_name(data)}. Expected e.g. "
             f'[{{"name": "Deck name", "cards": [{{"question": "...", "answer": "..."}}]}}]')
    decks: List[dict] = []
    for deck_index, deck in enumerate(data, 1):
        context = f"{source}: deck #{deck_index}"
        if not isinstance(deck, dict):
            fail(f"{context}: must be a JSON object, got {type_name(deck)}")
        name = require_string(deck, "name", context)
        context = f"{source}: deck '{name}'"
        raw_cards = deck.get("cards", [])
        if not isinstance(raw_cards, list):
            fail(f"{context}: 'cards' must be an array, got {type_name(raw_cards)}")
        cards: List[dict] = []
        for card_index, raw_card in enumerate(raw_cards, 1):
            card_context = f"{context} card #{card_index}"
            if not isinstance(raw_card, dict):
                fail(f"{card_context}: must be a JSON object, got {type_name(raw_card)}")
            card = {
                "question": require_string(raw_card, "question", card_context),
                "answer": require_string(raw_card, "answer", card_context),
                "isReversibleQA": raw_card.get("isReversibleQA", False),
            }
            if not isinstance(card["isReversibleQA"], bool):
                fail(f"{card_context}: 'isReversibleQA' must be a boolean, "
                     f"got {type_name(card['isReversibleQA'])}")
            for field in MEDIA_FIELDS:
                card[field] = require_string(raw_card, field, card_context,
                                             allow_empty=True) if field in raw_card else ""
            unknown = sorted(k for k in raw_card if k not in AUTHORING_CARD_KEYS)
            if unknown:
                warn(f"{card_context}: ignoring unknown field(s): {', '.join(unknown)}")
            cards.append(card)
        decks.append({"name": name, "cards": cards})
    return decks


# ---------------------------------------------------------------------------
# Deck-format (native export) JSON
# ---------------------------------------------------------------------------

def is_full_deck_format(data: object) -> bool:
    return (isinstance(data, list) and len(data) > 0
            and isinstance(data[0], dict) and "deck" in data[0])


def classify_deck_json(data: object, source: str) -> str:
    """Return 'full' (native deck format) or 'authoring'."""
    if not isinstance(data, list):
        fail(f"{source}: expected a JSON array of deck objects, got {type_name(data)}")
    with_deck = [isinstance(item, dict) and "deck" in item for item in data]
    if all(with_deck):
        return "full"
    if not any(with_deck):
        return "authoring"
    fail(f"{source}: mixed input: some deck objects have a 'deck' key and some "
         f"do not. Provide either the native deck format (objects with 'deck') "
         f"or the authoring format (objects with 'name' and 'cards'), not both.")


def validate_full_deck(data: object, source: str) -> None:
    """Minimal validation of native deck-format JSON. Values are not altered."""
    if not isinstance(data, list):
        fail(f"{source}: top-level JSON value must be an array of deck objects, "
             f"got {type_name(data)}")
    for deck_index, deck_model in enumerate(data, 1):
        context = f"{source}: deck #{deck_index}"
        if not isinstance(deck_model, dict):
            fail(f"{context}: must be a JSON object, got {type_name(deck_model)}")
        deck = deck_model.get("deck")
        if not isinstance(deck, dict):
            fail(f"{context}: 'deck' object is required, got {type_name(deck)}")
        for key in ("id", "name"):
            if key not in deck:
                fail(f"{context}: 'deck.{key}' is required")
        if not isinstance(deck["name"], str):
            fail(f"{context}: 'deck.name' must be a string, got {type_name(deck['name'])}")
        for key in ("createdDateTime", "updatedDateTime"):
            if key not in deck:
                fail(f"{context}: 'deck.{key}' is required")
            if not isinstance(deck[key], str):
                fail(f"{context}: 'deck.{key}' must be a string containing epoch "
                     f"milliseconds, got {type_name(deck[key])}")
        card_list = deck_model.get("cardList", [])
        if not isinstance(card_list, list):
            fail(f"{context}: 'cardList' must be an array, got {type_name(card_list)}")
        for card_index, card in enumerate(card_list, 1):
            card_context = f"{context} card #{card_index}"
            if not isinstance(card, dict):
                fail(f"{card_context}: must be a JSON object, got {type_name(card)}")
            for key in ("question", "answer"):
                if key not in card:
                    fail(f"{card_context}: '{key}' is required")
                if not isinstance(card[key], str):
                    fail(f"{card_context}: '{key}' must be a string, got {type_name(card[key])}")


def count_media_fields(deck_list: List[dict]) -> int:
    return sum(
        1
        for deck_model in deck_list
        for card in deck_model.get("cardList", [])
        for field in MEDIA_FIELDS
        if card.get(field)
    )


# ---------------------------------------------------------------------------
# Media bundling
# ---------------------------------------------------------------------------

def collect_media(field: str, raw_path: str, media_sources: Dict[str, Path],
                  context: str) -> str:
    """Register a local media file for bundling; return its zip-internal name."""
    source = Path(raw_path)
    if not source.is_file():
        fail(f"{context}: media file not found: {raw_path}")
    entry_name = MEDIA_DIRS[field] + source.name
    existing = media_sources.get(entry_name)
    if existing is None:
        media_sources[entry_name] = source
    elif existing.resolve() != source.resolve():
        fail(f"{context}: two different files share the media name "
             f"'{source.name}': '{existing}' and '{source}'. Rename one of them.")
    return source.name


def authoring_to_deck_models(decks: List[dict], output_kind: str,
                             source: str) -> Tuple[List[dict], Dict[str, Path]]:
    """Convert authoring decks to native deck models with generated ids.

    Deck ids are 1..n, card ids are 1..m per deck, ordinals are 0-based.
    All ids are discarded and reassigned by the app on import.
    """
    now_millis = str(int(time.time() * 1000))
    media_sources: Dict[str, Path] = {}
    warned_media: set = set()
    deck_models: List[dict] = []
    for deck_index, deck in enumerate(decks, 1):
        cards_out: List[dict] = []
        for card_index, card in enumerate(deck["cards"], 1):
            card_context = f"{source}: deck '{deck['name']}' card #{card_index}"
            card_out = {
                "id": card_index,
                "deckId": deck_index,
                "ordinal": card_index - 1,
                "question": card["question"],
                "questionImage": "",
                "questionVoice": "",
                "answer": card["answer"],
                "answerImage": "",
                "answerVoice": "",
                "isReversibleQA": card["isReversibleQA"],
                "isReversed": False,
            }
            for field in MEDIA_FIELDS:
                raw_path = card[field]
                if not raw_path:
                    continue
                if output_kind == "json":
                    if raw_path not in warned_media:
                        warned_media.add(raw_path)
                        warn(f"{source}: media file '{raw_path}' is referenced but a "
                             f"plain .json output cannot carry media files; use an "
                             f"--output ending in .zip to bundle media")
                    card_out[field] = raw_path
                    continue
                card_out[field] = collect_media(field, raw_path, media_sources,
                                                card_context)
            cards_out.append(card_out)
        deck_model = {
            "serialVersionUID": SERIAL_VERSION_UID,
            "deck": {
                "id": deck_index,
                "name": deck["name"],
                "createdDateTime": now_millis,
                "updatedDateTime": now_millis,
            },
        }
        if cards_out:
            deck_model["cardList"] = cards_out
        deck_models.append(deck_model)
    return deck_models, media_sources


# ---------------------------------------------------------------------------
# Output writing
# ---------------------------------------------------------------------------

def dumps_single_line(data: object) -> str:
    """Serialize as the one-line JSON the app's importer requires."""
    return json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n"


def output_kind(path: Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".zip":
        return "zip"
    if suffix == ".json":
        return "json"
    fail(f"{path}: output file name must end with .zip or .json")


def make_zip_info(entry_name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(entry_name, date_time=ZIP_DATE_TIME)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    return info


def write_output(deck_list: List[dict], media_sources: Dict[str, Path],
                 output_path: Path, kind: str) -> None:
    line = dumps_single_line(deck_list).encode("utf-8")
    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        if kind == "json":
            output_path.write_bytes(line)
            return
        # The importer scans all entries for the exact, case-sensitive name
        # "Decks.json"; we write it first to mirror the app's own exporter.
        with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
            zf.writestr(make_zip_info(DECKS_JSON_ENTRY), line)
            for entry_name in sorted(media_sources):
                zf.writestr(make_zip_info(entry_name),
                            media_sources[entry_name].read_bytes())
    except OSError as exc:
        fail(f"{output_path}: cannot write output file: {exc}")


def print_summary(label: str, deck_list: List[dict], media_count: int,
                  output_path: Path) -> None:
    data = output_path.read_bytes()
    card_total = sum(len(d.get("cardList", [])) for d in deck_list)
    print(f"{label} OK")
    print(f"  decks  : {len(deck_list)}")
    print(f"  cards  : {card_total}")
    print(f"  media  : {media_count}")
    print(f"  output : {output_path}")
    print(f"  sha256 : {hashlib.sha256(data).hexdigest()}")


# ---------------------------------------------------------------------------
# Subcommands
# ---------------------------------------------------------------------------

def run_build(args: argparse.Namespace) -> None:
    input_path = Path(args.input)
    output_path = Path(args.output)
    if not input_path.is_file():
        fail(f"input file not found: {input_path}")
    kind = output_kind(output_path)
    text = read_text(input_path)
    source = str(input_path)

    if input_is_json(input_path, text):
        data = parse_json(text, source)
        format_name = classify_deck_json(data, source)
        if format_name == "full":
            # Already native deck format: validate, then minify and package.
            validate_full_deck(data, source)
            deck_list = data
            media_sources: Dict[str, Path] = {}
            media_count = count_media_fields(deck_list)
        else:
            authoring = validate_authoring(data, source)
            deck_list, media_sources = authoring_to_deck_models(authoring, kind, source)
            media_count = len(media_sources) if kind == "zip" else count_media_fields(deck_list)
    else:
        deck_name = args.name if args.name else input_path.stem
        authoring = csv_to_authoring(text, input_path, deck_name)
        deck_list, media_sources = authoring_to_deck_models(authoring, kind, source)
        media_count = len(media_sources) if kind == "zip" else count_media_fields(deck_list)

    if not deck_list:
        warn(f"{source}: input contains 0 decks; the app will import nothing")

    write_output(deck_list, media_sources, output_path, kind)
    if args.verbose:
        print_summary("BUILD", deck_list, media_count, output_path)


def run_fix(args: argparse.Namespace) -> None:
    input_path = Path(args.input)
    output_path = Path(args.output)
    if not input_path.is_file():
        fail(f"input file not found: {input_path}")
    kind = output_kind(output_path)
    source = str(input_path)

    media_entries: Dict[str, bytes] = {}
    if input_path.suffix.lower() == ".zip":
        try:
            with zipfile.ZipFile(input_path) as zf:
                names = zf.namelist()
                if DECKS_JSON_ENTRY not in names:
                    fail(f"{source}: the ZIP does not contain an entry named exactly "
                         f"'{DECKS_JSON_ENTRY}' (case-sensitive). Entries found: "
                         f"{', '.join(names) if names else 'none'}. Without that "
                         f"entry the app silently imports 0 decks.")
                raw = zf.read(DECKS_JSON_ENTRY)
                for name in names:
                    if (name != DECKS_JSON_ENTRY and not name.endswith("/")
                            and name.startswith(MEDIA_PREFIXES)):
                        media_entries[name] = zf.read(name)
        except zipfile.BadZipFile as exc:
            fail(f"{source}: not a valid ZIP file: {exc}")
        except OSError as exc:
            fail(f"{source}: cannot read input file: {exc}")
    else:
        raw = read_text(input_path).encode("utf-8")

    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, ValueError) as exc:
        fail(f"{source}: invalid deck JSON: {exc}")
    validate_full_deck(data, source)

    line = dumps_single_line(data).encode("utf-8")
    try:
        output_path.parent.mkdir(parents=True, exist_ok=True)
        if kind == "json":
            output_path.write_bytes(line)
        else:
            with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
                zf.writestr(make_zip_info(DECKS_JSON_ENTRY), line)
                for entry_name in sorted(media_entries):
                    zf.writestr(make_zip_info(entry_name), media_entries[entry_name])
    except OSError as exc:
        fail(f"{output_path}: cannot write output file: {exc}")

    if args.verbose:
        print_summary("FIX", data, len(media_entries), output_path)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: Optional[List[str]] = None) -> None:
    parser = argparse.ArgumentParser(
        prog="build_deck.py",
        description="Build and fix importable deck files for the Flash Deck "
                    "Android app (a ZIP containing an entry named exactly "
                    "Decks.json with a minified single-line JSON array of decks).",
        epilog="Examples:\n"
               "  python build_deck.py build --input cards.csv --name Spanish --output Decks.zip\n"
               "  python build_deck.py build --input decks.json --output Decks.zip\n"
               "  python build_deck.py fix --input Decks-pretty.json --output Decks.json",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--verbose", action="store_true",
                        help="print a summary (decks, cards, media count, output "
                             "path, sha256 of the output)")

    parser_build = subparsers.add_parser(
        "build", parents=[common],
        help="create an importable deck file from CSV/TSV or JSON")
    parser_build.add_argument(
        "--input", required=True,
        help="input file: CSV/TSV with 'question' and 'answer' columns (optional: "
             "'reversible', 'questionImage', 'questionVoice', 'answerImage', "
             "'answerVoice'); a JSON array of {\"name\", \"cards\"} authoring "
             "decks; or an existing deck-format JSON array to minify and package")
    parser_build.add_argument(
        "--name", help="deck name for CSV/TSV input (default: input file name "
                       "without extension)")
    parser_build.add_argument(
        "--output", required=True,
        help="output file: *.zip produces the importable ZIP (bundles referenced "
             "media under media/...); *.json produces the plain single-line "
             "Decks.json (media files cannot be carried)")
    parser_build.set_defaults(func=run_build)

    parser_fix = subparsers.add_parser(
        "fix", parents=[common],
        help="rewrite a pretty-printed / multi-line deck JSON as a single "
             "line (only required for app releases up to and including 2.0.0, "
             "which parse only the first line; kept for maximum compatibility "
             "with all versions)")
    parser_fix.add_argument(
        "--input", required=True,
        help="deck-format JSON file (any whitespace is fine) or a ZIP containing "
             "Decks.json (media entries are carried over to a .zip output)")
    parser_fix.add_argument(
        "--output", required=True,
        help="output file: *.json single-line deck JSON, or *.zip importable ZIP")
    parser_fix.set_defaults(func=run_fix)

    args = parser.parse_args(argv)
    args.func(args)


if __name__ == "__main__":
    main()
