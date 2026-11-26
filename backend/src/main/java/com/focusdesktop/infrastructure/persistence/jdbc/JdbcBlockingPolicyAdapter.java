package com.focusdesktop.infrastructure.persistence.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focusdesktop.application.dto.BlockingRuleDto;
import com.focusdesktop.application.port.BlockingRuleAccess;
import com.focusdesktop.application.usecase.exeptions.BlockingRuleException;
import com.focusdesktop.domain.model.*;
import com.focusdesktop.domain.repository.BlockingPolicyRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Uses the same datasource/transaction as the existing JPA adapters, without managed aggregate snapshots. */
@Repository
public class JdbcBlockingPolicyAdapter implements BlockingPolicyRepository, BlockingRuleAccess {
    private static final UUID POLICY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JdbcBlockingPolicyAdapter(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActivePolicy() {
        jdbc.queryForObject("SELECT id FROM blocking_policy WHERE singleton_slot=1 FOR UPDATE", UUID.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BlockingPolicy> findActive() {
        List<UUID> ids = jdbc.query("SELECT id FROM blocking_policy WHERE singleton_slot=1",
                (row, index) -> row.getObject("id", UUID.class));
        return ids.stream().findFirst().map(id -> new BlockingPolicy(new BlockingPolicyId(id),
                list().stream().map(BlockingRuleDto::toDomain).toList()));
    }

    @Override
    public List<BlockingRuleDto> list() {
        return jdbc.query("SELECT * FROM blocking_rule WHERE policy_id=? ORDER BY created_at, id", this::row, POLICY_ID);
    }

    @Override
    public Optional<BlockingRuleDto> find(UUID id) {
        return jdbc.query("SELECT * FROM blocking_rule WHERE policy_id=? AND id=?", this::row, POLICY_ID, id)
                .stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(BlockingPolicy policy) {
        if (!policy.id().value().equals(POLICY_ID)) throw new IllegalArgumentException("Unknown installation policy");
        Map<UUID, BlockingRuleDto> existing = new HashMap<>();
        list().forEach(dto -> existing.put(dto.id(), dto));
        Set<UUID> retained = new HashSet<>();
        policy.rules().forEach(rule -> retained.add(rule.id().value()));
        for (UUID id : existing.keySet()) {
            if (!retained.contains(id)) jdbc.update("DELETE FROM blocking_rule WHERE policy_id=? AND id=?", POLICY_ID, id);
        }
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            for (BlockingRule rule : policy.rules()) {
                BlockingRuleDto previous = existing.get(rule.id().value());
                var target = BlockingRuleDto.Target.from(rule.target());
                if (previous != null && previous.displayName().equals(rule.displayName())
                        && previous.target().equals(target) && previous.category() == rule.category()
                        && previous.action() == rule.action() && previous.enabled() == rule.enabled()) continue;
                if (previous == null) {
                    jdbc.update("INSERT INTO blocking_rule (id,policy_id,display_name,target_type,platform,match_kind,"
                                    + "target_value,target_key,category,action,enabled,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            rule.id().value(), POLICY_ID, rule.displayName(), target.type(), name(target.platform()),
                            name(target.matchKind()), value(target), key(rule.target()), rule.category().name(),
                            rule.action().name(), rule.enabled(), now, now);
                } else {
                    jdbc.update("UPDATE blocking_rule SET display_name=?,target_type=?,platform=?,match_kind=?,target_value=?,"
                                    + "target_key=?,category=?,action=?,enabled=?,updated_at=? WHERE policy_id=? AND id=?",
                            rule.displayName(), target.type(), name(target.platform()), name(target.matchKind()), value(target),
                            key(rule.target()), rule.category().name(), rule.action().name(), rule.enabled(), now, POLICY_ID, rule.id().value());
                }
            }
            jdbc.update("UPDATE blocking_policy SET version=version+1,updated_at=? WHERE id=?", now, POLICY_ID);
        } catch (DuplicateKeyException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase(Locale.ROOT).contains("uq_blocking_rule_target")) {
                throw BlockingRuleException.duplicate();
            }
            throw exception;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void clear() {
        jdbc.update("DELETE FROM blocking_rule WHERE policy_id=?", POLICY_ID);
        jdbc.update("UPDATE blocking_policy SET applications_enabled=FALSE,application_consent_at=NULL,websites_enabled=FALSE,website_consent_at=NULL,version=version+1,updated_at=? WHERE id=?",
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC), POLICY_ID);
    }

    private BlockingRuleDto row(ResultSet row, int index) throws SQLException {
        String type = row.getString("target_type");
        var target = type.equals("WEBSITE")
                ? new BlockingRuleDto.Target(type, null, null, null, row.getString("target_value"))
                : new BlockingRuleDto.Target(type, TargetPlatform.valueOf(row.getString("platform")),
                        ApplicationMatchKind.valueOf(row.getString("match_kind")), row.getString("target_value"), null);
        return new BlockingRuleDto(row.getObject("id", UUID.class), row.getString("display_name"), target,
                DistractionCategory.valueOf(row.getString("category")), BlockingAction.valueOf(row.getString("action")),
                row.getBoolean("enabled"), row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private String key(DistractionTarget target) {
        List<String> components = target instanceof ApplicationTarget app
                ? List.of("APPLICATION", app.platform().name(), app.matchKind().name(), app.canonicalValue())
                : List.of("WEBSITE", ((WebsiteTarget) target).domain());
        try { return mapper.writeValueAsString(components); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not encode target key", exception); }
    }

    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static String value(BlockingRuleDto.Target target) { return target.domain() == null ? target.value() : target.domain(); }
}
