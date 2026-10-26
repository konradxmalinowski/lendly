package pl.lendly.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.lendly.config.AiProperties;

import java.util.Base64;
import java.util.Map;

/** OpenAI Responses API with a strict json_schema text format. */
class OpenAiClient extends HttpModelClient {
    OpenAiClient(AiProperties.Provider provider, int connectTimeout, int requestTimeout, ObjectMapper mapper) {
        super("openai", provider, connectTimeout, requestTimeout, mapper);
    }

    @Override
    public JsonNode generateJson(String systemInstruction, String userText, byte[] jpegImage, String schemaName,
                                 JsonNode jsonSchema, int maxOutputTokens) {
        requireConfigured();
        ObjectNode body = mapper.createObjectNode();
        body.put("model", provider.getModel());
        body.put("store", false);
        ArrayNode input = body.putArray("input");
        input.addObject().put("role", "system").put("content", systemInstruction);
        ObjectNode user = input.addObject();
        user.put("role", "user");
        ArrayNode content = user.putArray("content");
        content.addObject().put("type", "input_text").put("text", userText);
        if (jpegImage != null) {
            content.addObject().put("type", "input_image")
                    .put("image_url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegImage));
        }
        ObjectNode format = body.putObject("text").putObject("format");
        format.put("type", "json_schema").put("name", schemaName).put("strict", true);
        format.set("schema", jsonSchema);
        // Reasoning models spend part of the budget on reasoning tokens, so the cap leaves room above the JSON size.
        body.put("max_output_tokens", maxOutputTokens * 3);

        JsonNode root = post(provider.getBaseUrl() + "/v1/responses", Map.of("Authorization", "Bearer " + provider.getApiKey()), body);
        if (!"completed".equals(root.path("status").asText())) {
            throw new AiUnavailableException("status-" + root.path("status").asText("unknown"));
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode item : root.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode part : item.path("content")) {
                if ("refusal".equals(part.path("type").asText())) throw new AiUnavailableException("refusal");
                if ("output_text".equals(part.path("type").asText())) text.append(part.path("text").asText());
            }
        }
        return parseObject(text.toString());
    }
}
