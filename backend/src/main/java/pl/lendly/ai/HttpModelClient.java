package pl.lendly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.lendly.config.AiProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared HTTP plumbing. Prompts, responses, and keys are never logged; failures are logged by category only. */
abstract class HttpModelClient implements AiModelClient {
    private static final Logger log = LoggerFactory.getLogger(HttpModelClient.class);
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.\\-_]{1,62}");
    private static final int MAX_RESPONSE_CHARS = 200_000;
    private static final int MAX_JSON_CHARS = 20_000;
    protected final AiProperties.Provider provider;
    protected final ObjectMapper mapper;
    private final String name;
    private final HttpClient http;
    private final Duration requestTimeout;

    HttpModelClient(String name, AiProperties.Provider provider, int connectTimeout, int requestTimeout, ObjectMapper mapper) {
        this.name = name;
        this.provider = provider;
        this.mapper = mapper;
        if (!MODEL_ID.matcher(provider.getModel()).matches()) throw new IllegalStateException("The AI model name has an invalid format.");
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(connectTimeout))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        this.requestTimeout = Duration.ofSeconds(requestTimeout);
    }

    @Override
    public boolean isAvailable() { return provider.isConfigured(); }

    @Override
    public String providerName() { return name; }

    protected void requireConfigured() {
        if (!isAvailable()) throw new AiUnavailableException("not-configured");
    }

    protected JsonNode post(String url, Map<String, String> headers, JsonNode body) {
        HttpResponse<String> response;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            headers.forEach(request::header);
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            log.warn("AI request failed, provider={}, category={}", name, exception.getClass().getSimpleName());
            throw new AiUnavailableException("transport");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("interrupted");
        }
        if (response.statusCode() != 200) {
            log.warn("AI provider returned an error, provider={}, status={}", name, response.statusCode());
            throw new AiUnavailableException(response.statusCode() == 429 ? "rate-limited" : "http-" + response.statusCode());
        }
        if (response.body().length() > MAX_RESPONSE_CHARS) throw new AiUnavailableException("oversized");
        try {
            return mapper.readTree(response.body());
        } catch (IOException exception) {
            throw new AiUnavailableException("invalid-envelope");
        }
    }

    protected JsonNode parseObject(String text) {
        if (text.isEmpty() || text.length() > MAX_JSON_CHARS) throw new AiUnavailableException("empty-or-oversized");
        try {
            JsonNode parsed = mapper.readTree(text);
            if (!parsed.isObject()) throw new AiUnavailableException("not-an-object");
            return parsed;
        } catch (IOException exception) {
            throw new AiUnavailableException("invalid-json");
        }
    }
}
