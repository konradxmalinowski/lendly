package pl.lendly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lendly.ai")
public class AiProperties {
    private String provider = "gemini";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 30;
    private final Provider gemini = new Provider("gemini-3.5-flash-lite", "https://generativelanguage.googleapis.com");
    private final Provider openai = new Provider("gpt-6-luna", "https://api.openai.com");

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider == null ? "" : provider.trim().toLowerCase(); }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int value) { connectTimeoutSeconds = value; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int value) { requestTimeoutSeconds = value; }
    public Provider getGemini() { return gemini; }
    public Provider getOpenai() { return openai; }

    public static class Provider {
        private final String defaultModel;
        private final String defaultBaseUrl;
        private String apiKey = "";
        private String model;
        private String baseUrl;

        Provider(String defaultModel, String defaultBaseUrl) {
            this.defaultModel = defaultModel;
            this.defaultBaseUrl = defaultBaseUrl;
            this.model = defaultModel;
            this.baseUrl = defaultBaseUrl;
        }

        public boolean isConfigured() { return !apiKey.isBlank(); }
        public boolean usesDefaultBaseUrl() { return defaultBaseUrl.equals(baseUrl); }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey.trim(); }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model == null || model.isBlank() ? defaultModel : model.trim(); }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null || baseUrl.isBlank() ? defaultBaseUrl : baseUrl.trim(); }
    }
}
