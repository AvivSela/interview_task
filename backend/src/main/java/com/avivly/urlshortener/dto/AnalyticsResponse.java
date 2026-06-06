package com.avivly.urlshortener.dto;

import java.util.List;

public record AnalyticsResponse(
    int totalClicks,
    List<DailyCount> clicksOverTime,
    List<ReferrerCount> topReferrers,
    List<AgentCount> topUserAgents,
    List<CountryCount> topCountries,
    List<CityCount> topCities
) {
    public record DailyCount(String date, long count) {}
    public record ReferrerCount(String referer, long count) {}
    public record AgentCount(String userAgent, long count) {}
    public record CountryCount(String country, long clicks) {}
    public record CityCount(String city, String country, long clicks) {}
}
