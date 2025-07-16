package com.uplatform.wallet_tests.api.http.cap.client;

import com.uplatform.wallet_tests.api.http.cap.dto.cancel_kyc_check.CancelKycCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.check.CapTokenCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.check.CapTokenCheckResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.create_balance_adjustment.CreateBalanceAdjustmentRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.create_block_amount.CreateBlockAmountResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.get_block_amount_list.BlockAmountListResponseBody;
import com.uplatform.wallet_tests.api.http.cap.dto.get_blockers.GetBlockersResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.get_player_limits.GetPlayerLimitsResponse;
import com.uplatform.wallet_tests.api.http.cap.dto.update_blockers.UpdateBlockersRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.update_verification_status.UpdateVerificationStatusRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "capAdminClient", url = "${app.api.cap.base-url}")
public interface CapAdminClient {

    @PatchMapping("/_cap/player/api/v1/admin/players/{playerId}/properties")
    ResponseEntity<Void> cancelKycCheck(
            @PathVariable("playerId") String playerId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CancelKycCheckRequest request
    );

    @PostMapping("/_cap/api/token/check")
    ResponseEntity<CapTokenCheckResponse> getToken(@RequestBody CapTokenCheckRequest request);

    @PostMapping("/_cap/api/v1/wallet/{playerUUID}/create-balance-adjustment")
    ResponseEntity<Void> createBalanceAdjustment(
            @PathVariable("playerUUID") String walletId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestHeader("Platform-Userid") String userId,
            @RequestBody CreateBalanceAdjustmentRequest request
    );

    @PatchMapping("/_cap/api/v1/players/{playerUUID}/blockers")
    ResponseEntity<Void> updateBlockers(
            @PathVariable("playerUUID") String playerUUID,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody UpdateBlockersRequest request
    );

    @GetMapping("/_cap/api/v1/players/{playerUUID}/blockers")
    ResponseEntity<GetBlockersResponse> getBlockers(
            @PathVariable("playerUUID") String playerUUID,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId
    );

    @GetMapping(
            value = "/_cap/api/v1/player/{playerID}/limits",
            params = {"sort", "page", "perPage"}
    )
    ResponseEntity<GetPlayerLimitsResponse> getPlayerLimits(
            @PathVariable("playerID") String playerId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestParam(name = "sort", defaultValue = "status") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "perPage", defaultValue = "10") int perPage
    );
    default ResponseEntity<GetPlayerLimitsResponse> getPlayerLimits(
            String playerId,
            String authorizationHeader,
            String platformNodeId) {
        return getPlayerLimits(playerId, authorizationHeader, platformNodeId,
                "status", 1, 10);
    }

    @PostMapping("/_cap/api/v1/wallet/{playerUuid}/create-block-amount")
    ResponseEntity<CreateBlockAmountResponse> createBlockAmount(
            @PathVariable("playerUuid") String playerUuid,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody CreateBlockAmountRequest request
    );

    @GetMapping("/_cap/api/v1/wallet/{player_uuid}/block-amount-list")
    ResponseEntity<BlockAmountListResponseBody> getBlockAmountList(
            @RequestHeader("Authorization")     String authorizationHeader,
            @RequestHeader("Platform-NodeID")   String platformNodeId,
            @PathVariable("player_uuid")        String playerUuid);
            
    @DeleteMapping("/_cap/api/v1/wallet/delete-amount-block/{block_uuid}")
    ResponseEntity<Void> deleteBlockAmount(
            @PathVariable("block_uuid") String blockUuid,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestParam("walletId") String walletId,
            @RequestParam("playerId") String playerId
    );

    @PatchMapping("/_cap/api/v1/players/verification/{documentId}")
    ResponseEntity<Void> updateVerificationStatus(
            @PathVariable("documentId") String documentId,
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestHeader("Platform-NodeID") String platformNodeId,
            @RequestBody UpdateVerificationStatusRequest request
    );
}