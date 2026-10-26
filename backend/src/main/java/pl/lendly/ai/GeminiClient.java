package pl.lendly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.lendly.config.AiProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

/** Google Gemini generateContent with responseSchema (OpenAPI-style schema translated from strict JSON Schema). */
class GeminiClient extends HttpModelClient {
    GeminiClient(AiProperties.Provider provider, int connectTimeout, int requestTimeout, ObjectMapper mapper) {
        super("gemini", provider, connectTimeout, requestTimeout, mapper);
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userText, byte[] jpegImage, String schemaName,
                                 JsonNode jsonSchema, int maxOutputTokens) {
        requireConfigured();
        ObjectNode body = mapper.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
        ObjectNode content = body.putArray("contents").addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", userText);
        if (jpegImage != null) {
            parts.addObject().putObject("inlineData").put("mimeType", "image/jpeg")
                    .put("data", Base64.getEncoder().encodeToString(jpegImage));
        }
        ObjectNode config = body.putObject("generationConfig");
        config.put("responseMimeType", "application/json");
        config.set("responseSchema", toGeminiSchema(jsonSchema));
        config.put("temperature", 0.2);
        config.put("maxOutputTokens", maxOutputTokens);

        String url = provider.getBaseUrl() + "/v1beta/models/" + URLEncoder.encode(provider.getModel(), StandardCharsets.UTF_8)
                + ":generateContent";
        JsonNode root = post(url, Map.of("x-goog-api-key", provider.getApiKey()), body);
        if (root.path("promptFeedback").hasNonNull("blockReason")) throw new AiUnavailableException("blocked");
        JsonNode candidate = root.path("candidates").path(0);
        String finish = candidate.path("finishReason").asText("");
        if (!finish.isEmpty() && !"STOP".equals(finish)) throw new AiUnavailableException("finish-" + finish.toLowerCase());
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (part.has("text") && !part.path("thought").asBoolean(false)) text.append(part.get("text").asText());
        }
        return parseObject(text.toString());
    }

    /** Uppercases types, maps ["x", "null"] to nullable, and drops keywords Gemini's schema subset does not accept. */
    static JsonNode toGeminiSchema(JsonNode node) {
        ObjectNode out = node.deepCopy();
        out.remove("additionalProperties");
        JsonNode type = node.get("type");
        if (type != null && type.isArray()) {
            String concrete = "string";
            for (JsonNode entry : type) if (!"null".equals(entry.asText())) concrete = entry.asText();
            out.put("type", concrete.toUpperCase());
            out.put("nullable", true);
        } else if (type != null) {
            out.put("type", type.asText().toUpperCase());
        }
        if (node.has("properties")) {
            ObjectNode properties = out.putObject("properties");
            Iterator<Map.Entry<String, JsonNode>> fields = node.get("properties").fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                properties.set(field.getKey(), toGeminiSchema(field.getValue()));
            }
        }
        if (node.has("items")) out.set("items", toGeminiSchema(node.get("items")));
        return out;
    }
}
