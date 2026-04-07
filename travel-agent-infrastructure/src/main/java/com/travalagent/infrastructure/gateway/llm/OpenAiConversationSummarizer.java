package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.service.ConversationSummarizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpenAiConversationSummarizer implements ConversationSummarizer {

    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;

    public OpenAiConversationSummarizer(ChatClient.Builder chatClientBuilder, OpenAiAvailability openAiAvailability) {
        this.chatClientBuilder = chatClientBuilder;
        this.openAiAvailability = openAiAvailability;
    }

    @Override
    public String summarize(String existingSummary, List<ConversationMessage> messages) {
        if (messages.size() < 4 || !openAiAvailability.isAvailable()) {
            return existingSummary;
        }
        try {
            String summary = chatClientBuilder.build()
                    .prompt()
                    .system("""
                            你是会话摘要器。
                            输出一段 80 到 120 字的中文摘要，保留目的地、预算、天数、偏好、未完成问题和最新决策。
                            """)
                    .user("""
                            旧摘要:
                            %s

                            新消息:
                            %s
                            """.formatted(
                            existingSummary == null ? "-" : existingSummary,
                            messages.stream()
                                    .map(message -> "[%s] %s".formatted(message.role().name(), message.content()))
                                    .reduce((left, right) -> left + "\n" + right)
                                    .orElse("-")
                    ))
                    .call()
                    .content();
            return summary == null || summary.isBlank() ? existingSummary : summary;
        } catch (Exception exception) {
            return existingSummary;
        }
    }
}
