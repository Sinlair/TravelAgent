package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.gateway.AmapGateway;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.WeatherSnapshot;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WeatherAgent extends AbstractOpenAiSpecialistAgent {

    private static final Pattern EN_LOCATION = Pattern.compile("(?:weather|temperature|rain)(?:\\s+in)?\\s+([A-Za-z][A-Za-z\\s-]{1,40})", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZH_LOCATION = Pattern.compile("([\\p{IsHan}]{2,20})(?:今天|明天|后天|现在|近期)?(?:的)?(?:天气|气温)");

    private final AmapGateway amapGateway;

    public WeatherAgent(
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
        return AgentType.WEATHER;
    }

    @Override
    protected String systemPrompt() {
        return """
                You are the Weather Agent for a travel assistant.
                Respond in the user's language.
                Use tools for real-time weather whenever possible.
                Structure the answer with:
                1. Current weather conclusion
                2. Temperature, wind, and precipitation risk
                3. One practical travel or clothing suggestion
                Ask only the minimum follow-up question if the location is unclear.
                """;
    }

    @Override
    protected AgentExecutionResult fallback(AgentExecutionContext context, Exception exception) {
        String city = resolveCity(context);
        if (city == null || city.isBlank()) {
            return super.fallback(context, exception);
        }

        WeatherSnapshot snapshot = amapGateway.weather(city);
        return new AgentExecutionResult(
                supports(),
                renderSnapshot(snapshot, containsChinese(context.userMessage())),
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
            return "模型服务暂时不可用。请直接告诉我想查询天气的城市或区县，我会改用本地天气查询。";
        }
        return "The model service is temporarily unavailable. Please share the city or district and I will fall back to a direct weather lookup.";
    }

    private String resolveCity(AgentExecutionContext context) {
        if (context.taskMemory().destination() != null && !context.taskMemory().destination().isBlank()) {
            return context.taskMemory().destination();
        }
        String message = context.userMessage() == null ? "" : context.userMessage().trim();
        Matcher zh = ZH_LOCATION.matcher(message);
        if (zh.find()) {
            return zh.group(1).trim();
        }
        Matcher en = EN_LOCATION.matcher(message);
        if (en.find()) {
            return en.group(1).trim();
        }
        return null;
    }

    private String renderSnapshot(WeatherSnapshot snapshot, boolean chinese) {
        String city = snapshot.city() == null || snapshot.city().isBlank() ? "the destination" : snapshot.city();
        String suggestion = suggestion(snapshot, chinese);
        if (chinese) {
            return """
                    当前无法连接模型服务，已切换到本地天气查询。

                    城市：%s
                    天气：%s
                    温度：%s℃
                    风向/风力：%s %s级
                    建议：%s
                    """.formatted(
                    city,
                    blankToDefault(snapshot.description(), "晴"),
                    blankToDefault(snapshot.temperature(), "25"),
                    blankToDefault(snapshot.windDirection(), "-"),
                    blankToDefault(snapshot.windPower(), "-"),
                    suggestion
            ).trim();
        }
        return """
                The model service is unavailable, so I switched to a direct weather lookup.

                City: %s
                Weather: %s
                Temperature: %s C
                Wind: %s / %s
                Advice: %s
                """.formatted(
                city,
                blankToDefault(snapshot.description(), "Sunny"),
                blankToDefault(snapshot.temperature(), "25"),
                blankToDefault(snapshot.windDirection(), "-"),
                blankToDefault(snapshot.windPower(), "-"),
                suggestion
        ).trim();
    }

    private String suggestion(WeatherSnapshot snapshot, boolean chinese) {
        String description = blankToDefault(snapshot.description(), "").toLowerCase();
        int temperature = parseTemperature(snapshot.temperature());
        if (description.contains("雨") || description.contains("rain")) {
            return chinese ? "建议带伞，鞋子尽量防水。" : "Bring an umbrella and prefer water-resistant shoes.";
        }
        if (temperature <= 12) {
            return chinese ? "建议加外套或薄羽绒，早晚注意保暖。" : "Wear a coat or a light down layer, especially in the morning and evening.";
        }
        if (temperature <= 22) {
            return chinese ? "建议长袖加薄外套，体感会更稳妥。" : "A long-sleeve top with a light jacket should work well.";
        }
        return chinese ? "以轻便穿着为主，白天注意防晒和补水。" : "Light clothing should be fine. Keep sun protection and water handy.";
    }

    private int parseTemperature(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception exception) {
            return 25;
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
