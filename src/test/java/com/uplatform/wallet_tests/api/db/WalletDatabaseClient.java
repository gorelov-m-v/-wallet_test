package com.uplatform.wallet_tests.api.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.db.entity.wallet.*;
import com.uplatform.wallet_tests.api.db.repository.wallet.*;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Supplier;

@Component
@Slf4j
public class WalletDatabaseClient extends AbstractDatabaseClient {

    private final GamblingProjectionTransactionHistoryRepository transactionRepository;
    private final PlayerThresholdWinRepository playerThresholdWinRepository;
    private final WalletGameSessionRepository walletGameSessionRepository;
    private final WalletRepository walletRepository;
    private final BettingProjectionIframeHistoryRepository iframeHistoryRepository;
    private final ObjectMapper objectMapper;

    public WalletDatabaseClient(AllureAttachmentService attachmentService,
                                GamblingProjectionTransactionHistoryRepository transactionRepository,
                                PlayerThresholdWinRepository playerThresholdWinRepository,
                                WalletGameSessionRepository walletGameSessionRepository,
                                WalletRepository walletRepository,
                                BettingProjectionIframeHistoryRepository iframeHistoryRepository,
                                ObjectMapper objectMapper) {
        super(attachmentService);
        this.transactionRepository = transactionRepository;
        this.playerThresholdWinRepository = playerThresholdWinRepository;
        this.walletGameSessionRepository = walletGameSessionRepository;
        this.walletRepository = walletRepository;
        this.iframeHistoryRepository = iframeHistoryRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public GamblingProjectionTransactionHistory findTransactionByUuidOrFail(String uuid) {
        String description = String.format("transaction history record by UUID '%s'", uuid);
        String attachmentNamePrefix = String.format("Wallet Transaction Record [UUID: %s]", uuid);
        Supplier<Optional<GamblingProjectionTransactionHistory>> querySupplier = () ->
                transactionRepository.findById(uuid);
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public PlayerThresholdWin findThresholdByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("player threshold win record for player '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Player Threshold Win [Player: %s]", playerUuid);
        Supplier<Optional<PlayerThresholdWin>> querySupplier = () ->
                Optional.ofNullable(playerThresholdWinRepository.findByPlayerUuid(playerUuid));
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public WalletGameSession findSingleGameSessionByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("single game session for player UUID '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Wallet Game Session [PlayerUUID: %s]", playerUuid);
        Supplier<Optional<WalletGameSession>> querySupplier = () ->
                Optional.ofNullable(walletGameSessionRepository.findByPlayerUuid(playerUuid));
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public Wallet findWalletByUuidOrFail(String walletUuid) {
        String description = String.format("wallet record by UUID '%s'", walletUuid);
        String attachmentPrefix = String.format("Wallet Record [UUID: %s]", walletUuid);
        Supplier<Optional<Wallet>> querySupplier = () ->
                Optional.ofNullable(walletRepository.findByUuid(walletUuid));
        return awaitAndGetOrFail(description, attachmentPrefix, querySupplier);
    }

    @Transactional(readOnly = true)
    public BettingProjectionIframeHistory findLatestIframeHistoryByUuidOrFail(String uuid) {
        String description = String.format("latest betting iframe history record by UUID '%s'", uuid);
        String attachmentNamePrefix = String.format("Betting Iframe History [UUID: %s, Latest]", uuid);

        Supplier<Optional<BettingProjectionIframeHistory>> querySupplier = () ->
                iframeHistoryRepository.findFirstByUuidOrderBySeqDesc(uuid);

        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    @Override
    protected String createJsonAttachment(Object object) {
        if (object == null) {
            return "null";
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON for Allure attachment: {}", e.getMessage());
            return createSerializationErrorAttachmentContent(object, e);
        }
    }

    private String createSerializationErrorAttachmentContent(Object object, JsonProcessingException e) {
        return String.format("Status: Failed to serialize object to JSON\nType: %s\nError: %s\n\nData (toString()):\n%s",
                object.getClass().getName(),
                e.getMessage(),
                object);
    }
}