package com.uplatform.wallet_tests.tests.util.utils;

import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@UtilityClass
public final class StringGeneratorUtil {

    public static final String INTEGER = "integer";
    public static final String LETTERS = "letters";
    public static final String ALPHANUMERIC = "alphanum";
    public static final String NUMBER = "number";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String PASSWORD = "pass";
    public static final String BIRTHDAY_DDMMYYYY = "birthday_ddmmyyyy";
    public static final String BIRTHDAY_YYYYMMDD = "birthday_yyyymmdd";
    public static final String CYRILLIC = "cyrillic";
    public static final String SPECIAL = "special";
    public static final String HEX = "hex";
    public static final String NON_HEX = "non_hex";
    public static final String IBAN = "iban";
    public static final String PERSONAL_ID = "personal_id";
    public static final String BRAND_TITLE = "brandTitle";
    public static final String ALIAS = "alias";
    public static final String CATEGORY_TITLE = "category_title";
    public static final String COLLECTION_TITLE = "collection_title";
    public static final String GAME_TITLE = "game_title";
    public static final String PHONE = "phone";

    public static final String DIGITS = "0123456789";
    public static final String NON_ZERO_DIGITS = "123456789";
    public static final String LATIN_LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    public static final String LATIN_UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LATIN_LETTERS = LATIN_LOWERCASE + LATIN_UPPERCASE;
    public static final String ALPHANUMERIC_CHARS = LATIN_LETTERS + DIGITS;
    public static final String CYRILLIC_LOWERCASE = "абвгдеёжэийклмнопрстуфхцчшщъыьэюя";
    public static final String CYRILLIC_UPPERCASE = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
    public static final String CYRILLIC_LETTERS = CYRILLIC_LOWERCASE + CYRILLIC_UPPERCASE;
    public static final String CONSONANTS = "bcdfghjklmnpqrstvwxz";
    public static final String VOWELS = "aeiouy";
    public static final String GENERAL_SPECIAL_CHARS = "!@#$%^&_";
    public static final String PASSWORD_SPECIAL_CHARS = "!:@#$%";
    public static final String HEX_CHARS = "0123456789abcdef";
    public static final String NON_HEX_CHARS = "ghijklmnopqrstuvwxyz";
    public static final String ALIAS_CHARS = LATIN_LOWERCASE + DIGITS + "-";
    public static final String TITLE_CHARS = ALPHANUMERIC_CHARS + CYRILLIC_LETTERS + " -";
    public static final String GAME_TITLE_CHARS = ALPHANUMERIC_CHARS + " -";
    private static final int DEFAULT_LENGTH = 10;
    private static final int MAX_ALIAS_LEN = 100;
    private static final int MAX_TITLE_LEN = 25;
    private static final int MAX_GAME_TITLE_LEN = 255;
    private static final int MIN_GAME_TITLE_LEN = 2;
    private static final int MIN_PASSWORD_LEN = 4;
    private static final Random RAND = new SecureRandom();
    private static final int DEFAULT_AMOUNT_SCALE = 2;

