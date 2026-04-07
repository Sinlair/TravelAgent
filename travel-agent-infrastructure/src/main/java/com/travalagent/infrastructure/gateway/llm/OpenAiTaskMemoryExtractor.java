package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.service.TaskMemoryExtractor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OpenAiTaskMemoryExtractor implements TaskMemoryExtractor {

    private static final Pattern EN_FROM_TO = Pattern.compile("from\\s+([A-Za-z][A-Za-z\\s-]{1,40}?)\\s+to\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_FROM_TO = Pattern.compile("从([\\u4E00-\\u9FFF]{2,20})到([\\u4E00-\\u9FFF]{2,20})");
    private static final Pattern EN_DAYS = Pattern.compile("(\\d{1,2})\\s*[- ]?day", Pattern.CASE_INSENSITIVE);
    private static final Pattern EN_DESTINATION = Pattern.compile("(?:plan|arrange|build)?\\s*(?:a\\s+\\d{1,2}\\s*[- ]?day\\s+)?([A-Za-z][A-Za-z\\s-]{1,40})\\s+(?:trip|itinerary|travel)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_DAYS = Pattern.compile("(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*天");
    private static final Pattern BUDGET = Pattern.compile("(\\d{3,6})");
    private static final Pattern ZH_DESTINATION = Pattern.compile("(?:帮我|请帮我|麻烦|我想|想要|规划|计划|安排)?(?:[一二两三四五六七八九十0-9]{1,3}\\s*天)?([\\u4E00-\\u9FFF]{2,20})(?:行程|旅行|旅游|攻略)");
    private static final Pattern ZH_GO_TO = Pattern.compile("[去到]([\\u4E00-\\u9FFF]{2,20})");

    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;

    public OpenAiTaskMemoryExtractor(ChatClient.Builder chatClientBuilder, OpenAiAvailability openAiAvailability) {
        this.chatClientBuilder = chatClientBuilder;
        this.openAiAvailability = openAiAvailability;
    }

    @Override
    public TaskMemory extract(TaskMemory existing, List<ConversationMessage> messages) {
        TaskMemory heuristic = extractHeuristically(existing, messages);
        if (!openAiAvailability.isAvailable()) {
            return heuristic;
        }
        try {
            MemoryOutput output = chatClientBuilder.build()
                    .prompt()
                    .system("""
                            Extract structured trip state from the conversation.
                            Only copy user-confirmed facts and do not guess.
                            Allowed fields: origin, destination, days, budget, preferences, pendingQuestion, summary.
                            Return an empty field when the conversation does not provide a reliable update.
                            """)
                    .user(messages.stream()
                            .map(message -> "[%s] %s".formatted(message.role().name(), message.content()))
                            .reduce((left, right) -> left + "\n" + right)
                            .orElse(""))
                    .call()
                    .entity(MemoryOutput.class);
            TaskMemory llmCandidate = new TaskMemory(
                    existing.conversationId(),
                    output == null ? null : output.origin(),
                    output == null ? null : output.destination(),
                    output == null ? null : output.days(),
                    output == null ? null : output.budget(),
                    output == null || output.preferences() == null ? List.of() : output.preferences(),
                    output == null ? null : output.pendingQuestion(),
                    output == null ? null : output.summary(),
                    Instant.now()
            );
            return heuristic.merge(llmCandidate);
        } catch (Exception exception) {
            return heuristic;
        }
    }

    private TaskMemory extractHeuristically(TaskMemory existing, List<ConversationMessage> messages) {
        String userText = messages.stream()
                .filter(message -> message.role().name().equals("USER"))
                .map(ConversationMessage::content)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        boolean chinese = containsChinese(userText);
        String origin = firstNonBlank(existing.origin(), extractOrigin(userText));
        String destination = firstNonBlank(existing.destination(), extractDestination(userText));
        Integer days = existing.days() != null ? existing.days() : extractDays(userText);
        String budget = firstNonBlank(existing.budget(), extractBudgetText(userText, chinese));
        List<String> preferences = mergePreferences(existing.preferences(), extractPreferences(userText));
        String pendingQuestion = hasText(destination) && days != null && hasText(budget)
                ? null
                : chinese ? "请确认目的地、天数和预算，我再继续细化行程。" : "Please confirm the destination, trip length, and budget so the planner can continue.";
        return new TaskMemory(
                existing.conversationId(),
                emptyToNull(origin),
                emptyToNull(destination),
                days,
                emptyToNull(budget),
                preferences,
                pendingQuestion,
                existing.summary(),
                Instant.now()
        );
    }

    private String extractOrigin(String text) {
        Matcher zh = ZH_FROM_TO.matcher(text);
        if (zh.find()) {
            return cleanLocation(zh.group(1));
        }
        Matcher matcher = EN_FROM_TO.matcher(text);
        return matcher.find() ? cleanLocation(matcher.group(1)) : null;
    }

    private String extractDestination(String text) {
        Matcher zh = ZH_FROM_TO.matcher(text);
        if (zh.find()) {
            return cleanLocation(zh.group(2));
        }
        Matcher matcher = EN_FROM_TO.matcher(text);
        if (matcher.find()) {
            return cleanLocation(matcher.group(2));
        }
        Matcher destinationOnly = EN_DESTINATION.matcher(text);
        if (destinationOnly.find()) {
            return cleanLocation(destinationOnly.group(1));
        }
        Matcher toOnly = Pattern.compile("to\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (toOnly.find()) {
            return cleanLocation(toOnly.group(1));
        }
        Matcher destination = ZH_DESTINATION.matcher(text);
        if (destination.find()) {
            return cleanLocation(destination.group(1));
        }
        Matcher goTo = ZH_GO_TO.matcher(text);
        if (goTo.find()) {
            return cleanLocation(goTo.group(1));
        }
        return null;
    }

    private Integer extractDays(String text) {
        Matcher zh = ZH_DAYS.matcher(text);
        if (zh.find()) {
            return parseChineseNumber(zh.group(1));
        }
        Matcher matcher = EN_DAYS.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String extractBudgetText(String text, boolean chinese) {
        Matcher matcher = BUDGET.matcher(text.replace(",", ""));
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            if (value >= 300) {
                return chinese ? value + " 元" : value + " CNY";
            }
        }
        return null;
    }

    private List<String> extractPreferences(String text) {
        Set<String> preferences = new LinkedHashSet<>();
        String lower = text.toLowerCase();
        if (lower.contains("food") || lower.contains("cuisine") || text.contains("美食") || text.contains("小吃")) {
            preferences.add("local food");
        }
        if (lower.contains("relaxed") || lower.contains("slow pace") || text.contains("轻松") || text.contains("悠闲")) {
            preferences.add("relaxed pace");
        }
        return List.copyOf(preferences);
    }

    private List<String> mergePreferences(List<String> existing, List<String> incoming) {
        Set<String> merged = new LinkedHashSet<>();
        merged.addAll(existing == null ? List.of() : existing);
        merged.addAll(incoming == null ? List.of() : incoming);
        return List.copyOf(merged);
    }

    private String cleanLocation(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceFirst("^(帮我|请帮我|麻烦|我想|想要|想去|规划|计划|安排)+", "")
                .replaceFirst("^[一二两三四五六七八九十0-9]+\\s*天", "")
                .replace("trip", "")
                .replace("旅行", "")
                .replace("旅游", "")
                .replace("行程", "")
                .trim();
    }

    private Integer parseChineseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (raw.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(raw);
        }
        Map<Character, Integer> digits = Map.of(
                '一', 1,
                '二', 2,
                '两', 2,
                '三', 3,
                '四', 4,
                '五', 5,
                '六', 6,
                '七', 7,
                '八', 8,
                '九', 9
        );
        if ("十".equals(raw)) {
            return 10;
        }
        if (raw.endsWith("十")) {
            return digits.getOrDefault(raw.charAt(0), 0) * 10;
        }
        if (raw.startsWith("十")) {
            return 10 + digits.getOrDefault(raw.charAt(1), 0);
        }
        int index = raw.indexOf('十');
        if (index > 0) {
            int tens = digits.getOrDefault(raw.charAt(0), 0);
            int ones = index + 1 < raw.length() ? digits.getOrDefault(raw.charAt(index + 1), 0) : 0;
            return tens * 10 + ones;
        }
        return digits.getOrDefault(raw.charAt(0), 0);
    }

    private String firstNonBlank(String current, String candidate) {
        return hasText(current) ? current : candidate;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String emptyToNull(String value) {
        return hasText(value) ? value : null;
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private record MemoryOutput(
            String origin,
            String destination,
            Integer days,
            String budget,
            List<String> preferences,
            String pendingQuestion,
            String summary
    ) {
    }
}
