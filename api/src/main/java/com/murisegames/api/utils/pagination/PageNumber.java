package com.murisegames.api.utils.pagination;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Min;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = {})
@Min(0)
@ReportAsSingleViolation
@Parameter(
        description = "Zero-based page index.",
        example = "0"
)
@Target({FIELD, PARAMETER, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface PageNumber {

    String message() default
            "Page number cannot be negative.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
