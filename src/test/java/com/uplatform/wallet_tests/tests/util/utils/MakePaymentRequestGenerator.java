package com.uplatform.wallet_tests.tests.util.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.uplatform.wallet_tests.api.http.manager.dto.betting.MakePaymentRequest;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingCouponType;
import com.uplatform.wallet_tests.api.nats.dto.enums.NatsBettingTransactionOperation;
import lombok.experimental.UtilityClass;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@UtilityClass
public class MakePaymentRequestGenerator {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Random random = new Random();
    private static final String[] SPORTS = {"Football", "Basketball", "Tennis", "Ice Hockey", "Volleyball", "Handball", "CyberSport", "MMA"};
    private static final String[] CHAMP_PREFIXES = {"Grand", "World", "National", "Regional", "Local", "Cyber"};
    private static final String[] CHAMP_SUFFIXES = {"League", "Cup", "Trophy", "Championship", "Series", "Masters", "Open"};
    private static final String[] EVENT_TYPES = {"Win 1", "Draw", "Win 2", "Total Over", "Total Under", "Handicap", "Correct Score", "Next Goal"};
    private static final String[] EVENT_VALUES = {"", "", "", "(2.5)", "(3.5)", "(-1.5)", "(+1.5)", "1-0", "2-1"};
    private static final String[] TEAMS_ADJ = {"Red", "Blue", "Green", "Golden", "Iron", "Silver", "Flying", "Mighty"};
    private static final String[] TEAMS_NOUN = {"Eagles", "Lions", "Sharks", "Bears", "Wolves", "Tigers", "Hawks", "Panthers", "Dragons", "Raptors", "Vipers", "Giants"};

    public static MakePaymentRequest generateRequest(MakePaymentData inputData) {
        Objects.requireNonNull(inputData, "Input data (inputData) cannot be null");
        final long effectiveTime = determineEffectiveTime(inputData.getTime());
        final long effectiveChampId = determineEffectiveChampId(inputData.getChampId());
        final long effectiveBetId = determineEffectiveBetId(inputData.getBetId());
        final String sign = determineEffectiveSign(inputData.getSign());
        final String effectiveTotalCoef = determineEffectiveTotalCoef(inputData.getTotalCoef());

        NatsBettingTransactionOperation typeEnum = inputData.getType();
        NatsBettingCouponType couponTypeEnum = inputData.getCouponType();
        Objects.requireNonNull(inputData.getPlayerId(), "'playerId' is required");
        validateNumericString(inputData.getSumm(), "'summ'");
        Objects.requireNonNull(couponTypeEnum, "'couponType' (enum) is required");
        validateNumericString(effectiveTotalCoef, "'totalCoef'");
        if (inputData.getBetInfoCoef() != null) {
            validateNumericString(inputData.getBetInfoCoef(), "'betInfoCoef'");
        }

        String token2String = buildToken2(inputData.getPlayerId(), inputData.getCurrency());
        String betInfoString = buildBetInfo(
                inputData,
                effectiveChampId,
                effectiveTime,
                effectiveTotalCoef
        );

        return MakePaymentRequest.builder()
                .sign(sign)
                .time(effectiveTime)
                .type(typeEnum)
                .token2(token2String)
                .betId(effectiveBetId)
                .betInfo(betInfoString)
                .summ(inputData.getSumm())
                .totalCoef(effectiveTotalCoef)
                .build();
    }

    private static long determineEffectiveTime(Long inputTime) {
        if (inputTime != null) {
            if (inputTime <= 0L) throw new IllegalArgumentException("Time must be positive");
            return inputTime;
        }
        return System.currentTimeMillis() / 1000L;
    }

    private static long determineEffectiveChampId(Long inputChampId) {
        if (inputChampId != null) {
            if (inputChampId <= 0L) throw new IllegalArgumentException("ChampId must be positive");
            return inputChampId;
        }
        return generateRandomChampId();
    }

    private static long determineEffectiveBetId(Long inputBetId) {
        if (inputBetId != null) {
            return inputBetId;
        }
        return generateRandomBetId();
    }

    private static String determineEffectiveSign(String inputSign) {
        return inputSign != null ? inputSign : generatePlaceholderSign();
    }

    private static String determineEffectiveTotalCoef(String inputTotalCoef) {
        if (inputTotalCoef != null && !inputTotalCoef.isBlank()) {
            return inputTotalCoef;
        }
        return generateRandomTotalCoefString();
    }

    private static long generateRandomBetId() {
        return Math.abs(random.nextLong());
    }

    private static long generateRandomChampId() {
        return 1_000_000L + Math.abs(random.nextLong() % 9_000_000L);
    }

    private static String generatePlaceholderSign() {
        return "GENERATED_SIGN_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String generateRandomSportName() {
        return SPORTS[random.nextInt(SPORTS.length)];
    }

