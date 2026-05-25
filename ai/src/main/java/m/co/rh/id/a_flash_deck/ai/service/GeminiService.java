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

package m.co.rh.id.a_flash_deck.ai.service;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedCard;
import m.co.rh.id.a_flash_deck.ai.model.AiGeneratedDeck;
import m.co.rh.id.a_flash_deck.ai.model.AvailableModel;
import m.co.rh.id.a_flash_deck.ai.security.ApiKeyManager;
import m.co.rh.id.a_flash_deck.base.entity.Card;
import m.co.rh.id.a_flash_deck.base.entity.Deck;

public class GeminiService {
    private static final String TAG = GeminiService.class.getName();
    private static final String SYSTEM_INSTRUCTION = "You are a flash card creator. " +
            "Create educational flash cards based on the given topic. " +
            "Each card should have a clear, concise question and a clear, accurate answer. " +
            "The deck name should be descriptive and appropriate for the topic.";
    private static final String SYSTEM_INSTRUCTION_FROM_EXISTING = "You are a flash card creator and transformer. " +
            "You will receive existing flash card decks in JSON format. " +
            "Generate a NEW single deck based on the user's instructions. " +
            "The user may want to translate, expand, create harder versions, or transform the cards. " +
            "hasImage means the card has an image attached. hasVoice means the card has a voice recording attached. " +
            "Each card has 'q' (question) and 'a' (answer) fields. " +
            "Return JSON: {\"deck_name\": \"string\", \"cards\": [{\"question\": \"string\", \"answer\": \"string\"}]}";
    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";

    private final Context mContext;
    private final ApiKeyManager mApiKeyManager;
    private final ExecutorService mExecutorService;

    public GeminiService(ApiKeyManager apiKeyManager, ExecutorService executorService, Context context) {
        mApiKeyManager = apiKeyManager;
        mExecutorService = executorService;
        mContext = context.getApplicationContext();
    }

    public boolean isConfigured() {
        return mApiKeyManager.hasApiKey();
    }

    public void resetClient() {
    }

