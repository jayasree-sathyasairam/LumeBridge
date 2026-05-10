package com.lumebridge.pipeline;

import com.lumebridge.SentinelConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RequestContext {
    private byte[] rawPayload;
    private String payloadHash;
    private String requestId;
    private Long nonce;
    private String route;
    private Object result;
    private String status;
    private int httpStatus = 200;
    private final Map<String, Long> metrics = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private Exception error;
    /** First value per header name (case-insensitive keys). Populated by {@link com.lumebridge.LumeBridgeApp}. */
    private final Map<String, String> requestHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public RequestContext(byte[] rawPayload) {
        this.rawPayload = rawPayload;
        this.status = SentinelConstants.STATUS_PENDING;
    }

    public byte[] getRawPayload()                   { return rawPayload; }
    public void setRawPayload(byte[] rawPayload)    { this.rawPayload = rawPayload; }

    public String getPayloadHash()                  { return payloadHash; }
    public void setPayloadHash(String payloadHash)  { this.payloadHash = payloadHash; }

    public String getRequestId()                    { return requestId; }
    public void setRequestId(String requestId)      { this.requestId = requestId; }

    public Long getNonce()                          { return nonce; }
    public void setNonce(Long nonce)                { this.nonce = nonce; }

    public String getRoute()                        { return route; }
    public void setRoute(String route)              { this.route = route; }

    public Object getResult()                       { return result; }
    public void setResult(Object result)            { this.result = result; }

    public String getStatus()                       { return status; }
    public void setStatus(String status)            { this.status = status; }

    public int getHttpStatus()                      { return httpStatus; }
    public void setHttpStatus(int httpStatus)       { this.httpStatus = httpStatus; }

    public Map<String, Long> getMetrics()           { return metrics; }
    public Map<String, String> getMetadata()        { return metadata; }
    public List<String> getWarnings()               { return warnings; }

    public Exception getError()                     { return error; }
    public void setError(Exception error)           { this.error = error; }

    public void putRequestHeader(String name, String value) {
        if (name != null && value != null) {
            requestHeaders.putIfAbsent(name, value);
        }
    }

    public String getRequestHeader(String name) {
        return requestHeaders.get(name);
    }

    public Map<String, String> getRequestHeaders() {
        return Collections.unmodifiableMap(requestHeaders);
    }
}
