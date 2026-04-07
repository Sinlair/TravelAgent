package com.travalagent.domain.service;

import com.travalagent.domain.model.valobj.AgentExecutionContext;
import com.travalagent.domain.model.valobj.AgentExecutionResult;
import com.travalagent.domain.model.valobj.AgentType;

public interface SpecialistAgent {

    AgentType supports();

    AgentExecutionResult execute(AgentExecutionContext context);
}
