package com.uplatform.wallet_tests.api.db.repository.wallet;

import com.uplatform.wallet_tests.api.db.entity.wallet.BettingProjectionIframeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BettingProjectionIframeHistoryRepository
        extends JpaRepository<BettingProjectionIframeHistory, String> {

    Optional<BettingProjectionIframeHistory> findFirstByUuidOrderBySeqDesc(String uuid);
}