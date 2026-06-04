package com.memcyco.urlshortener.controller;

import com.memcyco.urlshortener.dto.AnalyticsResponse;
import com.memcyco.urlshortener.dto.CreateLinkRequest;
import com.memcyco.urlshortener.dto.UpdateLinkRequest;
import com.memcyco.urlshortener.model.ShortLink;
import com.memcyco.urlshortener.service.AnalyticsService;
import com.memcyco.urlshortener.service.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Links", description = "Manage short links")
@RestController
@RequestMapping("/api/links")
@RequiredArgsConstructor
public class LinkController {

    private final LinkService linkService;
    private final AnalyticsService analyticsService;

    @Operation(summary = "Create a short link")
    @ApiResponse(responseCode = "201", description = "Link created")
    @ApiResponse(responseCode = "409", description = "Short code conflict (SEQUENTIAL strategy)")
    @PostMapping
    public ResponseEntity<ShortLink> create(@Valid @RequestBody CreateLinkRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(linkService.create(req));
    }

    @Operation(summary = "List all links")
    @GetMapping
    public List<ShortLink> getAll() {
        return linkService.findAll();
    }

    @Operation(summary = "Update a link")
    @PutMapping("/{id}")
    public ShortLink update(@PathVariable Long id, @Valid @RequestBody UpdateLinkRequest req) {
        return linkService.update(id, req);
    }

    @Operation(summary = "Delete a link")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        linkService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get click analytics for a link")
    @ApiResponse(responseCode = "404", description = "Link not found")
    @GetMapping("/{shortCode}/analytics")
    public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
        return analyticsService.getAnalytics(shortCode);
    }
}
