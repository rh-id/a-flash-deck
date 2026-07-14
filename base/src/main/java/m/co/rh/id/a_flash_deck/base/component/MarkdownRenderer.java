/*
 *     Copyright (C) 2021-2026 Ruby Hartono
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
