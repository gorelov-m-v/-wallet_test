package com.uplatform.wallet_tests.tests.default_steps.steps;

import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.DirectionType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.OperationType;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.enums.ReasonType;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactType;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactVerificationRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.FullRegistrationRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.BonusChoiceType;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.Gender;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactResponse;
import com.uplatform.wallet_tests.api.kafka.client.PlayerAccountKafkaClient;
import com.uplatform.wallet_tests.api.kafka.dto.PlayerAccountMessage;
import com.uplatform.wallet_tests.api.redis.client.PlayerRedisClient;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.api.redis.model.WalletData;
import com.uplatform.wallet_tests.api.redis.model.WalletFilterCriteria;
import com.uplatform.wallet_tests.api.redis.model.WalletFullData;
import com.uplatform.wallet_tests.tests.default_steps.dto.RegisteredPlayerData;
import com.uplatform.wallet_tests.tests.util.utils.CapAdminTokenStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

import static com.uplatform.wallet_tests.tests.util.utils.StringGeneratorUtil.*;
import static io.qameta.allure.Allure.step;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Component
public class PlayerRegistrationStep {

    private final FapiClient publicClient;
    private final CapAdminClient capAdminClient;
    private final PlayerAccountKafkaClient playerAccountKafkaClient;
    private final PlayerRedisClient playerRedisClient;
    private final WalletRedisClient walletRedisClient;
    private final CapAdminTokenStorage tokenStorage;
    private final String defaultCurrency;
    private final String defaultCountry;
    private final String platformNodeId;