    public static String get(String config, int... lengths) {
        int length = (lengths.length > 0 && lengths[0] > 0) ? lengths[0] : DEFAULT_LENGTH;

        switch (config) {
            case PASSWORD: {
                if (length < MIN_PASSWORD_LEN) {
                    length = MIN_PASSWORD_LEN;
                }
                break;
            }
            case GAME_TITLE: {
                if (length < MIN_GAME_TITLE_LEN) {
                    length = MIN_GAME_TITLE_LEN;
                }
                break;
            }
        }

        switch (config) {
            case INTEGER:
                return randomStringFromSet(DIGITS, length);
            case LETTERS:
                return randomStringFromSet(LATIN_LETTERS, length);
            case ALPHANUMERIC:
                return randomStringFromSet(ALPHANUMERIC_CHARS, length);
            case NUMBER:
                return generateNumber(length);
            case NAME:
                return generateName(length);
            case EMAIL:
                String localPart = randomStringFromSet(ALPHANUMERIC_CHARS, length);
                return localPart + "@generated.com";
            case PASSWORD:
                try {
                    return generatePassword(length);
                } catch (IllegalArgumentException e) {
                    return "Error generating password: " + e.getMessage();
                }
            case BIRTHDAY_DDMMYYYY:
                return generateBirthday(length, "dd.MM.yyyy");
            case BIRTHDAY_YYYYMMDD:
                return generateBirthday(length, "yyyy-MM-dd");
            case CYRILLIC:
                return randomStringFromSet(CYRILLIC_LETTERS, length);
            case SPECIAL:
                return randomStringFromSet(GENERAL_SPECIAL_CHARS, length);
            case HEX:
                return randomStringFromSet(HEX_CHARS, length);
            case NON_HEX:
                return randomStringFromSet(NON_HEX_CHARS, length);
            case IBAN:
                return generateIban();
            case PERSONAL_ID:
                return generatePersonalId();
            case BRAND_TITLE:
                return generateTitleInternal(Math.min(length, MAX_TITLE_LEN), ALPHANUMERIC_CHARS);
            case ALIAS:
                return generateAlias(length);
            case CATEGORY_TITLE:
            case COLLECTION_TITLE:
                return generateStandardTitle(length);
            case GAME_TITLE:
                return generateGameTitle(length);
            case PHONE:
                return generateTelephoneNumber();
            default:
                return String.format("Unknown argument for random generation: \"%s\"", config);
        }
    }

