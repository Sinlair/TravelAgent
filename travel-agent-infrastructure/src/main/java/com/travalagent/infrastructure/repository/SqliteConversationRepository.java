package com.travalagent.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travalagent.domain.model.entity.ConversationMessage;
import com.travalagent.domain.model.entity.ConversationFeedback;
import com.travalagent.domain.model.entity.ConversationImageAttachment;
import com.travalagent.domain.model.entity.ConversationImageFacts;
import com.travalagent.domain.model.entity.ConversationImageContext;
import com.travalagent.domain.model.entity.ConversationSession;
import com.travalagent.domain.model.entity.TaskMemory;
import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.entity.TravelPlanVersionSnapshot;
import com.travalagent.domain.model.entity.TimelineEvent;
import com.travalagent.domain.model.valobj.AgentType;
import com.travalagent.domain.model.valobj.ExecutionStage;
import com.travalagent.domain.model.valobj.LongTermMemoryItem;
import com.travalagent.domain.model.valobj.MessageRole;
import com.travalagent.domain.model.valobj.TimelineEventStatus;
import com.travalagent.domain.repository.ConversationRepository;
import com.travalagent.domain.repository.LongTermMemoryRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class SqliteConversationRepository implements ConversationRepository, LongTermMemoryRepository {
    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ConversationImageAttachment>> IMAGE_ATTACHMENT_LIST = new TypeReference<>() {
    };
    private static final TypeReference<ConversationImageFacts> IMAGE_FACTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public SqliteConversationRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
        ensureTaskMemoryColumns();
        ensureTravelPlanVersionTable();
    }

    @Override
    public Optional<ConversationSession> findConversation(String conversationId) {
        return jdbcClient.sql("""
                        SELECT conversation_id, title, agent_type, summary, created_at, updated_at
                        FROM conversation_session
                        WHERE conversation_id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .query(this::mapConversation)
                .optional();
    }

    @Override
    public List<ConversationSession> listConversations() {
        return jdbcClient.sql("""
                        SELECT conversation_id, title, agent_type, summary, created_at, updated_at
                        FROM conversation_session
                        ORDER BY updated_at DESC
                        """)
                .query(this::mapConversation)
                .list();
    }

    @Override
    public void saveConversation(ConversationSession session) {
        jdbcClient.sql("""
                        INSERT INTO conversation_session (conversation_id, title, agent_type, summary, created_at, updated_at)
                        VALUES (:conversationId, :title, :agentType, :summary, :createdAt, :updatedAt)
                        ON CONFLICT(conversation_id) DO UPDATE SET
                          title = excluded.title,
                          agent_type = excluded.agent_type,
                          summary = excluded.summary,
                          updated_at = excluded.updated_at
                        """)
                .param("conversationId", session.conversationId())
                .param("title", session.title())
                .param("agentType", session.lastAgent() == null ? null : session.lastAgent().name())
                .param("summary", session.summary())
                .param("createdAt", session.createdAt().toString())
                .param("updatedAt", session.updatedAt().toString())
                .update();
    }

    @Override
    public void saveMessage(ConversationMessage message) {
        jdbcClient.sql("""
                        INSERT INTO conversation_message (id, conversation_id, role, content, agent_type, metadata_json, created_at)
                        VALUES (:id, :conversationId, :role, :content, :agentType, :metadataJson, :createdAt)
                        """)
                .param("id", message.id())
                .param("conversationId", message.conversationId())
                .param("role", message.role().name())
                .param("content", message.content())
                .param("agentType", message.agentType() == null ? null : message.agentType().name())
                .param("metadataJson", writeJson(message.metadata()))
                .param("createdAt", message.createdAt().toString())
                .update();
    }

    @Override
    public List<ConversationMessage> findMessages(String conversationId) {
        return jdbcClient.sql("""
                        SELECT id, conversation_id, role, content, agent_type, metadata_json, created_at
                        FROM conversation_message
                        WHERE conversation_id = :conversationId
                        ORDER BY created_at ASC
                        """)
                .param("conversationId", conversationId)
                .query(this::mapMessage)
                .list();
    }

    @Override
    public List<ConversationMessage> findRecentMessages(String conversationId, int limit) {
        List<ConversationMessage> reversed = jdbcClient.sql("""
                        SELECT id, conversation_id, role, content, agent_type, metadata_json, created_at
                        FROM conversation_message
                        WHERE conversation_id = :conversationId
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("conversationId", conversationId)
                .param("limit", limit)
                .query(this::mapMessage)
                .list();
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public Optional<TaskMemory> findTaskMemory(String conversationId) {
        return jdbcClient.sql("""
                        SELECT conversation_id, origin, destination, start_date, end_date, days, travelers, budget, preferences_json, pending_question, summary, updated_at
                        FROM task_memory
                        WHERE conversation_id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .query(this::mapTaskMemory)
                .optional();
    }

    @Override
    public Optional<ConversationFeedback> findFeedback(String conversationId) {
        return jdbcClient.sql("""
                        SELECT conversation_id, label, reason_code, note, agent_type, destination, days, budget,
                               has_travel_plan, metadata_json, created_at, updated_at
                        FROM conversation_feedback
                        WHERE conversation_id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .query(this::mapFeedback)
                .optional();
    }

    @Override
    public List<ConversationFeedback> listFeedback(int limit) {
        return jdbcClient.sql("""
                        SELECT conversation_id, label, reason_code, note, agent_type, destination, days, budget,
                               has_travel_plan, metadata_json, created_at, updated_at
                        FROM conversation_feedback
                        ORDER BY updated_at DESC
                        LIMIT :limit
                        """)
                .param("limit", limit)
                .query(this::mapFeedback)
                .list();
    }

    @Override
    public void saveTaskMemory(TaskMemory taskMemory) {
        jdbcClient.sql("""
                        INSERT INTO task_memory
                        (conversation_id, origin, destination, start_date, end_date, days, travelers, budget, preferences_json, pending_question, summary, updated_at)
                        VALUES (:conversationId, :origin, :destination, :startDate, :endDate, :days, :travelers, :budget, :preferencesJson, :pendingQuestion, :summary, :updatedAt)
                        ON CONFLICT(conversation_id) DO UPDATE SET
                          origin = excluded.origin,
                          destination = excluded.destination,
                          start_date = excluded.start_date,
                          end_date = excluded.end_date,
                          days = excluded.days,
                          travelers = excluded.travelers,
                          budget = excluded.budget,
                          preferences_json = excluded.preferences_json,
                          pending_question = excluded.pending_question,
                          summary = excluded.summary,
                          updated_at = excluded.updated_at
                        """)
                .param("conversationId", taskMemory.conversationId())
                .param("origin", taskMemory.origin())
                .param("destination", taskMemory.destination())
                .param("startDate", taskMemory.startDate())
                .param("endDate", taskMemory.endDate())
                .param("days", taskMemory.days())
                .param("travelers", taskMemory.travelers())
                .param("budget", taskMemory.budget())
                .param("preferencesJson", writeJson(taskMemory.preferences()))
                .param("pendingQuestion", taskMemory.pendingQuestion())
                .param("summary", taskMemory.summary())
                .param("updatedAt", taskMemory.updatedAt().toString())
                .update();
    }

    @Override
    public void saveFeedback(ConversationFeedback feedback) {
        jdbcClient.sql("""
                        INSERT INTO conversation_feedback
                        (conversation_id, label, reason_code, note, agent_type, destination, days, budget,
                         has_travel_plan, metadata_json, created_at, updated_at)
                        VALUES (:conversationId, :label, :reasonCode, :note, :agentType, :destination, :days, :budget,
                                :hasTravelPlan, :metadataJson, :createdAt, :updatedAt)
                        ON CONFLICT(conversation_id) DO UPDATE SET
                          label = excluded.label,
                          reason_code = excluded.reason_code,
                          note = excluded.note,
                          agent_type = excluded.agent_type,
                          destination = excluded.destination,
                          days = excluded.days,
                          budget = excluded.budget,
                          has_travel_plan = excluded.has_travel_plan,
                          metadata_json = excluded.metadata_json,
                          updated_at = excluded.updated_at
                        """)
                .param("conversationId", feedback.conversationId())
                .param("label", feedback.label())
                .param("reasonCode", feedback.reasonCode())
                .param("note", feedback.note())
                .param("agentType", feedback.agentType() == null ? null : feedback.agentType().name())
                .param("destination", feedback.destination())
                .param("days", feedback.days())
                .param("budget", feedback.budget())
                .param("hasTravelPlan", feedback.hasTravelPlan() ? 1 : 0)
                .param("metadataJson", writeJson(feedback.metadata()))
                .param("createdAt", feedback.createdAt().toString())
                .param("updatedAt", feedback.updatedAt().toString())
                .update();
    }

    @Override
    public Optional<ConversationImageContext> findPendingImageContext(String conversationId) {
        return jdbcClient.sql("""
                        SELECT conversation_id, summary, facts_json, attachments_json, created_at, updated_at
                        FROM conversation_image_context
                        WHERE conversation_id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .query(this::mapImageContext)
                .optional();
    }

    @Override
    public void savePendingImageContext(ConversationImageContext imageContext) {
        jdbcClient.sql("""
                        INSERT INTO conversation_image_context (conversation_id, summary, facts_json, attachments_json, created_at, updated_at)
                        VALUES (:conversationId, :summary, :factsJson, :attachmentsJson, :createdAt, :updatedAt)
                        ON CONFLICT(conversation_id) DO UPDATE SET
                          summary = excluded.summary,
                          facts_json = excluded.facts_json,
                          attachments_json = excluded.attachments_json,
                          updated_at = excluded.updated_at
                        """)
                .param("conversationId", imageContext.conversationId())
                .param("summary", imageContext.summary())
                .param("factsJson", writeJson(imageContext.facts()))
                .param("attachmentsJson", writeJson(imageContext.attachments()))
                .param("createdAt", imageContext.createdAt().toString())
                .param("updatedAt", imageContext.updatedAt().toString())
                .update();
    }

    @Override
    public void deletePendingImageContext(String conversationId) {
        jdbcClient.sql("DELETE FROM conversation_image_context WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
    }

    @Override
    public Optional<TravelPlan> findTravelPlan(String conversationId) {
        return jdbcClient.sql("""
                        SELECT plan_json
                        FROM travel_plan_snapshot
                        WHERE conversation_id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .query((rs, rowNum) -> readTravelPlan(rs.getString("plan_json")))
                .optional();
    }

    @Override
    public void saveTravelPlan(TravelPlan travelPlan) {
        jdbcClient.sql("""
                        INSERT INTO travel_plan_snapshot (conversation_id, plan_json, updated_at)
                        VALUES (:conversationId, :planJson, :updatedAt)
                        ON CONFLICT(conversation_id) DO UPDATE SET
                          plan_json = excluded.plan_json,
                          updated_at = excluded.updated_at
                        """)
                .param("conversationId", travelPlan.conversationId())
                .param("planJson", writeJson(travelPlan))
                .param("updatedAt", travelPlan.updatedAt().toString())
                .update();
    }

    @Override
    public List<TravelPlanVersionSnapshot> listTravelPlanVersions(String conversationId, int limit) {
        int normalizedLimit = limit <= 0 ? 10 : limit;
        return jdbcClient.sql("""
                        SELECT version_id, conversation_id, input_summary, scope, plan_json, created_at
                        FROM travel_plan_version
                        WHERE conversation_id = :conversationId
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("conversationId", conversationId)
                .param("limit", normalizedLimit)
                .query(this::mapTravelPlanVersion)
                .list();
    }

    @Override
    public void saveTravelPlanVersion(TravelPlanVersionSnapshot versionSnapshot) {
        jdbcClient.sql("""
                        INSERT INTO travel_plan_version (version_id, conversation_id, input_summary, scope, plan_json, created_at)
                        VALUES (:versionId, :conversationId, :inputSummary, :scope, :planJson, :createdAt)
                        """)
                .param("versionId", versionSnapshot.versionId())
                .param("conversationId", versionSnapshot.conversationId())
                .param("inputSummary", versionSnapshot.inputSummary())
                .param("scope", versionSnapshot.scope())
                .param("planJson", writeJson(versionSnapshot.travelPlan()))
                .param("createdAt", versionSnapshot.createdAt().toString())
                .update();
    }

    @Override
    public void saveTimeline(TimelineEvent timelineEvent) {
        jdbcClient.sql("""
                        INSERT INTO conversation_timeline (id, conversation_id, stage, message, details_json, created_at)
                        VALUES (:id, :conversationId, :stage, :message, :detailsJson, :createdAt)
                        """)
                .param("id", timelineEvent.id())
                .param("conversationId", timelineEvent.conversationId())
                .param("stage", timelineEvent.stage().name())
                .param("message", timelineEvent.message())
                .param("detailsJson", writeJson(encodeTimelineDetails(timelineEvent)))
                .param("createdAt", timelineEvent.endedAt().toString())
                .update();
    }

    @Override
    public List<TimelineEvent> findTimeline(String conversationId) {
        return jdbcClient.sql("""
                        SELECT id, conversation_id, stage, message, details_json, created_at
                        FROM conversation_timeline
                        WHERE conversation_id = :conversationId
                        ORDER BY created_at ASC
                        """)
                .param("conversationId", conversationId)
                .query(this::mapTimeline)
                .list();
    }

    @Override
    public void deleteConversation(String conversationId) {
        jdbcClient.sql("DELETE FROM long_term_memory WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM travel_plan_snapshot WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM travel_plan_version WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM conversation_timeline WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM conversation_feedback WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM conversation_image_context WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM task_memory WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM conversation_message WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
        jdbcClient.sql("DELETE FROM conversation_session WHERE conversation_id = :conversationId")
                .param("conversationId", conversationId)
                .update();
    }

    @Override
    public void saveMemory(String conversationId, String category, String content, Map<String, Object> metadata) {
        jdbcClient.sql("""
                        INSERT INTO long_term_memory (id, conversation_id, category, content, metadata_json, created_at)
                        VALUES (:id, :conversationId, :category, :content, :metadataJson, :createdAt)
                        """)
                .param("id", java.util.UUID.randomUUID().toString())
                .param("conversationId", conversationId)
                .param("category", category)
                .param("content", content)
                .param("metadataJson", writeJson(metadata))
                .param("createdAt", Instant.now().toString())
                .update();
    }

    @Override
    public List<LongTermMemoryItem> searchRelevant(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String like = "%" + query.trim() + "%";
        return jdbcClient.sql("""
                        SELECT id, conversation_id, category, content, metadata_json, created_at
                        FROM long_term_memory
                        WHERE content LIKE :keyword OR metadata_json LIKE :keyword
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .param("keyword", like)
                .param("limit", limit)
                .query((rs, rowNum) -> new LongTermMemoryItem(
                        rs.getString("id"),
                        rs.getString("conversation_id"),
                        rs.getString("category"),
                        rs.getString("content"),
                        readMap(rs.getString("metadata_json")),
                        Instant.parse(rs.getString("created_at"))
                ))
                .list();
    }

    private ConversationSession mapConversation(ResultSet rs, int rowNum) throws SQLException {
        String agentType = rs.getString("agent_type");
        return new ConversationSession(
                rs.getString("conversation_id"),
                rs.getString("title"),
                agentType == null ? null : AgentType.valueOf(agentType),
                rs.getString("summary"),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private ConversationMessage mapMessage(ResultSet rs, int rowNum) throws SQLException {
        String agentType = rs.getString("agent_type");
        return new ConversationMessage(
                rs.getString("id"),
                rs.getString("conversation_id"),
                MessageRole.valueOf(rs.getString("role")),
                rs.getString("content"),
                agentType == null ? null : AgentType.valueOf(agentType),
                Instant.parse(rs.getString("created_at")),
                readMap(rs.getString("metadata_json"))
        );
    }

    private TaskMemory mapTaskMemory(ResultSet rs, int rowNum) throws SQLException {
        return new TaskMemory(
                rs.getString("conversation_id"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getString("start_date"),
                rs.getString("end_date"),
                readInteger(rs, "days"),
                rs.getString("travelers"),
                rs.getString("budget"),
                readStringList(rs.getString("preferences_json")),
                rs.getString("pending_question"),
                rs.getString("summary"),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private ConversationFeedback mapFeedback(ResultSet rs, int rowNum) throws SQLException {
        String agentType = rs.getString("agent_type");
        Map<String, Object> metadata = readMap(rs.getString("metadata_json"));
        return new ConversationFeedback(
                rs.getString("conversation_id"),
                rs.getString("label"),
                metadataText(metadata, "targetId"),
                metadataText(metadata, "targetScope"),
                metadataText(metadata, "planVersion"),
                metadataStringList(metadata, "reasonLabels"),
                rs.getString("reason_code"),
                rs.getString("note"),
                agentType == null ? null : AgentType.valueOf(agentType),
                rs.getString("destination"),
                readInteger(rs, "days"),
                rs.getString("budget"),
                rs.getInt("has_travel_plan") == 1,
                metadata,
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private ConversationImageContext mapImageContext(ResultSet rs, int rowNum) throws SQLException {
        return new ConversationImageContext(
                rs.getString("conversation_id"),
                rs.getString("summary"),
                readImageFacts(rs.getString("facts_json")),
                readImageAttachments(rs.getString("attachments_json")),
                Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))
        );
    }

    private TimelineEvent mapTimeline(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> persistedDetails = readMap(rs.getString("details_json"));
        Map<String, Object> details = decodeTimelineDetails(persistedDetails);
        return new TimelineEvent(
                rs.getString("id"),
                rs.getString("conversation_id"),
                ExecutionStage.valueOf(rs.getString("stage")),
                readTimelineStatus(persistedDetails),
                rs.getString("message"),
                details,
                Instant.parse(rs.getString("created_at")),
                readTimelineInstant(persistedDetails, "startedAt", rs.getString("created_at")),
                readTimelineInstant(persistedDetails, "endedAt", rs.getString("created_at"))
        );
    }

    private TravelPlanVersionSnapshot mapTravelPlanVersion(ResultSet rs, int rowNum) throws SQLException {
        return new TravelPlanVersionSnapshot(
                rs.getString("version_id"),
                rs.getString("conversation_id"),
                rs.getString("input_summary"),
                rs.getString("scope"),
                readTravelPlan(rs.getString("plan_json")),
                Instant.parse(rs.getString("created_at"))
        );
    }

    private Map<String, Object> encodeTimelineDetails(TimelineEvent timelineEvent) {
        Map<String, Object> details = new LinkedHashMap<>(timelineEvent.details());
        details.put("status", timelineEvent.status().name());
        details.put("startedAt", timelineEvent.startedAt().toString());
        details.put("endedAt", timelineEvent.endedAt().toString());
        return Map.copyOf(details);
    }

    private Map<String, Object> decodeTimelineDetails(Map<String, Object> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> details = new LinkedHashMap<>(raw);
        details.remove("status");
        details.remove("startedAt");
        details.remove("endedAt");
        return Map.copyOf(details);
    }

    private TimelineEventStatus readTimelineStatus(Map<String, Object> detailsWithMetadata) {
        Object raw = detailsWithMetadata.get("status");
        if (raw instanceof String value && !value.isBlank()) {
            try {
                return TimelineEventStatus.valueOf(value.trim());
            } catch (IllegalArgumentException ignored) {
                return TimelineEventStatus.COMPLETED;
            }
        }
        return TimelineEventStatus.COMPLETED;
    }

    private Instant readTimelineInstant(Map<String, Object> detailsWithMetadata, String key, String fallback) {
        Object raw = detailsWithMetadata.get(key);
        if (raw instanceof String value && !value.isBlank()) {
            try {
                return Instant.parse(value.trim());
            } catch (Exception ignored) {
                return Instant.parse(fallback);
            }
        }
        return Instant.parse(fallback);
    }

    private List<String> readStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, STRING_LIST);
        } catch (IOException exception) {
            return List.of();
        }
    }

    private List<ConversationImageAttachment> readImageAttachments(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, IMAGE_ATTACHMENT_LIST);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read image attachments JSON", exception);
        }
    }

    private ConversationImageFacts readImageFacts(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ConversationImageFacts(null, null, null, null, null, null, null, null, List.of(), List.of());
        }
        try {
            return objectMapper.readValue(raw, IMAGE_FACTS_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read image facts JSON", exception);
        }
    }

    private TravelPlan readTravelPlan(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, TravelPlan.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to deserialize travel plan", exception);
        }
    }

    private Map<String, Object> readMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, MAP_TYPE);
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON payload", exception);
        }
    }

    private Integer readInteger(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String value = rs.getString(column);
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        Matcher matcher = INTEGER_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String metadataText(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private List<String> metadataStringList(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private void ensureTaskMemoryColumns() {
        ensureColumn("task_memory", "start_date", "TEXT");
        ensureColumn("task_memory", "end_date", "TEXT");
        ensureColumn("task_memory", "travelers", "TEXT");
    }

    private void ensureTravelPlanVersionTable() {
        jdbcClient.sql("""
                        CREATE TABLE IF NOT EXISTS travel_plan_version (
                            version_id TEXT PRIMARY KEY,
                            conversation_id TEXT NOT NULL,
                            input_summary TEXT,
                            scope TEXT NOT NULL,
                            plan_json TEXT NOT NULL,
                            created_at TEXT NOT NULL
                        )
                        """)
                .update();
    }

    private void ensureColumn(String table, String column, String type) {
        try {
            jdbcClient.sql("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type).update();
        } catch (Exception ignored) {
            // Column already exists or the database engine rejected a duplicate migration.
        }
    }
}

