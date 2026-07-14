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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented test for {@link MarkdownRenderer}. Exercises the full pipeline
 * (Anki delimiter translation -> Markwon parse -> plain text) on a real Android
 * device/emulator, since {@link MarkdownRenderer} is Android-bound (needs
 * {@code Context} + Markwon).
 *
 * <p>{@code translateMathDelimiters} is private, so it is validated indirectly
 * through the public {@link MarkdownRenderer#toPlainText(String)}, which runs
 * the complete {@code mMarkwon.toMarkdown(translateMathDelimiters(md))} chain.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MarkdownRendererTest {

    private Provider testProvider;
    private MarkdownRenderer mMarkdownRenderer;

    @Before
    public void beforeTest() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        testProvider = Provider.createProvider(appContext, new ProviderModule() {
            @Override
            public void provides(ProviderRegistry providerRegistry, Provider provider) {
                // MarkdownRenderer only needs an ExecutorService; no DB required.
                providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
            }

            @Override
            public void dispose(Provider provider) {
                // leave blank
            }
        });
        mMarkdownRenderer = new MarkdownRenderer(testProvider);
    }

    @After
    public void afterTest() {
        if (testProvider != null) {
            testProvider.dispose();
        }
    }

    @Test
    public void nullInput_returnsEmpty() {
        assertEquals("", mMarkdownRenderer.toPlainText(null));
    }

    @Test
    public void emptyInput_returnsEmpty() {
        assertEquals("", mMarkdownRenderer.toPlainText(""));
    }

    @Test
    public void plainText_passesThroughUnchanged() {
        assertEquals("Hello world", mMarkdownRenderer.toPlainText("Hello world"));
        assertEquals("What is photosynthesis?",
                mMarkdownRenderer.toPlainText("What is photosynthesis?"));
    }

    @Test
    public void boldMarkdown_strippedToPlainText() {
        // **bold** and *italic* -> plain text with formatting stripped. The
        // emphasis delimiter characters are consumed by the parser; only the
        // inner text survives in toPlainText.
        String result = mMarkdownRenderer.toPlainText("**bold** and *italic*");
        assertTrue("expected 'bold' present, got: " + result, result.contains("bold"));
        assertTrue("expected 'italic' present, got: " + result, result.contains("italic"));
        assertFalse("no raw '**' delimiter in result: " + result, result.contains("**"));
    }

    @Test
    public void headingMarkdown_strippedToPlainText() {
        // # Heading -> plain text containing "Heading" (the '# ' is consumed).
        String result = mMarkdownRenderer.toPlainText("# Heading");
        assertTrue("expected 'Heading' present, got: " + result, result.contains("Heading"));
    }

    @Test
    public void linkMarkdown_strippedToPlainText() {
        // [txt](http://example.com) -> link text "txt" survives; the markdown
        // link syntax does not appear in the rendered text.
        String result = mMarkdownRenderer.toPlainText("[txt](http://example.com)");
        assertTrue("expected 'txt' present, got: " + result, result.contains("txt"));
        assertFalse("no raw '](' link syntax in result: " + result, result.contains("]("));
    }

    @Test
    public void inlineAnkiMath_translatedAndRendered() {
        // \(\pi r^2\) -> translated to $$\pi r^2$$ (Markwon 4.6.2 requires double-$
        // for inline math too) -> latex node -> placeholder is the inner latex source.
        String result = mMarkdownRenderer.toPlainText("The area is \\(\\pi r^2\\).");
        assertTrue("expected latex source present, got: " + result,
                result.contains("\\pi r^2"));
        assertFalse("no raw \\( delimiter in result: " + result, result.contains("\\("));
        assertFalse("no raw \\) delimiter in result: " + result, result.contains("\\)"));
    }

    @Test
    public void displayAnkiMath_translatedAndRendered() {
        // \[\int_0^1 x\,dx\] -> block latex node -> inner source as placeholder.
        String result = mMarkdownRenderer.toPlainText("\\[\\int_0^1 x\\,dx\\]");
        assertTrue("expected latex source present, got: " + result,
                result.contains("\\int_0^1 x\\,dx"));
        assertFalse("no raw \\[ delimiter in result: " + result, result.contains("\\["));
        assertFalse("no raw \\] delimiter in result: " + result, result.contains("\\]"));
    }

    @Test
    public void literalDollar_staysLiteral() {
        // Currency "$5" must NOT be interpreted as math. The escaped \$ is
        // rendered back to a literal $.
        String result = mMarkdownRenderer.toPlainText("cost $5 today");
        assertTrue("expected literal '$5' present, got: " + result, result.contains("$5"));
        assertFalse("no dangling backslash-escape in result: " + result, result.contains("\\$"));
    }

    @Test
    public void twoCurrencyAmounts_doNotFormMathSpan() {
        // "$5 vs $10" must NOT be parsed as a math span between the two dollars.
        String result = mMarkdownRenderer.toPlainText("$5 vs $10");
        assertTrue("expected literal '$5' present, got: " + result, result.contains("$5"));
        assertTrue("expected literal '$10' present, got: " + result, result.contains("$10"));
    }

    @Test
    public void mixedMathAndCurrency_translatesCorrectly() {
        // Inline math plus currency in one field.
        String result = mMarkdownRenderer.toPlainText("cost is \\(x\\) dollars, about $5");
        assertTrue("expected latex source present, got: " + result, result.contains("x"));
        assertTrue("expected literal '$5' present, got: " + result, result.contains("$5"));
    }

    @Test
    public void displayMathBeforeInline_orderingSafe() {
        // Block math must be translated without being partially consumed by the
        // inline rule (block rule runs first).
        String result = mMarkdownRenderer.toPlainText("\\[a\\] and \\(b\\)");
        assertTrue("expected block latex 'a' present, got: " + result, result.contains("a"));
        assertTrue("expected inline latex 'b' present, got: " + result, result.contains("b"));
        assertFalse("no raw \\[ in result: " + result, result.contains("\\["));
        assertFalse("no raw \\( in result: " + result, result.contains("\\("));
    }

    @Test
    public void multilineDisplayMath_matchesAcrossLines() {
        // \[...\] spanning newlines should match (DOTALL).
        String input = "\\[a + b\n= c\\]";
        String result = mMarkdownRenderer.toPlainText(input);
        assertTrue("expected block latex present, got: " + result,
                result.contains("a + b"));
        assertTrue("expected newline-spanning content present, got: " + result,
                result.contains("= c"));
        assertFalse("no raw \\[ in result: " + result, result.contains("\\["));
    }
}
