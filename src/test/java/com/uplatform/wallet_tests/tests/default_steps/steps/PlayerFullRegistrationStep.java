package com.uplatform.wallet_tests.tests.default_steps.steps;

import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.cancel_kyc_check.CancelKycCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.update_verification_status.UpdateVerificationStatusRequest;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.check.TokenCheckResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactType;
import com.uplatform.wallet_tests.api.http.fapi.dto.contact_verification.ContactVerificationRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.identity.IdentityVerificationRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.identity.VerificationStatus;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.FullRegistrationRequestBody;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.BonusChoiceType;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.Gender;
import com.uplatform.wallet_tests.api.http.fapi.dto.single_bet.SetSingleBetLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.turnover.SetTurnoverLimitRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactRequest;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactResponse;
import com.uplatform.wallet_tests.api.http.fapi.dto.verify_contact.VerifyContactTypedRequest;
import com.uplatform.wallet_tests.api.kafka.client.PlayerAccountKafkaClient;
import com.uplatform.wallet_tests.api.kafka.dto.PlayerAccountMessage;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsLimitIntervalType;
import com.uplatform.wallet_tests.api.redis.client.PlayerRedisClient;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.fasterxml.jackson.core.type.TypeReference;
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
import static org.junit.jupiter.api.Assertions.*;

