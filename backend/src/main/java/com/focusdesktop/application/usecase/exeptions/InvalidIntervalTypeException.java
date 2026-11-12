package com.focusdesktop.application.usecase.exeptions;

public final class InvalidIntervalTypeException extends RuntimeException {
    public InvalidIntervalTypeException(String operation) {
        super("Cannot " + operation + " for the current interval type");
    }
}
