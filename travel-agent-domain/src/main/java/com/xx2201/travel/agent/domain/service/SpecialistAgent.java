package com.xx2201.travel.agent.domain.service;

import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionContext;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionResult;
import com.xx2201.travel.agent.domain.model.valobj.AgentType;

public interface SpecialistAgent {

    AgentType supports();

    AgentExecutionResult execute(AgentExecutionContext context);
}
