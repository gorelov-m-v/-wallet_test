package com.uplatform.wallet_tests.api.db.repository.wallet;

import com.uplatform.wallet_tests.api.db.entity.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {
    Wallet findByUuid(String uuid);
}