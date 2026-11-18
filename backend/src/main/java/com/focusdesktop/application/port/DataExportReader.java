package com.focusdesktop.application.port;

import com.focusdesktop.application.dto.DataExportBundle.SessionExport;
import java.util.List;

public interface DataExportReader {
    List<SessionExport> loadAllSessions();
}
