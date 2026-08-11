package com.translatelab.backend.common.web;

import java.util.UUID;

public final class CorrelationIdContext {

    public static final String HEADER = "X-Correlation-ID";
    public static final String REQUEST_ATTRIBUTE =
            CorrelationIdContext.class.getName() + ".value";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private CorrelationIdContext() {}

    public static void set(String correlationId) {
        CURRENT.set(correlationId);
    }

    public static String currentOrCreate() {
        String current = CURRENT.get();
        return current != null ? current : UUID.randomUUID().toString();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
