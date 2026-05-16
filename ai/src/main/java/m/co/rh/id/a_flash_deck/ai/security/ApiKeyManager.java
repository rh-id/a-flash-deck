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

package m.co.rh.id.a_flash_deck.ai.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import m.co.rh.id.a_flash_deck.ai.model.AvailableModel;

public class ApiKeyManager {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "a_flash_deck_gemini_api_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String PREFS_NAME = "ai_secure_prefs";
    private static final String KEY_ENCRYPTED = "encrypted_api_key";
    private static final String KEY_IV = "iv_api_key";
    private static final String KEY_SELECTED_MODEL = "selected_model_id";
    private static final String DEFAULT_MODEL = "gemini-3.1-flash-lite";

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public ApiKeyManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveApiKey(String apiKey) throws Exception {
        SecretKey secretKey = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] encrypted = cipher.doFinal(apiKey.getBytes("UTF-8"));
        mPrefs.edit()
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(KEY_ENCRYPTED, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply();
    }

    public String getApiKey() {
        try {
            String ivBase64 = mPrefs.getString(KEY_IV, null);
            String encryptedBase64 = mPrefs.getString(KEY_ENCRYPTED, null);
            if (ivBase64 == null || encryptedBase64 == null) {
                return null;
            }
            SecretKey secretKey = getOrCreateSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);
            GCMParameterSpec spec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            byte[] decrypted = cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP));
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasApiKey() {
        return mPrefs.contains(KEY_ENCRYPTED) && mPrefs.contains(KEY_IV);
    }

    public void removeApiKey() {
        mPrefs.edit()
                .remove(KEY_ENCRYPTED)
                .remove(KEY_IV)
                .apply();
    }

    public void saveSelectedModel(String modelId) {
        mPrefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply();
    }

    public String getSelectedModel() {
        return mPrefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL);
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}