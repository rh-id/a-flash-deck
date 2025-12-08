/*
 *     Copyright (C) 2021 Ruby Hartono
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

package m.co.rh.id.a_flash_deck.base.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Objects;


@Entity(tableName = "card")
public class Card implements Serializable, Cloneable {
    @PrimaryKey(autoGenerate = true)
    public Long id;

    /**
     * Deck ID that refers to Deck.id
     */
    @ColumnInfo(name = "deck_id")
    public Long deckId;

    /**
     * Ordinal/order start from 0
     */
    @ColumnInfo(name = "ordinal")
    public int ordinal;

    /**
     * Question part of card, usually at the top or front side card
     */
    @ColumnInfo(name = "question")
    public String question;

    /**
     * Question image file name, the file name only
     */
    @ColumnInfo(name = "question_image")
    public String questionImage;

    /**
     * Question voice file name, the file name only
     */
    @ColumnInfo(name = "question_voice")
    public String questionVoice;

    /**
     * Answer part of card, usually at the bottom or back side of card
     */
    @ColumnInfo(name = "answer")
    public String answer;

    /**
     * Answer image file name, the file name only
     */
    @ColumnInfo(name = "answer_image")
    public String answerImage;

    /**
     * Answer voice file name, the file name only
     */
    @ColumnInfo(name = "answer_voice")
    public String answerVoice;

    /**
     * Whether the question and answer can be swapped when questioned
     */
    @ColumnInfo(name = "is_reversible_qa")
    public boolean isReversibleQA;

    /**
     * Whether this card is shown reversed, a.k.a question is answer and answer is question
     */
    public boolean isReversed;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return ordinal == card.ordinal &&
                isReversibleQA == card.isReversibleQA &&
                isReversed == card.isReversed &&
                Objects.equals(id, card.id) &&
                Objects.equals(deckId, card.deckId) &&
                Objects.equals(question, card.question) &&
                Objects.equals(questionImage, card.questionImage) &&
                Objects.equals(questionVoice, card.questionVoice) &&
                Objects.equals(answer, card.answer) &&
                Objects.equals(answerImage, card.answerImage) &&
                Objects.equals(answerVoice, card.answerVoice);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, deckId, ordinal, question, questionImage, questionVoice, answer, answerImage, answerVoice, isReversibleQA, isReversed);
    }

    @Override
    public Card clone() {
        try {
            return (Card) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id);
        jsonObject.put("deckId", deckId);
        jsonObject.put("ordinal", ordinal);
        jsonObject.put("question", question);
        String questionImageStr = questionImage;
        if (questionImageStr == null) {
            questionImageStr = "";
        }
        jsonObject.put("questionImage", questionImageStr);
        String questionVoiceStr = questionVoice;
        if (questionVoiceStr == null) {
            questionVoiceStr = "";
        }
        jsonObject.put("questionVoice", questionVoiceStr);
        jsonObject.put("answer", answer);
        String answerImageStr = answerImage;
        if (answerImageStr == null) {
            answerImageStr = "";
        }
        jsonObject.put("answerImage", answerImageStr);
        String answerVoiceStr = answerVoice;
        if (answerVoiceStr == null) {
            answerVoiceStr = "";
        }
        jsonObject.put("answerVoice", answerVoiceStr);
        jsonObject.put("isReversibleQA", isReversibleQA);
        jsonObject.put("isReversed", isReversed);
        return jsonObject;
    }

    public void fromJson(JSONObject jsonObject) throws JSONException {
        id = jsonObject.getLong("id");
        deckId = jsonObject.getLong("deckId");
        ordinal = jsonObject.getInt("ordinal");
        question = jsonObject.getString("question");
        try {
            questionImage = jsonObject.getString("questionImage");
            if (questionImage.isEmpty()) {
                questionImage = null;
            }
        } catch (JSONException jsonException) {
            // leave blank
        }
        try {
            questionVoice = jsonObject.getString("questionVoice");
            if (questionVoice.isEmpty()) {
                questionVoice = null;
            }
        } catch (JSONException jsonException) {
            // leave blank
        }
        answer = jsonObject.getString("answer");
        try {
            answerImage = jsonObject.getString("answerImage");
            if (answerImage.isEmpty()) {
                answerImage = null;
            }
        } catch (JSONException jsonException) {
            // leave blank
        }
        try {
            answerVoice = jsonObject.getString("answerVoice");
            if (answerVoice.isEmpty()) {
                answerVoice = null;
            }
        } catch (JSONException jsonException) {
            // leave blank
        }
        try {
            isReversibleQA = jsonObject.getBoolean("isReversibleQA");
        } catch (JSONException jsonException) {
            // leave blank
        }
        try {
            isReversed = jsonObject.getBoolean("isReversed");
        } catch (JSONException jsonException) {
            // leave blank
        }
    }
}
