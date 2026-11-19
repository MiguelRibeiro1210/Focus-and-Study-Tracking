package com.focusdesktop.infrastructure.persistence.jpa;

import com.focusdesktop.application.port.SessionSettingsProvider;
import com.focusdesktop.domain.model.AppSettings;
import com.focusdesktop.domain.model.ApplicationTheme;
import com.focusdesktop.domain.model.DailyFocusGoal;
import com.focusdesktop.domain.model.DataRetentionPolicy;
import com.focusdesktop.domain.model.TimerPreferences;
import com.focusdesktop.domain.model.TimerProfile;
import com.focusdesktop.domain.model.TimerProfileId;
import com.focusdesktop.domain.repository.AppSettingsRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JpaAppSettingsRepositoryAdapter implements AppSettingsRepository, SessionSettingsProvider {
    private static final String INSTALLATION_ID = "installation";
    private final SpringDataAppSettingsRepository settings;
    private final SpringDataTimerProfileRepository profiles;
    private final EntityManager entityManager;
    private final TransactionTemplate initializationTransaction;
    private final Object initializationLock = new Object();

    public JpaAppSettingsRepositoryAdapter(SpringDataAppSettingsRepository settings,
                                           SpringDataTimerProfileRepository profiles,
                                           EntityManager entityManager,
                                           PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.profiles = profiles;
        this.entityManager = entityManager;
        this.initializationTransaction = new TransactionTemplate(transactionManager);
        this.initializationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public AppSettings loadOrCreate() {
        AppSettingsJpaEntity entity = settings.findById(INSTALLATION_ID).orElseGet(() -> {
            initializeDefaults();
            return settings.findById(INSTALLATION_ID).orElseThrow();
        });
        return toDomain(entity);
    }

    private void initializeDefaults() {
        // Hold the lock through commit, including when the caller already has a transaction.
        synchronized (initializationLock) {
            initializationTransaction.executeWithoutResult(status -> {
                if (settings.findById(INSTALLATION_ID).isEmpty()) {
                    persistNew(AppSettings.defaults());
                }
            });
        }
    }

    @Override
    @Transactional
    public AppSettings save(AppSettings aggregate) {
        AppSettingsJpaEntity entity = settings.findById(aggregate.id())
                .orElseGet(() -> settings.save(new AppSettingsJpaEntity(aggregate.id())));
        entity.apply(aggregate.defaultProfileId().value(), aggregate.dailyFocusGoal().hours(),
                aggregate.theme(), aggregate.notificationsEnabled(), aggregate.retentionPolicy().retentionDays());
        entity = settings.saveAndFlush(entity);
        List<TimerProfileJpaEntity> existingProfiles = profiles.findAllBySettings_Id(entity.id());
        Map<UUID, TimerProfileJpaEntity> existingById = existingProfiles.stream()
                .collect(Collectors.toMap(TimerProfileJpaEntity::id, Function.identity()));
        java.util.Set<UUID> retainedIds = aggregate.profiles().stream()
                .map(profile -> profile.id().value()).collect(Collectors.toSet());
        List<UUID> removedIds = existingProfiles.stream().map(TimerProfileJpaEntity::id)
                .filter(id -> !retainedIds.contains(id)).toList();
        if (!removedIds.isEmpty()) profiles.deleteAllByIdInBatch(removedIds);
        for (TimerProfile profile : aggregate.profiles()) {
            TimerProfileJpaEntity row = existingById.getOrDefault(profile.id().value(),
                    new TimerProfileJpaEntity(profile.id().value(), entity));
            row.apply(profile.name(), profile.preferences());
            profiles.save(row);
        }
        profiles.flush();
        return toDomain(entity);
    }

    @Override
    @Transactional
    public AppSettings resetToDefaults() {
        profiles.deleteAllInBatch();
        settings.deleteAllInBatch();
        entityManager.clear();
        return toDomain(persistNew(AppSettings.defaults()));
    }

    @Override
    @Transactional
    public java.util.Optional<com.focusdesktop.domain.model.SessionSettings> findCurrentSettings() {
        return java.util.Optional.of(loadOrCreate().defaultProfile().preferences().toSessionSettings());
    }

    private AppSettingsJpaEntity persistNew(AppSettings aggregate) {
        AppSettingsJpaEntity entity = new AppSettingsJpaEntity(aggregate.id());
        entity.apply(aggregate.defaultProfileId().value(), aggregate.dailyFocusGoal().hours(),
                aggregate.theme(), aggregate.notificationsEnabled(), aggregate.retentionPolicy().retentionDays());
        entity = settings.saveAndFlush(entity);
        for (TimerProfile profile : aggregate.profiles()) {
            TimerProfileJpaEntity row = new TimerProfileJpaEntity(profile.id().value(), entity);
            row.apply(profile.name(), profile.preferences());
            profiles.save(row);
        }
        profiles.flush();
        return entity;
    }

    private AppSettings toDomain(AppSettingsJpaEntity entity) {
        List<TimerProfile> profileList = profiles.findAllBySettings_Id(entity.id()).stream()
                .map(row -> new TimerProfile(new TimerProfileId(row.id()), row.name(), row.preferences())).toList();
        return new AppSettings(entity.id(), profileList, new TimerProfileId(entity.defaultProfileId()),
                new DailyFocusGoal(entity.dailyGoalHours()), entity.theme(), entity.notificationsEnabled(),
                new DataRetentionPolicy(entity.retentionDays()));
    }
}
