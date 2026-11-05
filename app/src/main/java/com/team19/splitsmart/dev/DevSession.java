package com.team19.splitsmart.dev;

/**
 * The test user picked in Developer tools. AuthService.getCurrentUserId() falls back to it when
 * nobody is signed in with Firebase, so every part can test before real login exists.
 * Development only: remove together with the dev package before the demo.
 */
public final class DevSession {

    private static String userId;

    private DevSession() {
    }

    public static String getUserId() {
        return userId;
    }

    public static void setUserId(String uid) {
        userId = uid;
    }
}
