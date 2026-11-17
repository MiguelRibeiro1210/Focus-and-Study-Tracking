package com.focusdesktop.application.usecase.exeptions;

public class AnalyticsRangeTooLargeException extends RuntimeException {
    public AnalyticsRangeTooLargeException() {
        super("Analytics date range must not exceed 366 inclusive calendar days");
    }
}