    private static String randomStringFromSet(String charset, int length) {
        if (length <= 0 || charset == null || charset.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(RAND.nextInt(charset.length())));
        }
        return sb.toString();
    }

    private static String generateNumber(int length) {
        if (length <= 0) {
            return "";
        }
        if (length == 1) {
            return randomStringFromSet(DIGITS, 1);
        }
        String firstDigit = randomStringFromSet(NON_ZERO_DIGITS, 1);
        String rest = randomStringFromSet(DIGITS, length - 1);
        return firstDigit + rest;
    }

    private static String generateName(int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);

        char firstChar = CONSONANTS.charAt(RAND.nextInt(CONSONANTS.length()));
        sb.append(Character.toUpperCase(firstChar));

        for (int i = 1; i < length; i++) {
            if (i % 2 != 0) {
                sb.append(VOWELS.charAt(RAND.nextInt(VOWELS.length())));
            } else {
                sb.append(CONSONANTS.charAt(RAND.nextInt(CONSONANTS.length())));
            }
        }
        return sb.toString();
    }

    private static String generatePassword(int length) {
        if (length < MIN_PASSWORD_LEN) {
            throw new IllegalArgumentException("Password length must be " + MIN_PASSWORD_LEN + " or more");
        }

        List<String> requiredCharSets = List.of(
                DIGITS,
                LATIN_UPPERCASE,
                LATIN_LOWERCASE,
                PASSWORD_SPECIAL_CHARS
        );

        for (String set : requiredCharSets) {
            if (set == null || set.isEmpty()) {
                throw new IllegalArgumentException("Password generation error: A required character set is empty");
            }
        }

        String allChars = ALPHANUMERIC_CHARS + PASSWORD_SPECIAL_CHARS;

        List<Character> resultChars = new ArrayList<>(length);

        for (String set : requiredCharSets) {
            resultChars.add(set.charAt(RAND.nextInt(set.length())));
        }

        for (int i = requiredCharSets.size(); i < length; i++) {
            resultChars.add(allChars.charAt(RAND.nextInt(allChars.length())));
        }

        Collections.shuffle(resultChars, RAND);

        return resultChars.stream()
                .map(String::valueOf)
                .collect(Collectors.joining());
    }

    private static String generateBirthday(int yearsAgo, String pattern) {
        if (yearsAgo <= 0) {
            yearsAgo = 18;
        }

        LocalDate now = LocalDate.now();
        int birthYear = now.getYear() - yearsAgo;

        int month = RAND.nextInt(12) + 1;

        YearMonth yearMonth = YearMonth.of(birthYear, month);
        int daysInMonth = yearMonth.lengthOfMonth();

        int day = RAND.nextInt(daysInMonth) + 1;

        LocalDate birthDate = LocalDate.of(birthYear, month, day);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return birthDate.format(formatter);
    }

    private static String generateIban() {
        return "LV" + randomStringFromSet(DIGITS, 19);
    }

    private static String generatePersonalId() {
        String part1 = randomStringFromSet(DIGITS, 6);
        String part2 = randomStringFromSet(DIGITS, 5);
        return part1 + "-" + part2;
    }

    private static String generateAlias(int length) {
        if (length > MAX_ALIAS_LEN) {
            length = MAX_ALIAS_LEN;
        }
        if (length <= 0) {
            return "";
        }

        String allowedNonHyphen = LATIN_LOWERCASE + DIGITS;
        StringBuilder sb = new StringBuilder(length);

        sb.append(allowedNonHyphen.charAt(RAND.nextInt(allowedNonHyphen.length())));

        boolean lastWasHyphen = false;
        for (int i = 1; i < length; i++) {
            String charSet = lastWasHyphen ? allowedNonHyphen : ALIAS_CHARS;

            char chosenChar = charSet.charAt(RAND.nextInt(charSet.length()));
            sb.append(chosenChar);
            lastWasHyphen = (chosenChar == '-');
        }

        if (sb.charAt(length - 1) == '-') {
            sb.setCharAt(length - 1, allowedNonHyphen.charAt(RAND.nextInt(allowedNonHyphen.length())));
        }

        return sb.toString();
    }

    private static String generateStandardTitle(int length) {
        if (length > MAX_TITLE_LEN) {
            length = MAX_TITLE_LEN;
        }
        return generateTitleInternal(length, TITLE_CHARS);
    }

    private static String generateGameTitle(int length) {
        if (length > MAX_GAME_TITLE_LEN) {
            length = MAX_GAME_TITLE_LEN;
        }
        return generateTitleInternal(length, GAME_TITLE_CHARS);
    }

    private static String generateTitleInternal(int length, String charSet) {
        if (length <= 0 || charSet == null || charSet.isEmpty()) {
            return "";
        }

        String nonSpaceChars = charSet.replace(" ", "");
        if (nonSpaceChars.isEmpty()) {
            nonSpaceChars = LATIN_LETTERS;
        }

        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = charSet.charAt(RAND.nextInt(charSet.length()));
        }

        if (result.length > 0 && result[0] == ' ') {
            result[0] = nonSpaceChars.charAt(RAND.nextInt(nonSpaceChars.length()));
        }

        if (length > 1 && result[length - 1] == ' ') {
            result[length - 1] = nonSpaceChars.charAt(RAND.nextInt(nonSpaceChars.length()));
        }

        return new String(result);
    }

    private static String generateTelephoneNumber() {
        String countryCode = "+370";
        String firstDigit = "2";
        String numberPart = randomStringFromSet(DIGITS, 7);
        return countryCode + firstDigit + numberPart;
    }

    public static BigDecimal generateBigDecimalAmount(BigDecimal maxAmount) {
        return generateBigDecimalAmount(maxAmount, DEFAULT_AMOUNT_SCALE);
    }

    public static BigDecimal generateBigDecimalAmount(BigDecimal maxAmount, int scale) {
        if (maxAmount == null || maxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Максимальная сумма (maxAmount) должна быть положительной и не null.");
        }
        if (scale < 0) {
            throw new IllegalArgumentException("Масштаб (scale) должен быть неотрицательным.");
        }

        BigDecimal smallestPossibleValue = BigDecimal.ONE.movePointLeft(scale);

        if (maxAmount.compareTo(smallestPossibleValue) < 0) {
            throw new IllegalArgumentException(String.format(
                    "maxAmount (%s) меньше, чем наименьшее возможное положительное значение с масштабом %d (%s).",
                    maxAmount.toPlainString(), scale, smallestPossibleValue.toPlainString()
            ));
        }

        BigDecimal scaledMaxAmount = maxAmount.movePointRight(scale).setScale(0, RoundingMode.DOWN);
        long maxLong = scaledMaxAmount.longValueExact();

        if (maxLong < 1) {
            return smallestPossibleValue;
        }

        long randomLong = RAND.nextLong(maxLong) + 1;

        return new BigDecimal(randomLong).movePointLeft(scale).setScale(scale, RoundingMode.HALF_UP);
    }
}
