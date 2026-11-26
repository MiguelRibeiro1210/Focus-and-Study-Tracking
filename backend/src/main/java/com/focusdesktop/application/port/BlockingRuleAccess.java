package com.focusdesktop.application.port;

import com.focusdesktop.application.dto.BlockingRuleDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence metadata and transaction locking, separate from pure policy evaluation. */
public interface BlockingRuleAccess {
    void lockActivePolicy();
    List<BlockingRuleDto> list();
    Optional<BlockingRuleDto> find(UUID id);
    void clear();
}
