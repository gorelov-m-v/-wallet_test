package com.uplatform.wallet_tests.api.db.repository.core;

import com.uplatform.wallet_tests.api.db.entity.core.CoreWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CoreWalletRepository extends JpaRepository<CoreWallet, Integer> {
    Optional<CoreWallet> findByUuid(String uuid);
}