package com.xx2201.travel.agent.domain.service;

import com.xx2201.travel.agent.domain.model.entity.TravelPlan;
import com.xx2201.travel.agent.domain.model.valobj.AgentExecutionContext;

public interface TravelPlanBuilder {

    TravelPlan build(AgentExecutionContext context);

    String render(TravelPlan plan, AgentExecutionContext context);
}

