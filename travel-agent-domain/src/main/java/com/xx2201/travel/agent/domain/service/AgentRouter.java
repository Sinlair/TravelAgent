package com.xx2201.travel.agent.domain.service;

import com.xx2201.travel.agent.domain.model.valobj.AgentRouteDecision;
import com.xx2201.travel.agent.domain.model.valobj.RoutingContext;

public interface AgentRouter {

    AgentRouteDecision route(RoutingContext context);
}
