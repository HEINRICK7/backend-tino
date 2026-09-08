package com.tino.backend.messaging.application.service;

import com.tino.backend.messaging.application.exception.WhatsAppPhoneInvalidException;
import com.tino.backend.messaging.application.exception.WhatsAppPhoneMissingException;

public final class WhatsAppPhoneNumbers {
    private WhatsAppPhoneNumbers() {}

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new WhatsAppPhoneMissingException();
        }
        var digits = raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("55") && digits.length() == 12 && digits.charAt(4) >= '6') {
            digits = digits.substring(0, 4) + "9" + digits.substring(4);
        } else if ((digits.length() == 10 || digits.length() == 11) && !digits.startsWith("55")) {
            digits = "55" + digits;
        }
        if (!digits.matches("55[1-9][0-9](9[0-9]{8}|[2-5][0-9]{7})")) {
            throw new WhatsAppPhoneInvalidException();
        }
        return "+" + digits;
    }
}
