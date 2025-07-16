package com.uplatform.wallet_tests.api.db.repository.wallet;

import com.uplatform.wallet_tests.api.db.entity.wallet.WalletGameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletGameSessionRepository extends JpaRepository<WalletGameSession, String> {
    WalletGameSession findByPlayerUuid(String playerUuid);
}