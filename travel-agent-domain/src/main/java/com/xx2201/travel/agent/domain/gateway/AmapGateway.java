package com.xx2201.travel.agent.domain.gateway;

import com.xx2201.travel.agent.domain.model.valobj.GeoLocation;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSearchQuery;
import com.xx2201.travel.agent.domain.model.valobj.PlaceSuggestion;
import com.xx2201.travel.agent.domain.model.valobj.TransitRoutePlan;
import com.xx2201.travel.agent.domain.model.valobj.TransitRouteQuery;
import com.xx2201.travel.agent.domain.model.valobj.WeatherSnapshot;

import java.util.List;

public interface AmapGateway {

    WeatherSnapshot weather(String city);

    GeoLocation geocode(String address);

    GeoLocation reverseGeocode(String longitude, String latitude);

    default List<PlaceSuggestion> inputTips(String keyword, String city) {
        return inputTips(new PlaceSearchQuery(keyword, city, null, null, false, "poi"));
    }

    List<PlaceSuggestion> inputTips(PlaceSearchQuery query);

    TransitRoutePlan transitRoute(TransitRouteQuery query);
}
