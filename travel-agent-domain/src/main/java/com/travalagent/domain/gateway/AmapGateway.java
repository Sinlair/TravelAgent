package com.travalagent.domain.gateway;

import com.travalagent.domain.model.valobj.GeoLocation;
import com.travalagent.domain.model.valobj.PlaceSearchQuery;
import com.travalagent.domain.model.valobj.PlaceSuggestion;
import com.travalagent.domain.model.valobj.TransitRoutePlan;
import com.travalagent.domain.model.valobj.TransitRouteQuery;
import com.travalagent.domain.model.valobj.WeatherSnapshot;

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
