package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.valobj.AgentRouteDecision;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.RoutingContext;
import com.travalagent.domain.service.AgentRouter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class OpenAiAgentRouter implements AgentRouter {

    private static final Pattern ZH_DAYS = Pattern.compile("(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*天");
    private static final Pattern ZH_DESTINATION = Pattern.compile("(?:帮我|请帮我|麻烦|我想|想要|规划|计划|安排)?(?:[一二两三四五六七八九十0-9]{1,3}\\s*天)?([\\u4E00-\\u9FFF]{2,20})(?:行程|旅行|旅游|攻略)");
    private static final Pattern ZH_GO_TO = Pattern.compile("[去到]([\\u4E00-\\u9FFF]{2,20})");
    private static final Pattern EN_DESTINATION = Pattern.compile("(?:plan|arrange|build)?\\s*(?:a\\s+\\d{1,2}\\s*[- ]?day\\s+)?([A-Za-z][A-Za-z\\s-]{1,40})\\s+(?:trip|itinerary|travel)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUDGET = Pattern.compile("(\\d{3,6})\\s*(?:CNY|RMB|元|块)?", Pattern.CASE_INSENSITIVE);

    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;

    public OpenAiAgentRouter(ChatClient.Builder chatClientBuilder, OpenAiAvailability openAiAvailability) {
        this.chatClientBuilder = chatClientBuilder;
        this.openAiAvailability = openAiAvailability;
    }

    @Override
    public AgentRouteDecision route(RoutingContext context) {
        if (!openAiAvailability.isAvailable()) {
            return heuristic(context.userMessage());
        }
        try {
            RouterOutput output = chatClientBuilder.build()
                    .prompt()
                    .system("""
                            You are the router for a travel assistant.
                            You must choose exactly one agent from WEATHER, GEO, TRAVEL_PLANNER, or GENERAL.
                            Rules:
                            1. Weather, temperature, rain, clothes, and seasonal conditions go to WEATHER.
                            2. Geocoding, reverse geocoding, addresses, coordinates, and district lookups go to GEO.
                            3. Itinerary planning, trip length, budget, sightseeing strategy, and route design go to TRAVEL_PLANNER.
                            4. Everything else goes to GENERAL.
                            5. If a travel-planning request is missing destination, duration, or budget, you may set clarificationRequired=true and ask one minimal blocking question.
                            6. If clarificationQuestion is needed, write it in the user's language.
                            Return structured data only.
                            """)
                    .user("""
                            User request: %s
                            Task memory: %s
                            Conversation summary: %s
                            """.formatted(
                            context.userMessage(),
                            context.taskMemory(),
                            context.conversationSummary()
                    ))
                    .call()
                    .entity(RouterOutput.class);
            if (output == null || output.agentType() == null) {
                return heuristic(context.userMessage());
            }
            return new AgentRouteDecision(
                    output.agentType(),
                    output.reason() == null ? "LLM route" : output.reason(),
                    output.clarificationRequired(),
                    output.clarificationQuestion()
            );
        } catch (Exception exception) {
            return heuristic(context.userMessage());
        }
    }

    private AgentRouteDecision heuristic(String message) {
        String content = message == null ? "" : message.toLowerCase();
        boolean chinese = containsChinese(message);
        if (containsAny(content, "weather", "temperature", "rain") || containsAny(message, "天气", "温度", "下雨")) {
            return new AgentRouteDecision(AgentType.WEATHER, "keyword route: weather", false, null);
        }
        if (containsAny(content, "coordinate", "address", "geocode", "latitude", "longitude") || containsAny(message, "经纬度", "地址", "坐标", "地理编码")) {
            return new AgentRouteDecision(AgentType.GEO, "keyword route: geo", false, null);
        }
        if (containsAny(content, "trip", "itinerary", "budget", "travel", "days") || containsAny(message, "旅行", "旅游", "攻略", "行程", "预算", "几天")) {
            boolean missingDestination = !hasDestinationHint(message, content);
            boolean missingDays = !hasDayHint(message, content);
            boolean missingBudget = !hasBudgetHint(message, content);
            boolean clarify = missingDestination || missingDays || missingBudget;
            return new AgentRouteDecision(
                    AgentType.TRAVEL_PLANNER,
                    "keyword route: planner",
                    clarify,
                    clarify ? (chinese ? "请补充目的地、天数和预算，我再把行程细化成可执行方案。" : "Please share the destination, trip length, and budget so I can compute a constrained plan.") : null
            );
        }
        return new AgentRouteDecision(AgentType.GENERAL, "fallback route", false, null);
    }

    private boolean hasDestinationHint(String message, String content) {
        if (content.contains(" to ")) {
            return true;
        }
        if (message == null || message.isBlank()) {
            return false;
        }
        if (EN_DESTINATION.matcher(message).find()) {
            return true;
        }
        return ZH_DESTINATION.matcher(message).find()
                || ZH_GO_TO.matcher(message).find()
                || containsAny(message, "杭州", "上海", "北京", "广州", "深圳", "成都", "重庆", "西安", "苏州", "南京");
    }

    private boolean hasDayHint(String message, String content) {
        return content.contains("day") || (message != null && ZH_DAYS.matcher(message).find());
    }

    private boolean hasBudgetHint(String message, String content) {
        return content.contains("budget")
                || content.contains("cny")
                || content.contains("rmb")
                || (message != null && BUDGET.matcher(message).find());
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value != null && value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    private record RouterOutput(
            AgentType agentType,
            String reason,
            boolean clarificationRequired,
            String clarificationQuestion
    ) {
    }
}
