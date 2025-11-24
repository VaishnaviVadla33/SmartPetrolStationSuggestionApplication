package com.example.findingbunks_part2;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.List;
import java.util.Locale;

/**
 * VoiceNotificationManager - Modular voice notification system for fuel station tracking
 *
 * This class can be easily integrated or removed from the app without affecting core functionality.
 * Simply comment out initialization and method calls to disable voice features.
 *
 * Features:
 * - Announces nearest safe station changes
 * - Warns when leaving/entering safe fuel range
 * - Provides critical low fuel warnings
 * - Smart throttling to prevent notification spam
 */
public class VoiceNotificationManager {

    private static final String TAG = "VoiceNotifications";

    // Throttling settings
    private static final long MIN_ANNOUNCEMENT_INTERVAL_MS = 30000; // 30 seconds
    private static final float SIGNIFICANT_DISTANCE_CHANGE_KM = 2.0f;

    // Critical thresholds
    private static final float CRITICAL_RANGE_KM = 10.0f;
    private static final float WARNING_RANGE_KM = 20.0f;

    // State tracking
    private String lastAnnouncedStation = null;
    private float lastAnnouncedDistance = -1;
    private boolean isInSafeZone = false;
    private long lastVoiceAnnouncementTime = 0;
    private boolean hasAnnouncedCritical = false;
    private boolean hasAnnouncedWarning = false;
    private boolean isEnabled = true;
    private boolean isInitialized = false;

    // Text-to-Speech engine
    private TextToSpeech textToSpeech;
    private Context context;

    /**
     * Constructor - Initializes TTS engine
     */
    public VoiceNotificationManager(Context context) {
        this.context = context;
        initializeTTS();
    }

