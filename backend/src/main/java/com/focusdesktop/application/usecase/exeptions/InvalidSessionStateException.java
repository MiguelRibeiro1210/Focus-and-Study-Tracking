package com.focusdesktop.application.usecase.exeptions;

public final class InvalidSessionStateException extends RuntimeException {
    public InvalidSessionStateException(String operation, RuntimeException cause) {
        super("Cannot " + operation + " session in its current state", cause);
    }
}
