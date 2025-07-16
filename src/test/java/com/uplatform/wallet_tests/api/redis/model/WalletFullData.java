package com.uplatform.wallet_tests.api.redis.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers;
import com.uplatform.wallet_tests.api.redis.model.enums.IFrameRecordType;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletFullData {
    @JsonProperty("WalletUUID")
    private String walletUUID;
    @JsonProperty("PlayerUUID")
    private String playerUUID;
    @JsonProperty("PlayerBonusUUID")
    private String playerBonusUUID;
    @JsonProperty("NodeUUID")
    private String nodeUUID;
    @JsonProperty("Type")
    private int type;
    @JsonProperty("Status")
    private int status;
    @JsonProperty("Valid")
    private boolean valid;
    @JsonProperty("IsGamblingActive")
    private boolean isGamblingActive;
    @JsonProperty("IsBettingActive")
    private boolean isBettingActive;
    @JsonProperty("Currency")
    private String currency;
    @JsonProperty("Balance")
    @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class)
    private BigDecimal balance;
    @JsonProperty("AvailableWithdrawalBalance")
    @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class)
    private BigDecimal availableWithdrawalBalance;
    @JsonProperty("BalanceBefore")
    @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class)
    private BigDecimal balanceBefore;
    @JsonProperty("CreatedAt")
    private long createdAt;
    @JsonProperty("UpdatedAt")
    private long updatedAt;
    @JsonProperty("BlockDate")
    private long blockDate;
    @JsonProperty("SumSubBlockDate")
    private long sumSubBlockDate;
    @JsonProperty("KYCVerificationUpdateTo")
    private long kycVerificationUpdateTo;
    @JsonProperty("LastSeqNumber")
    private int lastSeqNumber;
    @JsonProperty("Default")
    private boolean isDefault;
    @JsonProperty("Main")
    private boolean main;
    @JsonProperty("IsBlocked")
    private boolean isBlocked;
    @JsonProperty("IsKYCUnverified")
    private boolean isKYCUnverified;
    @JsonProperty("IsSumSubVerified")
    private boolean isSumSubVerified;
    @JsonProperty("BonusInfo")
    private BonusInfo bonusInfo;
    @JsonProperty("BonusTransferTransactions")
    private Map<String, Object> bonusTransferTransactions;
    @JsonProperty("Limits")
    private List<LimitData> limits;
    @JsonProperty("IFrameRecords")
    private List<IFrameRecord> iFrameRecords;
    @JsonProperty("Gambling")
    private Map<String, GamblingTransaction> gambling;
    @JsonProperty("Deposits")
    private List<DepositData> deposits;
    @JsonProperty("BlockedAmounts")
    private List<BlockedAmount> blockedAmounts;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BonusInfo {
        @JsonProperty("BonusUUID") private String bonusUUID;
        @JsonProperty("BonusCategory") private String bonusCategory;
        @JsonProperty("PlayerBonusUUID") private String playerBonusUUID;
        @JsonProperty("NodeUUID") private String nodeUUID;
        @JsonProperty("Wager") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal wager;
        @JsonProperty("Threshold") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal threshold;
        @JsonProperty("TransferValue") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal transferValue;
        @JsonProperty("BetMin") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal betMin;
        @JsonProperty("BetMax") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal betMax;
        @JsonProperty("TransferType") private int transferType;
        @JsonProperty("RealPercent") private int realPercent;
        @JsonProperty("BonusPercent") private int bonusPercent;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LimitData {
        @JsonProperty("ExternalID") private String externalID;
        @JsonProperty("LimitType") private String limitType;
        @JsonProperty("IntervalType") private String intervalType;
        @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal amount;
        @JsonProperty("Spent") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal spent;
        @JsonProperty("Rest") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal rest;
        @JsonProperty("CurrencyCode") private String currencyCode;
        @JsonProperty("StartedAt") private Long startedAt;
        @JsonProperty("ExpiresAt") private Long expiresAt;
        @JsonProperty("Status") private boolean status;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IFrameRecord {
        @JsonProperty("UUID") private String uuid;
        @JsonProperty("BetID") private long betID;
        @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal amount;
        @JsonProperty("TotalCoeff") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal totalCoeff;
        @JsonProperty("Time") private Long time;
        @JsonProperty("CreatedAt") private Long createdAt;
        @JsonProperty("Type")
        private IFrameRecordType type;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GamblingTransaction {
        @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal amount;
        @JsonProperty("CreatedAt") private Long createdAt;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DepositData {
        @JsonProperty("UUID") private String uuid;
        @JsonProperty("NodeUUID") private String nodeUUID;
        @JsonProperty("BonusID") private String bonusID;
        @JsonProperty("CurrencyCode") private String currencyCode;
        @JsonProperty("Status") private int status;
        @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal amount;
        @JsonProperty("WageringAmount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal wageringAmount;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BlockedAmount {
        @JsonProperty("UUID") private String uuid;
        @JsonProperty("UserUUID") private String userUUID;
        @JsonProperty("Type") private int type;
        @JsonProperty("Status") private int status;
        @JsonProperty("Amount") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal amount;
        @JsonProperty("DeltaAvailableWithdrawalBalance") @JsonDeserialize(using = NumberDeserializers.BigDecimalDeserializer.class) private BigDecimal deltaAvailableWithdrawalBalance;
        @JsonProperty("Reason") private String reason;
        @JsonProperty("UserName") private String userName;
        @JsonProperty("CreatedAt") private Long createdAt;
        @JsonProperty("ExpiredAt") private Long expiredAt;
    }
}