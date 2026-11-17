package com.focusdesktop.application.usecase.exeptions;

public class InvalidDateRangeException extends RuntimeException {
    public InvalidDateRangeException() {
        super("from date must not be after to date");
    }
}
