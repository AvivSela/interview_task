package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.AnalyticsResponse;
import com.avivly.urlshortener.model.ClickAnalytics;
import com.avivly.urlshortener.repository.ClickAnalyticsRepository;
import com.avivly.urlshortener.repository.ShortLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ClickAnalyticsRepository clickRepo;
    private final ShortLinkRepository linkRepo;
    private final GeoResolverService geoResolverService;

    @Async("analyticsTaskExecutor")
    public void logClickAsync(String shortCode, String referer, String userAgent, String ip) {
        var geo = geoResolverService.resolve(ip);
        clickRepo.save(ClickAnalytics.builder()
            .shortCode(shortCode)
            .referer(referer)
            .userAgent(userAgent)
            .ipAddress(ip)
            .geoStatus(geo.status())
            .country(geo.country())
            .city(geo.city())
            .build());
    }

    public AnalyticsResponse getAnalytics(String shortCode) {
        int totalClicks = linkRepo.findByShortCode(shortCode)
            .map(l -> l.getTotalClicks())
            .orElse(0);

        List<AnalyticsResponse.DailyCount> clicksOverTime = clickRepo.countClicksByDay(shortCode)
            .stream()
            .map(row -> new AnalyticsResponse.DailyCount(str(row, 0), count(row, 1)))
            .toList();

        List<AnalyticsResponse.ReferrerCount> topReferrers = clickRepo.topReferrers(shortCode, 10)
            .stream()
            .map(row -> new AnalyticsResponse.ReferrerCount(str(row, 0), count(row, 1)))
            .toList();

        List<AnalyticsResponse.AgentCount> topUserAgents = clickRepo.topUserAgents(shortCode, 10)
            .stream()
            .map(row -> new AnalyticsResponse.AgentCount(str(row, 0), count(row, 1)))
            .toList();

        List<AnalyticsResponse.CountryCount> topCountries = geoResolverService.isEnabled()
            ? clickRepo.topCountries(shortCode, 10).stream()
                .map(row -> new AnalyticsResponse.CountryCount(str(row, 0), count(row, 1)))
                .toList()
            : List.of();

        List<AnalyticsResponse.CityCount> topCities = geoResolverService.isEnabled()
            ? clickRepo.topCities(shortCode, 10).stream()
                .map(row -> new AnalyticsResponse.CityCount(str(row, 0), str(row, 1), count(row, 2)))
                .toList()
            : List.of();

        return new AnalyticsResponse(totalClicks, clicksOverTime, topReferrers, topUserAgents,
                topCountries, topCities);
    }

    private static String str(Object[] r, int i) {
        return r[i] != null ? r[i].toString() : "";
    }

    private static long count(Object[] r, int i) {
        return r[i] != null ? ((Number) r[i]).longValue() : 0L;
    }
}
