package net.atos.digital.multitenant.tenant;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.models.Operation;
import lombok.RequiredArgsConstructor;
import net.atos.digital.multitenant.properties.SchemaConfigurationProperties;
import net.atos.digital.multitenant.tenant.properties.TenantConfigurationRegistry;
import net.atos.digital.multitenant.tenant.properties.TenantData;
import net.atos.digital.multitenant.utils.ProfileConstants;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springdoc.core.customizers.OperationCustomizer;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@ConditionalOnClass({OperationCustomizer.class, Operation.class})
@RequiredArgsConstructor
public class SwaggerUtils {

    private final TenantConfigurationRegistry tenantConfigurationRegistry;
    private final SchemaConfigurationProperties schemaConfigurationProperties;

    public OperationCustomizer customGlobalHeaders() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            Map<String, Example> tenants = new HashMap<>();

            if (CollectionUtils.isNotEmpty(schemaConfigurationProperties.getSchemas())) {
                schemaConfigurationProperties.getSchemas().forEach(itemSchemaConfiguration -> {
                    tenants.put(itemSchemaConfiguration.schemaName(), new Example().value(itemSchemaConfiguration.schemaName()));
                });
            }
            if (tenantConfigurationRegistry.isMultitenantActive()) {
                tenants.putAll(tenantConfigurationRegistry.getTenants().stream().collect(Collectors.toMap(TenantData::displayName, tenant -> new Example().value(tenant.tenantName()))));
            } else {
                tenants.put(schemaConfigurationProperties.getDefaultSchema().schemaName(), new Example().value(schemaConfigurationProperties.getDefaultSchema().schemaName()));
            }

            if (!tenants.isEmpty()) {
                operation.addParametersItem(new Parameter()
                        .in(ParameterIn.HEADER.toString())
                        .schema(new StringSchema())
                        .name(ProfileConstants.X_TENANT)
                        .examples(tenants)
                        .required(true));
            }

            return operation;
        };

    }
}