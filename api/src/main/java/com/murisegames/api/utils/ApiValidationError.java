package com.murisegames.api.utils;

public record ApiValidationError(
        String field,
        String message
) {
}
