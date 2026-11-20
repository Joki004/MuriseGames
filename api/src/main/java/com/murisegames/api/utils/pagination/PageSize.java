package com.murisegames.api.utils.pagination;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = {})
@Min(1)
@Max(100)
@ReportAsSingleViolation
@Parameter(
        description = "Number of records per page (1-100).",
        example = "20"
)
@Target({FIELD, PARAMETER, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface PageSize {

    String message() default
            "Page size must be between 1 and 100.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
