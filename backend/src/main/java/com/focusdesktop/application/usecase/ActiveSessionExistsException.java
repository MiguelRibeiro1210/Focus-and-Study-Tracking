package com.focusdesktop.application.usecase;

public final class ActiveSessionExistsException extends RuntimeException {
    public ActiveSessionExistsException() {
        super("An active focus session already exists");
    }
}
