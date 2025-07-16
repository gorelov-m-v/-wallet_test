package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.GameProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreGameProviderRepository extends JpaRepository<GameProvider, Integer> {
    Optional<GameProvider> findByUuid(String uuid);
}