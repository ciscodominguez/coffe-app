package com.cofeapp.backend.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Emisión y verificación del JWT de sesión, con jjwt.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

  private static final String CLAIM_BUSINESS_ID = "businessId";
  private static final String CLAIM_LOCATION_ID = "locationId";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_EMAIL = "email";
  /** Versión de sesión del usuario (V9) — ver {@code User#tokenVersion} y {@link SessionValidator}. */
  private static final String CLAIM_VERSION = "ver";
  /** V15 — "platform" en los tokens del superadmin (no pertenecen a ningún café). */
  private static final String CLAIM_SCOPE = "scope";
  private static final String SCOPE_PLATFORM = "platform";
  public static final String SUPERADMIN_ROLE = "SUPERADMIN";

  /**
   * Token ya verificado (firma y vencimiento) + la versión de sesión con la
   * que se emitió. {@code platform}: es del superadmin (V15), sin café.
   */
  public record ParsedToken(AuthenticatedUser user, int version, boolean platform) {
    public ParsedToken(AuthenticatedUser user, int version) {
      this(user, version, false);
    }
  }

  private final JwtProperties jwtProperties;

  private SecretKey signingKey() {
    return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @param tokenVersion {@code User#tokenVersion} al momento de emitir: si
   *     después sube (cambio de contraseña, baja), este token deja de valer.
   */
  public String generateToken(AuthenticatedUser user, int tokenVersion) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(jwtProperties.getExpiration());

    return Jwts.builder()
        .subject(user.userId())
        .claim(CLAIM_BUSINESS_ID, user.businessId())
        .claim(CLAIM_LOCATION_ID, user.locationId())
        .claim(CLAIM_ROLE, user.role())
        .claim(CLAIM_EMAIL, user.email())
        .claim(CLAIM_VERSION, tokenVersion)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(signingKey())
        .compact();
  }

  /**
   * V15 — token del superadmin: sin businessId, con {@code scope=platform}
   * y rol {@code SUPERADMIN}. Solo sirve para {@code /platform/**} (ver
   * {@code SecurityConfig}).
   */
  public String generatePlatformToken(String adminId, String email, int tokenVersion) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(adminId)
        .claim(CLAIM_SCOPE, SCOPE_PLATFORM)
        .claim(CLAIM_ROLE, SUPERADMIN_ROLE)
        .claim(CLAIM_EMAIL, email)
        .claim(CLAIM_VERSION, tokenVersion)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(jwtProperties.getExpiration())))
        .signWith(signingKey())
        .compact();
  }

  /**
   * Verifica firma y expiración, y exige que el payload traiga
   * businessId/sub/role — un token sin estos campos no es válido para este
   * sistema aunque esté bien firmado.
   *
   * @throws JwtException si la firma/expiración no son válidas, o si el
   *     payload no trae los campos obligatorios.
   */
  public AuthenticatedUser parseToken(String token) {
    return parse(token).user();
  }

  /**
   * Igual que {@link #parseToken}, más la versión de sesión. Un token
   * emitido antes de la V9 no trae "ver": cuenta como versión 0, así las
   * sesiones abiertas antes de actualizar siguen andando (hasta que el
   * usuario cambie la contraseña o lo den de baja).
   */
  public ParsedToken parse(String token) {
    Claims claims = Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();

    String userId = claims.getSubject();
    String businessId = claims.get(CLAIM_BUSINESS_ID, String.class);
    String locationId = claims.get(CLAIM_LOCATION_ID, String.class);
    String role = claims.get(CLAIM_ROLE, String.class);
    String email = claims.get(CLAIM_EMAIL, String.class);
    Number version = claims.get(CLAIM_VERSION, Number.class);

    if (SCOPE_PLATFORM.equals(claims.get(CLAIM_SCOPE, String.class))) {
      if (userId == null || !SUPERADMIN_ROLE.equals(role)) {
        throw new JwtException("Token de plataforma mal formado.");
      }
      return new ParsedToken(
          new AuthenticatedUser(userId, null, null, role, email), version == null ? 0 : version.intValue(), true);
    }
    if (SUPERADMIN_ROLE.equals(role)) {
      // Un token de café nunca puede traer el rol de superadmin.
      throw new JwtException("Token mal formado.");
    }

    if (userId == null || businessId == null || role == null) {
      throw new JwtException("Token mal formado: falta businessId, sub o role.");
    }

    return new ParsedToken(
        new AuthenticatedUser(userId, businessId, locationId, role, email), version == null ? 0 : version.intValue());
  }
}
