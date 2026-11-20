package com.murisegames.api.utils;

import com.murisegames.api.modules.User.UserException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.murisegames.api.security.SecurityErrorResponseWriter.ACCESS_DENIED_MESSAGE;
import static com.murisegames.api.security.SecurityErrorResponseWriter.UNAUTHORIZED_MESSAGE;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED = "Validation failed.";
    private static final String INTERNAL_ERROR = "An unexpected error occurred.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        Stream<ApiValidationError> fieldErrors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ApiValidationError(
                        error.getField(),
                        resolveMessage(error)
                ));

        Stream<ApiValidationError> globalErrors = exception
                .getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> new ApiValidationError(
                        error.getObjectName(),
                        resolveMessage(error)
                ));

        List<ApiValidationError> errors = Stream
                .concat(fieldErrors, globalErrors)
                .distinct()
                .toList();

        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, errors);
    }

    public ResponseEntity<CustomApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        return handleValidation(exception);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleHandlerMethodValidation(
            HandlerMethodValidationException exception
    ) {
        if (exception.isForReturnValue()) {
            log.error(
                    "Controller return-value validation failed; requestId={}",
                    requestId(),
                    exception
            );
            return error(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        List<ApiValidationError> errors = exception
                .getAllErrors()
                .stream()
                .map(error -> new ApiValidationError(
                        "request",
                        resolveMessage(error)
                ))
                .distinct()
                .toList();

        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<ApiValidationError> errors = exception
                .getConstraintViolations()
                .stream()
                .map(violation -> new ApiValidationError(
                        finalPathSegment(violation.getPropertyPath().toString()),
                        violation.getMessage()
                ))
                .distinct()
                .toList();

        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        ApiValidationError error = new ApiValidationError(
                exception.getParameterName(),
                "Parameter is required."
        );
        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, List.of(error));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMissingRequestHeader(
            MissingRequestHeaderException exception
    ) {
        ApiValidationError error = new ApiValidationError(
                exception.getHeaderName(),
                "Header is required."
        );
        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, List.of(error));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMissingRequestPart(
            MissingServletRequestPartException exception
    ) {
        ApiValidationError error = new ApiValidationError(
                exception.getRequestPartName(),
                "Request part is required."
        );
        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, List.of(error));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        ApiValidationError error = new ApiValidationError(
                exception.getName(),
                "Value has an invalid type or format."
        );
        return fail(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, List.of(error));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleUnreadableMessage(
            HttpMessageNotReadableException exception
    ) {
        log.debug("Malformed request body; requestId={}", requestId(), exception);
        return fail(HttpStatus.BAD_REQUEST, "Malformed JSON request.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleAuthentication(
            AuthenticationException exception
    ) {
        log.debug("Authentication rejected; requestId={}", requestId(), exception);
        return fail(HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleAccessDenied(
            AccessDeniedException exception
    ) {
        log.debug("Access denied; requestId={}", requestId(), exception);
        return fail(HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleSecurityException(
            SecurityException exception
    ) {
        log.debug("Security check rejected; requestId={}", requestId(), exception);
        return fail(HttpStatus.FORBIDDEN, ACCESS_DENIED_MESSAGE);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleNotFound(
            EntityNotFoundException exception
    ) {
        return fail(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    public ResponseEntity<CustomApiResponse<Void>> handleEntityNotFound(
            EntityNotFoundException exception
    ) {
        return handleNotFound(exception);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException exception
    ) {
        return fail(HttpStatus.NOT_FOUND, "Resource not found.");
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class
    })
    public ResponseEntity<CustomApiResponse<Void>> handleDatabaseConflict(
            RuntimeException exception
    ) {
        log.warn(
                "Data conflict; requestId={}; exceptionType={}",
                requestId(),
                exception.getClass().getSimpleName()
        );
        return fail(HttpStatus.CONFLICT, "Request conflicts with existing data.");
    }

    public ResponseEntity<CustomApiResponse<Void>> handleDataConflict(
            RuntimeException exception
    ) {
        return handleDatabaseConflict(exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return fail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method is not supported for this endpoint."
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception
    ) {
        return fail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content type is not supported."
        );
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMediaTypeNotAcceptable(
            HttpMediaTypeNotAcceptableException exception
    ) {
        return fail(
                HttpStatus.NOT_ACCEPTABLE,
                "Requested response media type is not supported."
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception
    ) {
        return fail(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file is too large.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        log.debug("Invalid argument; requestId={}", requestId(), exception);
        return fail(HttpStatus.BAD_REQUEST, "Invalid request.");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleResponseStatus(
            ResponseStatusException exception
    ) {
        HttpStatusCode status = exception.getStatusCode();

        if (status.is5xxServerError()) {
            log.error(
                    "Request failed with server status {}; requestId={}",
                    status.value(),
                    requestId(),
                    exception
            );
            return error(status);
        }

        String message = Optional
                .ofNullable(exception.getReason())
                .filter(reason -> !reason.isBlank())
                .orElse("Request failed.");

        return fail(status, message);
    }

    @ExceptionHandler(UserException.class)
    public ResponseEntity<CustomApiResponse<Void>> handleUserException(
            UserException exception
    ) {
        return businessConflict(
                exception,
                "User operation conflicts with the current state."
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomApiResponse<Void>> handleUnexpectedError(
            Exception exception
    ) {
        ResponseStatus responseStatus = AnnotatedElementUtils
                .findMergedAnnotation(exception.getClass(), ResponseStatus.class);

        if (responseStatus != null) {
            HttpStatus status = responseStatus.code();
            if (!status.is5xxServerError()) {
                String message = responseStatus.reason().isBlank()
                        ? status.getReasonPhrase()
                        : responseStatus.reason();
                return fail(status, message);
            }
        }

        log.error("Unhandled exception; requestId={}", requestId(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public ResponseEntity<CustomApiResponse<Void>> handleUnexpectedException(
            Exception exception
    ) {
        return handleUnexpectedError(exception);
    }

    protected ResponseEntity<CustomApiResponse<Void>> businessConflict(
            Exception exception,
            String fallbackMessage
    ) {
        log.debug(
                "Business operation conflict; requestId={}; exceptionType={}",
                requestId(),
                exception.getClass().getSimpleName()
        );
        return fail(HttpStatus.CONFLICT, publicMessage(exception, fallbackMessage));
    }

    private String publicMessage(Exception exception, String fallbackMessage) {
        return Optional
                .ofNullable(exception.getMessage())
                .filter(message -> !message.isBlank())
                .orElse(fallbackMessage);
    }

    private ResponseEntity<CustomApiResponse<Void>> fail(
            HttpStatusCode status,
            String message
    ) {
        return fail(status, message, List.of());
    }

    private ResponseEntity<CustomApiResponse<Void>> fail(
            HttpStatusCode status,
            String message,
            List<ApiValidationError> errors
    ) {
        return ResponseEntity
                .status(status)
                .body(CustomApiResponse.<Void>fail(message, status.value(), errors));
    }

    private ResponseEntity<CustomApiResponse<Void>> error(HttpStatusCode status) {
        return ResponseEntity
                .status(status)
                .body(CustomApiResponse.<Void>error(INTERNAL_ERROR, status.value()));
    }

    private String resolveMessage(MessageSourceResolvable error) {
        return Optional
                .ofNullable(error.getDefaultMessage())
                .filter(message -> !message.isBlank())
                .orElse("Invalid value.");
    }

    private String finalPathSegment(String propertyPath) {
        int finalDot = propertyPath.lastIndexOf('.');
        if (finalDot < 0 || finalDot == propertyPath.length() - 1) {
            return propertyPath;
        }
        return propertyPath.substring(finalDot + 1);
    }

    private String requestId() {
        return Optional
                .ofNullable(MDC.get("requestId"))
                .orElse("unavailable");
    }
}