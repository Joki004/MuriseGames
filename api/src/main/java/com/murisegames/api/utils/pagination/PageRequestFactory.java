package com.murisegames.api.utils.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PageRequestFactory {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static final String DEFAULT_PAGE_VALUE = "0";
    public static final String DEFAULT_SIZE_VALUE = "20";

    private PageRequestFactory() {
    }

    public static PageRequest fixed(
            int page,
            int size,
            Sort sort
    ) {
        return fixed(page, size, sort, null);
    }

    public static PageRequest fixed(
            int page,
            int size,
            Sort sort,
            String tieBreaker
    ) {
        validate(page, size);

        Sort resolvedSort = Objects.requireNonNull(
                sort,
                "Sort is required."
        );

        if (tieBreaker != null && !tieBreaker.isBlank()) {
            String normalizedTieBreaker = tieBreaker.trim();

            if (resolvedSort.getOrderFor(normalizedTieBreaker) == null) {
                Sort.Direction direction = resolvedSort.stream()
                        .findFirst()
                        .map(Sort.Order::getDirection)
                        .orElse(Sort.Direction.ASC);

                resolvedSort = resolvedSort.and(
                        Sort.by(direction, normalizedTieBreaker)
                );
            }
        }

        return PageRequest.of(
                page,
                size,
                resolvedSort
        );
    }

    public static PageRequest sorted(
            int page,
            int size,
            String sortBy,
            String sortDir,
            String defaultSortBy,
            Sort.Direction defaultDirection,
            Set<String> allowedSortFields,
            String tieBreaker
    ) {
        validate(page, size);

        Objects.requireNonNull(
                allowedSortFields,
                "Allowed sort fields are required."
        );

        String resolvedSortBy = normalizeSortField(
                sortBy,
                defaultSortBy
        );

        if (!allowedSortFields.contains(resolvedSortBy)) {
            throw new IllegalArgumentException(
                    "Unsupported sort field."
            );
        }

        Sort.Direction direction = resolveDirection(
                sortDir,
                defaultDirection
        );

        Sort sort = Sort.by(direction, resolvedSortBy);

        if (tieBreaker != null
                && !tieBreaker.isBlank()
                && !tieBreaker.equals(resolvedSortBy)) {
            sort = sort.and(
                    Sort.by(direction, tieBreaker.trim())
            );
        }

        return PageRequest.of(page, size, sort);
    }

    public static String normalizeOptionalFilter(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public static void validate(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative."
            );
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100."
            );
        }
    }

    private static String normalizeSortField(
            String sortBy,
            String defaultSortBy
    ) {
        String fallback = Objects.requireNonNull(
                defaultSortBy,
                "Default sort field is required."
        ).trim();

        return sortBy == null || sortBy.isBlank()
                ? fallback
                : sortBy.trim();
    }

    private static Sort.Direction resolveDirection(
            String sortDir,
            Sort.Direction defaultDirection
    ) {
        Sort.Direction fallback = Objects.requireNonNull(
                defaultDirection,
                "Default sort direction is required."
        );

        if (sortDir == null || sortDir.isBlank()) {
            return fallback;
        }

        return switch (sortDir.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException(
                    "Sort direction must be 'asc' or 'desc'."
            );
        };
    }
}
