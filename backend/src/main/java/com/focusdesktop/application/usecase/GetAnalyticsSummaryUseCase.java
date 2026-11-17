package com.focusdesktop.application.usecase;

import com.focusdesktop.application.dto.AnalyticsBestDay;
import com.focusdesktop.application.dto.AnalyticsDailySummary;
import com.focusdesktop.application.dto.AnalyticsSummary;
import com.focusdesktop.application.usecase.exeptions.AnalyticsRangeTooLargeException;
import com.focusdesktop.application.usecase.exeptions.InvalidDateRangeException;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.repository.FocusSessionRepository;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public class GetAnalyticsSummaryUseCase {
    private static final int MAX_RANGE_DAYS = 366;
    private static final int ALL_TIME_CHART_DAYS = 7;

    private final FocusSessionRepository sessions;
    private final ZoneId localZone;

    public GetAnalyticsSummaryUseCase(FocusSessionRepository sessions, ZoneId localZone) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.localZone = Objects.requireNonNull(localZone, "localZone");
    }

    public AnalyticsSummary execute(String fromValue, String toValue) {
        if ((fromValue == null) != (toValue == null)) {
            throw new InvalidDateRangeException();
        }
        LocalDate from = parseDate(fromValue);
        LocalDate to = parseDate(toValue);
        if (from != null && from.isAfter(to)) {
            throw new InvalidDateRangeException();
        }
        if (from != null && java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new AnalyticsRangeTooLargeException();
        }

        Instant fromInstant = from == null ? null : from.atStartOfDay(localZone).toInstant();
        Instant toInstant = to == null ? null : to.plusDays(1).atStartOfDay(localZone).toInstant();
        List<FocusSession> completed = sessions.findCompletedSessions(fromInstant, toInstant);
        TreeMap<LocalDate, DayTotals> totalsByDay = new TreeMap<>();
        long totalFocus = 0;
        long totalBreak = 0;
        long plannedWork = 0;
        for (FocusSession session : completed) {
            LocalDate date = session.startedAt().value().atZone(localZone).toLocalDate();
            DayTotals day = totalsByDay.computeIfAbsent(date, ignored -> new DayTotals());
            long focus = session.actualFocusDuration().seconds();
            long breaks = session.intervals().stream()
                    .filter(interval -> interval.type() != SessionType.WORK)
                    .mapToLong(interval -> interval.actualDuration().seconds()).sum();
            long planned = session.intervals().stream()
                    .filter(interval -> interval.type() == SessionType.WORK && interval.startedAt() != null)
                    .mapToLong(interval -> interval.plannedDuration().seconds()).sum();
            totalFocus = Math.addExact(totalFocus, focus);
            totalBreak = Math.addExact(totalBreak, breaks);
            plannedWork = Math.addExact(plannedWork, planned);
            day.focusSeconds = Math.addExact(day.focusSeconds, focus);
            day.breakSeconds = Math.addExact(day.breakSeconds, breaks);
            day.completedSessions++;
        }

        List<AnalyticsDailySummary> daily = dailyBreakdown(from, to, totalsByDay);
        AnalyticsBestDay bestDay = totalsByDay.entrySet().stream()
                .filter(entry -> entry.getValue().completedSessions > 0)
                .max(Comparator.<Map.Entry<LocalDate, DayTotals>>comparingLong(entry -> entry.getValue().focusSeconds)
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new AnalyticsBestDay(entry.getKey(), entry.getValue().focusSeconds,
                        entry.getValue().completedSessions))
                .orElse(null);
        int focusScore = plannedWork == 0 ? 0 : (int) Math.max(0, Math.min(100,
                Math.round(100.0 * totalFocus / plannedWork)));
        long count = completed.size();
        return new AnalyticsSummary(from, to, totalFocus, totalBreak, count,
                count == 0 ? 0 : totalFocus / count, focusScore, bestDay, daily);
    }

    private List<AnalyticsDailySummary> dailyBreakdown(LocalDate from, LocalDate to,
                                                       TreeMap<LocalDate, DayTotals> totalsByDay) {
        LocalDate today = LocalDate.now(localZone);
        LocalDate firstDay = from == null ? today.minusDays(ALL_TIME_CHART_DAYS - 1) : from;
        LocalDate lastDay = from == null ? today : to;
        List<AnalyticsDailySummary> daily = new ArrayList<>();
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            DayTotals totals = totalsByDay.getOrDefault(date, DayTotals.ZERO);
            daily.add(new AnalyticsDailySummary(date, totals.focusSeconds, totals.breakSeconds,
                    totals.completedSessions));
        }
        return daily;
    }

    private static LocalDate parseDate(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw new InvalidDateRangeException();
        }
    }

    private static final class DayTotals {
        private static final DayTotals ZERO = new DayTotals();
        private long focusSeconds;
        private long breakSeconds;
        private long completedSessions;
    }
}
