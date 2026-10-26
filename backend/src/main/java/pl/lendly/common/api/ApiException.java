package pl.lendly.common.api;

import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Long retryAfterSeconds;
    private final Map<String, List<String>> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, (Long) null);
    }

    public ApiException(HttpStatus status, String code, String message, Long retryAfterSeconds) {
        this(status, code, message, retryAfterSeconds, null);
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, List<String>> fieldErrors) {
        this(status, code, message, null, fieldErrors);
    }

    private ApiException(HttpStatus status, String code, String message, Long retryAfterSeconds,
                         Map<String, List<String>> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fieldErrors = fieldErrors == null ? null : Map.copyOf(fieldErrors);
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Long retryAfterSeconds() { return retryAfterSeconds; }
    public Map<String, List<String>> fieldErrors() { return fieldErrors; }
}
