package com.cofeapp.backend.common.crypto;

import com.cofeapp.backend.mercadopago.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Cifra secretos que el sistema tiene que poder volver a leer (hoy, el
 * Access Token de Mercado Pago de cada negocio) — a diferencia de las
 * contraseñas, que se guardan con hash y nunca se descifran.
 *
 * <p>AES-256-GCM (cifra y además detecta si alguien tocó el dato), con un
 * vector aleatorio nuevo por cada valor. La clave sale de la frase de
 * {@code MP_TOKEN_ENCRYPTION_KEY} (SHA-256 de esa frase). Formato guardado:
 * {@code v1:<base64(iv + texto cifrado)>}.
 *
 * <p>Importante: si se cambia {@code MP_TOKEN_ENCRYPTION_KEY}, los tokens
 * ya guardados dejan de poder leerse y cada negocio tiene que volver a
 * conectar Mercado Pago.
 */
@Component
public class SecretCipher {

  private static final String PREFIX = "v1:";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public SecretCipher(MercadoPagoProperties properties) {
    String passphrase = properties.tokenEncryptionKey();
    if (passphrase == null || passphrase.length() < 16) {
      throw new IllegalStateException(
          "Falta app.mercadopago.token-encryption-key (MP_TOKEN_ENCRYPTION_KEY) o es demasiado corta:"
              + " tiene que tener al menos 16 caracteres.");
    }
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes(StandardCharsets.UTF_8));
      this.key = new SecretKeySpec(digest, "AES");
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("No se pudo preparar el cifrado de secretos.", ex);
    }
  }

  public String encrypt(String plainText) {
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
      return PREFIX + Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("No se pudo cifrar el dato.", ex);
    }
  }

  public String decrypt(String stored) {
    if (stored == null || !stored.startsWith(PREFIX)) {
      throw new IllegalStateException("Dato cifrado con un formato desconocido.");
    }
    try {
      byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
      byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
      return new String(plain, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException ex) {
      throw new IllegalStateException(
          "No se pudo descifrar el token de Mercado Pago. ¿Cambió MP_TOKEN_ENCRYPTION_KEY? Si es así,"
              + " hay que volver a conectar Mercado Pago.",
          ex);
    }
  }
}