    @Autowired
    public PlayerRegistrationStep(FapiClient publicClient,
                                  CapAdminClient capAdminClient,
                                  PlayerAccountKafkaClient playerAccountKafkaClient,
                                  PlayerRedisClient playerRedisClient,
                                  WalletRedisClient walletRedisClient,
                                  CapAdminTokenStorage tokenStorage,
                                  @Value("${app.settings.default.currency}") String defaultCurrency,
                                  @Value("${app.settings.default.country}") String defaultCountry,
                                  @Value("${app.settings.default.platform-node-id}") String platformNodeId) {
        this.publicClient = Objects.requireNonNull(publicClient);
        this.capAdminClient = Objects.requireNonNull(capAdminClient);
        this.playerAccountKafkaClient = Objects.requireNonNull(playerAccountKafkaClient);
        this.playerRedisClient = Objects.requireNonNull(playerRedisClient);
        this.walletRedisClient = Objects.requireNonNull(walletRedisClient);
        this.tokenStorage = Objects.requireNonNull(tokenStorage);
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency);
        this.defaultCountry = Objects.requireNonNull(defaultCountry);
        this.platformNodeId = Objects.requireNonNull(platformNodeId);
    }

    private static final class TestContext {
        ContactVerificationRequest verificationRequest;
        PlayerAccountMessage confirmationMessage;
        ResponseEntity<VerifyContactResponse> verifyContactResponse;
        FullRegistrationRequestBody fullRegistrationRequest;
        PlayerAccountMessage fullRegistrationMessage;
        ResponseEntity<TokenCheckResponse> authorizationResponse;
        WalletData playerWalletData;
        WalletFullData updatedWalletData;
    }

    public RegisteredPlayerData registerNewPlayer(BigDecimal adjustmentAmount) {
        final TestContext ctx = new TestContext();

        step("Public API: Запрос на верификацию телефона", () -> {
            ctx.verificationRequest = ContactVerificationRequest.builder()
                    .contact(get(PHONE))
                    .type(ContactType.PHONE)
                    .build();
            var response = this.publicClient.requestVerification(
                    ctx.verificationRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.request_verification.status_code");
        });

        step("Kafka: Ожидание и получение OTP кода", () -> {
            ctx.confirmationMessage = this.playerAccountKafkaClient.expectPhoneConfirmationMessage(
                    ctx.verificationRequest.getContact());
            assertNotNull(ctx.confirmationMessage, "kafka.phone_confirmation.message");
            assertNotNull(ctx.confirmationMessage.getContext(), "kafka.phone_confirmation.context");
            assertNotNull(ctx.confirmationMessage.getContext().getConfirmationCode(), "kafka.phone_confirmation.code");
        });

        step("Public API: Подтверждение номера телефона", () -> {
            var request = VerifyContactRequest.builder()
                    .contact(ctx.verificationRequest.getContact().substring(1))
                    .code(ctx.confirmationMessage.getContext().getConfirmationCode())
                    .build();
            ctx.verifyContactResponse = this.publicClient.verifyContact(request);
            assertEquals(HttpStatus.OK, ctx.verifyContactResponse.getStatusCode(), "fapi.verify_contact.status_code");
            assertNotNull(ctx.verifyContactResponse.getBody(), "fapi.verify_contact.body_not_null");
            assertNotNull(ctx.verifyContactResponse.getBody().getHash(), "fapi.verify_contact.hash");
        });

        step("Public API: Выполнение полной регистрации пользователя", () -> {
            ctx.fullRegistrationRequest = FullRegistrationRequestBody.builder()
                    .currency(this.defaultCurrency)
                    .country(this.defaultCountry)
                    .bonusChoice(BonusChoiceType.NONE)
                    .phone(ctx.verificationRequest.getContact().substring(1))
                    .phoneConfirmation(ctx.verifyContactResponse.getBody().getHash())
                    .firstName(get(NAME))
                    .lastName(get(NAME))
                    .birthday("1991-05-15")
                    .gender(Gender.MALE)
                    .personalId(get(PERSONAL_ID))
                    .iban(get(IBAN))
                    .city("Riga")
                    .permanentAddress("Brivibas iela 1")
                    .postalCode("LV-1010")
                    .profession("Developer")
                    .password(get(PASSWORD))
                    .rulesAgreement(true)
                    .context(Collections.emptyMap())
                    .build();
            var fullRegResponse = this.publicClient.fullRegistration(ctx.fullRegistrationRequest);
            assertEquals(HttpStatus.CREATED, fullRegResponse.getStatusCode(), "fapi.full_registration.status_code");
        });

        step("Kafka: Получение сообщения о регистрации", () -> {
            ctx.fullRegistrationMessage = this.playerAccountKafkaClient.expectPlayerSignUpV2FullMessage(
                    ctx.verificationRequest.getContact());
            assertNotNull(ctx.fullRegistrationMessage, "kafka.player_signup.message");
            assertNotNull(ctx.fullRegistrationMessage.getPlayer(), "kafka.player_signup.player");
            assertNotNull(ctx.fullRegistrationMessage.getPlayer().getExternalId(), "kafka.player_signup.player_external_id");
        });
        
        

        step("Public API: Авторизация", () -> {
            var tokenRequest = TokenCheckRequest.builder()
                    .username(ctx.fullRegistrationMessage.getPlayer().getAccountId())
                    .password(ctx.fullRegistrationRequest.getPassword())
                    .build();
            ctx.authorizationResponse = this.publicClient.tokenCheck(tokenRequest);
            assertEquals(HttpStatus.OK, ctx.authorizationResponse.getStatusCode(), "fapi.token_check.status_code");
            assertNotNull(ctx.authorizationResponse.getBody(), "fapi.token_check.body_not_null");
            assertNotNull(ctx.authorizationResponse.getBody().getToken(), "fapi.token_check.token");
        });

        step("Redis (Player): Получение основного кошелька игрока", () -> {
            var criteria = WalletFilterCriteria.builder()
                    .type(Optional.of(1))
                    .status(Optional.of(1))
                    .currency(Optional.of(this.defaultCurrency))
                    .build();
            ctx.playerWalletData = this.playerRedisClient.getPlayerWalletByCriteria(
                    ctx.fullRegistrationMessage.getPlayer().getExternalId(),
                    criteria);
            assertNotNull(ctx.playerWalletData, "redis.player.wallet.not_found");
            assertNotNull(ctx.playerWalletData.getWalletUUID(), "redis.player.wallet.uuid");
            assertNotNull(ctx.playerWalletData.getCurrency(), "redis.player.wallet.currency");
        });

        if (adjustmentAmount != null && adjustmentAmount.compareTo(BigDecimal.ZERO) > 0) {
            step("CAP API: Корректировка баланса на " + adjustmentAmount + " " + ctx.playerWalletData.getCurrency(), () -> {
                var request = CreateBalanceAdjustmentRequest.builder()
                        .currency(ctx.playerWalletData.getCurrency())
                        .amount(adjustmentAmount)
                        .reason(ReasonType.MALFUNCTION)
                        .operationType(OperationType.CORRECTION)
                        .direction(DirectionType.INCREASE)
                        .build();
                var response = capAdminClient.createBalanceAdjustment(
                        ctx.fullRegistrationMessage.getPlayer().getExternalId(),
                        tokenStorage.getAuthorizationHeader(),
                        platformNodeId,
                        "6dfe249e-e967-477b-8a42-83efe85c7c3a",
                        request);
                assertEquals(HttpStatus.OK, response.getStatusCode(), "cap.create_balance_adjustment.status_code");
            });
        }

        step("Redis (Wallet): Получение и проверка полных данных кошелька", () -> {
            ctx.updatedWalletData = this.walletRedisClient.getWithRetry(
                    ctx.playerWalletData.getWalletUUID());
            assertNotNull(ctx.updatedWalletData, "redis.wallet.full_data_not_found");
            assertNotNull(ctx.updatedWalletData.getPlayerUUID(), "redis.wallet.player_uuid");
        });

        return new RegisteredPlayerData(
                ctx.authorizationResponse,
                ctx.updatedWalletData);
    }
}