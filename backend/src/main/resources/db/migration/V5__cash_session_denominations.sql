-- ============================================================================
-- COFFE_APP — V5__cash_session_denominations.sql (MySQL 8)
-- ============================================================================
-- "Hacer la caja más profesional" — mejoras pedidas por Fran sobre el
-- Bloque C (ver README, sección 10), después de confirmar que los locales
-- van a manejar plata real: agrega el "conteo de efectivo por billete" al
-- cierre de turno, en vez de un único monto suelto.
--
-- Una tabla nueva:
--   - cash_session_denominations: una fila por cada denominación contada al
--     cerrar un turno (ej. "8 billetes de $2.000"). Opcional — un café que
--     prefiera seguir cargando un único total puede no mandar ninguna fila
--     acá (ver CashSessionService#close).
--
-- `denomination` está restringido por CHECK a los billetes vigentes en
-- Argentina, confirmados vía BCRA/prensa en septiembre de 2026 (bills:
-- $20.000/$10.000/$2.000/$1.000/$500/$200/$100/$50/$20/$10). Las monedas y
-- los billetes de $50 o menos quedan afuera a propósito: por la
-- devaluación, son inviables para el conteo de caja de un café real. Si el
-- BCRA emite una denominación nueva más adelante, hay que agregarla acá Y
-- en CashSessionService.KNOWN_DENOMINATIONS.
--
-- `subtotal` se calcula en Java (CashSessionService#saveDenominationBreakdown,
-- nunca columna generada — acá no hace falta el truco de CashSession, no
-- hay ningún índice único de por medio) pero además queda verificado con un
-- CHECK multi-columna (soportado desde MySQL 8.0.16): ni siquiera un bug
-- futuro puede persistir una fila con la cuenta mal hecha.
-- ============================================================================

CREATE TABLE cash_session_denominations (
  id              CHAR(36)      NOT NULL,
  business_id     CHAR(36)      NOT NULL,
  cash_session_id CHAR(36)      NOT NULL,
  denomination    DECIMAL(10,2) NOT NULL,
  quantity        INT           NOT NULL,
  subtotal        DECIMAL(10,2) NOT NULL,
  created_at      DATETIME(6)   NOT NULL,
  updated_at      DATETIME(6)   NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_cash_session_denominations (cash_session_id, denomination),
  KEY idx_cash_session_denominations_business_id (business_id),
  KEY idx_cash_session_denominations_session (cash_session_id),
  CONSTRAINT fk_cash_session_denominations_business
    FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
  CONSTRAINT fk_cash_session_denominations_session
    FOREIGN KEY (cash_session_id) REFERENCES cash_sessions (id) ON DELETE CASCADE,
  CONSTRAINT chk_cash_session_denominations_value
    CHECK (denomination IN (20000, 10000, 2000, 1000, 500, 200, 100, 50, 20, 10)),
  CONSTRAINT chk_cash_session_denominations_quantity CHECK (quantity >= 0),
  CONSTRAINT chk_cash_session_denominations_subtotal CHECK (subtotal = denomination * quantity)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
