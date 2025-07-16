package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreGameRepository extends JpaRepository<CoreGame, Integer> {
    Optional<CoreGame> findByUuid(String uuid);
    Optional<CoreGame> findByAlias(String alias);
}