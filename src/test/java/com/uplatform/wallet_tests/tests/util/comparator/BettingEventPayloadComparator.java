package com.uplatform.wallet_tests.tests.util.comparator;

import com.uplatform.wallet_tests.api.nats.dto.NatsBettingEventPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Slf4j
public class BettingEventPayloadComparator implements PayloadComparatorStrategy {

    private static final String BETTED_EVENT_TYPE = "betted_from_iframe";
    private static final String WON_EVENT_TYPE = "won_from_iframe";
    private static final String LOSS_EVENT_TYPE = "loosed_from_iframe";
    private static final String REFUNDED_EVENT_TYPE = "refunded_from_iframe";
    private static final String RECALCULATED_EVENT_TYPE = "recalculated_from_iframe";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            BETTED_EVENT_TYPE,
            WON_EVENT_TYPE,
            LOSS_EVENT_TYPE,
            REFUNDED_EVENT_TYPE,
            RECALCULATED_EVENT_TYPE
    );

    @Override
    public Set<String> getSupportedEventTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public Class<?> getPayloadClass() {
        return NatsBettingEventPayload.class;
    }

    @Override
    public boolean compareAndLog(Object deserializedKafkaPayload, Object natsPayload, long seqNum, String actualEventType) {
        NatsBettingEventPayload kafka = (NatsBettingEventPayload) deserializedKafkaPayload;
        NatsBettingEventPayload nats = (NatsBettingEventPayload) natsPayload;

        boolean areEqual = true;

        if (!Objects.equals(kafka.getUuid(), nats.getUuid())) {
            logMismatch(seqNum, "uuid", kafka.getUuid(), nats.getUuid(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getType(), nats.getType())) {
            logMismatch(seqNum, "payload.type", kafka.getType(), nats.getType(), actualEventType);
            areEqual = false;
        }
        if (kafka.getBetId() != nats.getBetId()) {
            logMismatch(seqNum, "bet_id", kafka.getBetId(), nats.getBetId(), actualEventType);
            areEqual = false;
        }

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getAmount(), nats.getAmount()) != 0) {
            logMismatch(seqNum, "amount", kafka.getAmount(), nats.getAmount(), actualEventType);
            areEqual = false;
        }
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getRawAmount(), nats.getRawAmount()) != 0) {
            logMismatch(seqNum, "raw_amount", kafka.getRawAmount(), nats.getRawAmount(), actualEventType);
            areEqual = false;
        }

        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getTotalCoeff(), nats.getTotalCoeff()) != 0) {
            logMismatch(seqNum, "total_coeff", kafka.getTotalCoeff(), nats.getTotalCoeff(), actualEventType);
            areEqual = false;
        }

        if (kafka.getTime() != nats.getTime()) {
            logMismatch(seqNum, "time", kafka.getTime(), nats.getTime(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getCreatedAt(), nats.getCreatedAt())) {
            logMismatch(seqNum, "created_at", kafka.getCreatedAt(), nats.getCreatedAt(), actualEventType);
            areEqual = false;
        }

        boolean kafkaWageredEmpty = kafka.getWageredDepositInfo() == null || kafka.getWageredDepositInfo().isEmpty();
        boolean natsWageredEmpty = nats.getWageredDepositInfo() == null || nats.getWageredDepositInfo().isEmpty();
        if (kafkaWageredEmpty != natsWageredEmpty) {
            logMismatch(seqNum, "wagered_deposit_info (emptiness)",
                    kafkaWageredEmpty ? "empty/null" : "not empty",
                    natsWageredEmpty ? "empty/null" : "not empty",
                    actualEventType);
            areEqual = false;
        } else if (!kafkaWageredEmpty && !Objects.equals(kafka.getWageredDepositInfo(), nats.getWageredDepositInfo())) {
            logMismatch(seqNum, "wagered_deposit_info (content)",
                    kafka.getWageredDepositInfo(), nats.getWageredDepositInfo(), actualEventType);
            areEqual = false;
        }

        boolean betInfoCompared = compareBetInfoLists(kafka.getBetInfo(), nats.getBetInfo(), seqNum, actualEventType);
        if (!betInfoCompared) {
            areEqual = false;
        }

        if (!areEqual) {
            log.debug("Comparison finished with mismatches (SeqNum: {}, Type: {}).", seqNum, actualEventType);
        }

        return areEqual;
    }

    private boolean compareBetInfoLists(List<NatsBettingEventPayload.BetInfoDetail> kafkaList,
                                        List<NatsBettingEventPayload.BetInfoDetail> natsList,
                                        long seqNum, String actualEventType) {

        boolean bothNullOrEmpty = (kafkaList == null || kafkaList.isEmpty()) && (natsList == null || natsList.isEmpty());
        if (bothNullOrEmpty) {
            return true;
        }

        if (kafkaList == null || natsList == null) {
            logMismatch(seqNum, "bet_info list (existence)",
                    kafkaList == null ? "null" : "exists",
                    natsList == null ? "null" : "exists",
                    actualEventType);
            return false;
        }

        if (kafkaList.size() != natsList.size()) {
            logMismatch(seqNum, "bet_info list size", kafkaList.size(), natsList.size(), actualEventType);
            return false;
        }

        boolean listsAreEqual = true;
        for (int i = 0; i < kafkaList.size(); i++) {
            NatsBettingEventPayload.BetInfoDetail kafkaDetail = kafkaList.get(i);
            NatsBettingEventPayload.BetInfoDetail natsDetail = natsList.get(i);

            if (kafkaDetail == null || natsDetail == null) {
                logMismatch(seqNum, "bet_info item[" + i + "] (existence)",
                        kafkaDetail == null ? "null" : "exists",
                        natsDetail == null ? "null" : "exists",
                        actualEventType);
                listsAreEqual = false;
                continue;
            }

            if (!compareBetInfoDetail(kafkaDetail, natsDetail, seqNum, actualEventType, i)) {
                listsAreEqual = false;
            }
        }

        return listsAreEqual;
    }

    private boolean compareBetInfoDetail(NatsBettingEventPayload.BetInfoDetail kafka,
                                         NatsBettingEventPayload.BetInfoDetail nats,
                                         long seqNum, String actualEventType, int index) {
        boolean areEqual = true;
        String prefix = "bet_info[" + index + "].";

        if (!Objects.equals(kafka.getChampId(), nats.getChampId())) {
            logMismatch(seqNum, prefix + "ChampId", kafka.getChampId(), nats.getChampId(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getChampName(), nats.getChampName())) {
            logMismatch(seqNum, prefix + "ChampName", kafka.getChampName(), nats.getChampName(), actualEventType);
            areEqual = false;
        }
        if (PayloadComparatorStrategy.compareBigDecimals(kafka.getCoef(), nats.getCoef()) != 0) {
            logMismatch(seqNum, prefix + "Coef", kafka.getCoef(), nats.getCoef(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getCouponType(), nats.getCouponType())) {
            logMismatch(seqNum, prefix + "CouponType", kafka.getCouponType(), nats.getCouponType(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getDateStart(), nats.getDateStart())) {
            logMismatch(seqNum, prefix + "DateStart", kafka.getDateStart(), nats.getDateStart(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getEvent(), nats.getEvent())) {
            logMismatch(seqNum, prefix + "Event", kafka.getEvent(), nats.getEvent(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getGameName(), nats.getGameName())) {
            logMismatch(seqNum, prefix + "GameName", kafka.getGameName(), nats.getGameName(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getScore(), nats.getScore())) {
            logMismatch(seqNum, prefix + "Score", kafka.getScore(), nats.getScore(), actualEventType);
            areEqual = false;
        }
        if (!Objects.equals(kafka.getSportName(), nats.getSportName())) {
            logMismatch(seqNum, prefix + "SportName", kafka.getSportName(), nats.getSportName(), actualEventType);
            areEqual = false;
        }

        return areEqual;
    }
}