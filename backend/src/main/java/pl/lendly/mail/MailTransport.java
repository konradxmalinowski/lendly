package pl.lendly.mail;

public interface MailTransport {
    /** Throws MailDeliveryException when the message could not be handed over. */
    void send(String to, String subject, String body);
}
