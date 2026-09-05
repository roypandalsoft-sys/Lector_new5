package com.roypandal.lectornotificaciones;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentParser {
    private static final String[] WORDS = {
            "pago", "pagó", "pago recibido", "recibiste", "recibido",
            "te enviaron", "te envio", "te envió", "transferencia recibida",
            "deposito recibido", "depósito recibido", "abono", "cobro",
            "yape", "plineo", "plin"
    };

    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?:S\\s*/\\.?|S/|PEN|sol(?:es)?|\\$)\\s*([0-9]{1,7}(?:[.,][0-9]{1,2})?)" +
            "|([0-9]{1,7}(?:[.,][0-9]{1,2})?)\\s*(?:sol(?:es)?|PEN)"
    );

    // Yape real: NOMBRE te envió un pago por S/ MONTO. El código de seguridad es opcional.
    private static final Pattern YAPE_PAYMENT = Pattern.compile(
            "(?iu)^\\s*(.+?)\\s+te\\s+envi[oó]\\s+un\\s+pago\\s+por\\s+S\\s*/\\s*([0-9]{1,7}(?:[.,][0-9]{1,2})?)\\b"
    );

    private PaymentParser(){}

    public static Result parse(String text) {
        if (text == null) return new Result(false, 0, "", "");
        String clean = text.replace('\n',' ').trim();
        String normalized = normalize(clean);
        boolean keyword = false;
        for (String w : WORDS) if (normalized.contains(normalize(w))) { keyword = true; break; }

        Matcher m = MONEY.matcher(clean);
        if (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            try { return new Result(keyword, Double.parseDouble(raw.replace(",", ".")), clean, ""); }
            catch(Exception ignored) {}
        }
        return new Result(false, 0, clean, "");
    }

    public static Result parseYape(String title, String notificationText) {
        String t = title == null ? "" : title.trim();
        String body = notificationText == null ? "" : notificationText.replace('\n',' ').trim();
        // Primera barrera: el título debe ser exactamente Confirmación de Pago (ignorando tildes/mayúsculas).
        if (!normalize(t).equals("confirmacion de pago")) return new Result(false, 0, body, "");

        Matcher m = YAPE_PAYMENT.matcher(body);
        if (!m.find()) return new Result(false, 0, body, "");
        try {
            String name = cleanName(m.group(1));
            if (name.isEmpty()) return new Result(false, 0, body, "");
            double amount = Double.parseDouble(m.group(2).replace(",", "."));
            return new Result(true, amount, body, name);
        } catch(Exception ignored) { return new Result(false, 0, body, ""); }
    }

    public static String cleanName(String name) {
        if (name == null) return "";
        return name.replace("*", "")
                .replaceAll("\\s+", " ")
                .replaceAll("^[\\s.,;:-]+|[\\s.,;:-]+$", "")
                .trim();
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    public static final class Result {
        public final boolean isPayment;
        public final double amount;
        public final String original;
        public final String customerName;
        public Result(boolean isPayment, double amount, String original, String customerName) {
            this.isPayment = isPayment; this.amount = amount; this.original = original; this.customerName = customerName;
        }
    }
}
