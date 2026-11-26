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
    private final ManageBlockingRulesUseCase blockingRules;
    private final com.focusdesktop.application.port.ProtectionPreferences protectionPreferences;
    private final com.focusdesktop.application.port.WebsiteProtectionPreferences websitePreferences;
    private final com.focusdesktop.application.port.DistractionLogReader distractions;

    public ExportAllDataUseCase(SettingsUseCase settings, DataExportReader sessions,
                                GetAnalyticsSummaryUseCase analytics, ObjectMapper objectMapper,
                                ManageBlockingRulesUseCase blockingRules,
                                com.focusdesktop.application.port.ProtectionPreferences protectionPreferences,
                                com.focusdesktop.application.port.WebsiteProtectionPreferences websitePreferences,
                                com.focusdesktop.application.port.DistractionLogReader distractions) {
        this.settings = settings;
        this.sessions = sessions;
        this.analytics = analytics;
        this.objectMapper = objectMapper;
        this.blockingRules = blockingRules;
        this.protectionPreferences = protectionPreferences;
        this.websitePreferences = websitePreferences;
        this.distractions = distractions;
    }

    @Transactional
    public ExportFile execute(String format) {
        DataExportBundle bundle = new DataExportBundle(settings.get(), sessions.loadAllSessions(),
                analytics.execute(null, null), blockingRules.list(), protectionPreferences.get(), websitePreferences.get(), distractions.exportAll());
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
                add(zip, "blocking-rules.csv", blockingRulesCsv(bundle));
                add(zip, "protection-preferences.csv", csv("applicationsEnabled", "applicationConsentAt")
                        + csv(bundle.protectionPreferences().applicationsEnabled(), bundle.protectionPreferences().applicationConsentAt()));
                add(zip, "website-protection-preferences.csv", csv("enabled", "consentAt")
                        + csv(bundle.websiteProtectionPreferences().enabled(), bundle.websiteProtectionPreferences().consentAt()));
                add(zip, "distractions.csv", distractionsCsv(bundle));
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

    private String distractionsCsv(DataExportBundle bundle) throws JsonProcessingException {
        StringBuilder result = new StringBuilder(csv("detectionId", "sessionId", "ruleId", "target", "observationType", "application",
                "host", "category", "action", "occurredAt", "outcome", "outcomeAt", "blocked", "failureReason"));
        for (var entry : bundle.distractions()) result.append(csv(entry.detectionId(), entry.sessionId(), entry.ruleId(),
                objectMapper.writeValueAsString(entry.target()), entry.observationType(),
                entry.application() == null ? null : objectMapper.writeValueAsString(entry.application()), entry.host(),
                entry.category(), entry.action(), entry.occurredAt(), entry.outcome(), entry.outcomeAt(), entry.blocked(), entry.failureReason()));
        return result.toString();
    }

    private String blockingRulesCsv(DataExportBundle bundle) {
        StringBuilder result = new StringBuilder(csv("id", "displayName", "type", "platform", "matchKind",
                "value", "domain", "category", "action", "enabled", "createdAt", "updatedAt"));
        bundle.blockingRules().forEach(rule -> result.append(csv(rule.id(), rule.displayName(), rule.target().type(),
                rule.target().platform(), rule.target().matchKind(), rule.target().value(), rule.target().domain(),
                rule.category(), rule.action(), rule.enabled(), rule.createdAt(), rule.updatedAt())));
        return result.toString();
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
