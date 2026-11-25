package com.labflow.shared.web;

import org.slf4j.MDC;

/** 读取当前请求的 traceId。 */
public final class TraceContext {

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        return traceId == null ? "" : traceId;
    }
}
