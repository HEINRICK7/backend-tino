package com.tino.backend.business.domain.model;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A normalized Pix key. No bank credential or secret is accepted here. */
public record PixKey(PixKeyType type, String value) {
    private static final Pattern EVP = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})$");

    public PixKey {
        Objects.requireNonNull(type, "type");
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isBlank() || value.length() > 77) {
            throw new IllegalArgumentException("invalid Pix key");
        }
    }

    public static PixKey parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Pix key is required");
        }
        var trimmed = raw.trim();
        if (EVP.matcher(trimmed).matches()) {
            return new PixKey(PixKeyType.RANDOM, trimmed.toLowerCase(Locale.ROOT));
        }

        if (trimmed.contains("@")) {
            var email = trimmed.toLowerCase(Locale.ROOT);
            if (email.length() <= 77 && EMAIL.matcher(email).matches()) {
                return new PixKey(PixKeyType.EMAIL, email);
            }
            throw new IllegalArgumentException("invalid Pix email key");
        }

        var digits = trimmed.replaceAll("\\D", "");
        if (trimmed.startsWith("+") || (digits.startsWith("55") && digits.length() >= 12)) {
            var phone = digits.startsWith("55") ? "+" + digits : "+55" + digits;
            if (PHONE.matcher(phone).matches()) {
                return new PixKey(PixKeyType.PHONE, phone);
            }
            throw new IllegalArgumentException("invalid Pix phone key");
        }

        if (digits.length() == 11 && validCpf(digits)) {
            return new PixKey(PixKeyType.CPF, digits);
        }
        if (digits.length() == 14 && validCnpj(digits)) {
            return new PixKey(PixKeyType.CNPJ, digits);
        }
        throw new IllegalArgumentException("unsupported Pix key format");
    }

    private static boolean validCpf(String value) {
        if (value.chars().distinct().count() == 1) return false;
        var first = checkDigit(value, 9, 10);
        var second = checkDigit(value, 10, 11);
        return value.charAt(9) - '0' == first && value.charAt(10) - '0' == second;
    }

    private static boolean validCnpj(String value) {
        if (value.chars().distinct().count() == 1) return false;
        var first = checkDigit(value, 12, 5);
        var second = checkDigit(value, 13, 6);
        return value.charAt(12) - '0' == first && value.charAt(13) - '0' == second;
    }

    private static int checkDigit(String value, int length, int firstWeight) {
        var weight = firstWeight;
        var sum = 0;
        for (var index = 0; index < length; index++) {
            sum += (value.charAt(index) - '0') * weight--;
            if (weight < 2) weight = 9;
        }
        var remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
