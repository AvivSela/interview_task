package com.avivly.urlshortener.service;

import com.avivly.urlshortener.dto.CreateLinkRequest;
import com.avivly.urlshortener.dto.UpdateLinkRequest;
import com.avivly.urlshortener.model.ShortLink;
import com.avivly.urlshortener.model.User;
import com.avivly.urlshortener.repository.ShortLinkRepository;
import com.avivly.urlshortener.repository.UserRepository;
import com.avivly.urlshortener.util.strategy.StrategyRegistry;
import com.avivly.urlshortener.util.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinkService {

    private final ShortLinkRepository repo;
    private final StrategyRegistry strategyRegistry;
    private final UserRepository userRepo;

    @Cacheable(value = "shortLinks", key = "#shortCode")
    public ShortLink findByShortCode(String shortCode) {
        return repo.findByShortCode(shortCode).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ShortLink> findAllByOwner(Long ownerId) {
        return repo.findByOwnerId(ownerId);
    }

    // Strips BOM, RTL/LTR marks, zero-width spaces, and regular whitespace from a URL string.
    private static String sanitizeUrl(String url) {
        return url == null ? null : url.replaceAll("^[\\s\\u200B-\\u200F\\uFEFF]+|[\\s\\u200B-\\u200F\\uFEFF]+$", "");
    }

    @Transactional
    public ShortLink create(CreateLinkRequest req, Long callerId) {
        String originalUrl = sanitizeUrl(req.originalUrl());
        String strategyName = req.strategy();
        StrategyType strategyType;
        try {
            strategyType = (strategyName != null && !strategyName.isBlank())
                ? StrategyType.valueOf(strategyName)
                : StrategyType.RANDOM_BASE62;
        } catch (IllegalArgumentException e) {
            strategyType = StrategyType.RANDOM_BASE62;
        }

        ShortLink partialEntity = ShortLink.builder()
            .originalUrl(originalUrl)
            .strategy(strategyType.name())
            .maxClicks(req.maxClicks())
            .expiresAt(req.expiresAt())
            .tags(req.tags())
            .build();

        if (callerId != null) {
            User owner = userRepo.findById(callerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            partialEntity.setOwner(owner);
        }

        if (req.customAlias() != null && !req.customAlias().isBlank()) {
            String code = req.customAlias();
            if (repo.findByShortCode(code).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already taken: " + code);
            }
            partialEntity.setShortCode(code);
            return repo.save(partialEntity);
        }

        if (strategyType == StrategyType.SEQUENTIAL) {
            ShortLink saved = repo.saveAndFlush(partialEntity);
            String code = strategyRegistry.validateAndGenerate(
                    strategyType, originalUrl, saved.getId(), req.strategyParams());
            if (repo.findByShortCode(code).isPresent()) {
                repo.delete(saved);
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already taken: " + code);
            }
            saved.setShortCode(code);
            return repo.save(saved);
        }

        String code = strategyRegistry.validateAndGenerate(
                strategyType, originalUrl, null, req.strategyParams());
        if (repo.findByShortCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Short code already taken: " + code);
        }
        partialEntity.setShortCode(code);
        return repo.save(partialEntity);
    }

    @Transactional
    public ShortLink createGuest(CreateLinkRequest req) {
        return create(req, null);
    }

    @Transactional
    @CacheEvict(value = "shortLinks", key = "#result.shortCode")
    public ShortLink update(Long id, UpdateLinkRequest req, Long callerId) {
        ShortLink link = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found: " + id));

        if (link.getOwner() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest links cannot be updated");
        }
        if (!link.getOwner().getId().equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
        }

        if (req.originalUrl() != null) link.setOriginalUrl(sanitizeUrl(req.originalUrl()));
        if (req.isActive() != null) link.setActive(req.isActive());
        if (req.expiresAt() != null) link.setExpiresAt(req.expiresAt());
        if (req.tags() != null) link.setTags(req.tags());
        if (req.maxClicks() != null) link.setMaxClicks(req.maxClicks());

        return repo.save(link);
    }

    @Transactional
    public void delete(Long id, Long callerId) {
        ShortLink link = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found: " + id));

        if (link.getOwner() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Guest links cannot be deleted");
        }
        if (!link.getOwner().getId().equals(callerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the owner");
        }

        evictCache(link.getShortCode());
        repo.delete(link);
    }

    @CacheEvict(value = "shortLinks", key = "#shortCode")
    public void evictCache(String shortCode) {}

    @Transactional
    @CacheEvict(value = "shortLinks", key = "#shortCode")
    public void recordClick(String shortCode) {
        repo.incrementClicks(shortCode);
    }
}
