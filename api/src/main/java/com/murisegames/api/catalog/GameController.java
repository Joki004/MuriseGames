package com.murisegames.api.catalog;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameController {
    private final GameRepository games;

    public GameController(GameRepository games) { this.games = games; }

    @GetMapping("/api/games")
    public List<GameSummary> list() {
        return games.findAll(Sort.by("name")).stream()
            .map(game -> new GameSummary(game.getCode(), game.getName(), game.getRulesStatus()))
            .toList();
    }

    public record GameSummary(String code, String name, String rulesStatus) {}
}
