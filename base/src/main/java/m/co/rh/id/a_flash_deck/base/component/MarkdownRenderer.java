/*
 *     Copyright (C) 2021-present Ruby Hartono
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package m.co.rh.id.a_flash_deck.base.component;

import android.content.Context;
import android.text.Spanned;
import android.text.util.Linkify;
import android.util.LruCache;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.aprovider.Provider;

/**
 * Renders card question/answer text as Markdown, with LaTeX math and clickable links.
 *
 * <p>Math delimiters follow Anki conventions: {@code \(...\)} for inline math and
 * {@code \[...\]} for display/block math. These are translated to Markwon's
 * {@code $...$} / {@code $$...$$} internally right before rendering, because the
 * underlying JLatexMathPlugin only understands the dollar forms. Storing text in
 * the Anki-native form keeps {@code .apkg} import/export transparent (no
 * delimiter translation needed at those boundaries) and avoids currency
 * conflicts that dollar signs would cause (e.g. {@code $5}).
 *
 * <p>Literal {@code $} characters (currency, etc.) are escaped before the
 * delimiter translation so they are never interpreted as math.
 *
 * <p>The {@link Markwon} instance is thread-safe (since 4.1.1) and shared app-wide.
 * LaTeX formulas render asynchronously on a background executor.
 *
 * <p>Markdown parsing (commonmark-java) is CPU work, so callers should prefer the
 * async APIs ({@link #toPlainTextAsync(String)} and {@link #parseAsync(String)}),
 * which run on the app-wide shared {@link ExecutorService} and apply the result on
 * the main thread. Plain-text results are memoized in an {@link LruCache} so
 * scroll-back over previously-seen rows is instant. Parsed {@link Spanned} is NOT
 * cached: latex/image {@code AsyncDrawableSpans} are tied to a specific TextView
 * and must not be reused across views.
 *
 * <p>This class also owns the voice path: {@link #toSpeakableText(String)}
 * decides what a text-to-speech engine should say. The rendering APIs above
 * serve the eyes (math becomes formulas, images become drawables), while the
 * speech path drops math and images outright, keeps currency dollars intact,
 * and is plain conservative synchronous regex rather than the Markwon
 * pipeline.
 */
public class MarkdownRenderer {

    /**
     * Matches Anki display math {@code \[...\]}. DOTALL so multi-line blocks match.
     * Captures the inner latex source as group 1.
     */
    private static final Pattern DISPLAY_MATH =
            Pattern.compile("\\\\\\[(.*?)\\\\\\]", Pattern.DOTALL);

    /**
     * Matches Anki inline math {@code \(...\)}. DOTALL for consistency.
     * Captures the inner latex source as group 1.
     */
    private static final Pattern INLINE_MATH =
            Pattern.compile("\\\\\\((.*?)\\\\\\)", Pattern.DOTALL);

    // ---- speech path ({@link #toSpeakableText}) patterns, prefixed SPEECH_
    // where they would otherwise clash with the render patterns above ----

    /** Images {@code ![alt](url)}: dropped entirely (alt text is not spoken). */
    private static final Pattern IMAGES =
            Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");

    /** Inline links {@code [text](url)}: keep only the link text. */
    private static final Pattern LINKS =
            Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");

    /** Autolinks {@code <https://...>}: keep the bare URL as the text. */
    private static final Pattern AUTOLINKS =
            Pattern.compile("<(https?://[^>\\s]+)>");

    /**
     * Fenced code blocks: drop the {@code ```} fence lines, keep the inner
     * code text. DOTALL lets the body span lines, MULTILINE anchors the
     * fences at line starts.
     */
    private static final Pattern FENCED_CODE = Pattern.compile(
            "^[ \\t]{0,3}`{3,}[^`\\n]*\\n(.*?)^[ \\t]{0,3}`{3,}[ \\t]*$",
            Pattern.MULTILINE | Pattern.DOTALL);

    /** Inline code {@code `code`}: keep the code text, drop the backticks. */
    private static final Pattern INLINE_CODE =
            Pattern.compile("`([^`\\n]+)`");

    /** Display math {@code $$...$$}: dropped (speaking math is noise). */
    private static final Pattern SPEECH_DISPLAY_MATH =
            Pattern.compile("\\$\\$(.+?)\\$\\$", Pattern.DOTALL);

    /**
     * Inline math {@code $...$}: dropped. Conservative dollar handling so
     * currency never matches: the content must not span lines, must not
     * contain another {@code $}, and no whitespace may sit directly inside
     * either delimiter ({@code $5 and $10} therefore stays intact).
     */
    private static final Pattern SPEECH_INLINE_MATH =
            Pattern.compile("\\$(?!\\s)([^$\\n]+?)(?<!\\s)\\$");

    /** Anki block math {@code \\[...\\]}: dropped (see MarkdownRenderer). */
    private static final Pattern SPEECH_ANKI_BLOCK_MATH =
            Pattern.compile("\\\\\\[(.*?)\\\\\\]", Pattern.DOTALL);

