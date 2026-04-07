package com.travalagent.infrastructure.gateway.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travalagent.domain.model.entity.ConversationImageFacts;
import com.travalagent.domain.model.valobj.ImageAttachment;
import com.travalagent.domain.model.valobj.ImageAttachmentInterpretation;
import com.travalagent.domain.service.ImageAttachmentInterpreter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAiImageAttachmentInterpreter implements ImageAttachmentInterpreter {

    private final ChatClient.Builder chatClientBuilder;
    private final OpenAiAvailability openAiAvailability;
    private final ObjectMapper objectMapper;

    public OpenAiImageAttachmentInterpreter(
            ChatClient.Builder chatClientBuilder,
            OpenAiAvailability openAiAvailability,
            ObjectMapper objectMapper
    ) {
        this.chatClientBuilder = chatClientBuilder;
        this.openAiAvailability = openAiAvailability;
        this.objectMapper = objectMapper;
    }

    @Override
    public ImageAttachmentInterpretation interpretTravelContext(String userMessage, List<ImageAttachment> attachments) {
        if (attachments == null || attachments.isEmpty() || !openAiAvailability.isAvailable()) {
            return fallbackInterpretation();
        }
        try {
            String content = chatClientBuilder.build()
                    .prompt()
                    .system("""
                            You extract structured travel-planning facts from uploaded images.
                            Return strict JSON only.

                            JSON shape:
                            {
                              "origin": string|null,
                              "destination": string|null,
                              "startDate": string|null,
                              "endDate": string|null,
                              "days": number|null,
                              "budget": string|null,
                              "hotelName": string|null,
                              "hotelArea": string|null,
                              "activities": string[],
                              "missingFields": string[]
                            }

                            Rules:
                            - Only include facts that are visible in the images.
                            - Do not guess.
                            - Allowed missingFields values:
                              ["origin","destination","startDate","endDate","days","budget","hotelName","hotelArea","activities"]
                            - If an activity is not clearly visible, leave it out.
                            - Prefer the user's language for string values when the image already contains it.
                            """)
                    .user(user -> user
                            .text("""
                                    User request:
                                    %s

                                    Extract only the travel-relevant facts from the uploaded images.
                                    """.formatted(userMessage == null ? "" : userMessage))
                            .media(ImageAttachmentMediaSupport.toMediaArray(attachments)))
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                return fallbackInterpretation();
            }
            InterpreterOutput output = objectMapper.readValue(content, InterpreterOutput.class);
            ConversationImageFacts facts = normalizeFacts(output);
            return new ImageAttachmentInterpretation(buildSummary(facts), facts);
        } catch (Exception exception) {
            return fallbackInterpretation();
        }
    }

    private ImageAttachmentInterpretation fallbackInterpretation() {
        ConversationImageFacts facts = new ConversationImageFacts(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of("origin", "destination", "startDate", "endDate", "days", "budget", "hotelName", "hotelArea", "activities")
        );
        return new ImageAttachmentInterpretation(
                "I could not extract clear structured travel facts from the uploaded images.",
                facts
        );
    }

    private ConversationImageFacts normalizeFacts(InterpreterOutput output) {
        List<String> activities = output.activities() == null ? List.of() : output.activities().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<String> missingFields = output.missingFields() == null ? List.of() : output.missingFields().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return new ConversationImageFacts(
                normalizeText(output.origin()),
                normalizeText(output.destination()),
                normalizeText(output.startDate()),
                normalizeText(output.endDate()),
                output.days(),
                normalizeText(output.budget()),
                normalizeText(output.hotelName()),
                normalizeText(output.hotelArea()),
                activities,
                missingFields
        );
    }

    private String buildSummary(ConversationImageFacts facts) {
        List<String> lines = new ArrayList<>();
        appendLine(lines, "Origin", facts.origin());
        appendLine(lines, "Destination", facts.destination());
        appendLine(lines, "Start Date", facts.startDate());
        appendLine(lines, "End Date", facts.endDate());
        appendLine(lines, "Days", facts.days() == null ? null : String.valueOf(facts.days()));
        appendLine(lines, "Budget", facts.budget());
        appendLine(lines, "Hotel", facts.hotelName());
        appendLine(lines, "Hotel Area", facts.hotelArea());
        if (!facts.activities().isEmpty()) {
            lines.add("- Activities: " + String.join(", ", facts.activities()));
        }
        if (lines.isEmpty()) {
            lines.add("- No clear travel facts extracted.");
        }
        return String.join("\n", lines);
    }

    private void appendLine(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add("- " + label + ": " + value);
        }
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record InterpreterOutput(
            String origin,
            String destination,
            String startDate,
            String endDate,
            Integer days,
            String budget,
            String hotelName,
            String hotelArea,
            List<String> activities,
            List<String> missingFields
    ) {
    }
}
