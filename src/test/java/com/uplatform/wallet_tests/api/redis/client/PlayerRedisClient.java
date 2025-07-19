package com.uplatform.wallet_tests.api.redis.client;

import com.uplatform.wallet_tests.api.redis.exception.RedisClientException;
import com.uplatform.wallet_tests.api.redis.model.WalletData;
import com.uplatform.wallet_tests.api.redis.model.WalletFilterCriteria;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

@Slf4j
public class PlayerRedisClient extends AbstractRedisClient<Map<String, WalletData>> {

    public PlayerRedisClient(@Qualifier("playerRedisTemplate") RedisTemplate<String, String> redisTemplate,
                             RedisRetryHelper retryHelper,
                             AllureAttachmentService attachmentService) {
        super("PLAYER", redisTemplate, retryHelper, attachmentService,
                new TypeReference<Map<String, WalletData>>() {});
    }

    private boolean matchesCriteria(WalletData wallet, WalletFilterCriteria criteria) {
        if (wallet == null || criteria == null) {
            return false;
        }
        return criteria.getCurrency().map(c -> Objects.equals(c, wallet.getCurrency())).orElse(true)
                && criteria.getType().map(t -> Objects.equals(t, wallet.getType())).orElse(true)
                && criteria.getStatus().map(s -> Objects.equals(s, wallet.getStatus())).orElse(true);
    }

    public WalletData getPlayerWalletByCriteria(String playerId, WalletFilterCriteria criteria) {
        if (playerId == null) { throw new RedisClientException("[PLAYER] Cannot get wallet: playerId is null."); }
        if (criteria == null) { throw new RedisClientException("[PLAYER] Cannot get wallet: criteria is null."); }

        String criteriaDesc = describeCriteria(criteria);

        BiFunction<Map<String, WalletData>, String, CheckResult> checkMapAndCriteria = (walletsMap, rawJson) -> {
            if (CollectionUtils.isEmpty(walletsMap)) {
                return new CheckResult(false, "Wallets map is null or empty");
            }
            boolean matchFound = walletsMap.values().stream().anyMatch(wallet -> matchesCriteria(wallet, criteria));
            if (matchFound) {
                return new CheckResult(true, "Map contains matching wallet for criteria: " + criteriaDesc);
            } else {
                return new CheckResult(false, "Map found, but no wallet matches criteria: " + criteriaDesc);
            }
        };

        Map<String, WalletData> walletsMapResult = getWithCheck(playerId, checkMapAndCriteria);

        Optional<WalletData> foundWallet = walletsMapResult.values().stream()
                .filter(wallet -> matchesCriteria(wallet, criteria))
                .findFirst();

        WalletData wallet = foundWallet.orElseThrow(() -> new RedisClientException(String.format(
                "[PLAYER] Internal error: Wallet matching criteria %s for player '%s' found during check but lost afterwards.",
                criteriaDesc, playerId)));

        log.info("<<< Found wallet {} matching criteria {} for player {} >>>", wallet.getWalletUUID(), criteriaDesc, playerId);
        attachmentService.attachText("Found Player Wallet", retryHelper.createAttachmentContent(
                instanceName,
                playerId + " [Criteria: " + criteriaDesc + "]",
                wallet,
                null,
                "Wallet found matching criteria"
        ));
        return wallet;
    }
}
