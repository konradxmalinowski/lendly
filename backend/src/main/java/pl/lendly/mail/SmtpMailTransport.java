package pl.lendly.mail;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import pl.lendly.config.MailProperties;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SmtpMailTransport implements MailTransport {
    private final JavaMailSenderImpl sender = new JavaMailSenderImpl();
    private final String from;

    public SmtpMailTransport(MailProperties properties) {
        sender.setHost(properties.host());
        sender.setPort(properties.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (!properties.username().isEmpty()) {
            sender.setUsername(properties.username());
            sender.setPassword(properties.password());
        }
        Properties smtp = sender.getJavaMailProperties();
        smtp.put("mail.smtp.auth", String.valueOf(!properties.username().isEmpty()));
        smtp.put("mail.smtp.connectiontimeout", "10000");
        smtp.put("mail.smtp.timeout", "10000");
        smtp.put("mail.smtp.writetimeout", "10000");
        smtp.put("mail.smtp.ssl.checkserveridentity", "true");
        // Port 465 is implicit TLS; every other port must upgrade with STARTTLS or the send fails.
        if (properties.port() == 465) smtp.put("mail.smtp.ssl.enable", "true");
        else {
            smtp.put("mail.smtp.starttls.enable", "true");
            smtp.put("mail.smtp.starttls.required", "true");
        }
        this.from = properties.from();
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
        } catch (Exception exception) {
            throw new MailDeliveryException(exception);
        }
    }
}
