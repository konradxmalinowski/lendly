package pl.lendly.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One structured-output call to a hosted model. Schemas use the strict JSON Schema subset (all properties required,
 * {@code additionalProperties: false}, nullable as {@code ["string", "null"]}); each provider adapts it.
 */
public interface AiModelClient {
    boolean isAvailable();

    String providerName();

    JsonNode generateJson(String systemInstruction, String userText, byte[] jpegImage, String schemaName,
                          JsonNode jsonSchema, int maxOutputTokens);
}
