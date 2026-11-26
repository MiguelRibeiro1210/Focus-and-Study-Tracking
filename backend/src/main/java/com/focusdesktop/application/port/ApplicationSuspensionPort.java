package com.focusdesktop.application.port;

public interface ApplicationSuspensionPort {
    void suspend(ActiveApplicationPort.Observation observation);
    void resume(ActiveApplicationPort.Observation observation);
    boolean isSuspended(ActiveApplicationPort.Observation observation);
    void releaseAll();
    void recover();
}
