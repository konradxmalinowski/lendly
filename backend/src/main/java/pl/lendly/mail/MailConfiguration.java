package pl.lendly.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import pl.lendly.config.AuthProperties;
import pl.lendly.config.MailProperties;

@Configuration
public class MailConfiguration {
    /** Null without an SMTP host unless the profile is local over plain HTTP (the log transport prints codes). */
    @Bean
    MailService mailService(MailProperties properties, Environment environment, AuthProperties auth) {
        MailTransport transport = null;
        if (!properties.host().isEmpty()) transport = new SmtpMailTransport(properties);
        else if (environment.matchesProfiles("local") && !auth.isSecureCookies()) transport = new LogMailTransport();
        return new MailService(transport);
    }
}
