package pl.lendly.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import pl.lendly.config.AiProperties;

/** Selects the provider configured in {@code lendly.ai.provider} ({@code AI_PROVIDER}): gemini or openai. */
@Component
public class AiRouter {
    private final AiModelClient client;

    public AiRouter(AiProperties properties, ObjectMapper mapper, Environment environment) {
        AiProperties.Provider selected = switch (properties.getProvider()) {
            case "gemini" -> properties.getGemini();
            case "openai" -> properties.getOpenai();
            default -> throw new IllegalStateException("AI_PROVIDER must be gemini or openai.");
        };
        // A different endpoint is accepted only locally, for a stub used during manual verification.
        if (!selected.usesDefaultBaseUrl() && !environment.matchesProfiles("local")) {
            throw new IllegalStateException("The AI base URL may be changed only in the local profile.");
        }
        int connect = properties.getConnectTimeoutSeconds();
        int request = properties.getRequestTimeoutSeconds();
        this.client = "openai".equals(properties.getProvider())
                ? new OpenAiClient(selected, connect, request, mapper)
                : new GeminiClient(selected, connect, request, mapper);
    }

    public AiModelClient client() { return client; }
}