    public Single<Boolean> validateApiKey(String apiKey) {
        return Single.fromCallable(() -> {
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API key is empty");
            }
            String url = BASE_URL + "/models?key=" + URLEncoder.encode(apiKey, "UTF-8");
            httpGet(url);
            return true;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<List<AvailableModel>> fetchAvailableModels() {
        return Single.fromCallable(() -> {
            List<AvailableModel> models = new ArrayList<>();
            String apiKey = mApiKeyManager.getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API key not configured");
            }
            String url = BASE_URL + "/models?key=" + URLEncoder.encode(apiKey, "UTF-8");
            String nextPageToken = null;
            do {
                String requestUrl = url;
                if (nextPageToken != null) {
                    requestUrl += "&pageToken=" + URLEncoder.encode(nextPageToken, "UTF-8");
                }
                String responseBody = httpGet(requestUrl);
                JSONObject response = new JSONObject(responseBody);
                JSONArray modelsArray = response.optJSONArray("models");
                if (modelsArray != null) {
                    for (int i = 0; i < modelsArray.length(); i++) {
                        JSONObject modelObj = modelsArray.getJSONObject(i);
                        String name = modelObj.optString("name", "");
                        String displayName = modelObj.optString("displayName", name);
                        JSONArray methodsArray = modelObj.optJSONArray("supportedGenerationMethods");
                        if (methodsArray != null) {
                            boolean supportsGenerateContent = false;
                            for (int j = 0; j < methodsArray.length(); j++) {
                                if ("generateContent".equals(methodsArray.getString(j))) {
                                    supportsGenerateContent = true;
                                    break;
                                }
                            }
                            if (supportsGenerateContent) {
                                if (name.startsWith("models/")) {
                                    name = name.substring(7);
                                }
                                models.add(new AvailableModel(name, displayName));
                            }
                        }
                    }
                }
                nextPageToken = response.optString("nextPageToken", null);
            } while (nextPageToken != null);
            return models;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<AiGeneratedDeck> generateDeckFromTopic(String topic, int cardCount, String modelId) {
        return Single.fromCallable(() -> {
            String apiKey = mApiKeyManager.getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API key not configured");
            }
            String url = BASE_URL + "/models/" + URLEncoder.encode(modelId, "UTF-8") + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
            String userPrompt = "Create " + cardCount + " flash cards about \"" + topic +
                    "\". Return JSON: {\"deck_name\": string, \"cards\": [{\"question\": string, \"answer\": string}]}";

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("candidateCount", 1);

            JSONObject requestBody = new JSONObject();
            requestBody.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", SYSTEM_INSTRUCTION))));
            requestBody.put("contents", new JSONArray()
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("parts", new JSONArray()
                                    .put(new JSONObject().put("text", userPrompt)))));
            requestBody.put("generationConfig", generationConfig);

            String responseBody = httpPost(url, requestBody);
            AiGeneratedDeck result = parseGenerateContentResponse(responseBody);
            return result;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<AiGeneratedDeck> generateDeckFromExisting(List<Deck> decks, List<Card> cards, String prompt, int maxCards, String modelId) {
        return Single.fromCallable(() -> {
            String apiKey = mApiKeyManager.getApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                throw new IllegalStateException("API key not configured");
            }
            String url = BASE_URL + "/models/" + URLEncoder.encode(modelId, "UTF-8") + ":generateContent?key=" + URLEncoder.encode(apiKey, "UTF-8");
            String deckDataJson = buildDeckDataPayload(decks, cards);
            String userPrompt = deckDataJson + "\n\nUser instruction: \"" + prompt +
                    "\"\nMaximum cards to generate: " + maxCards;

            JSONObject generationConfig = new JSONObject();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("candidateCount", 1);

            JSONObject requestBody = new JSONObject();
            requestBody.put("systemInstruction", new JSONObject()
                    .put("parts", new JSONArray()
                            .put(new JSONObject().put("text", SYSTEM_INSTRUCTION_FROM_EXISTING))));
            requestBody.put("contents", new JSONArray()
                    .put(new JSONObject()
                            .put("role", "user")
                            .put("parts", new JSONArray()
                                    .put(new JSONObject().put("text", userPrompt)))));
            requestBody.put("generationConfig", generationConfig);

            String responseBody = httpPost(url, requestBody);
            AiGeneratedDeck result = parseGenerateContentResponse(responseBody);
            return result;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    private String buildDeckDataPayload(List<Deck> decks, List<Card> cards) throws Exception {
        Map<Long, List<Card>> cardsByDeckId = new HashMap<>();
        for (Card card : cards) {
            List<Card> deckCards = cardsByDeckId.get(card.deckId);
            if (deckCards == null) {
                deckCards = new ArrayList<>();
                cardsByDeckId.put(card.deckId, deckCards);
            }
            deckCards.add(card);
        }

        JSONObject root = new JSONObject();
        JSONArray decksArray = new JSONArray();
        int totalCards = 0;

        for (Deck deck : decks) {
            JSONObject deckObj = new JSONObject();
            deckObj.put("name", deck.name);

            JSONArray cardsArray = new JSONArray();
            List<Card> deckCards = cardsByDeckId.get(deck.id);
            if (deckCards != null) {
                for (Card card : deckCards) {
                    JSONObject cardObj = new JSONObject();
                    cardObj.put("q", card.question);
                    cardObj.put("a", card.answer);
                    cardObj.put("hasImage", (card.questionImage != null || card.answerImage != null));
                    cardObj.put("hasVoice", (card.questionVoice != null || card.answerVoice != null));
                    cardsArray.put(cardObj);
                }
            }
            deckObj.put("cards", cardsArray);
            deckObj.put("cardCount", cardsArray.length());
            decksArray.put(deckObj);
            totalCards += cardsArray.length();
        }

        root.put("decks", decksArray);
        root.put("totalCards", totalCards);
        root.put("totalDecks", decks.size());

        return root.toString();
    }

    private AiGeneratedDeck parseGenerateContentResponse(String responseBody) throws Exception {
        JSONObject response = new JSONObject(responseBody);
        JSONArray candidatesArray = response.optJSONArray("candidates");
        if (candidatesArray == null || candidatesArray.length() == 0) {
            throw new RuntimeException("No candidates in response");
        }
        JSONObject candidate = candidatesArray.getJSONObject(0);
        JSONObject content = candidate.optJSONObject("content");
        if (content == null) {
            throw new RuntimeException("No content in candidate");
        }
        JSONArray partsArray = content.optJSONArray("parts");
        if (partsArray == null || partsArray.length() == 0) {
            throw new RuntimeException("No parts in content");
        }
        JSONObject part = partsArray.getJSONObject(0);
        String responseText = part.optString("text", "");
        if (responseText.isEmpty()) {
            throw new RuntimeException("Empty response text");
        }
        return parseDeckResponse(responseText);
    }

    private AiGeneratedDeck parseDeckResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        String deckName = root.optString("deck_name", "Generated Deck");
        JSONArray cardsArray = root.optJSONArray("cards");
        List<AiGeneratedCard> cards = new ArrayList<>();
        if (cardsArray != null) {
            for (int i = 0; i < cardsArray.length(); i++) {
                JSONObject cardObj = cardsArray.getJSONObject(i);
                String question = cardObj.optString("question", "");
                String answer = cardObj.optString("answer", "");
                if (!question.isEmpty() && !answer.isEmpty()) {
                    cards.add(new AiGeneratedCard(question, answer));
                }
            }
        }
        return new AiGeneratedDeck(deckName, cards);
    }

    private String httpGet(String urlString) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                throw new RuntimeException("HTTP " + responseCode + ": " + readErrorStream(connection));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String httpPost(String urlString, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(300000);
            connection.setDoOutput(true);
            String requestBody = body.toString();
            connection.setRequestProperty("Content-Length", String.valueOf(requestBody.getBytes("UTF-8").length));
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(requestBody.getBytes("UTF-8"));
            outputStream.flush();
            outputStream.close();
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                throw new RuntimeException("HTTP " + responseCode + ": " + readErrorStream(connection));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readErrorStream(HttpURLConnection connection) {
        try {
            if (connection.getErrorStream() == null) {
                return "";
            }
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "UTF-8"));
            StringBuilder errorResponse = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errorResponse.append(line);
            }
            errorReader.close();
            return errorResponse.toString();
        } catch (Exception e) {
            return "";
        }
    }
}