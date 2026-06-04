package com.memcyco.urlshortener.controller;

import com.memcyco.urlshortener.util.strategy.StrategyParamDefinition;
import com.memcyco.urlshortener.util.strategy.StrategyRegistry;
import com.memcyco.urlshortener.util.strategy.StrategyType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Strategies", description = "Available short-code generation strategies")
@RestController
@RequestMapping("/api/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyRegistry strategyRegistry;

    record StrategyParamView(String name, String type, boolean required, String description) {
        static StrategyParamView from(StrategyParamDefinition d) {
            return new StrategyParamView(
                d.name(), d.type().name().toLowerCase(), d.required(), d.description());
        }
    }

    @Operation(summary = "List available code-generation strategies and their parameters")
    @GetMapping
    public Map<String, List<StrategyParamView>> getAll() {
        Map<String, List<StrategyParamView>> result = new LinkedHashMap<>();
        strategyRegistry.getAllSchemas().forEach((type, defs) ->
            result.put(type.name(), defs.stream().map(StrategyParamView::from).toList()));
        return result;
    }
}
