package pl.lendly.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Local profile only: the log is the mailbox, so it prints the address and the message. */
public class LogMailTransport implements MailTransport {
    private static final Logger log = LoggerFactory.getLogger(LogMailTransport.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Local mail transport, to={}, subject={}\n{}", to, subject, body);
    }
}
