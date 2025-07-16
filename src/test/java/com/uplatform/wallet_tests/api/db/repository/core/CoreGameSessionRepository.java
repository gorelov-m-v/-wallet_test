package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreGameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface CoreGameSessionRepository extends JpaRepository<CoreGameSession, Integer>, JpaSpecificationExecutor<CoreGameSession> {

    CoreGameSession findByPlayerUuidOrderByStartedAtDesc(String playerUuid);
}