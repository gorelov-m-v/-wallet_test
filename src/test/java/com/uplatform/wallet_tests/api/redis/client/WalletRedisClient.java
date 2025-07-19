package com.uplatform.wallet_tests.api.redis.client;

import com.uplatform.wallet_tests.api.redis.exception.RedisClientException;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
public class WalletRedisClient extends AbstractRedisClient<WalletFullData> {

    public WalletRedisClient(@Qualifier("walletRedisTemplate") RedisTemplate<String, String> redisTemplate,
                             RedisRetryHelper retryHelper,
                             AllureAttachmentService attachmentService) {
        super("WALLET", redisTemplate, retryHelper, attachmentService,
                new TypeReference<WalletFullData>() {});
    }

    public WalletFullData getWalletDataWithSeqCheck(String key, int expectedSeq) {
        if (key == null) { throw new RedisClientException("[WALLET] Cannot check wallet sequence: key is null."); }
        Function<WalletFullData, Integer> seqExtractor = WalletFullData::getLastSeqNumber;
        BiFunction<WalletFullData, String, CheckResult> checkFunc = (data, rawJson) -> {
            if (data == null) {
                return new CheckResult(false, "Deserialized data is null");
            }
            int currentSeq = seqExtractor.apply(data);
            if (currentSeq >= expectedSeq) {
                return new CheckResult(true, String.format("Sequence match: current=%d, expected=%d", currentSeq, expectedSeq));
            }
            return new CheckResult(false, String.format("Sequence mismatch: current=%d, expected=%d", currentSeq, expectedSeq));
        };
        return getWithCheck(key, checkFunc);
    }
}
