package com.ppiyaki.user.service;

import java.util.Optional;

public interface InviteCodeStore {

    void save(String codeHash, Long seniorId, long ttlSeconds);

    Optional<Long> consume(String codeHash);
}
