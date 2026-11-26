package com.focusdesktop.domain.repository;

import com.focusdesktop.domain.model.BlockingPolicy;
import java.util.Optional;

/** FST-048 supplies persistence and enforces the single-active-policy constraint. */
public interface BlockingPolicyRepository {
    Optional<BlockingPolicy> findActive();

    void save(BlockingPolicy policy);
}
