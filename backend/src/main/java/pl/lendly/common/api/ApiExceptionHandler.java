package pl.lendly.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.sql.SQLException;
import java.util.regex.Pattern;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    private static final Pattern SAFE_FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_.]{0,127}");

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request) {
        ResponseEntity<ApiError> response = error(exception.status(), exception.code(), exception.getMessage(),
                exception.fieldErrors(), request);
        if (exception.retryAfterSeconds() == null) return response;
        return ResponseEntity.status(exception.status()).header("Retry-After", exception.retryAfterSeconds().toString())
                .body(response.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, List<String>> fieldErrors = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(fieldError -> {
            String field = safeFieldName(fieldError.getField());
            String message = safeValidationMessage(fieldError.getCode());
            fieldErrors.computeIfAbsent(field, ignored -> new ArrayList<>()).add(message);
        });
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                fieldErrors, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "The request body is invalid.", null, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request contains invalid fields.",
                Map.of(safeFieldName(exception.getName()), List.of("is invalid")), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleUploadTooLarge(HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "The image must be at most 5 MB.", null, request);
    }

    @ExceptionHandler({MultipartException.class, org.springframework.web.multipart.support.MissingServletRequestPartException.class})
    ResponseEntity<ApiError> handleMultipart(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_UPLOAD", "The upload request is invalid.", null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        SQLException sqlException = findSqlException(exception);
        if (sqlException != null) {
            log.error("Unhandled database error, correlationId={}, exceptionType={}, sqlState={}, vendorCode={}",
                    correlationId, sqlException.getClass().getSimpleName(), sqlException.getSQLState(), sqlException.getErrorCode());
        } else {
            log.error("Unhandled API error, correlationId={}, exceptionType={}",
                    correlationId, exception.getClass().getSimpleName());
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred.", null, correlationId));
    }

    private SQLException findSqlException(Throwable exception) {
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 32; depth++, cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) return sqlException;
        }
        return null;
    }

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message,
            Map<String, List<String>> fieldErrors,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status)
                .body(new ApiError(code, message, fieldErrors, correlationId(request)));
    }

    private String safeFieldName(String field) {
        String normalized = field.replaceAll("\\[[^\\]]*\\]", "");
        return SAFE_FIELD_NAME.matcher(normalized).matches() ? normalized : "request";
    }

    private String safeValidationMessage(String code) {
        if (code == null) {
            return "is invalid";
        }
        return switch (code) {
            case "NotBlank", "NotEmpty" -> "must not be blank";
            case "NotNull" -> "must not be null";
            case "Size" -> "has an invalid length";
            case "Email" -> "must be a valid email address";
            case "Pattern" -> "has an invalid format";
            case "Positive", "PositiveOrZero" -> "must be positive";
            case "Negative", "NegativeOrZero" -> "must be negative";
            case "Min", "DecimalMin" -> "is below the allowed minimum";
            case "Max", "DecimalMax" -> "exceeds the allowed maximum";
            default -> "is invalid";
        };
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CORRELATION_ID_ATTRIBUTE);
        return value instanceof String id ? id : "unavailable";
    }
}
