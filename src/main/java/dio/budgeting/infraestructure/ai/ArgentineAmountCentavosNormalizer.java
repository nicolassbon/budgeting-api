package dio.budgeting.infraestructure.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArgentineAmountCentavosNormalizer {

    private static final BigDecimal CENTAVOS_PER_PESO = BigDecimal.valueOf(100);
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1_000);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private ArgentineAmountCentavosNormalizer() {
    }

    static InterpretationPayload normalize(String prompt, InterpretationPayload payload) {
        if (payload == null || payload.amount() == null) {
            return payload;
        }

        return singlePromptAmountInCentavos(prompt)
                .filter(promptAmount -> shouldTrustPromptAmount(payload.amount(), promptAmount))
                .map(promptAmount -> new InterpretationPayload(
                        payload.status(),
                        payload.description(),
                        promptAmount,
                        payload.category()
                ))
                .orElse(payload);
    }

    private static Optional<Long> singlePromptAmountInCentavos(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalizeText(prompt);
        List<Long> matches = new ArrayList<>();

        // Pattern 1: Numbers with magnitude and pesos: e.g. "100 mil pesos", "$100 mil pesos"
        Pattern p1 = Pattern.compile("(?:\\$\\s*)?(\\d+(?:[.,]\\d+)*)\\s+(mil|millon|millones)\\s+(?:de\\s+)?pesos?\\b");
        Matcher m1 = p1.matcher(normalized);
        while (m1.find()) {
            parseToCentavos(m1.group(1), m1.group(2)).ifPresent(matches::add);
        }

        if (matches.isEmpty()) {
            // Pattern 2: Numbers with pesos but no magnitude: e.g. "100.000 pesos", "$100.000 pesos"
            Pattern p2 = Pattern.compile("(?:\\$\\s*)?(\\d+(?:[.,]\\d+)*)\\s+(?:de\\s+)?pesos?\\b");
            Matcher m2 = p2.matcher(normalized);
            while (m2.find()) {
                parseToCentavos(m2.group(1), null).ifPresent(matches::add);
            }
        }

        if (matches.isEmpty()) {
            // Pattern 3: Numbers with $ prefix but no pesos/magnitude: e.g. "$100.000", "$ 100000"
            Pattern p3 = Pattern.compile("\\$\\s*(\\d+(?:[.,]\\d+)*)\\b");
            Matcher m3 = p3.matcher(normalized);
            while (m3.find()) {
                parseToCentavos(m3.group(1), null).ifPresent(matches::add);
            }
        }

        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private static Optional<Long> parseToCentavos(String numberStr, String magnitude) {
        try {
            String normalizedNumber = numberStr;
            int lastDot = normalizedNumber.lastIndexOf('.');
            int lastComma = normalizedNumber.lastIndexOf(',');

            BigDecimal val;
            if (lastDot != -1 && lastComma != -1) {
                if (lastDot > lastComma) {
                    normalizedNumber = normalizedNumber.replace(",", "");
                    val = new BigDecimal(normalizedNumber);
                } else {
                    normalizedNumber = normalizedNumber.replace(".", "").replace(',', '.');
                    val = new BigDecimal(normalizedNumber);
                }
            } else if (lastDot != -1) {
                int digitsAfter = normalizedNumber.length() - lastDot - 1;
                if (digitsAfter == 3) {
                    normalizedNumber = normalizedNumber.replace(".", "");
                    val = new BigDecimal(normalizedNumber);
                } else {
                    val = new BigDecimal(normalizedNumber);
                }
            } else if (lastComma != -1) {
                int digitsAfter = normalizedNumber.length() - lastComma - 1;
                if (digitsAfter == 3) {
                    normalizedNumber = normalizedNumber.replace(",", "");
                    val = new BigDecimal(normalizedNumber);
                } else {
                    normalizedNumber = normalizedNumber.replace(',', '.');
                    val = new BigDecimal(normalizedNumber);
                }
            } else {
                val = new BigDecimal(normalizedNumber);
            }

            BigDecimal multiplier = BigDecimal.ONE;
            if (magnitude != null) {
                if (magnitude.equals("mil")) {
                    multiplier = THOUSAND;
                } else if (magnitude.equals("millon") || magnitude.equals("millones")) {
                    multiplier = MILLION;
                }
            }

            return Optional.of(val.multiply(multiplier)
                    .multiply(CENTAVOS_PER_PESO)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact());
        } catch (ArithmeticException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static boolean shouldTrustPromptAmount(Long modelAmount, long promptAmountCentavos) {
        return modelAmount == promptAmountCentavos
                || BigDecimal.valueOf(modelAmount).multiply(CENTAVOS_PER_PESO).compareTo(BigDecimal.valueOf(promptAmountCentavos)) == 0;
    }

    private static String normalizeText(String text) {
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase();
    }
}
