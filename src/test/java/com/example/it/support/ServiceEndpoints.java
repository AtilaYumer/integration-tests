package com.example.it.support;

/** Resolves service base URLs from system properties, then env vars, then local defaults. */
public final class ServiceEndpoints {

    private ServiceEndpoints() {
    }

    public static String catalog() {
        return resolve("catalog.base-url", "CATALOG_BASE_URL", "http://localhost:8081");
    }

    public static String order() {
        return resolve("order.base-url", "ORDER_BASE_URL", "http://localhost:8082");
    }

    private static String resolve(String property, String env, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(env);
        }
        return value == null || value.isBlank() ? fallback : value;
    }
}