    private static String generateRandomChampName(long champId, String sportName) {
        return sportName + " " + CHAMP_PREFIXES[random.nextInt(CHAMP_PREFIXES.length)] + " " + CHAMP_SUFFIXES[random.nextInt(CHAMP_SUFFIXES.length)] + " #" + (champId % 1000);
    }

    private static String generateRandomGameName(long champId, String sportName) {
        String t1 = TEAMS_ADJ[random.nextInt(TEAMS_ADJ.length)] + " " + TEAMS_NOUN[random.nextInt(TEAMS_NOUN.length)];
        String t2 = TEAMS_ADJ[random.nextInt(TEAMS_ADJ.length)] + " " + TEAMS_NOUN[random.nextInt(TEAMS_NOUN.length)];
        if (t1.equals(t2)) t2 += " Jr";
        return String.format("%s: %s vs %s (Match %d)", sportName, t1, t2, (champId + random.nextInt(100)));
    }

    private static String generateRandomEvent() {
        int i = random.nextInt(EVENT_TYPES.length);
        String et = EVENT_TYPES[i];
        String ev = EVENT_VALUES[i];
        if (et.contains("Total") || et.contains("Handicap")) {
            double rv = (random.nextInt(5) + 1) * 0.5 + (random.nextBoolean() ? 0.0 : 0.25);
            ev = String.format("(%.2f)", rv);
            if (et.contains("H") && random.nextBoolean()) {
                ev = ev.replace('(', '-').replace(')', '\0');
            } else if (et.contains("H")) {
                ev = ev.replace('(', '+').replace(')', '\0');
            }
        } else if (et.contains("Score")) {
            ev = String.format("%d-%d", random.nextInt(3), random.nextInt(3));
        }
        return et + (ev.isEmpty() ? "" : " " + ev);
    }

    private static long generateDefaultDateStart(long effectiveTime) {
        int min = 900;
        int max = 172800;
        return effectiveTime + (min + random.nextInt(max - min));
    }

    private static String generateDefaultScore() {
        return "0-0";
    }

    private static String generateRandomTotalCoefString() {
        int minHundredths = 101;
        int maxHundredths = 999;
        int randomHundredths;
        do {
            randomHundredths = minHundredths + random.nextInt(maxHundredths - minHundredths + 1);
        } while (randomHundredths % 10 == 0);

        BigDecimal bd = new BigDecimal(randomHundredths).movePointLeft(2);

        return bd.toPlainString();
    }

    private static void validateNumericString(String value, String fieldName) {
        if (value == null) throw new IllegalArgumentException("Field " + fieldName + " cannot be null.");
        try {
            new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field " + fieldName + " must be valid num string, but was: '" + value + "'", e);
        }
    }

    private static String buildToken2(String playerId, String currency) {
        try {
            ObjectNode n = objectMapper.createObjectNode();
            n.put("playerId", playerId);
            n.put("currency", currency);
            return objectMapper.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Err building token2", e);
        }
    }

    private static String buildBetInfo(
            MakePaymentData inputData,
            long effectiveChampId,
            long effectiveTime,
            String effectiveTotalCoef
    ) {
        try {
            ArrayNode betInfoArray = objectMapper.createArrayNode();
            ObjectNode betDetails = objectMapper.createObjectNode();

            String coefString = inputData.getBetInfoCoef() != null
                    ? inputData.getBetInfoCoef()
                    : effectiveTotalCoef;
            validateNumericString(coefString, "coefficient for betInfo (betInfoCoef or totalCoef)");
            BigDecimal coef = new BigDecimal(coefString);

            NatsBettingCouponType couponTypeEnum = inputData.getCouponType();
            Objects.requireNonNull(couponTypeEnum, "CouponType enum cannot be null for betInfo");
            String couponTypeValue = couponTypeEnum.getValue();
            String sportName = inputData.getSportName() != null ? inputData.getSportName() : generateRandomSportName();
            String champName = inputData.getChampName() != null ? inputData.getChampName() : generateRandomChampName(effectiveChampId, sportName);
            String gameName = inputData.getGameName() != null ? inputData.getGameName() : generateRandomGameName(effectiveChampId, sportName);
            String event = inputData.getEvent() != null ? inputData.getEvent() : generateRandomEvent();
            long dateStart = inputData.getDateStart() != null ? inputData.getDateStart() : generateDefaultDateStart(effectiveTime);
            String score = inputData.getScore() != null ? inputData.getScore() : generateDefaultScore();

            betDetails.put("ChampId", effectiveChampId);
            betDetails.put("ChampName", champName);
            betDetails.put("Coef", coef);
            betDetails.put("CouponType", couponTypeValue);
            betDetails.put("DateStart", dateStart);
            betDetails.put("Event", event);
            betDetails.put("GameName", gameName);
            betDetails.put("Score", score);
            betDetails.put("SportName", sportName);

            betInfoArray.add(betDetails);
            return objectMapper.writeValueAsString(betInfoArray);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error building betInfo JSON array string", e);
        }
    }
}