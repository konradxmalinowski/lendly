package pl.lendly.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final MailTransport transport;

    MailService(MailTransport transport) { this.transport = transport; }

    public boolean available() { return transport != null; }

    /** Returns false when the mail could not be sent; the failure is logged without the address or the code. */
    public boolean sendVerificationCode(String to, String code, int validMinutes) {
        if (transport == null) return false;
        String body = "Twój kod weryfikacyjny do Lendly: " + code + "\n\n"
                + "Kod jest ważny przez " + validMinutes + " minut. Wpisz go na stronie weryfikacji konta.\n\n"
                + "Jeśli to nie Ty zakładasz konto w Lendly, zignoruj tę wiadomość.\n";
        try {
            transport.send(to, "Kod weryfikacyjny Lendly", body);
            return true;
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            log.warn("Verification mail was not delivered, exceptionType={}", cause.getClass().getSimpleName());
            return false;
        }
    }
}
