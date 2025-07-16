package com.uplatform.wallet_tests.api.http.manager.client;

import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentResponse;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.WinRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BalanceResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.BetRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.GamblingResponseBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RefundRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.RollbackRequestBody;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.TournamentRequestBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "managerClient", url = "${app.api.manager.base-url}")
public interface ManagerClient {

    @PostMapping("/_core_gas_processing/bet")
    ResponseEntity<GamblingResponseBody> bet(
            @RequestHeader("X-Casino-Id") String casinoIdHeader,
            @RequestHeader("Signature") String signatureHeader,
            @RequestBody BetRequestBody request
    );

    @PostMapping("/_core_gas_processing/win")
    ResponseEntity<GamblingResponseBody> win(
            @RequestHeader("X-Casino-Id") String casinoIdHeader,
            @RequestHeader("Signature") String signatureHeader,
            @RequestBody WinRequestBody request
    );

    @PostMapping("/_core_gas_processing/refund")
    ResponseEntity<GamblingResponseBody> refund(
            @RequestHeader("X-Casino-Id") String casinoIdHeader,
            @RequestHeader("Signature") String signatureHeader,
            @RequestBody RefundRequestBody request
    );

    @PostMapping("/_core_gas_processing/rollback")
    ResponseEntity<GamblingResponseBody> rollback(
            @RequestHeader("X-Casino-Id") String casinoIdHeader,
            @RequestHeader("Signature") String signatureHeader,
            @RequestBody RollbackRequestBody request
    );

    @PostMapping("/_core_gas_processing/tournament")
    ResponseEntity<GamblingResponseBody> tournament(
            @RequestHeader("X-Casino-Id") String casinoIdHeader,
            @RequestHeader("Signature") String signatureHeader,
            @RequestBody TournamentRequestBody request
    );

    @GetMapping("/_core_gas_processing/balance")
    ResponseEntity<BalanceResponseBody> getBalance(
            @RequestHeader("X-Casino-Id") String casinoIdHeader,
            @RequestHeader("Signature") String signatureHeader,
            @RequestParam("sessionToken") String sessionToken
    );

    @PostMapping(value = "/_wallet_manager/api/v1/wallet/iframe-callback/make-payment",
            headers = {"X-Skip-Auth=true"})
    ResponseEntity<MakePaymentResponse> makePayment(
            @RequestBody MakePaymentRequest request
    );
}