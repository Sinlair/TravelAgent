package com.travalagent.infrastructure.gateway.llm;

import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.LongTermMemoryItem;
import com.travalagent.domain.service.SpecialistAgent;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

abstract class AbstractOpenAiSpecialistAgent implements SpecialistAgent {

    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;
    private final ToolCallbackProvider[] toolCallbackProviders;

    protected AbstractOpenAiSpecialistAgent(
            ChatClient.Builder chatClientBuilder,
            OpenAiAvailability openAiAvailability,
            ToolCallbackProvider... toolCallbackProviders
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.openAiAvailability = openAiAvailability;
        this.toolCallbackProviders = toolCallbackProviders == null ? new ToolCallbackProvider[0] : toolCallbackProviders;
    }

    @Override
    public AgentExecutionResult execute(AgentExecutionContext context) {
        if (!openAiAvailability.isAvailable()) {
            return fallback(context, new IllegalStateException("OpenAI API key is missing or disabled"));
        }
        try {
            String content;
            ChatClient.ChatClientRequestSpec prompt = chatClientBuilder.build()
                    .prompt()
                    .system(systemPrompt());

            if (context.imageAttachments().isEmpty()) {
                prompt = prompt.user(buildUserPrompt(context));
            } else {
                prompt = prompt.user(user -> user
                        .text(buildUserPrompt(context))
                        .media(ImageAttachmentMediaSupport.toMediaArray(context.imageAttachments())));
            }

            if (toolCallbackProviders.length > 0) {
                content = prompt.toolCallbacks(toolCallbackProviders)
                        .toolContext(Map.of("conversationId", context.conversationId()))
                        .call()
                        .content();
            } else {
                content = prompt.call().content();
            }

            return new AgentExecutionResult(
                    supports(),
                    content == null ? "I could not produce a result this turn. Please try again." : content,
                    metadata(context, false, null),
                    null
            );
        } catch (Exception exception) {
            return fallback(context, exception);
        }
    }

    protected abstract String systemPrompt();

    protected AgentExecutionResult fallback(AgentExecutionContext context, Exception exception) {
        return new AgentExecutionResult(
                supports(),
                fallbackAnswer(context, exception),
                metadata(context, true, exception),
                null
        );
    }

    protected String fallbackAnswer(AgentExecutionContext context, Exception exception) {
        if (containsChinese(context.userMessage())) {
            return "模型服务暂时不可用，请稍后再试。";
        }
        return "The model service is temporarily unavailable. Please try again later.";
    }

    protected String buildUserPrompt(AgentExecutionContext context) {
        return """
                Respond in the user's language.

                Latest user request:
                %s

                Structured task memory:
                %s

                Conversation summary:
                %s

                Long-term memory:
                %s

                Recent messages:
                %s
                """.formatted(
                context.userMessage(),
                renderTaskMemory(context),
                emptyToDash(context.conversationSummary()),
                renderLongTermMemory(context),
                renderRecentMessages(context)
        );
    }

    private String renderTaskMemory(AgentExecutionContext context) {
        var memory = context.taskMemory();
        return """
                Origin: %s
                Destination: %s
                Days: %s
                Budget: %s
                Preferences: %s
                Pending question: %s
                """.formatted(
                emptyToDash(memory.origin()),
                emptyToDash(memory.destination()),
                memory.days() == null ? "-" : memory.days(),
                emptyToDash(memory.budget()),
                memory.preferences().isEmpty() ? "-" : String.join(", ", memory.preferences()),
                emptyToDash(memory.pendingQuestion())
        );
    }

    private String renderLongTermMemory(AgentExecutionContext context) {
        if (context.longTermMemories().isEmpty()) {
            return "-";
        }
        return context.longTermMemories().stream()
                .map(LongTermMemoryItem::content)
                .collect(Collectors.joining("\n- ", "- ", ""));
    }

    private String renderRecentMessages(AgentExecutionContext context) {
        return context.recentMessages().stream()
                .map(this::renderMessage)
                .collect(Collectors.joining("\n"));
    }

    private String renderMessage(ConversationMessage message) {
        return "[%s] %s".formatted(message.role().name(), message.content());
    }

    private String emptyToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    protected boolean containsChinese(String value) {
        return value != null && value.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }

    protected String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : current.getMessage();
    }

    private Map<String, Object> metadata(AgentExecutionContext context, boolean fallback, Exception exception) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (context.routeReason() != null && !context.routeReason().isBlank()) {
            metadata.put("routeReason", context.routeReason());
        }
        metadata.put("toolEnabled", toolCallbackProviders.length > 0);
        metadata.put("imageAttachmentCount", context.imageAttachments().size());
        metadata.put("hasImageContext", context.imageContextSummary() != null && !context.imageContextSummary().isBlank());
        metadata.put("fallback", fallback);
        if (fallback && exception != null) {
            metadata.put("fallbackReason", rootMessage(exception));
        }
        return metadata;
    }
}
