package com.focusdesktop.application.usecase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.dto.AnalyticsSummary;
import com.focusdesktop.application.dto.DataExportBundle;
import com.focusdesktop.application.dto.ExportFile;
import com.focusdesktop.application.dto.SettingsDto;
import com.focusdesktop.application.port.DataExportReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportAllDataUseCase {
    private final SettingsUseCase settings;
    private final DataExportReader sessions;
    private final GetAnalyticsSummaryUseCase analytics;
    private final ObjectMapper objectMapper;

    public ExportAllDataUseCase(SettingsUseCase settings, DataExportReader sessions,
                                GetAnalyticsSummaryUseCase analytics, ObjectMapper objectMapper) {
        this.settings = settings;
        this.sessions = sessions;
        this.analytics = analytics;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExportFile execute(String format) {
        DataExportBundle bundle = new DataExportBundle(settings.get(), sessions.loadAllSessions(),
                analytics.execute(null, null));
        return switch (format == null ? "" : format.toLowerCase(java.util.Locale.ROOT)) {
            case "json" -> json(bundle);
            case "csv-zip" -> csvZip(bundle);
            default -> throw new IllegalArgumentException("Export format must be json or csv-zip");
        };
    }

    private ExportFile json(DataExportBundle bundle) {
        try {
            return new ExportFile("focusdesktop-export.json", "application/json", objectMapper.writeValueAsBytes(bundle));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not encode the data export", exception);
        }
    }

    private ExportFile csvZip(DataExportBundle bundle) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
                add(zip, "settings.csv", settingsCsv(bundle.settings()));
                add(zip, "profiles.csv", profilesCsv(bundle.settings()));
                add(zip, "sessions.csv", sessionsCsv(bundle));
                add(zip, "intervals.csv", intervalsCsv(bundle));
                add(zip, "analytics.csv", analyticsCsv(bundle.analytics()));
            }
            return new ExportFile("focusdesktop-export.zip", "application/zip", bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the CSV archive", exception);
        }
    }

    private String settingsCsv(SettingsDto settings) {
        return csv("defaultProfileId", "dailyGoalHours", "theme", "notificationsEnabled", "retentionDays")
                + csv(settings.defaultProfileId(), settings.dailyGoalHours(), settings.theme(),
                settings.notificationsEnabled(), settings.retentionDays());
    }

    private String profilesCsv(SettingsDto settings) {
        StringBuilder csv = new StringBuilder(csv("profileId", "name", "isDefault", "workSeconds",
                "shortBreakSeconds", "longBreakSeconds", "intervalsBeforeLongBreak", "autoStartBreaks", "autoStartWork"));
        settings.profiles().forEach(profile -> csv.append(csv(profile.id(), profile.name(), profile.isDefault(),
                profile.workSeconds(), profile.shortBreakSeconds(), profile.longBreakSeconds(),
                profile.intervalsBeforeLongBreak(), profile.autoStartBreaks(), profile.autoStartWork())));
        return csv.toString();
    }

    private String sessionsCsv(DataExportBundle bundle) {
        StringBuilder csv = new StringBuilder(csv("sessionId", "projectName", "sessionType", "status",
                "createdAt", "startedAt", "endedAt", "settingsSnapshot"));
        bundle.sessions().forEach(session -> csv.append(csv(session.sessionId(), session.projectName(),
                session.sessionType(), session.status(), session.createdAt(), session.startedAt(), session.endedAt(),
                writeJson(session.settingsSnapshot()))));
        return csv.toString();
    }

    private String intervalsCsv(DataExportBundle bundle) {
        StringBuilder csv = new StringBuilder(csv("sessionId", "intervalId", "sequence", "type", "state",
                "plannedSeconds", "actualSeconds", "remainingSeconds", "startedAt", "endedAt", "pausedAt"));
        bundle.sessions().forEach(session -> session.intervals().forEach(interval -> csv.append(csv(session.sessionId(),
                interval.intervalId(), interval.sequence(), interval.type(), interval.state(), interval.plannedSeconds(),
                interval.actualSeconds(), interval.remainingSeconds(), interval.startedAt(), interval.endedAt(),
                interval.pausedAt()))));
        return csv.toString();
    }

    private String analyticsCsv(AnalyticsSummary analytics) {
        StringBuilder csv = new StringBuilder(csv("recordType", "date", "focusSeconds", "breakSeconds",
                "completedSessions", "averageSessionSeconds", "focusScore", "bestDayDate", "bestDayFocusSeconds"));
        csv.append(csv("TOTAL", "", analytics.totalFocusSeconds(), analytics.totalBreakSeconds(),
                analytics.completedSessions(), analytics.averageSessionSeconds(), analytics.focusScore(),
                analytics.bestDay() == null ? "" : analytics.bestDay().date(),
                analytics.bestDay() == null ? "" : analytics.bestDay().focusSeconds()));
        analytics.daily().forEach(day -> csv.append(csv("DAY", day.date(), day.focusSeconds(), day.breakSeconds(),
                day.completedSessions(), "", "", "", "")));
        return csv.toString();
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException(exception); }
    }

    private static void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String csv(Object... fields) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) line.append(',');
            String value = fields[index] == null ? "" : fields[index].toString();
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return line.append('\n').toString();
    }
}
