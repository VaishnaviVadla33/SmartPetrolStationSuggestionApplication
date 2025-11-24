package com.example.findingbunks_part2.security;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SecureStorage {
    private static final String PREFS_NAME = "VehicleAppSecurePrefs";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_IS_REGISTERED = "is_registered";
    private static final String KEY_ANDROID_ID = "android_id";
    private static final String KEY_VIN = "vin";
    private static final String KEY_MAC = "mac";
    private static final String KEY_BUNKS_CACHE = "bunks_cache";

    // --- NEW KEYS FOR OFFLINE FEATURES ---
    private static final String KEY_LAST_KNOWN_RANGE = "last_known_range";
    private static final String KEY_LAST_KNOWN_FUEL = "last_known_fuel";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_CACHED_NAV_STEPS = "cached_nav_steps";
    // --- END NEW KEYS ---

    private static SharedPreferences getEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    // --- NEW METHODS FOR OFFLINE FEATURES ---

    /**
     * Saves the last known vehicle data from OBD
     */
    public static void saveLastKnownVehicleData(Context context, float range, float fuel) {
        SharedPreferences.Editor editor = getEncryptedPrefs(context).edit();
        editor.putFloat(KEY_LAST_KNOWN_RANGE, range);
        editor.putFloat(KEY_LAST_KNOWN_FUEL, fuel);
        editor.putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis());
        editor.apply();
    }

    /**
     * @return float array: [0] = range, [1] = fuel
     */
    public static float[] getLastKnownVehicleData(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        float range = prefs.getFloat(KEY_LAST_KNOWN_RANGE, -1f);
        float fuel = prefs.getFloat(KEY_LAST_KNOWN_FUEL, -1f);
        return new float[]{range, fuel};
    }

    public static long getLastSyncTime(Context context) {
        return getEncryptedPrefs(context).getLong(KEY_LAST_SYNC_TIME, 0);
    }

    /**
     * Saves the turn-by-turn steps (as JSON) for a specific station
     * We use the station's LatLng as a simple unique ID
     */
    public static void saveNavigationSteps(Context context, String stationId, String stepsJson) {
        getEncryptedPrefs(context).edit().putString(KEY_CACHED_NAV_STEPS + stationId, stepsJson).apply();
    }

    /**
     * Gets the cached turn-by-turn steps for a station
     */
    public static String getNavigationSteps(Context context, String stationId) {
        return getEncryptedPrefs(context).getString(KEY_CACHED_NAV_STEPS + stationId, null);
    }

    // --- END NEW METHODS ---


    // (All your other methods: saveBunksJson, saveUserRole, etc.)
    // ...

    public static void saveBunksJson(Context context, String json) {
        getEncryptedPrefs(context).edit().putString(KEY_BUNKS_CACHE, json).apply();
    }

    public static String getBunksJson(Context context) {
        return getEncryptedPrefs(context).getString(KEY_BUNKS_CACHE, null);
    }

    public static void saveUserName(Context context, String name) {
        getEncryptedPrefs(context).edit().putString(KEY_USER_NAME, name).apply();
    }

    public static String getUserName(Context context) {
        return getEncryptedPrefs(context).getString(KEY_USER_NAME, "Welcome");
    }

    public static void saveUserRole(Context context, String role) {
        getEncryptedPrefs(context).edit().putString(KEY_USER_ROLE, role).apply();
    }

    public static String getUserRole(Context context) {
        return getEncryptedPrefs(context).getString(KEY_USER_ROLE, null);
    }

    public static void setRegistered(Context context, boolean registered) {
        getEncryptedPrefs(context).edit().putBoolean(KEY_IS_REGISTERED, registered).apply();
    }

    public static boolean isRegistered(Context context) {
        return getEncryptedPrefs(context).getBoolean(KEY_IS_REGISTERED, false);
    }

    public static void saveVehicleDetails(Context context, String androidId, String vin, String mac) {
        SharedPreferences.Editor editor = getEncryptedPrefs(context).edit();
        editor.putString(KEY_ANDROID_ID, androidId);
        editor.putString(KEY_VIN, vin);
        editor.putString(KEY_MAC, mac);
        editor.apply();
    }

    public static void clearAll(Context context) {
        getEncryptedPrefs(context).edit().clear().apply();
    }

    /**
     * Save voice notifications enabled preference
     */

    public static void saveVoiceNotificationsEnabled(Context context, boolean enabled) {
        getEncryptedPrefs(context).edit()
                .putBoolean("voice_notifications_enabled", enabled)
                .apply();
    }

    /**
     * Get voice notifications enabled preference
     */
    public static boolean getVoiceNotificationsEnabled(Context context, boolean defaultValue) {
        return getEncryptedPrefs(context)
                .getBoolean("voice_notifications_enabled", defaultValue);
    }
}