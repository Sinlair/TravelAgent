package com.travalagent.domain.service;

import com.travalagent.domain.model.valobj.AgentRouteDecision;
import com.travalagent.domain.model.valobj.RoutingContext;

public interface AgentRouter {

    AgentRouteDecision route(RoutingContext context);
}