    /** Anki inline math {@code \\(...\\)}: dropped (see MarkdownRenderer). */
    private static final Pattern SPEECH_ANKI_INLINE_MATH =
            Pattern.compile("\\\\\\((.*?)\\\\\\)", Pattern.DOTALL);

    /**
     * Horizontal rules ({@code ---}, {@code ***}, {@code ___} and spaced
     * variants): dropped. Must run before the list-marker and emphasis
     * passes, whose markers these lines consist of.
     */
    private static final Pattern HORIZONTAL_RULE = Pattern.compile(
            "^[ \\t]{0,3}(?:[-*_][ \\t]*){3,}$", Pattern.MULTILINE);

    /** ATX headers ({@code ## Title}): drop the marker, keep the title. */
    private static final Pattern ATX_HEADER = Pattern.compile(
            "^[ \\t]{0,3}#{1,6}[ \\t]+", Pattern.MULTILINE);

    /** Blockquotes ({@code > text}): drop the markers, keep the text. */
    private static final Pattern BLOCKQUOTE = Pattern.compile(
            "^(?:[ \\t]*>[ \\t]*)+", Pattern.MULTILINE);

    /**
     * List markers ({@code -}, {@code +}, {@code *}, {@code 1.}, {@code 2)}):
     * drop the marker, keep the item text. A space is required after the
     * marker (CommonMark), so {@code -5 degrees} stays plain text.
     */
    private static final Pattern LIST_MARKER = Pattern.compile(
            "^[ \\t]{0,3}(?:[-+*]|\\d{1,9}[.)])[ \\t]+", Pattern.MULTILINE);

    /** Bold {@code **text**}: keep the text, drop the markers. */
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");

    /** Italic {@code *text*}: keep the text, drop the markers. */
    private static final Pattern ITALIC_STAR = Pattern.compile("\\*([^*\\n]+)\\*");

    /** Bold {@code __text__}: keep the text, drop the markers. */
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("__(.+?)__");

    /**
     * Italic {@code _text_}: keep the text, drop the markers. Underscore
     * emphasis is only recognized outside words (CommonMark), so
     * {@code some_var_name} stays intact.
     */
    private static final Pattern ITALIC_UNDERSCORE =
            Pattern.compile("(?<!\\w)_([^_\\n]+)_(?!\\w)");

    /** HTML tags: stripped, the inner text is kept. */
    private static final Pattern HTML_TAG = Pattern.compile("<[^>\\n]+>");

    /** Collapses all whitespace runs (incl. newlines) into single spaces. */
    private static final Pattern WHITESPACE_RUNS = Pattern.compile("\\s+");

    /** Bounded cache for plain-text conversions (list-row previews). */
    private static final int PLAIN_TEXT_CACHE_SIZE = 256;

    private final Markwon mMarkwon;
    private final ExecutorService mExecutorService;
    private final LruCache<String, String> mPlainTextCache;

    public MarkdownRenderer(Provider provider) {
        Context context = provider.getContext();
        // LaTeX drawable text size follows the card display size (26sp).
        float latexPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, 26f, context.getResources().getDisplayMetrics());

