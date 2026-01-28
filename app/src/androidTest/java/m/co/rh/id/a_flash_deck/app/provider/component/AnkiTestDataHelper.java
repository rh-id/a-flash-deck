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

package m.co.rh.id.a_flash_deck.app.provider.component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;

 /**
 * Helper class for creating test data for Anki import/export tests.
 * 
 * <p>Provides factory methods to create test entities (Deck, Card) and test media files
 * (JPEG, PNG, MP3) with minimal valid headers. This simplifies test setup by
 * providing ready-to-use test data without requiring actual media files.</p>
 * 
 * <p>Test media files created by this class have:</p>
 * <ul>
 *   <li>Valid file headers for proper file type detection</li>
 *   <li>Minimal content size (1-2KB) for fast test execution</li>
 *   <li>No actual meaningful content (just dummy bytes)</li>
 * </ul>
 * 
 * @since 1.0
 */
public class AnkiTestDataHelper {
    
    /**
     * Creates a test Deck entity with the specified name.
     * 
     * <p>The deck is initialized with current timestamp for createdDateTime and
     * updatedDateTime fields. The deck has no ID assigned (will be auto-generated
     * when inserted into database).</p>
     * 
     * @param name the name of the deck to create
     * @return a new Deck entity with specified name and current timestamps
     */
    public static Deck createTestDeck(String name) {
        Date date = new Date();
        Deck deck = new Deck();
        deck.name = name;
        deck.createdDateTime = date;
        deck.updatedDateTime = date;
        return deck;
    }
    
    /**
     * Creates a test Card entity with basic question and answer text.
     * 
     * <p>The card has no media (images or audio) and is not marked as reversible.
     * Use {@link #createTestCardWithImages} or {@link #createTestCardWithVoice}
     * to create cards with media.</p>
     * 
     * @param deckId the ID of the deck this card belongs to
     * @param ordinal the display order/position of this card in the deck
     * @param question the question text for the card
     * @param answer the answer text for the card
     * @return a new Card entity with specified properties
     */
    public static Card createTestCard(Long deckId, int ordinal, String question, String answer) {
        Card card = new Card();
        card.deckId = deckId;
        card.ordinal = ordinal;
        card.question = question;
        card.answer = answer;
        card.isReversibleQA = false;
        card.isReversed = false;
        return card;
    }
    
    /**
     * Creates a test Card entity with question and answer images.
     * 
     * <p>This method creates a card with text content and attaches image files
     * for both question and answer sides. The image parameters should be filenames
     * (not full paths) as stored in the Card entity.</p>
     * 
     * @param deckId the ID of the deck this card belongs to
     * @param ordinal the display order/position of this card in the deck
     * @param question the question text for the card
     * @param answer the answer text for the card
     * @param questionImage the filename of the question side image
     * @param answerImage the filename of the answer side image
     * @return a new Card entity with images attached
     */
    public static Card createTestCardWithImages(Long deckId, int ordinal, String question, String answer, 
                                                 String questionImage, String answerImage) {
        Card card = createTestCard(deckId, ordinal, question, answer);
        card.questionImage = questionImage;
        card.answerImage = answerImage;
        return card;
    }
    
    /**
     * Creates a test Card entity with question and answer audio recordings.
     * 
     * <p>This method creates a card with text content and attaches voice files
     * for both question and answer sides. The voice parameters should be filenames
     * (not full paths) as stored in the Card entity.</p>
     * 
     * @param deckId the ID of the deck this card belongs to
     * @param ordinal the display order/position of this card in the deck
     * @param question the question text for the card
     * @param answer the answer text for the card
     * @param questionVoice the filename of the question side audio
     * @param answerVoice the filename of the answer side audio
     * @return a new Card entity with voice recordings attached
     */
    public static Card createTestCardWithVoice(Long deckId, int ordinal, String question, String answer,
                                                String questionVoice, String answerVoice) {
        Card card = createTestCard(deckId, ordinal, question, answer);
        card.questionVoice = questionVoice;
        card.answerVoice = answerVoice;
        return card;
    }
    
    /**
     * Creates a test JPEG image file with valid JPEG header.
     * 
     * <p>The file contains a minimal valid JPEG header followed by 1KB of dummy data.
     * This is sufficient for FileHelper to recognize the file type and process it
     * correctly during tests.</p>
     * 
     * @param directory the directory where the image file should be created
     * @param fileName the name to give the image file
     * @return the created File object
     * @throws IOException if the file cannot be written
     */
    public static File createTestImageFile(File directory, String fileName) throws IOException {
        File imageFile = new File(directory, fileName);
        FileOutputStream fos = new FileOutputStream(imageFile);
        
        byte[] fakeJpegHeader = new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x01, 0x00, 0x48, 0x00, 0x48, 0x00, 0x00
        };
        fos.write(fakeJpegHeader);
        fos.write(new byte[1000]);
        fos.close();
        
        return imageFile;
    }
    
    /**
     * Creates a test MP3 audio file with valid MP3 header.
     * 
     * <p>The file contains a minimal valid MP3 frame header followed by 2KB of dummy data.
     * This is sufficient for FileHelper to recognize the file type and process it
     * correctly during tests.</p>
     * 
     * @param directory the directory where the audio file should be created
     * @param fileName the name to give the audio file
     * @return the created File object
     * @throws IOException if the file cannot be written
     */
    public static File createTestAudioFile(File directory, String fileName) throws IOException {
        File audioFile = new File(directory, fileName);
        FileOutputStream fos = new FileOutputStream(audioFile);
        
        byte[] fakeMp3Header = new byte[]{
            (byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x44,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00
        };
        fos.write(fakeMp3Header);
        fos.write(new byte[2000]);
        fos.close();
        
        return audioFile;
    }
    
    /**
     * Creates a test PNG image file with valid PNG signature.
     * 
     * <p>The file contains the standard PNG file signature followed by 1KB of dummy data.
     * This is useful for testing PNG-specific handling in the import/export code.</p>
     * 
     * @param directory the directory where the image file should be created
     * @param fileName the name to give the image file
     * @return the created File object
     * @throws IOException if the file cannot be written
     */
    public static File createTestPngFile(File directory, String fileName) throws IOException {
        File imageFile = new File(directory, fileName);
        FileOutputStream fos = new FileOutputStream(imageFile);
        
        byte[] pngSignature = new byte[]{
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, 
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
        };
        fos.write(pngSignature);
        fos.write(new byte[1000]);
        fos.close();
        
        return imageFile;
    }
}
