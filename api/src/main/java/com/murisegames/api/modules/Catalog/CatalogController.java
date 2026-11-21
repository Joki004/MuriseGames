package com.murisegames.api.modules.Catalog;

import com.murisegames.api.modules.Catalog.dto.CatalogGameCreateRequest;
import com.murisegames.api.utils.CustomApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Game Catalog", description = "Shared game definitions and published versions")
@RestController
@RequestMapping("/api/catalog/games")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "List active published games")
    @GetMapping
    public ResponseEntity<CustomApiResponse<List<CatalogGameResponse>>> list() {
        return ResponseEntity.ok(
                CustomApiResponse.success(
                        catalogService.listPublishedGames(),
                        "Catalog games retrieved.",
                        HttpStatus.OK.value()
                )
        );
    }

    @Operation(summary = "Create and publish a new catalog game (v1)")
    @PostMapping
    public ResponseEntity<CustomApiResponse<CatalogGameResponse>> create(
            @Valid @RequestBody CatalogGameCreateRequest request
    ) {
        CatalogGameResponse created = catalogService.createAndPublish(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CustomApiResponse.success(
                        created,
                        "Catalog game created.",
                        HttpStatus.CREATED.value()
                ));
    }
}