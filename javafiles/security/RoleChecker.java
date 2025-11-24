package com.example.findingbunks_part2.security;

import android.content.Context;

public class RoleChecker {

    /**
     * Check if current user is MAIN user
     * This works 100% OFFLINE after registration!
     */
    public static boolean isMainUser(Context context) {
        String role = SecureStorage.getUserRole(context);
        return "MAIN".equalsIgnoreCase(role);
    }

    /**
     * Check if current user is SECONDARY user
     */
    public static boolean isSecondaryUser(Context context) {
        String role = SecureStorage.getUserRole(context);
        return "SECONDARY".equalsIgnoreCase(role);
    }

    /**
     * Get role as string
     */
    public static String getRole(Context context) {
        String role = SecureStorage.getUserRole(context);
        return role != null ? role : "UNKNOWN";
    }

    /**
     * Check if user is registered
     */
    public static boolean isRegistered(Context context) {
        return SecureStorage.isRegistered(context);
    }
}