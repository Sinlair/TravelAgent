package com.travalagent.domain.service;

import com.travalagent.domain.model.entity.TravelPlan;
import com.travalagent.domain.model.valobj.AgentExecutionContext;

public interface TravelPlanBuilder {

    TravelPlan build(AgentExecutionContext context);

    String render(TravelPlan plan, AgentExecutionContext context);
}

