package com.team19.splitsmart.service;

/**
 * Thrown (or passed to Callback.onFailure) when user input breaks a rule. getField() names the
 * input that caused it, so the screen can show the message next to that field.
 */
public class ValidationException extends Exception {

    private final String field;

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
