package com.focusdesktop.application.usecase.exeptions;

public final class ActiveSessionExistsException extends RuntimeException {
    public ActiveSessionExistsException() {
        super("An active focus session already exists");
    }
}
