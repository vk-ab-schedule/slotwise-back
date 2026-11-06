package ru.vk.itmo.scheduler.slotwise.common.api;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenApiCustomizer nullableReferences() {
        return api -> api.getComponents().getSchemas().values().forEach(OpenApiConfiguration::normalizeNullableReferences);
    }

    private static void normalizeNullableReferences(Schema<?> schema) {
        if (schema == null) return;
        if (schema.get$ref() != null && ("null".equals(schema.getType())
                || schema.getTypes() != null && schema.getTypes().contains("null"))) {
            String reference = schema.get$ref();
            schema.set$ref(null);
            schema.setType(null);
            schema.setTypes(null);
            schema.setAnyOf(List.of(new Schema<>().$ref(reference), new Schema<>().types(Set.of("null"))));
        }
        if (schema.getProperties() != null) {
            schema.getProperties().values().forEach(OpenApiConfiguration::normalizeNullableReferences);
        }
        normalizeNullableReferences(schema.getItems());
        if (schema.getAllOf() != null) {
            schema.getAllOf().forEach(OpenApiConfiguration::normalizeNullableReferences);
        }
        if (schema.getAnyOf() != null) {
            schema.getAnyOf().forEach(OpenApiConfiguration::normalizeNullableReferences);
        }
        if (schema.getOneOf() != null) {
            schema.getOneOf().forEach(OpenApiConfiguration::normalizeNullableReferences);
        }
    }

    @Bean
    OpenAPI slotwiseOpenApi() {
        ModelResolver.enumsAsRef = true;
        Components components = new Components()
                .addSecuritySchemes("sessionCookie", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE).name("SESSION")
                        .description("Spring Session HttpOnly cookie; no JWT"))
                .addSecuritySchemes("csrfHeader", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")
                        .description("Value of XSRF-TOKEN cookie; refreshed after login/logout"));
        ModelConverters.getInstance(true).readAll(ApiProblem.class).forEach(components::addSchemas);
        return new OpenAPI().info(new Info().title("SlotWise API").version("1.0.0")
                        .description("MVP contract. Unimplemented business operations return 501 Problem Details. "
                                + "Success responses describe the agreed target API; JPA entities are not exposed."))
                .servers(List.of(new Server().url("/")))
                .components(components)
                .security(List.of(new SecurityRequirement().addList("sessionCookie")))
                // LogoutFilter handles this route before MVC, so it has no controller to scan.
                .path(ApiPaths.AUTH + "/logout", new PathItem().post(new Operation()
                        .operationId("logout")
                        .summary("Завершить серверную сессию")
                        .description("Handled by Spring Security. Requires CSRF; refresh XSRF-TOKEN after logout.")
                        .tags(List.of("auth-controller"))
                        .security(List.of(new SecurityRequirement().addList("csrfHeader")))
                        .responses(new ApiResponses()
                                .addApiResponse("204", new ApiResponse().description("No Content"))
                                .addApiResponse("403", problemResponse("Missing or invalid CSRF token")))));
    }

    private static ApiResponse problemResponse(String description) {
        return new ApiResponse().description(description).content(new Content().addMediaType(
                "application/problem+json", new MediaType().schema(
                        new Schema<>().$ref("#/components/schemas/ApiProblem"))));
    }

    @Bean
    OperationCustomizer commonApiResponses() {
        Map<String, String> errors = Map.of(
                "400", "Invalid JSON, parameters or DTO fields",
                "401", "Authentication required or credentials rejected",
                "403", "Insufficient permissions or invalid CSRF token",
                "404", "Resource not found or not accessible",
                "409", "Concurrent modification, duplicate or invalid current state",
                "422", "Domain constraints violated; see issues",
                "500", "Unexpected server error",
                "501", "Contract stub; business operation is not implemented");
        return (operation, handler) -> {
            if (operation.getResponses() == null) {
                operation.setResponses(new ApiResponses());
            }
            errors.forEach((code, description) -> operation.getResponses().addApiResponse(code,
                    problemResponse(description)));
            if (List.of("login", "getCurrentUser").contains(operation.getOperationId())) {
                operation.getResponses().remove("501");
            }
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
            boolean mutation = mapping != null && mapping.method().length > 0
                    && !List.of(RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS).contains(mapping.method()[0]);
            SecurityRequirement security = new SecurityRequirement();
            if (!"login".equals(operation.getOperationId())) {
                security.addList("sessionCookie");
            }
            if (mutation) {
                security.addList("csrfHeader");
            }
            operation.setSecurity(List.of(security));
            return operation;
        };
    }
}
