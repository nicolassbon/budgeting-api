package dio.budgeting.infraestructure.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArgentineAmountCentavosNormalizer {

    private static final Pattern SINGLE_MAGNITUDE_AMOUNT = Pattern.compile(
            "(?<!\\d)(\\d+(?:[,.]\\d+)?)\\s+(mil|millon|millones)\\s+(?:de\\s+)?pesos?\\b"
    );
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

        Matcher matcher = SINGLE_MAGNITUDE_AMOUNT.matcher(normalizeText(prompt));
        Long amount = null;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            if (matches > 1) {
                return Optional.empty();
            }
            amount = toCentavos(matcher.group(1), matcher.group(2)).orElse(null);
        }

        return matches == 1 ? Optional.ofNullable(amount) : Optional.empty();
    }

    private static Optional<Long> toCentavos(String numericText, String magnitudeText) {
        try {
            BigDecimal base = new BigDecimal(numericText.replace(',', '.'));
            BigDecimal multiplier = magnitudeText.equals("mil") ? THOUSAND : MILLION;
            return Optional.of(base.multiply(multiplier)
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
