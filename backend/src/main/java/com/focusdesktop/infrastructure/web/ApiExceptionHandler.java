package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.usecase.exeptions.ActiveSessionExistsException;
import com.focusdesktop.application.usecase.exeptions.InvalidSessionStateException;
import com.focusdesktop.application.usecase.exeptions.SessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ActiveSessionExistsException.class)
    ResponseEntity<ApiErrorResponse> activeSessionExists(ActiveSessionExistsException exception) {
        return error(HttpStatus.CONFLICT, "ACTIVE_SESSION_EXISTS", exception.getMessage());
    }

    @ExceptionHandler(SessionNotFoundException.class)
    ResponseEntity<ApiErrorResponse> sessionNotFound(SessionNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(InvalidSessionStateException.class)
    ResponseEntity<ApiErrorResponse> invalidState(InvalidSessionStateException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SESSION_STATE", exception.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> badRequest(Exception exception) {
        String message = exception instanceof HttpMessageNotReadableException
                ? "Request body is missing or malformed" : exception.getMessage();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message));
    }
}
