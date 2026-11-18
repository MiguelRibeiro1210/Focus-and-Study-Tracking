package com.focusdesktop.infrastructure.web;

import com.focusdesktop.application.dto.SettingsDto;
import com.focusdesktop.application.usecase.SettingsUseCase;
import com.focusdesktop.application.usecase.DeleteAllDataUseCase;
import com.focusdesktop.application.usecase.ExportAllDataUseCase;
import com.focusdesktop.application.dto.ExportFile;
import com.focusdesktop.domain.model.ApplicationTheme;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final SettingsUseCase settings;
    private final DeleteAllDataUseCase deleteAllData;
    private final ExportAllDataUseCase exportAllData;

    public SettingsController(SettingsUseCase settings, DeleteAllDataUseCase deleteAllData,
                              ExportAllDataUseCase exportAllData) {
        this.settings = settings;
        this.deleteAllData = deleteAllData;
        this.exportAllData = exportAllData;
    }

    @GetMapping
    public SettingsDto get() { return settings.get(); }

    @PostMapping("/profiles")
    public ResponseEntity<SettingsDto> createProfile(@RequestBody CreateProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settings.createProfile(request.name()));
    }

    @PutMapping("/profiles/{id}")
    public SettingsDto updateProfile(@PathVariable UUID id, @RequestBody UpdateProfileRequest request) {
        return settings.updateProfile(id, request.name(), request.workSeconds(), request.shortBreakSeconds(),
                request.longBreakSeconds(), request.intervalsBeforeLongBreak(), request.autoStartBreaks(),
                request.autoStartWork());
    }

    @PutMapping("/profiles/default")
    public SettingsDto setDefault(@RequestBody DefaultProfileRequest request) {
        return settings.setDefault(request.profileId());
    }

    @DeleteMapping("/profiles/{id}")
    public ResponseEntity<Void> deleteProfile(@PathVariable UUID id) {
        settings.deleteProfile(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/application")
    public SettingsDto updatePreferences(@RequestBody ApplicationSettingsRequest request) {
        return settings.updatePreferences(request.dailyGoalHours(), request.theme(),
                request.notificationsEnabled(), request.retentionDays());
    }

    @DeleteMapping("/data")
    public ResponseEntity<Void> deleteAllData(@RequestBody DeleteAllDataRequest request) {
        deleteAllData.execute(request.confirmation());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String format) {
        ExportFile file = exportAllData.execute(format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }

    public record CreateProfileRequest(String name) { }
    public record UpdateProfileRequest(String name, long workSeconds, long shortBreakSeconds,
                                       long longBreakSeconds, int intervalsBeforeLongBreak,
                                       boolean autoStartBreaks, boolean autoStartWork) { }
    public record DefaultProfileRequest(UUID profileId) { }
    public record ApplicationSettingsRequest(int dailyGoalHours, ApplicationTheme theme,
                                             boolean notificationsEnabled, Integer retentionDays) { }
    public record DeleteAllDataRequest(String confirmation) { }
}
