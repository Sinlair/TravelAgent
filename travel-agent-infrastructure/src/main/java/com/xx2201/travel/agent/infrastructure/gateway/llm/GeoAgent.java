package com.xx2201.travel.agent.infrastructure.gateway.llm;

import com.xx2201.travel.agent.domain.gateway.AmapGateway;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionContext;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionResult;
import com.xx2201.travel.agent.domain.model.valobj.AgentType;
import com.xx2201.travel.agent.domain.model.valobj.GeoLocation;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSuggestion;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GeoAgent extends AbstractOpenAiSpecialistAgent {

    private static final Pattern COORDINATE = Pattern.compile("(-?\\d+(?:\\.\\d+)?)\\s*[,，]\\s*(-?\\d+(?:\\.\\d+)?)");

    private final AmapGateway amapGateway;

    public GeoAgent(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("amapToolCallbackProvider") ToolCallbackProvider toolCallbackProvider,
            OpenAiAvailability openAiAvailability,
            AmapGateway amapGateway
    ) {
        super(chatClientBuilder, openAiAvailability, toolCallbackProvider);
        this.amapGateway = amapGateway;
    }

    @Override
    public AgentType supports() {
        return AgentType.GEO;
    }

    @Override
    protected String systemPrompt() {
        return """
                You are the Geo Agent for a travel assistant.
                Respond in the user's language.
                Handle address parsing, coordinates, reverse geocoding, and place disambiguation.
                Prefer tool results over guesses.
                Present the resolved address, coordinates, administrative area, and any ambiguity clearly.
                """;
    }

    @Override
    protected AgentExecutionResult fallback(AgentExecutionContext context, Exception exception) {
        String message = context.userMessage() == null ? "" : context.userMessage().trim();
        Matcher coordinate = COORDINATE.matcher(message);
        boolean chinese = containsChinese(message);
        if (coordinate.find()) {
            GeoLocation location = amapGateway.reverseGeocode(coordinate.group(1), coordinate.group(2));
            return new AgentExecutionResult(
                    supports(),
                    renderReverseLocation(location, chinese),
                    java.util.Map.of(
                            "toolEnabled", true,
                            "fallback", true,
                            "fallbackReason", rootMessage(exception)
                    ),
                    null
            );
        }

        String keyword = resolveKeyword(context);
        if (keyword == null || keyword.isBlank()) {
            return super.fallback(context, exception);
        }

        GeoLocation location = amapGateway.geocode(keyword);
        List<PlaceSuggestion> suggestions = amapGateway.inputTips(keyword, context.taskMemory().destination());
        return new AgentExecutionResult(
                supports(),
                renderLocation(keyword, location, suggestions, chinese),
                java.util.Map.of(
                        "toolEnabled", true,
                        "fallback", true,
                        "fallbackReason", rootMessage(exception)
                ),
                null
        );
    }

    @Override
    protected String fallbackAnswer(AgentExecutionContext context, Exception exception) {
        if (containsChinese(context.userMessage())) {
            return "模型服务暂时不可用。请直接提供地点名称、地址，或者一组经纬度，我会改用本地地理查询。";
        }
        return "The model service is temporarily unavailable. Please share a place name, address, or coordinates and I will use a direct geo lookup.";
    }

    private String resolveKeyword(AgentExecutionContext context) {
        if (context.taskMemory().destination() != null && !context.taskMemory().destination().isBlank()) {
            return context.taskMemory().destination();
        }
        String message = context.userMessage() == null ? "" : context.userMessage();
        return message
                .replaceAll("(?i)where is|address of|coordinates of|geocode|reverse geocode|locate|find", " ")
                .replaceAll("地址|坐标|经纬度|地理编码|逆地理编码|在哪里|在哪儿|帮我查|请帮我查|请告诉我|看看", " ")
                .replaceAll("[?？!！,，。:：]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String renderReverseLocation(GeoLocation location, boolean chinese) {
        if (chinese) {
            return """
                    当前无法连接模型服务，已切换到本地坐标反查。

                    名称：%s
                    地址：%s
                    坐标：%s, %s
                    区划编码：%s
                    """.formatted(
                    blankToDefault(location.name(), "坐标点"),
                    blankToDefault(location.address(), "-"),
                    blankToDefault(location.longitude(), "-"),
                    blankToDefault(location.latitude(), "-"),
                    blankToDefault(location.adCode(), "-")
            ).trim();
        }
        return """
                The model service is unavailable, so I switched to a direct reverse-geocoding lookup.

                Name: %s
                Address: %s
                Coordinates: %s, %s
                AdCode: %s
                """.formatted(
                blankToDefault(location.name(), "Coordinate point"),
                blankToDefault(location.address(), "-"),
                blankToDefault(location.longitude(), "-"),
                blankToDefault(location.latitude(), "-"),
                blankToDefault(location.adCode(), "-")
        ).trim();
    }

    private String renderLocation(String keyword, GeoLocation location, List<PlaceSuggestion> suggestions, boolean chinese) {
        String candidates = suggestions.stream()
                .filter(item -> item != null && item.name() != null && !item.name().isBlank())
                .map(PlaceSuggestion::name)
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + " / " + right)
                .orElse("-");
        if (chinese) {
            return """
                    当前无法连接模型服务，已切换到本地地理查询。

                    查询词：%s
                    地址：%s
                    坐标：%s, %s
                    区划编码：%s
                    候选地点：%s
                    """.formatted(
                    keyword,
                    blankToDefault(location.address(), keyword),
                    blankToDefault(location.longitude(), "-"),
                    blankToDefault(location.latitude(), "-"),
                    blankToDefault(location.adCode(), "-"),
                    candidates
            ).trim();
        }
        return """
                The model service is unavailable, so I switched to a direct geo lookup.

                Query: %s
                Address: %s
                Coordinates: %s, %s
                AdCode: %s
                Candidates: %s
                """.formatted(
                keyword,
                blankToDefault(location.address(), keyword),
                blankToDefault(location.longitude(), "-"),
                blankToDefault(location.latitude(), "-"),
                blankToDefault(location.adCode(), "-"),
                candidates
        ).trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
