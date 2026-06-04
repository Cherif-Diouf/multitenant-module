package net.atos.digital.multitenant.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaUtils {

    public String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}