package com.avivly.urlshortener.controller;

import com.avivly.urlshortener.util.strategy.StrategyParamDefinition;
import com.avivly.urlshortener.util.strategy.StrategyRegistry;
import com.avivly.urlshortener.util.strategy.StrategyType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @GetMapping
    public Map<String, List<StrategyParamView>> getAll() {
        Map<String, List<StrategyParamView>> result = new LinkedHashMap<>();
        strategyRegistry.getAllSchemas().forEach((type, defs) ->
            result.put(type.name(), defs.stream().map(StrategyParamView::from).toList()));
        return result;
    }
}