@Component
public class PlayerFullRegistrationStep {

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
    public PlayerFullRegistrationStep(FapiClient publicClient,
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

    private static final class RegistrationTestData {
        ContactVerificationRequest verificationRequest;
        PlayerAccountMessage confirmationMessage;
        ResponseEntity<VerifyContactResponse> verifyContactResponse;
        FullRegistrationRequestBody fullRegistrationRequest;
        PlayerAccountMessage fullRegistrationMessage;
        ResponseEntity<TokenCheckResponse> authorizationResponse;
        IdentityVerificationRequest identityRequest;
        VerificationStatus verificationStatus;
        ContactVerificationRequest emailVerificationRequest;
        PlayerAccountMessage emailConfirmationMessage;
        ResponseEntity<VerifyContactResponse> emailVerifyResponse;
        WalletData playerWalletData;
        WalletFullData updatedWalletData;
    }

    public RegisteredPlayerData registerNewPlayerWithKyc() {
        final RegistrationTestData testData = new RegistrationTestData();

        step("Public API: Запрос на верификацию телефона", () -> {
            testData.verificationRequest = ContactVerificationRequest.builder()
                    .contact(get(PHONE))
                    .type(ContactType.PHONE)
                    .build();
            var response = this.publicClient.requestVerification(
                    testData.verificationRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.request_verification.status_code");
        });

        step("Kafka: Ожидание и получение OTP кода", () -> {
            testData.confirmationMessage = this.playerAccountKafkaClient.expectPhoneConfirmationMessage(
                    testData.verificationRequest.getContact());
            assertNotNull(testData.confirmationMessage, "kafka.phone_confirmation.message");
            assertNotNull(testData.confirmationMessage.getContext(), "kafka.phone_confirmation.context");
            assertNotNull(testData.confirmationMessage.getContext().getConfirmationCode(), "kafka.phone_confirmation.code");
        });

        step("Public API: Подтверждение номера телефона", () -> {
            var request = VerifyContactRequest.builder()
                    .contact(testData.verificationRequest.getContact().substring(1))
                    .code(testData.confirmationMessage.getContext().getConfirmationCode())
                    .build();
            testData.verifyContactResponse = this.publicClient.verifyContact(request);
            assertEquals(HttpStatus.OK, testData.verifyContactResponse.getStatusCode(), "fapi.verify_contact.status_code");
            assertNotNull(testData.verifyContactResponse.getBody(), "fapi.verify_contact.body_not_null");
            assertNotNull(testData.verifyContactResponse.getBody().getHash(), "fapi.verify_contact.hash");
        });

        step("Public API: Выполнение полной регистрации пользователя", () -> {
            testData.fullRegistrationRequest = FullRegistrationRequestBody.builder()
                    .currency(this.defaultCurrency)
                    .country(this.defaultCountry)
                    .bonusChoice(BonusChoiceType.NONE)
                    .phone(testData.verificationRequest.getContact().substring(1))
                    .phoneConfirmation(testData.verifyContactResponse.getBody().getHash())
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
            var fullRegResponse = this.publicClient.fullRegistration(testData.fullRegistrationRequest);
            assertEquals(HttpStatus.CREATED, fullRegResponse.getStatusCode(), "fapi.full_registration.status_code");
        });

        step("Kafka: Получение сообщения о регистрации", () -> {
            testData.fullRegistrationMessage = this.playerAccountKafkaClient.expectPlayerSignUpV2FullMessage(
                    testData.verificationRequest.getContact());
            assertNotNull(testData.fullRegistrationMessage, "kafka.player_signup.message");
            assertNotNull(testData.fullRegistrationMessage.getPlayer(), "kafka.player_signup.player");
            assertNotNull(testData.fullRegistrationMessage.getPlayer().getExternalId(), "kafka.player_signup.player_external_id");
        });

        step("Public API: Авторизация", () -> {
            var tokenRequest = TokenCheckRequest.builder()
                    .username(testData.fullRegistrationMessage.getPlayer().getAccountId())
                    .password(testData.fullRegistrationRequest.getPassword())
                    .build();
            testData.authorizationResponse = this.publicClient.tokenCheck(tokenRequest);
            assertEquals(HttpStatus.OK, testData.authorizationResponse.getStatusCode(), "fapi.token_check.status_code");
            assertNotNull(testData.authorizationResponse.getBody(), "fapi.token_check.body_not_null");
            assertNotNull(testData.authorizationResponse.getBody().getToken(), "fapi.token_check.token");
        });

        step("Redis (Player): Получение основного кошелька игрока", () -> {
            var criteria = WalletFilterCriteria.builder()
                    .type(Optional.of(1))
                    .status(Optional.of(1))
                    .currency(Optional.of(this.defaultCurrency))
                    .build();
            testData.playerWalletData = this.playerRedisClient.getPlayerWalletByCriteria(
                    testData.fullRegistrationMessage.getPlayer().getExternalId(),
                    criteria);
            assertNotNull(testData.playerWalletData, "redis.player.wallet.not_found");
            assertNotNull(testData.playerWalletData.getWalletUUID(), "redis.player.wallet.uuid");
            assertNotNull(testData.playerWalletData.getCurrency(), "redis.player.wallet.currency");
        });

        step("Public API: Создание запроса на подтверждение личности", () -> {
            var issuedMillis = java.time.LocalDateTime.now()
                    .minusYears(10)
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli();
            var expiryMillis = java.time.LocalDateTime.now()
                    .plusYears(10)
                    .toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli();
            var request = IdentityVerificationRequest.builder()
                    .number(get(NUMBER, 9))
                    .type("4")
                    .issuedDate(String.format("%.3f", issuedMillis / 1000.0))
                    .expiryDate(String.format("%.3f", expiryMillis / 1000.0))
                    .build();
            testData.identityRequest = request;
            var response = publicClient.createIdentityVerification(
                    testData.authorizationResponse.getBody().getToken(),
                    request.getNumber(),
                    request.getType(),
                    request.getIssuedDate(),
                    request.getExpiryDate());
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.create_identity_verification.status_code");
        });

        step("Public API: Получение статуса верификации", () -> {
            var response = publicClient.getVerificationStatus(
                    testData.authorizationResponse.getBody().getToken());
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.get_verification_status.status_code");
            assertNotNull(response.getBody(), "fapi.get_verification_status.body_not_null");
            assertFalse(response.getBody().isEmpty(), "fapi.get_verification_status.body_not_empty");
            testData.verificationStatus = response.getBody().get(0);
        });

        step("CAP API: Обновление статуса верификации", () -> {
            var request = UpdateVerificationStatusRequest.builder()
                    .note("")
                    .reason("")
                    .status(2)
                    .build();
            var response = capAdminClient.updateVerificationStatus(
                    testData.verificationStatus.getDocumentId(),
                    tokenStorage.getAuthorizationHeader(),
                    platformNodeId,
                    request);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.update_verification_status.status_code");
        });

        step("Public API: Запрос верификации email", () -> {
            testData.emailVerificationRequest = ContactVerificationRequest.builder()
                    .contact(get(EMAIL))
                    .type(ContactType.EMAIL)
                    .build();
            var response = publicClient.requestVerification(testData.emailVerificationRequest);
            assertEquals(HttpStatus.OK, response.getStatusCode(), "fapi.request_verification_email.status_code");
        });

        step("Kafka: Получение сообщения о подтверждении email", () -> {
            testData.emailConfirmationMessage = playerAccountKafkaClient.expectEmailConfirmationMessage(
                    testData.emailVerificationRequest.getContact());
            assertNotNull(testData.emailConfirmationMessage, "kafka.email_confirmation.message");
            assertNotNull(testData.emailConfirmationMessage.getContext(), "kafka.email_confirmation.context");
            assertNotNull(testData.emailConfirmationMessage.getContext().getConfirmationCode(), "kafka.email_confirmation.code");
        });

        step("Public API: Подтверждение email", () -> {
            var request = VerifyContactTypedRequest.builder()
                    .contact(testData.emailVerificationRequest.getContact())
                    .type(ContactType.EMAIL)
                    .code(testData.emailConfirmationMessage.getContext().getConfirmationCode())
                    .build();
            var response = publicClient.verifyContactWithAuth(
                    testData.authorizationResponse.getBody().getToken(),
                    request);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.verify_contact_email.status_code");
        });

        step("Public API: Установка лимита на одиночную ставку", () -> {
            var amount = generateBigDecimalAmount(new BigDecimal("100.00"));
            var request = SetSingleBetLimitRequest.builder()
                    .currency(testData.playerWalletData.getCurrency())
                    .amount(amount.toString())
                    .build();
            var response = publicClient.setSingleBetLimit(
                    testData.authorizationResponse.getBody().getToken(),
                    request);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_single_bet_limit.status_code");
        });

        step("Public API: Установка лимита на оборот средств", () -> {
            var amount = generateBigDecimalAmount(new BigDecimal("1000.00"));
            var request = SetTurnoverLimitRequest.builder()
                    .currency(testData.playerWalletData.getCurrency())
                    .type(NatsLimitIntervalType.DAILY)
                    .startedAt((int) (System.currentTimeMillis() / 1000))
                    .amount(amount.toString())
                    .build();
            var response = publicClient.setTurnoverLimit(
                    testData.authorizationResponse.getBody().getToken(),
                    request);
            assertEquals(HttpStatus.CREATED, response.getStatusCode(), "fapi.set_turnover_limit.status_code");
        });

        step("CAP API: Отмена KYC проверки", () -> {
            var request = CancelKycCheckRequest.builder()
                    .kycCheckProceed(false)
                    .build();
            var response = capAdminClient.cancelKycCheck(
                    testData.fullRegistrationMessage.getPlayer().getExternalId(),
                    tokenStorage.getAuthorizationHeader(),
                    platformNodeId,
                    request);
            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(), "cap.cancel_kyc_check.status_code");
        });

        step("Redis (Wallet): Получение и проверка полных данных кошелька", () -> {
            testData.updatedWalletData = this.walletRedisClient.getWithRetry(
                    testData.playerWalletData.getWalletUUID(),
                    new TypeReference<WalletFullData>() {});
            assertNotNull(testData.updatedWalletData, "redis.wallet.full_data_not_found");
            assertNotNull(testData.updatedWalletData.getPlayerUUID(), "redis.wallet.player_uuid");
        });

        return new RegisteredPlayerData(
                testData.authorizationResponse,
                testData.updatedWalletData);
    }
}