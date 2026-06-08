package com.avivly.urlshortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "short_links")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = true)
    private String shortCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Builder.Default
    private String strategy = "RANDOM_BASE62";

    @Builder.Default
    private boolean isActive = true;

    private Integer maxClicks;

    @Builder.Default
    private int totalClicks = 0;

    private LocalDateTime expiresAt;

    private String tags;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public boolean isValid() {
        if (!isActive) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        if (maxClicks != null && totalClicks >= maxClicks) return false;
        return true;
    }
}
