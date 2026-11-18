package com.focusdesktop.infrastructure.persistence.jpa;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAppSettingsRepository extends JpaRepository<AppSettingsJpaEntity, String> { }

interface SpringDataTimerProfileRepository extends JpaRepository<TimerProfileJpaEntity, UUID> {
    List<TimerProfileJpaEntity> findAllBySettings_Id(String settingsId);
    void deleteAllBySettings_Id(String settingsId);
}
