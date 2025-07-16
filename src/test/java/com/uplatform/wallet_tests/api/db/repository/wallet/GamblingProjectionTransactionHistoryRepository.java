package com.uplatform.wallet_tests.api.db.repository.wallet;

import com.uplatform.wallet_tests.api.db.entity.wallet.GamblingProjectionTransactionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GamblingProjectionTransactionHistoryRepository
        extends JpaRepository<GamblingProjectionTransactionHistory, String> {
    GamblingProjectionTransactionHistory findByUuid(String betUuid);
}