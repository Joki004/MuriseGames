package com.murisegames.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.murisegames.api.security.SecurityErrorResponseWriter.ACCESS_DENIED_MESSAGE;
import static com.murisegames.api.security.SecurityErrorResponseWriter.UNAUTHORIZED_MESSAGE;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MuriseGames API",
                version = "v1",
                description = "Authoritative backend API for MuriseGames events, memberships, schedules, and scoring."
        )
)
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        description = "Supabase JWT access token"
)
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";
    static final String REQUEST_ID_HEADER = RequestIdFilter.REQUEST_ID_HEADER;

    private static final String JSON = "application/json";
    private static final String ERROR_SCHEMA = "ApiErrorResponse";
    private static final String VALIDATION_ERROR_SCHEMA = "ApiValidationError";

    private static final String BAD_REQUEST = "BadRequest";
    private static final String UNAUTHORIZED = "Unauthorized";
    private static final String FORBIDDEN = "Forbidden";
    private static final String NOT_FOUND = "NotFound";
    private static final String CONFLICT = "Conflict";
    private static final String INTERNAL_ERROR = "InternalServerError";

    @Bean
    OpenApiCustomizer standardApiDocumentation() {
        return openApi -> {
            registerComponents(openApi);
            customizeOperations(openApi);
        };
    }

    private void registerComponents(OpenAPI openApi) {
        Components components = openApi.getComponents();

        if (components == null) {
            components = new Components();
            openApi.setComponents(components);
        }

        components.addSchemas(VALIDATION_ERROR_SCHEMA, validationErrorSchema());
        components.addSchemas(ERROR_SCHEMA, apiErrorSchema());

        components.addResponses(
                BAD_REQUEST,
                errorResponse(
                        "Request validation or parsing failed.",
                        "FAIL",
                        "Validation failed.",
                        400,
                        List.of(
                                Map.of(
                                        "field", "size",
                                        "message", "Page size must be between 1 and 100."
                                )
                        )
                )
        );
        components.addResponses(
                UNAUTHORIZED,
                errorResponse(
                        "Authentication is missing or invalid.",
                        "FAIL",
                        UNAUTHORIZED_MESSAGE,
                        401,
                        List.of()
                )
        );
        components.addResponses(
                FORBIDDEN,
                errorResponse(
                        "The authenticated user lacks permission.",
                        "FAIL",
                        ACCESS_DENIED_MESSAGE,
                        403,
                        List.of()
                )
        );
        components.addResponses(
                NOT_FOUND,
                errorResponse(
                        "The requested resource was not found.",
                        "FAIL",
                        "Resource not found.",
                        404,
                        List.of()
                )
        );
        components.addResponses(
                CONFLICT,
                errorResponse(
                        "The request conflicts with current resource state.",
                        "FAIL",
                        "Request conflicts with existing data.",
                        409,
                        List.of()
                )
        );
        components.addResponses(
                INTERNAL_ERROR,
                errorResponse(
                        "An unexpected server error occurred.",
                        "ERROR",
                        "An unexpected error occurred.",
                        500,
                        List.of()
                )
        );
    }

    private void customizeOperations(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        openApi.getPaths().forEach((path, pathItem) -> {
            if (!path.equals("/api") && !path.startsWith("/api/")) {
                return;
            }

            pathItem.readOperationsMap().forEach(
                    (method, operation) -> customizeOperation(path, method, operation)
            );
        });
    }

    private void customizeOperation(
            String path,
            PathItem.HttpMethod method,
            Operation operation
    ) {
        ApiResponses responses = operation.getResponses();

        if (responses == null) {
            responses = new ApiResponses();
            operation.setResponses(responses);
        }

        addRequestIdParameter(operation);
        addRequestIdToInlineResponses(responses);

        addResponseIfMissing(responses, "400", BAD_REQUEST);
        addResponseIfMissing(responses, "500", INTERNAL_ERROR);

        if (isPublicOperation(path, method)) {
            operation.setSecurity(List.of());
        } else {
            addBearerSecurity(operation);
            addResponseIfMissing(responses, "401", UNAUTHORIZED);
            addResponseIfMissing(responses, "403", FORBIDDEN);
        }

        if (path.contains("{")) {
            addResponseIfMissing(responses, "404", NOT_FOUND);
        }

        if (isMutation(method)) {
            addResponseIfMissing(responses, "409", CONFLICT);
        }
    }

    private void addBearerSecurity(Operation operation) {
        if (operation.getSecurity() == null || operation.getSecurity().isEmpty()) {
            operation.setSecurity(
                    List.of(new SecurityRequirement().addList(BEARER_AUTH))
            );
        }
    }

    private void addRequestIdParameter(Operation operation) {
        boolean alreadyPresent = operation.getParameters() != null
                && operation.getParameters()
                .stream()
                .anyMatch(parameter ->
                        REQUEST_ID_HEADER.equalsIgnoreCase(parameter.getName())
                                && "header".equals(parameter.getIn())
                );

        if (alreadyPresent) {
            return;
        }

        StringSchema schema = new StringSchema();
        schema.setMaxLength(100);
        schema.setPattern("^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$");

        Parameter parameter = new Parameter();
        parameter.setName(REQUEST_ID_HEADER);
        parameter.setIn("header");
        parameter.setRequired(false);
        parameter.setDescription(
                "Optional caller-provided request identifier. "
                        + "The server generates one when omitted or invalid."
        );
        parameter.setSchema(schema);

        operation.addParametersItem(parameter);
    }

    private void addRequestIdToInlineResponses(ApiResponses responses) {
        responses.values().stream()
                .filter(response -> response.get$ref() == null)
                .forEach(response -> response.addHeaderObject(
                        REQUEST_ID_HEADER,
                        requestIdHeader()
                ));
    }

    private void addResponseIfMissing(
            ApiResponses responses,
            String status,
            String componentName
    ) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(
                    status,
                    new ApiResponse().$ref("#/components/responses/" + componentName)
            );
        }
    }

    private boolean isPublicOperation(String path, PathItem.HttpMethod method) {
        // Documentation mirror of PublicEndpointMatcher for /api/** routes.
        // Currently all /api/** routes require a verified Supabase JWT.
        return false;
    }

    private boolean isMutation(PathItem.HttpMethod method) {
        return method == PathItem.HttpMethod.POST
                || method == PathItem.HttpMethod.PUT
                || method == PathItem.HttpMethod.PATCH
                || method == PathItem.HttpMethod.DELETE;
    }

    private ObjectSchema validationErrorSchema() {
        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("One invalid request field or parameter.");
        schema.setRequired(List.of("field", "message"));
        schema.addProperty(
                "field",
                new StringSchema().description("Field or parameter name.")
        );
        schema.addProperty(
                "message",
                new StringSchema().description("Safe validation message.")
        );
        return schema;
    }

    private ObjectSchema apiErrorSchema() {
        StringSchema status = new StringSchema();
        status.setDescription("FAIL for client errors; ERROR for server errors.");
        status.setEnum(List.of("FAIL", "ERROR"));

        ObjectSchema data = new ObjectSchema();
        data.setNullable(true);
        data.setDescription("Always null for unsuccessful responses.");

        ObjectSchema schema = new ObjectSchema();
        schema.setDescription("Standard MuriseGames API error envelope.");
        schema.setRequired(
                List.of("status", "message", "code", "data", "errors", "requestId")
        );
        schema.addProperty("status", status);
        schema.addProperty(
                "message",
                new StringSchema().description("Public, non-sensitive response message.")
        );
        schema.addProperty(
                "code",
                new IntegerSchema().format("int32").description("HTTP status code.")
        );
        schema.addProperty("data", data);
        schema.addProperty(
                "errors",
                new ArraySchema()
                        .items(new ObjectSchema().$ref("#/components/schemas/" + VALIDATION_ERROR_SCHEMA))
                        .description("Validation details; empty for other errors.")
        );
        schema.addProperty(
                "requestId",
                new StringSchema().description("Identifier returned in X-Request-ID.")
        );
        return schema;
    }

    private ApiResponse errorResponse(
            String description,
            String status,
            String message,
            int code,
            List<?> errors
    ) {
        MediaType mediaType = new MediaType();
        mediaType.setSchema(new ObjectSchema().$ref("#/components/schemas/" + ERROR_SCHEMA));
        mediaType.setExample(errorExample(status, message, code, errors));

        Content content = new Content();
        content.addMediaType(JSON, mediaType);

        ApiResponse response = new ApiResponse();
        response.setDescription(description);
        response.setContent(content);
        response.addHeaderObject(REQUEST_ID_HEADER, requestIdHeader());
        return response;
    }

    private Map<String, Object> errorExample(
            String status,
            String message,
            int code,
            List<?> errors
    ) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("status", status);
        example.put("message", message);
        example.put("code", code);
        example.put("data", null);
        example.put("errors", errors);
        example.put("requestId", "0f37f21c-7cf9-43f8-b922-54e563ed1806");
        return example;
    }

    private Header requestIdHeader() {
        Header header = new Header();
        header.setDescription("Request identifier for log correlation and support.");
        header.setRequired(true);
        header.setSchema(new StringSchema());
        return header;
    }
}