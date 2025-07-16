package com.uplatform.wallet_tests.api.db.repository.wallet;

import com.uplatform.wallet_tests.api.db.entity.wallet.PlayerThresholdWin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerThresholdWinRepository
        extends JpaRepository<PlayerThresholdWin, String> {
     PlayerThresholdWin findByPlayerUuid(String playerUuid);
}