        mMarkwon = Markwon.builder(context)
                // CorePlugin is added automatically by builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(MarkwonInlineParserPlugin.create())   // enables $...$ after translation
                .usePlugin(JLatexMathPlugin.create(latexPx, builder -> builder
                        .inlinesEnabled(true)
                        .blocksEnabled(true)))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .build();
        mExecutorService = provider.get(ExecutorService.class);
        mPlainTextCache = new LruCache<>(PLAIN_TEXT_CACHE_SIZE);
    }

    /**
     * Convert Markdown source to clean plain text (synchronous). Suitable only
     * for short/cached inputs; for list rows and arbitrary user content prefer
     * {@link #toPlainTextAsync(String)}.
     */
    public String toPlainText(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        return mMarkwon.toMarkdown(translateMathDelimiters(markdown)).toString();
    }

    /**
     * Async variant of {@link #toPlainText(String)}. Plain-text results are
     * memoized in an LRU cache (keyed by the raw markdown), so cache hits return
     * immediately via {@link Single#just(Object)} without an executor hop. Misses
     * run on the shared background executor.
     */
    public Single<String> toPlainTextAsync(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return Single.just("");
        }
        String cached = mPlainTextCache.get(markdown);
        if (cached != null) {
            return Single.just(cached);
        }
        return Single.fromCallable(() -> {
                    String result = toPlainText(markdown);
                    mPlainTextCache.put(markdown, result);
                    return result;
                })
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Converts raw card markdown/LaTeX into speakable plain text, deciding
     * what a speech engine should say: math and images are dropped rather
     * than rendered, inline markup is stripped to the carried text, and
     * dollar signs are handled conservatively so currency is never eaten
     * as math.
     *
     * <p>This is deliberately a different contract from
     * {@link #toPlainText(String)}: {@code toPlainText} renders for the eyes
     * through the Markwon pipeline (async, cached, produces formatted
     * output), while this path intentionally avoids it — it is conservative
     * synchronous regex over the raw source, no Markwon, no executor, no
     * cache.
     *
     * @param raw the raw card text, may be null
     * @return null for null input, empty for empty input, otherwise the
     * plain text with markup removed and whitespace collapsed
     */
    public String toSpeakableText(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty()) {
            return "";
        }
        String text = raw;
        // images first, otherwise the link pass would strip their target and
        // leave the "!" behind; autolinks must precede the generic HTML tag
        // strip below, otherwise <https://...> would be dropped entirely
        text = IMAGES.matcher(text).replaceAll(" ");
        text = LINKS.matcher(text).replaceAll("$1");
        text = AUTOLINKS.matcher(text).replaceAll("$1");
        // code first so math dollar signs inside code are not eaten
        text = FENCED_CODE.matcher(text).replaceAll(" $1 ");
        text = INLINE_CODE.matcher(text).replaceAll("$1");
        text = SPEECH_DISPLAY_MATH.matcher(text).replaceAll(" ");
        text = SPEECH_INLINE_MATH.matcher(text).replaceAll(" ");
        text = SPEECH_ANKI_BLOCK_MATH.matcher(text).replaceAll(" ");
        text = SPEECH_ANKI_INLINE_MATH.matcher(text).replaceAll(" ");
        // line-level constructs before the emphasis pass, otherwise rule
        // lines made of * or _ would pair up as emphasis
        text = HORIZONTAL_RULE.matcher(text).replaceAll(" ");
        text = ATX_HEADER.matcher(text).replaceAll("");
        text = BLOCKQUOTE.matcher(text).replaceAll("");
        text = LIST_MARKER.matcher(text).replaceAll("");
        // bold before italic within each marker family, so **x** does not
        // degrade into *x* for the italic pass
        text = BOLD.matcher(text).replaceAll("$1");
        text = ITALIC_STAR.matcher(text).replaceAll("$1");
        text = BOLD_UNDERSCORE.matcher(text).replaceAll("$1");
        text = ITALIC_UNDERSCORE.matcher(text).replaceAll("$1");
        text = HTML_TAG.matcher(text).replaceAll(" ");
        return WHITESPACE_RUNS.matcher(text).replaceAll(" ").trim();
    }

    /**
     * Asynchronously parse Markdown into a {@link Spanned} on the background
     * executor. The result is NOT cached (latex/image
     * {@code AsyncDrawableSpans} are tied to a single TextView and must not be
     * reused). Apply the result on the main thread via
     * {@link #applyParsedMarkdown(TextView, Spanned)}.
     */
    public Single<Spanned> parseAsync(String markdown) {
        return Single.fromCallable(() ->
                        mMarkwon.toMarkdown(translateMathDelimiters(markdown)))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Apply an already-parsed {@link Spanned} to a TextView. Must be called on
     * the main thread (it triggers TextView#setText and the latex plugin's
     * drawable scheduling).
     */
    public void applyParsedMarkdown(TextView textView, Spanned spanned) {
        mMarkwon.setParsedMarkdown(textView, spanned);
    }

    /**
     * Normalize Anki math delimiters into the {@code $$...$$} form that
     * Markwon 4.6.2's JLatexMathPlugin recognizes, escaping any literal
     * {@code $} first so currency never triggers math rendering.
     *
     * <p><b>Important:</b> Markwon 4.6.2's inline processor regex is
     * {@code (\${2})([\s\S]+?)\1} — it requires <b>double</b> dollar signs on
     * both ends. There is no single-{@code $} inline math support. So both
     * Anki inline {@code \(...\)} and Anki block {@code \[...\]} are translated
     * to {@code $$...$$}; inline math still renders mid-sentence because the
     * inline processor matches {@code $$...$$} inline (not just as a block).
     *
     * <p>Order matters:
     * <ol>
     *   <li>Escape user-typed {@code $} → {@code \$} (stays literal in output)</li>
     *   <li>Translate block {@code \[...\]} → {@code $$...$$}</li>
     *   <li>Translate inline {@code \(...\)} → {@code $$...$$}</li>
     * </ol>
     */
    private String translateMathDelimiters(String markdown) {
        if (markdown == null) {
            return "";
        }
        // (1) Escape literal dollar signs so "$5" stays "$5" and cannot form a
        //     "$$" math delimiter. In a replaceAll replacement string, "$" must
        //     be written as "\\$" and "\" as "\\\\"; "\\$" -> the literal "\$".
        markdown = markdown.replaceAll("\\$", "\\\\\\$");
        // (2) \[...\] -> $$...$$
        markdown = DISPLAY_MATH.matcher(markdown).replaceAll("\\$\\$$1\\$\\$");
        // (3) \(...\) -> $$...$$ (Markwon 4.6.2 has no single-$ inline math)
        markdown = INLINE_MATH.matcher(markdown).replaceAll("\\$\\$$1\\$\\$");
        return markdown;
    }
}
