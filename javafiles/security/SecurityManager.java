package com.example.findingbunks_part2.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

public class SecurityManager {
    private static final String TAG = "SecurityManager";
    private static final String KEY_ALIAS = "VehicleAppRSAKey";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";

    /**
     * Generate RSA key pair (only once during first registration)
     * Private key stays in Android Keystore (hardware protected)
     * Public key is sent to backend
     */
    public static void generateKeyPair() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);

            // Check if key already exists
            if (keyStore.containsAlias(KEY_ALIAS)) {
                Log.i(TAG, "Key pair already exists. Skipping generation.");
                return;
            }

            // Generate new RSA key pair
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_RSA,
                    KEYSTORE_PROVIDER
            );

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
            )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setKeySize(2048)
                    .setUserAuthenticationRequired(false) // No biometric needed
                    .build();

            keyPairGenerator.initialize(spec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            Log.i(TAG, "✅ RSA Key Pair generated successfully!");

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to generate key pair", e);
        }
    }

    /**
     * Get Public Key as Base64 string (to send to backend)
     */
    public static String getPublicKeyString() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);

            PublicKey publicKey = keyStore.getCertificate(KEY_ALIAS).getPublicKey();
            byte[] publicKeyBytes = publicKey.getEncoded();

            return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to get public key", e);
            return null;
        }
    }

    /**
     * Sign data with Private Key (proves device identity)
     * This is used when backend needs to verify it's really this device
     */
    public static String signData(String data) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes());

            byte[] signatureBytes = signature.sign();
            return Base64.encodeToString(signatureBytes, Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to sign data", e);
            return null;
        }
    }

    /**
     * Check if keys exist
     */
    public static boolean keysExist() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
            keyStore.load(null);
            return keyStore.containsAlias(KEY_ALIAS);
        } catch (Exception e) {
            return false;
        }
    }
}