package com.avivly.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "click_analytics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shortCode;

    private LocalDateTime clickedAt;

    @Column(columnDefinition = "TEXT")
    private String referer;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GeoStatus geoStatus = GeoStatus.DISABLED;

    @Column(length = 100)
    private String country;

    @Column(length = 100)
    private String city;

    @PrePersist
    protected void onCreate() {
        if (clickedAt == null) {
            clickedAt = LocalDateTime.now();
        }
    }
}
