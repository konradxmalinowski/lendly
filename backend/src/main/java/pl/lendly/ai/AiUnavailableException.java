package pl.lendly.ai;

/** A model call failed or is not configured; carries only a short category for logs, never prompt content. */
public class AiUnavailableException extends RuntimeException {
    private final String category;

    public AiUnavailableException(String category) {
        super("AI provider is unavailable: " + category);
        this.category = category;
    }

    public String category() { return category; }
}
