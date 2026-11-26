package com.focusdesktop.application.port;

import com.focusdesktop.application.dto.*;
import java.time.Instant;
import java.util.*;

public interface DistractionLogReader {
    DistractionLogPage find(UUID sessionId, Instant fromInclusive, Instant toExclusive, int page, int size);
    List<DistractionLogDto> exportAll();
}
