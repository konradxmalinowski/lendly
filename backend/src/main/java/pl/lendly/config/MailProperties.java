package pl.lendly.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SMTP settings; an empty host means no SMTP transport (the log transport exists only in the local profile). */
@ConfigurationProperties("lendly.mail")
public record MailProperties(String host, int port, String username, String password, String from) {
    public MailProperties {
        host = host == null ? "" : host.trim();
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
        from = from == null ? "" : from.trim();
    }
}
