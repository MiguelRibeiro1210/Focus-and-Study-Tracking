package com.focusdesktop.domain.factories.snapshots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.focusdesktop.domain.factories.FocusSessionFactory;
import com.focusdesktop.domain.model.Duration;
import com.focusdesktop.domain.model.FocusSession;
import com.focusdesktop.domain.model.IntervalId;
import com.focusdesktop.domain.model.IntervalState;
import com.focusdesktop.domain.model.SessionId;
import com.focusdesktop.domain.model.SessionSettings;
import com.focusdesktop.domain.model.SessionState;
import com.focusdesktop.domain.model.SessionType;
import com.focusdesktop.domain.model.TimeStamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FocusSessionSnapshotTest {
    private static final TimeStamp T0 = at(0);

    @Test
    void keepsItsIntervalCollectionIndependentAndUnmodifiable() {
        List<SessionIntervalSnapshot> source = new ArrayList<>();
        FocusSessionSnapshot snapshot = new FocusSessionSnapshot(SessionId.generate(), SessionType.WORK,
                settings(25), T0, SessionState.IDLE, null, null, source);
        SessionIntervalSnapshot interval = pendingWorkInterval();

        source.add(interval);

        assertEquals(0, snapshot.intervals().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.intervals().add(interval));
    }

    @Test
    void preservesTheSessionSettingsWhenPreferencesAreReplaced() {
        SessionSettings originalSettings = settings(25);
        FocusSession session = FocusSessionFactory.create(originalSettings, T0);
        FocusSessionSnapshot snapshot = session.snapshot();
        SessionSettings updatedPreferences = settings(50);
        FocusSession nextSession = FocusSessionFactory.create(updatedPreferences, at(1));

        FocusSession restored = FocusSessionFactory.reconstitute(snapshot);

        assertEquals(25 * 60L, snapshot.settings().workDuration().seconds());
        assertEquals(25 * 60L, restored.settings().workDuration().seconds());
        assertEquals(50 * 60L, nextSession.settings().workDuration().seconds());
    }

    private static SessionIntervalSnapshot pendingWorkInterval() {
        return new SessionIntervalSnapshot(IntervalId.generate(), 1, SessionType.WORK,
                Duration.ofSeconds(60), Duration.ofSeconds(0), Duration.ofSeconds(60),
                IntervalState.PENDING, null, null, null);
    }

    private static SessionSettings settings(long workMinutes) {
        return new SessionSettings(Duration.ofSeconds(workMinutes * 60), Duration.ofSeconds(5 * 60),
                Duration.ofSeconds(10 * 60), 4, true, true);
    }

    private static TimeStamp at(long seconds) {
        return new TimeStamp(Instant.parse("2026-01-01T00:00:00Z").plusSeconds(seconds));
    }
}
