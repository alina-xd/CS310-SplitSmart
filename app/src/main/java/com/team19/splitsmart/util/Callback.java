package com.team19.splitsmart.util;

/**
 * Result of an asynchronous Firebase call. Every repository and service method that talks to
 * Firebase reports its result through one of these instead of returning a value directly.
 * Both methods are called on the main (UI) thread.
 */
public interface Callback<T> {
    void onSuccess(T result);

    void onFailure(Exception e);
}
