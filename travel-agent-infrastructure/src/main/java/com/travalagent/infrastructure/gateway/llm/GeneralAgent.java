package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.AgentType;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class GeneralAgent extends AbstractOpenAiSpecialistAgent {

    public GeneralAgent(ChatClient.Builder chatClientBuilder, OpenAiAvailability openAiAvailability) {
        super(chatClientBuilder, openAiAvailability);
    }

    @Override
    public AgentType supports() {
        return AgentType.GENERAL;
    }

    @Override
    protected String systemPrompt() {
        return """
                You are the General Agent for a travel assistant.
                Respond in the user's language.
                Handle travel-adjacent questions that do not require specialist weather, geo, or itinerary planning behavior.
                Stay concise and do not claim to have used tools when you did not.
                """;
    }

    @Override
    protected AgentExecutionResult fallback(AgentExecutionContext context, Exception exception) {
        boolean chinese = containsChinese(context.userMessage());
        String answer = chinese
                ? """
                  当前模型服务暂时不可用，所以这次不能生成自由问答结果。

                  你仍然可以继续让我做这些事情：
                  1. 直接规划行程，比如“帮我规划三天杭州行程，预算3000元”。
                  2. 查询天气，请明确城市或区县。
                  3. 查询地点或坐标，请直接给地点名、地址或经纬度。
                  """.trim()
                : """
                  The model service is temporarily unavailable, so I cannot produce a free-form answer for this request.

                  You can still ask me to:
                  1. Build a travel plan, for example: plan a 3-day Hangzhou trip with a 3000 CNY budget.
                  2. Check weather when you provide a city or district.
                  3. Resolve a place or coordinates when you provide a place name, address, or latitude/longitude.
                  """.trim();
        return new AgentExecutionResult(
                supports(),
                answer,
                java.util.Map.of(
                        "toolEnabled", false,
                        "fallback", true,
                        "fallbackReason", rootMessage(exception)
                ),
                null
        );
    }
}
