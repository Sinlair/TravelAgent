package com.travalagent.infrastructure.gateway.llm;

final class LlmExtractionSanitizer {

    private LlmExtractionSanitizer() {
    }

    static String sanitizeStructuredText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()) {
            return null;
        }

        String lower = normalized.toLowerCase();
        if (lower.contains("i could not extract clear structured")
                || lower.contains("i could not extract clear travel facts")
                || lower.contains("could not extract clear")
                || lower.contains("no clear travel facts")
                || normalized.contains("未能从图片里提取出确定的旅行信息")) {
            return null;
        }
        return normalized;
    }
}
