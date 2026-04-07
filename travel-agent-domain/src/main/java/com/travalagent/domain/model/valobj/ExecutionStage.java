package com.travalagent.domain.model.valobj;

public enum ExecutionStage {
    ANALYZE_QUERY,
    RECALL_MEMORY,
    SELECT_AGENT,
    SPECIALIST,
    CALL_TOOL,
    VALIDATE_PLAN,
    REPAIR_PLAN,
    FINALIZE_MEMORY,
    COMPLETED,
    ERROR
}