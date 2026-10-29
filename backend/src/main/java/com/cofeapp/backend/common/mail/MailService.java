package com.cofeapp.backend.common.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Envío de mails (hoy: el link de "Olvidé mi contraseña").
 *
 * <p><b>Sin servidor de correo configurado</b> (desarrollo, o antes de
 * contratar uno) NO falla: escribe el mail completo en el log del backend,
 * así se puede copiar el link y probar el flujo igual. Spring Boot solo arma
 * el {@link JavaMailSender} si hay un {@code spring.mail.host} configurado
 * (ver {@code .env.example}); si no, este service cae en el log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

  private final ObjectProvider<JavaMailSender> mailSender;

  @Value("${app.mail.from:COFFE_APP <no-responder@coffeapp.local>}")
  private String from;

  /** @return true si salió por el servidor de correo; false si se escribió en el log (sin servidor o con error). */
  public boolean send(String to, String subject, String body) {
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.warn(
          "\n==== MAIL (no hay servidor de correo configurado: se muestra acá) ====\nPara: {}\nAsunto: {}\n\n{}\n=====================================================================",
          to,
          subject,
          body);
      return false;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(to);
      message.setSubject(subject);
      message.setText(body);
      sender.send(message);
      return true;
    } catch (MailException ex) {
      // No se le muestra el error al usuario (no tiene que enterarse de si un
      // email existe o no) — queda en el log para que lo vea quien administra.
      log.error("No se pudo enviar el mail a {}: {}", to, ex.getMessage());
      return false;
    }
  }
}
