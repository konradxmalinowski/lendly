package pl.lendly.mail;

public class MailDeliveryException extends RuntimeException {
    public MailDeliveryException(Throwable cause) {
        super("Mail delivery failed.", cause);
    }
}
