package com.focusdesktop.application.dto;

public record ExportFile(String fileName, String contentType, byte[] content) { }