    /**
     * Initialize Text-to-Speech engine
     */
    private void initializeTTS() {
        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS Language not supported");
                    isInitialized = false;
                } else {
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setSpeechRate(0.9f); // Slightly slower for clarity
                    isInitialized = true;
                    Log.i(TAG, "✅ TTS initialized successfully");
                }
            } else {
                Log.e(TAG, "❌ TTS initialization failed");
                isInitialized = false;
            }
        });
    }

    /**
     * Main method to announce station updates based on smart logic
     * Call this whenever station list is updated
     */
    public void announceStationUpdates(List<PetrolStation> stations, boolean isNavigating) {
        if (!isEnabled || !isInitialized || isNavigating) return;
        if (stations == null || stations.isEmpty()) return;

        long currentTime = System.currentTimeMillis();

        // Minimum interval check - prevent spam
        if (currentTime - lastVoiceAnnouncementTime < MIN_ANNOUNCEMENT_INTERVAL_MS) {
            return;
        }

        PetrolStation nearest = stations.get(0);
        long safeStationCount = stations.stream()
                .filter(PetrolStation::isReachable)
                .count();

        // Check various conditions for announcements
        checkAndAnnounceNearestStationChange(nearest, currentTime);
        checkAndAnnounceSafeZoneChange(nearest, currentTime);
        checkAndAnnounceNoSafeStations(safeStationCount, nearest, currentTime);
    }

    /**
     * Announce when nearest safe station changes
     */
    private void checkAndAnnounceNearestStationChange(PetrolStation nearest, long currentTime) {
        if (!nearest.isReachable()) return;

        boolean nearestChanged = lastAnnouncedStation == null ||
                !lastAnnouncedStation.equals(nearest.getName());

        boolean significantDistanceChange =
                Math.abs(nearest.getDistance() - lastAnnouncedDistance) > SIGNIFICANT_DISTANCE_CHANGE_KM;

        if (nearestChanged || (significantDistanceChange && lastAnnouncedStation != null)) {
            String message = String.format(Locale.US,
                    "Nearest safe station: %s, %.1f kilometers away",
                    cleanStationName(nearest.getName()),
                    nearest.getDistance());

            speak(message);
            lastAnnouncedStation = nearest.getName();
            lastAnnouncedDistance = nearest.getDistance();
            lastVoiceAnnouncementTime = currentTime;
        }
    }

    /**
     * Announce when entering/leaving safe fuel range zone
     */
    private void checkAndAnnounceSafeZoneChange(PetrolStation nearest, long currentTime) {
        boolean currentlyInSafeZone = nearest.isReachable();
        boolean zoneChanged = (isInSafeZone != currentlyInSafeZone);

        if (zoneChanged) {
            String message;
            if (currentlyInSafeZone) {
                message = "You are now in safe fuel range";
                hasAnnouncedWarning = false;
                hasAnnouncedCritical = false;
            } else {
                message = String.format(Locale.US,
                        "Warning: Leaving safe range. Nearest station is %.1f kilometers away",
                        nearest.getDistance());
            }

            speak(message);
            isInSafeZone = currentlyInSafeZone;
            lastVoiceAnnouncementTime = currentTime;
        }
    }

    /**
     * Announce when no safe stations are in range
     */
    private void checkAndAnnounceNoSafeStations(long safeCount, PetrolStation nearest, long currentTime) {
        if (safeCount == 0 && lastAnnouncedStation == null) {
            String message = String.format(Locale.US,
                    "Warning: No safe stations in range. Nearest risky station is %.1f kilometers away",
                    nearest.getDistance());

            speak(message);
            lastAnnouncedStation = "NONE_SAFE";
            lastVoiceAnnouncementTime = currentTime;
        }
    }

    /**
     * Announce fuel range warnings based on remaining range
     * Call this when fuel/range data updates
     */
    public void announceFuelRangeWarning(float remainingRangeKm, float nearestStationDistanceKm) {
        if (!isEnabled || !isInitialized) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVoiceAnnouncementTime < MIN_ANNOUNCEMENT_INTERVAL_MS) {
            return;
        }

        // Critical fuel warning (below 10km range)
        if (remainingRangeKm <= CRITICAL_RANGE_KM && !hasAnnouncedCritical) {
            String message = String.format(Locale.US,
                    "Critical fuel level! Only %.1f kilometers remaining. Nearest station is %.1f kilometers away",
                    remainingRangeKm, nearestStationDistanceKm);

            speak(message);
            hasAnnouncedCritical = true;
            hasAnnouncedWarning = true;
            lastVoiceAnnouncementTime = currentTime;

            // Warning fuel level (below 20km range)
        } else if (remainingRangeKm <= WARNING_RANGE_KM && !hasAnnouncedWarning) {
            String message = String.format(Locale.US,
                    "Low fuel warning. %.1f kilometers remaining. Nearest station is %.1f kilometers away",
                    remainingRangeKm, nearestStationDistanceKm);

            speak(message);
            hasAnnouncedWarning = true;
            lastVoiceAnnouncementTime = currentTime;
        }

        // Reset warnings if fuel increases (refueled)
        if (remainingRangeKm > WARNING_RANGE_KM) {
            hasAnnouncedWarning = false;
            hasAnnouncedCritical = false;
        }
    }

    /**
     * Announce when tracking starts
     */
    public void announceTrackingStarted(int totalStations, long safeStationCount) {
        if (!isEnabled || !isInitialized) return;

        String message;
        if (safeStationCount > 0) {
            message = String.format(Locale.US,
                    "Live tracking started. Found %d safe stations nearby",
                    safeStationCount);
        } else if (totalStations > 0) {
            message = String.format(Locale.US,
                    "Live tracking started. Warning: No safe stations in range. Found %d risky stations",
                    totalStations);
        } else {
            message = "Live tracking started. Searching for nearby stations";
        }

        speak(message);
        lastVoiceAnnouncementTime = System.currentTimeMillis();
    }

    /**
     * Announce when tracking stops
     */
    public void announceTrackingStopped() {
        if (!isEnabled || !isInitialized) return;
        speak("Live tracking stopped");
        reset();
    }

    /**
     * Announce when navigation starts
     */
    public void announceNavigationStarted(String stationName, float distanceKm) {
        if (!isEnabled || !isInitialized) return;

        String message = String.format(Locale.US,
                "Navigation started to %s, %.1f kilometers away",
                cleanStationName(stationName),
                distanceKm);

        speak(message);
    }

    /**
     * Core text-to-speech method
     */
    private void speak(String text) {
        if (textToSpeech != null && isInitialized) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            Log.i(TAG, "🔊 Voice: " + text);
        }
    }

    /**
     * Clean station name for better TTS pronunciation
     * Removes special characters and common suffixes
     */
    private String cleanStationName(String name) {
        if (name == null) return "Station";

        // Remove common suffixes that don't need to be spoken
        name = name.replaceAll("(?i)(\\s*-\\s*petrol\\s*pump)", "");
        name = name.replaceAll("(?i)(\\s*-\\s*fuel\\s*station)", "");
        name = name.replaceAll("(?i)(\\s*petrol\\s*pump)", "");
        name = name.replaceAll("(?i)(\\s*fuel\\s*station)", "");

        return name.trim();
    }

    /**
     * Enable/disable voice notifications
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        Log.i(TAG, "Voice notifications " + (enabled ? "enabled" : "disabled"));

        if (!enabled && textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    /**
     * Check if voice notifications are enabled
     */
    public boolean isEnabled() {
        return isEnabled && isInitialized;
    }

    /**
     * Reset all state tracking
     * Call this when tracking stops or user location changes significantly
     */
    public void reset() {
        lastAnnouncedStation = null;
        lastAnnouncedDistance = -1;
        isInSafeZone = false;
        hasAnnouncedCritical = false;
        hasAnnouncedWarning = false;
        lastVoiceAnnouncementTime = 0;
        Log.i(TAG, "Voice notification state reset");
    }

    /**
     * Cleanup and release TTS resources
     * MUST be called in onDestroy()
     */
    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
            Log.i(TAG, "TTS engine shutdown");
        }
    }

    /**
     * Check if TTS is ready to use
     */
    public boolean isInitialized() {
        return isInitialized;
    }
}