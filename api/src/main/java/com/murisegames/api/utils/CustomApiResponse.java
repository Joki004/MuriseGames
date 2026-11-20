package com.murisegames.api.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomApiResponse<T> {

    private ApiResponseStatus status;
    private String message;
    private int code;
    private T data;

    @Builder.Default
    private List<ApiValidationError> errors = List.of();

    private String requestId;

    public static <T> CustomApiResponse<T> success(
            T data,
            String message,
            int code
    ) {
        return CustomApiResponse.<T>builder()
                .status(ApiResponseStatus.SUCCESS)
                .message(message)
                .code(code)
                .data(data)
                .errors(List.of())
                .requestId(currentRequestId())
                .build();
    }

    public static <T> CustomApiResponse<T> fail(
            String message,
            int code
    ) {
        return fail(
                message,
                code,
                List.of()
        );
    }

    public static <T> CustomApiResponse<T> fail(
            String message,
            int code,
            List<ApiValidationError> errors
    ) {
        return CustomApiResponse.<T>builder()
                .status(ApiResponseStatus.FAIL)
                .message(message)
                .code(code)
                .data(null)
                .errors(
                        errors == null
                                ? List.of()
                                : List.copyOf(errors)
                )
                .requestId(currentRequestId())
                .build();
    }

    public static <T> CustomApiResponse<T> error(
            String message,
            int code
    ) {
        return CustomApiResponse.<T>builder()
                .status(ApiResponseStatus.ERROR)
                .message(message)
                .code(code)
                .data(null)
                .errors(List.of())
                .requestId(currentRequestId())
                .build();
    }

    private static String currentRequestId() {
        return MDC.get("requestId");
    }
}