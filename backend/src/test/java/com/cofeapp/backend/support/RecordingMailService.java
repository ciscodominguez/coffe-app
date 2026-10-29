package com.cofeapp.backend.support;

import com.cofeapp.backend.common.mail.MailService;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

/** En los tests, los mails se guardan en memoria en vez de enviarse. Ver {@link TestSupportConfig}. */
public class RecordingMailService extends MailService {

  public record SentMail(String to, String subject, String body) {}

  private static final Pattern RESET_TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

  private final List<SentMail> sent = new CopyOnWriteArrayList<>();

  public RecordingMailService(ObjectProvider<JavaMailSender> mailSender) {
    super(mailSender);
  }

  @Override
  public boolean send(String to, String subject, String body) {
    sent.add(new SentMail(to, subject, body));
    return false;
  }

  public List<SentMail> sentTo(String to) {
    return sent.stream().filter(mail -> mail.to().equalsIgnoreCase(to)).toList();
  }

  /** El token del último link de "Olvidé mi contraseña" mandado a ese mail, si hay. */
  public Optional<String> lastResetTokenFor(String to) {
    List<SentMail> mails = sentTo(to);
    if (mails.isEmpty()) return Optional.empty();
    Matcher matcher = RESET_TOKEN.matcher(mails.get(mails.size() - 1).body());
    return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
  }
}
