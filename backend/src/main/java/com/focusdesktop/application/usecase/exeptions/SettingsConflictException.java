package com.focusdesktop.application.usecase.exeptions;

public class SettingsConflictException extends RuntimeException {
    public SettingsConflictException(String message) { super(message); }
}
