# COFFE_APP

Sistema para cafés, en la nube y para varios cafés a la vez (cada café ve
solo lo suyo). Se usa desde el navegador: compu, tablet o celular.

**Es la única versión del proyecto** (Java + Angular). La versión anterior
(NestJS + React) quedó descartada; si en algún comentario viejo aparece
"el original", se refiere a ella y se puede ignorar.

## Qué hace

- **Pedidos** desde el celular del mozo: por mesa o para llevar, con
  variantes y agregados (tamaño, leche, extras), agregar productos a un
  pedido abierto, y una cola sin internet que manda los pedidos cuando
  vuelve la conexión.
- **Cocina (KDS)**: los pedidos llegan al instante y se marcan en
  preparación / listos.
- **Caja**: apertura, cobro en efectivo, tarjeta, transferencia o **QR de
  Mercado Pago**, dividir la cuenta (partes iguales o por productos),
  descuentos, ingresos y egresos, y cierre con conteo de billetes y
  diferencia. Tickets e impresión térmica.
- **Carta**: categorías, productos con foto, grupos de opciones,
  descuentos, mesas.
- **Insumos y stock**: por producto o por receta; baja al vender, vuelve al
  cancelar, alerta de mínimo.
- **Gestión**: Inicio con los números del día, reportes en Excel para el
  contador, equipo con roles (dueño, administrador, supervisor, mozo,
  cocina), varios locales.
- **Cuentas**: "Olvidé mi contraseña", cambiar la contraseña, sesiones que
  se cortan al instante si cambia la contraseña o se da de baja a alguien.
- **El negocio de COFFE_APP**: suscripción mensual con débito automático
  de Mercado Pago y prueba gratis, panel de superadmin para ver y manejar
  todos los cafés, términos y condiciones, política de privacidad y
  botones de arrepentimiento y de baja.

Qué falta para salir a vender: ver la [hoja de ruta](#16-hoja-de-ruta-para-hacerla-vendible)
(sección 16). Las secciones 8 a 15 y los "Paso … hecho" son el historial
detallado de cada cosa que se fue agregando.

## Tecnología

- **Backend**: Java 21, Spring Boot 3.3, Spring Data JPA (Hibernate),
  Spring Security + JWT, WebSocket (STOMP) para el tiempo real, MySQL 8,
  Flyway para las migraciones.
- **Frontend**: Angular 18 (componentes standalone, signals), TypeScript,
  CSS propio (tema claro y oscuro).

```
cofe-app/
├── README.md
├── backend/                         # Maven / Spring Boot
│   ├── .env.example                 # copiá a .env y completá
│   └── src/
│       ├── main/java/com/cofeapp/backend/   # un paquete por tema (order, cash, stock, billing, legal, platform…)
│       ├── main/resources/db/migration/     # V1…V17 (Flyway las corre solas al arrancar)
│       └── test/                            # tests de integración (ver "Paso 2.1")
└── frontend/                        # Angular
    └── src/app/
        ├── core/                    # servicios, modelos, guards, interceptor
        ├── features/                # una carpeta por pantalla
        └── shared/                  # componentes compartidos (menú, ventanas, íconos…)
```

## Cómo correrla en tu compu

### Backend

Requisitos: JDK 21, Maven (o IntelliJ) y MySQL 8.

```bash
cd backend
cp .env.example .env
# Editá .env: como mínimo poné un JWT_SECRET real (openssl rand -base64 48)

# Crear la base y el usuario (una sola vez):
#   CREATE DATABASE cofe_app CHARACTER SET utf8mb4;
#   CREATE USER 'cofe_user'@'%' IDENTIFIED BY 'cofe_password';
#   GRANT ALL PRIVILEGES ON cofe_app.* TO 'cofe_user'@'%';

mvn spring-boot:run
# Flyway corre las migraciones solas al arrancar.
# Queda escuchando en http://localhost:3000/api/v1
```

Desde IntelliJ: correr `CofeAppApplication`. El backend encuentra su
`backend/.env` aunque IntelliJ lo arranque desde otra carpeta.

**Imágenes de producto:** se guardan en disco, en la carpeta de
`UPLOADS_DIR` (por defecto `./uploads`; se crea sola). Si el backend no
corre en `localhost:3000`, actualizá `PUBLIC_BASE_URL` para que las URLs de
las imágenes apunten bien.

**Tests:** `mvn verify` (necesitan la base `cofe_app_test` — ver
"Paso 2.1").

### Frontend

Requisitos: Node.js 18+.

```bash
cd frontend
npm install
npm start
# http://localhost:4200 — apunta a http://localhost:3000/api/v1
# (ver src/environments/environment.development.ts)
```

Para producción: `npm run build` (usa `src/environments/environment.ts`;
actualizá `apiBaseUrl` y `wsBaseUrl` ahí antes).

### Variables del `.env`

| Variable | Para qué |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | La base MySQL |
| `JWT_SECRET`, `JWT_EXPIRES_IN` | Firma y duración de las sesiones |
| `PORT`, `CORS_ORIGIN` | Puerto del backend y dirección del frontend |
| `UPLOADS_DIR`, `PUBLIC_BASE_URL` | Dónde se guardan las fotos y con qué dirección se sirven |
| `FRONTEND_URL` | Para los links de los mails y la vuelta de Mercado Pago |
| `MAIL_FROM` y `spring.mail.*` | Envío de mails (sin esto, los mails se escriben en el log) |
| `MP_TOKEN_ENCRYPTION_KEY` (+ opcionales `MP_QR_EXPIRATION_MINUTES`, `MP_SYNC_INTERVAL_MS`) | Cobros con QR de cada café (paso 2.2) |
| `SUPERADMIN_EMAIL`, `SUPERADMIN_PASSWORD`, `SUPPORT_CONTACT` | Tu cuenta del panel de superadmin (paso 3.1) |
| `BILLING_MP_ACCESS_TOKEN` | Tu cuenta de Mercado Pago para cobrar las suscripciones (paso 3.2) |
| `LEGAL_VERSION` (opcional) | Versión vigente de los términos (paso 3.3) |

## Cómo se separan los datos de cada café (la parte más importante)

Cada café es un "tenant": todas sus tablas tienen `business_id` y nadie de
un café puede ver ni tocar nada de otro. Se logra con dos mecanismos
combinados:

1. **Lecturas (`SELECT`) y `UPDATE`/`DELETE` por query**: filtro de
   Hibernate (`@FilterDef`/`@Filter(condition = "business_id = :businessId")`)
   declarado **explícitamente en cada entidad** que hereda de
   `TenantScopedEntity` (nunca heredado automático desde la clase base: si
   una entidad nueva se olvida del filtro, se nota en la revisión).
   `TenantFilterInterceptor` habilita el filtro una vez por request.

2. **Altas (`INSERT`)**: los filtros de Hibernate **no aplican a
   `INSERT`**, por eso `TenantScopedEntity` tiene un `@PrePersist` que
   asigna `businessId` (y `id`, fechas) solo antes de guardar.

3. **El detalle que hay que conocer si tocás este código**: el filtro
   **no se aplica a `EntityManager.find()`** (o sea, tampoco a
   `JpaRepository.findById()`), solo a JPQL/Criteria. Si alguien llamara
   `.findById(id)` sobre una entidad de un café, habría fuga de datos entre
   cafés. Por eso `TenantSafeRepositoryImpl` **bloquea en tiempo de
   ejecución** `findById`/`getReferenceById` para esas entidades y obliga a
   usar `findByIdScoped(...)` (JPQL con `businessId` en el `WHERE`).

Lo que no es de un café (el café en sí, la cuenta de superadmin, la
suscripción, los pedidos de baja) no lleva el filtro y lo usan solo
servicios que eligen el café a mano (del token, nunca de la URL).

Ver el Javadoc de `TenantScopedEntity`, `TenantSafeRepositoryImpl` y
`TenantFilterInterceptor` para el detalle completo, incluido por qué
`open-in-view` quedó en `true` a propósito. Los tests de
`tenant/TenantIsolationTest` lo verifican solos.

## Probarlo a mano

Con el backend corriendo, `curl` y `jq`:

```bash
# 1. Alta de 2 cafés distintos
curl -s -X POST localhost:3000/api/v1/auth/register-business -H 'Content-Type: application/json' -d '{
  "legalName":"Café Uno SRL","taxId":"20-11111111-1","fiscalCondition":"MONOTRIBUTO",
  "ownerEmail":"owner1@test.com","ownerPassword":"claveSegura123","ownerFirstName":"Uno","ownerLastName":"Owner",
  "acceptTerms":true
}' | tee /tmp/biz1.json

curl -s -X POST localhost:3000/api/v1/auth/register-business -H 'Content-Type: application/json' -d '{
  "legalName":"Café Dos SRL","taxId":"20-22222222-2","fiscalCondition":"MONOTRIBUTO",
  "ownerEmail":"owner2@test.com","ownerPassword":"claveSegura123","ownerFirstName":"Dos","ownerLastName":"Owner",
  "acceptTerms":true
}' | tee /tmp/biz2.json

TOKEN1=$(jq -r .accessToken /tmp/biz1.json)
TOKEN2=$(jq -r .accessToken /tmp/biz2.json)

# 2. Cada token ve SU café y ningún otro
curl -s localhost:3000/api/v1/businesses/me -H "Authorization: Bearer $TOKEN1"
curl -s localhost:3000/api/v1/businesses/me -H "Authorization: Bearer $TOKEN2"

# 3. Con el token del café 1, pedí un usuario del café 2: tiene que dar 404
USER_ID_BIZ2=$(jq -r .user.id /tmp/biz2.json)
curl -s -o /dev/null -w '%{http_code}\n' localhost:3000/api/v1/users/$USER_ID_BIZ2 -H "Authorization: Bearer $TOKEN1"

# 4. Cada café nace con su local, y ninguno ve el del otro
curl -s localhost:3000/api/v1/locations -H "Authorization: Bearer $TOKEN1"
curl -s localhost:3000/api/v1/locations -H "Authorization: Bearer $TOKEN2"

# 5. Con el token del café 2, querer cambiar el local del café 1: tiene que dar 404
LOCATION_ID_BIZ1=$(curl -s localhost:3000/api/v1/locations -H "Authorization: Bearer $TOKEN1" | jq -r '.[0].id')
curl -s -o /dev/null -w '%{http_code}\n' -X PATCH localhost:3000/api/v1/locations/$LOCATION_ID_BIZ1 \
  -H "Authorization: Bearer $TOKEN2" -H 'Content-Type: application/json' -d '{"name":"Hackeo"}'
```

## Decisiones técnicas a propósito

- **Cambios parciales (PATCH)**: un campo que no viene en el JSON y uno que
  viene en `null` se tratan igual (no se cambia). Distinguirlos necesitaría
  una librería extra y ningún formulario de la app lo necesita.
- **Alta de un café en una sola transacción**: si falla cualquier paso
  (crear el local o el dueño), se deshace todo y no queda un café a medio
  crear.
- **Montos con `BigDecimal`** y redondeo explícito en todos los cálculos
  de plata (cobros, descuentos, cuenta dividida).
- **Los pedidos guardan una "foto" del producto** (nombre, precio,
  opciones, descuentos) al momento de la venta: editar o borrar un producto
  después no cambia lo que ya se vendió.

---

# Historial

Lo que se fue agregando, en orden (se conserva la numeración de siempre:
empieza en la 8). La hoja de ruta actual es la sección 16.

## 8. Mejoras de catálogo agregadas después de la Etapa 2

Ronda de mejoras pedidas sobre el catálogo/pedidos ya existente (no es
una etapa nueva del roadmap, extiende Categories/Products/Orders):

- **Salón / para llevar**: toggle explícito en la pantalla de Meseras —
  antes era implícito (mesa sin elegir = para llevar); ahora "Salón"
  exige elegir una mesa antes de poder enviar el pedido.
- **Buscador de productos**: por nombre o SKU, tanto en Meseras como en
  la nueva pantalla admin de Productos.
- **Imágenes de producto**: subida real de archivo (`POST
  /media/products/image`, ver "Cómo correrla en tu compu" más arriba sobre
  `UPLOADS_DIR`/`PUBLIC_BASE_URL`) — el campo `image_url` ya existía en
  el esquema desde la Etapa 2, ahora se puede completar sin pegar una
  URL a mano.
- **Ingredientes**: campo de texto libre en `products.ingredients`
  (`V3__product_ingredients.sql`), deliberadamente simple — sin tabla
  aparte.
- **Alta de categorías/productos desde el front**: pantallas nuevas
  `/categorias` y `/productos` (OWNER/ADMIN) — hasta ahora el catálogo
  solo se podía cargar pegándole directo a la API.

Es una mejora agregada después del fin de la Etapa 2.

## 9. Panel diferenciado por rol (inicio + visibilidad)

- **Inicio según rol**: WAITER ve un acceso directo grande a "Hacer
  pedido" (`/pedidos`); KITCHEN uno a "Ver cocina" (`/kds`); OWNER/ADMIN/
  SUPERVISOR ven el resumen del negocio + accesos directos a las
  pantallas de gestión.
- **Visibilidad por rol**: Negocio/Usuarios/Locales/Categorías quedan
  ocultos en el menú y bloqueados por ruta (`roleGuard`) para
  WAITER/KITCHEN — solo OWNER/ADMIN/SUPERVISOR los ven. En esta ronda era
  solo un control del FRONTEND; después se reforzó también en el backend —
  ver la sección 13.
- **Productos es la excepción dentro del grupo de gestión**: WAITER y
  KITCHEN también ven la pantalla `/productos` (sin `roleGuard`, link
  siempre visible en el nav) para poder buscar un producto y consultar
  sus ingredientes — lo pidió Fran explícitamente después de probar el
  panel diferenciado. Dentro de esa pantalla, `ProductListComponent`
  sigue ocultando alta/edición/baja a quien no sea OWNER/ADMIN
  (`canManage`), así que para esos roles queda de solo lectura. El
  catálogo (`GET /categories`, `GET /products`) ya era legible para
  cualquier rol autenticado desde antes — lo nuevo acá es que la
  pantalla admin de productos también se puede abrir, no solo la API.

## 10. Caja (Bloque C — ítems 5, 11, 14 y 15)

Migración `V4__cash_management.sql`. Tres tablas nuevas — `cash_sessions`,
`cash_movements`, `order_payments` — y se dropea `orders.payment_method`
(existía desde la Etapa 1 sin uso real, ver el comentario de esa columna en
el `Order.java` de antes de esta migración).

- **Ítems 5/11 — método de pago + "abono en dos etapas"**: interpretado, a
  pedido explícito de Fran, como **pago dividido entre métodos** (mitad
  efectivo, mitad tarjeta) al momento del cobro — NO como seña pagada al
  hacer el pedido y saldo pagado después. Un pedido ahora puede tener
  varias filas en `order_payments`, cada una con su propio método
  (`CASH`/`DEBIT_CARD`/`CREDIT_CARD`/`TRANSFER`/`QR`/`OTHER`) — `POST
  /orders/{id}/payments` se llama una vez por línea.
- **Cambio de comportamiento importante**: antes de este bloque se podía
  marcar un pedido como DELIVERED sin haberlo cobrado. Desde acá,
  `OrderService#updateStatus` exige que esté pagado por completo (suma de
  `order_payments` ≥ `total`) antes de dejar pasar esa transición — es
  consecuencia directa de haber elegido pago dividido y no seña/saldo (no
  hay una noción de "entregar fiado"). En el frontend, el botón "Cobrar" en
  el tablero de Cocina/Barra (KDS) — visible para WAITER/SUPERVISOR/ADMIN/
  OWNER, nunca KITCHEN — abre un formulario de método + monto; el botón
  "Marcar entregado" queda deshabilitado (con un aviso) mientras el pedido
  tenga saldo pendiente.
- **Ítem 14 — gestión de la caja**: turnos de caja (`CashSession`) por
  local, uno abierto a la vez (garantizado a nivel de BASE con una columna
  generada + UNIQUE KEY — ver el Javadoc de `CashSession.java` — no solo a
  nivel de aplicación). Se abre con un fondo inicial, se pueden cargar
  movimientos manuales de efectivo (ingreso/egreso, con motivo obligatorio)
  y se cierra contando el efectivo físico. Los totales (ventas por método,
  efectivo esperado, diferencia) se calculan siempre al leer, nunca se
  persisten — así nunca quedan desincronizados de los pagos/movimientos
  reales. Pantalla nueva: `/caja` (`CashSessionComponent`).
- **Permisos de caja**: "todos los que atienden" (WAITER incluido, no solo
  gerencia) pueden abrir/cerrar turno y cargar movimientos — así lo pidió
  Fran explícitamente. KITCHEN nunca: ni ve la pantalla de Caja ni el botón
  "Cobrar" del KDS (mismo `FRONT_OF_HOUSE_ROLES` que ya se usaba para
  crear/entregar pedidos).
- **Ítem 15 — lote de Postnet**: un solo lote por cierre (no una tabla
  aparte) — `postnetBatchNumber`/`postnetReportedAmount` en el propio
  `CashSession`, ambos opcionales (no todo local cobra con Postnet). Se
  cargan al cerrar el turno, para comparar a simple vista contra lo que el
  sistema calculó en pagos electrónicos.
- **Tiempo real de cobros**: `addPayment` publica `order:updated` por
  WebSocket (agregado después — ver la sección 13).

### 10.1. Caja "más profesional" (mejoras post-Bloque C)

Migración `V5__cash_session_denominations.sql`. Fran pidió explícitamente
estas tres mejoras después de probar el Bloque C original, con vista a
locales que manejan plata real — dejó afuera a propósito una cuarta opción
("alertas y permisos más estrictos", los permisos actuales le alcanzan):

- **Nombres reales, no solo totales**: `CashSessionResponse` ahora trae
  `openedByName`/`closedByName`, cada `CashMovementResponse`/
  `OrderPaymentResponse` trae `createdByName`, y se agregó el detalle
  línea por línea de cobros del turno (`CashSessionResponse.payments` —
  antes solo existía el agregado `salesByMethod`). Todo se resuelve con
  UN SOLO lookup en lote (`UserRepository#findAllByIdInScoped`) por
  respuesta, nunca una query por fila — ver `CashSessionService#resolveNames`
  (y el mismo criterio aplicado a pedidos individuales en
  `OrderService#resolvePaymentCreatorNames`, para que un pedido con cobros
  muestre lo mismo).
- **Conteo de efectivo por billete**: al cerrar, en vez de un único monto
  suelto, se cuenta billete por billete (tabla `cash_session_denominations`
  — una fila por denominación, ej. "8 billetes de $2.000"). Las
  denominaciones válidas están hardcodeadas en
  `CashSessionService.KNOWN_DENOMINATIONS` (billetes vigentes en Argentina,
  confirmado vía BCRA/prensa en septiembre 2026:
  $20.000/$10.000/$2.000/$1.000/$500/$200/$100/$50/$20/$10) y también
  restringidas por un CHECK multi-columna en la migración
  (`subtotal = denomination * quantity`, soportado desde MySQL 8.0.16).
  Las monedas y los billetes de $50 o menos quedan afuera a propósito: por
  la devaluación son inviables para el conteo de caja de un café real. Si
  el BCRA emite una denominación nueva, hay que agregarla en DOS lugares:
  `CashSessionService.KNOWN_DENOMINATIONS` y el CHECK de la migración
  (más `CASH_DENOMINATIONS` en el frontend). **Decisión de diseño**: cuando
  se manda el desglose (`CloseCashSessionRequest.denominationBreakdown`),
  el `countedCashAmount` efectivamente guardado se RECALCULA del lado del
  backend como la suma de ese desglose — el valor suelto que mande el
  cliente se ignora — para que el número que se compara contra lo esperado
  nunca pueda desviarse de lo que la persona contó billete por billete
  (auditable). Sigue existiendo el flujo viejo (mandar solo
  `countedCashAmount`, sin desglose) para no romper nada.
- **Reporte de cierre para imprimir/exportar**: pantalla nueva dentro de
  `CashSessionComponent` — un "Ver reporte" en cada turno del historial (o
  automático al cerrar uno) abre un detalle imprimible con
  `window.print()` nativo del navegador (sin ninguna librería nueva, "Guardar
  como PDF" del propio diálogo de impresión cubre el "exportar"). El CSS
  de impresión (`@media print` + clase `.no-print`) oculta todo lo demás de
  la pantalla y deja solo el reporte. Como el historial ya trae el detalle
  completo de cada turno (mismo `CashSessionResponse` que el turno abierto:
  movimientos, cobros, desglose por billete), no hace falta ningún fetch
  extra para armar el reporte.

## 11. Stock (Bloque D — ítems 12 y 13)

Migración `V6__stock.sql`. Seis tablas nuevas (`ingredients`,
`ingredient_stocks`, `product_stocks`, `product_recipe_items`,
`ingredient_stock_adjustments`, `product_stock_adjustments`) y dos
columnas nuevas en `products` (`stock_mode`, `min_stock`, default `NONE` —
preserva el comportamiento de todo el catálogo ya cargado). Fran confirmó
el alcance respondiendo preguntas puntuales antes de escribir el código:

- **Qué se controla — "ambos"**: TANTO insumos/ingredientes COMO
  productos terminados, no uno u otro. Se resolvió con `StockMode` (enum
  `NONE`/`DIRECT`/`RECIPE`) elegido POR PRODUCTO, no una única opción a
  nivel negocio: `NONE` (default, sin control), `DIRECT` (el producto
  tiene su propio stock por local — ítems simples ya armados, ej.
  medialunas/sanguches) o `RECIPE` (consume insumos según una receta —
  ítems preparados, ej. "1 café cortado consume 18g de café en grano +
  50ml de leche"). Un producto en `DIRECT` nunca tiene receta; uno en
  `RECIPE` nunca tiene su propio `product_stocks` — son caminos
  mutuamente excluyentes, nunca combinados.
- **Descuento automático vs. corrección manual**: Fran fue explícito acá
  — "se corrige manual en cada apertura y cierre, pero se descuenta
  automáticamente en caso de medialunas, sanguches y esos elementos
  fáciles de descontar automáticamente". Se implementó exactamente así:
  `OrderService#create` descuenta stock automático (vía
  `StockService#consumeForOrder`) para cada ítem DIRECT/RECIPE del
  pedido, apenas se CREA el pedido (no al entregarlo — se toma de la
  vitrina o se empieza a preparar en el momento). Si el pedido se
  cancela, `OrderService#updateStatus` repone ese mismo stock (vía
  `StockService#restoreForOrder`). La corrección manual "de apertura y
  cierre" que menciona Fran es el ajuste con auditoría (ver más abajo) —
  no hay ningún automatismo atado al abrir/cerrar un turno de caja; es
  gerencia contando a mano y cargando el número real cuando le parece.
- **Alertas de stock bajo**: umbral configurable por insumo
  (`ingredients.min_stock`) o por producto DIRECT (`products.min_stock`,
  solo aplica en ese modo — en RECIPE el umbral vive en cada insumo de la
  receta). `IngredientStockLevelResponse`/`ProductStockLevelResponse` ya
  traen `belowMinimum` calculado por el backend; el frontend solo lo
  pinta en rojo, nunca lo recalcula.
- **Stock NEGATIVO permitido a propósito**: la pregunta explícita fue qué
  hacer cuando se vende sin stock suficiente, y la respuesta de Fran fue
  "permitir igual, marcar en rojo" — nunca trabar una venta en la hora
  pico por un desajuste de stock. `IngredientStock#currentStock`/
  `ProductStock#currentStock` pueden quedar negativos sin problema; queda
  marcado en rojo (`belowMinimum`, si hay umbral configurado) para
  corregir después con un ajuste manual.
- **Permisos — "gerencia carga, todos consultan"**: alta/edición de
  insumos, configurar el stock de un producto (modo + receta) y los
  ajustes manuales exigen OWNER/ADMIN/SUPERVISOR (`StockController.
  MANAGEMENT`, mismo grupo que Negocio/Usuarios/Locales/Categorías). La
  consulta de niveles (`GET /stock/ingredients/levels`, `GET
  /stock/products/levels`) y el catálogo de insumos (`GET
  /stock/ingredients`) están abiertos a CUALQUIER rol autenticado —
  WAITER/KITCHEN incluidos, para que puedan ver qué se está por acabar.
  Sin flujo de "solicitud de reposición" todavía — quedó explícitamente
  fuera de esta ronda (Fran no lo pidió).
- **Ajuste manual = mismo patrón que el conteo de efectivo de la Caja**:
  se manda la cantidad REAL contada (`AdjustStockRequest.newQuantity`),
  nunca un delta a mano — el backend calcula `delta = newQuantity -
  previousQuantity` y lo guarda solo para referencia rápida en el
  historial (`IngredientStockAdjustment`/`ProductStockAdjustment`, mismo
  patrón exacto que `CashMovement`: sin endpoint de edición/baja, es un
  registro contable — si se cargó mal, se hace un ajuste nuevo que lo
  corrija).
- **Scoping por local, igual que la Caja**: `Ingredient` (la definición,
  ej. "Café en grano") es del NEGOCIO, compartida por todos los locales —
  mismo criterio que `Category`/`Product`. La cantidad ACTUAL
  (`IngredientStock`/`ProductStock`) es de cada LOCAL — mismo criterio
  que `CashSession` — creada perezosamente la primera vez que ese insumo
  o producto se toca en ese local (nunca se precargan todas las
  combinaciones al crear un insumo/local nuevo).
- **Simplificación de concurrencia aceptada**: a diferencia de
  `TableRepository#findByIdScopedForUpdate` (lock pesimista para "una
  mesa, un pedido a la vez"), los incrementos/decrementos de
  `IngredientStock`/`ProductStock` NO usan lock pesimista — dos ventas
  simultáneas del mismo insumo en el mismo local, bajo carga muy alta,
  podrían perder un update. Se aceptó el riesgo porque Fran ya va a
  corregir el conteo a mano en cada apertura/cierre de local (mismo
  criterio que el conteo de efectivo), así que un desvío chico se
  autocorrige solo en la próxima corrección manual. Si llega a doler en
  el uso real, agregar el lock es una mejora futura acotada.
- **"Reemplazar toda la receta" en vez de PATCH incremental**: guardar la
  receta de un producto (`PATCH /stock/products/{id}/config` con
  `stockMode: RECIPE`) borra todas las líneas viejas de
  `product_recipe_items` de ese producto e inserta las nuevas — nunca un
  PATCH línea por línea (mismo criterio que otros formularios "todo o
  nada" de este proyecto). Un insumo usado en alguna receta no se puede
  dar de baja físicamente (`ON DELETE RESTRICT`, mismo criterio que
  `fk_products_category` de la V1) — solo baja lógica (`isActive`).
- **Pantallas nuevas**: `/insumos` (`IngredientListComponent`) — catálogo
  de insumos, niveles de stock de insumos y de productos DIRECT del local
  actual, ajuste manual con historial; abierta a todos los roles, con el
  alta/edición/ajustes ocultos para quien no sea gerencia (mismo criterio
  `canManage` que Categorías/Productos). La pantalla de Productos
  (`ProductListComponent`) se extendió con un botón "Configurar stock"
  por producto (gerencia) para elegir `stockMode` y, en modo RECIPE,
  armar la receta completa (selector de insumo + cantidad por unidad, con
  filas para agregar/quitar) — la receta de un producto se edita ahí, no
  en `/insumos`.

## 12. Dashboard de KPIs (Bloque E — cierra la lista de 15 mejoras)

Sin migración nueva — todo lo que necesita ya existe (`orders`,
`order_items`, `order_payments`, más los niveles de stock del Bloque D).
Es un módulo nuevo `dashboard` (`DashboardController`/`DashboardService`,
DTOs `DashboardSummaryResponse`/`TopProductResponse`) que solo LEE datos
existentes — no agrega tablas ni columnas. Fran confirmó el alcance con
tres preguntas puntuales antes de escribir el código:

- **Qué se ve — las cuatro cosas, todas juntas**: ventas del
  día/período (total facturado, cantidad de pedidos, ticket promedio),
  productos más vendidos, ventas por método de pago y alertas de stock
  bajo, en un único panel (`kpi-panel` de `DashboardComponent`) arriba de
  los accesos rápidos de siempre. No se armó un módulo separado de
  "reportes" — es el mismo Inicio, con el panel nuevo solo para quien
  tiene permiso de verlo (ver más abajo).
- **Qué cuenta como "venta"**: pedidos con `status = DELIVERED` cuyo
  `closedAt` (no `createdAt`) cae dentro del rango pedido —
  deliberadamente quedan afuera los cancelados y los que siguen
  abiertos. Se eligió `closedAt` porque es el momento en que el pedido
  quedó cobrado y entregado de verdad (mismo criterio que
  `OrderService#assertFullyPaid`), no cuando se cargó. El ticket
  promedio es `totalSales / orderCount` (0 si no hubo ventas, para no
  dividir por cero). "Productos más vendidos" agrupa las líneas
  (`OrderItem`, con el nombre/precio ya congelados al momento de la
  venta) de esos mismos pedidos por producto y muestra el top 5 por
  cantidad. "Ventas por método de pago" agrupa `OrderPayment` (no
  `Order` — el pedido en sí no guarda método de pago, eso vive en cada
  cobro, ver Bloque C) de esos mismos pedidos por `PaymentMethod`.
- **Por local, con selector**: cada consulta (`GET /dashboard/summary`)
  pide un `locationId` puntual — nunca un consolidado de todo el
  negocio. `DashboardComponent` trae los locales del negocio y arranca
  mostrando el del usuario logueado (o el primero, si no tiene uno
  fijo — mismo patrón `resolveLocationId` de Caja/Pedidos/KDS); si hay
  más de un local, aparece un selector para cambiar de vista sin
  recargar la página. El selector de período (Hoy/Esta semana/Este mes)
  se calcula en el navegador, en hora LOCAL de Argentina (hoy arranca a
  las 00:00 de hoy, la semana el lunes, el mes el día 1 — nunca
  domingo, convención local), y recién ahí se convierte a ISO-8601 para
  mandarlo al backend — así "hoy" siempre coincide con el hoy real del
  usuario, no con UTC.
- **Solo gerencia — reforzado en el backend, no solo escondido en la
  pantalla**: `GET /dashboard/summary` tiene
  `@PreAuthorize("hasAnyAuthority('OWNER', 'ADMIN', 'SUPERVISOR')")`
  directo en el controller — igual criterio que Negocio/Usuarios/
  Locales/Categorías/Stock. `DashboardComponent` ni siquiera pide el
  panel si el rol logueado no es de gerencia (WAITER/KITCHEN mantienen
  su vía rápida de siempre, sin cambios), pero el gating real es el del
  backend, no el de la pantalla.
- **Alertas de stock bajo = mismo dato del Bloque D, sin duplicar
  lógica**: `DashboardService` llama a
  `StockService#ingredientLevels`/`productLevels` del local pedido y
  se queda solo con los que ya vienen marcados `belowMinimum` — ningún
  cálculo de umbral nuevo. El panel muestra la lista (insumo o
  producto + cantidad actual) y linkea a `/insumos` para corregir.
- **`from`/`to` como `String` en el controller, no `Instant`
  directo**: se parsean a mano con `Instant.parse` dentro de
  `DashboardController`, para poder devolver un mensaje de error propio
  y prolijo (`BadRequestException`) si el front manda algo mal
  formado, en vez de depender de la conversión implícita de Spring
  para query params — mismo criterio cuidadoso que se viene aplicando
  en todo el proyecto con los tipos de fecha/hora.

## 13. Pendientes chicos cerrados (post-Bloque E)

- **Cobros en tiempo real**: `OrderService#addPayment` ahora publica el
  evento `order:updated` (vía `OrderEventPublisher#orderUpdated`, después
  del commit, mismo criterio que alta/cambio de estado) con el pedido
  completo — `payments`/`paidTotal`/`balanceDue` ya actualizados. El KDS
  no necesitó cambios de lógica: ya hacía `upsertOrder` con cualquier
  evento que llegara; solo se sumó `'order:updated'` al tipo
  `OrderEventName`. Resultado: si un mesero cobra, otro que tenga el KDS
  abierto ve el saldo en 0 y "Marcar entregado" habilitado al instante,
  sin esperar al polling de respaldo.
- **Permisos de gestión reforzados en el backend** (antes solo frontend,
  ver sección 9):

  | Endpoint | Antes | Ahora |
  |---|---|---|
  | `GET /businesses/me` | cualquier rol | OWNER/ADMIN/SUPERVISOR |
  | `GET /businesses/me/display-name` | — (nuevo) | cualquier rol |
  | `GET /users`, `GET /users/{id}` | cualquier rol | OWNER/ADMIN/SUPERVISOR |
  | `GET /locations`, `GET /locations/{id}` | cualquier rol | **sin cambios** — cualquier rol |

  - **Por qué `/locations` sigue abierto**: Meseras, KDS, Caja e Insumos
    lo usan para resolver el local actual cuando el JWT no trae
    `locationId` (`resolveLocationId`), y un local solo expone
    nombre/dirección/teléfono. La edición ya era solo OWNER/ADMIN.
  - **Por qué hizo falta `display-name`**: el reporte imprimible de cierre
    de Caja mostraba el nombre del negocio pidiendo `GET /businesses/me`,
    y la Caja la usan también los WAITER — con la restricción nueva, ese
    reporte les habría fallado. `CashSessionComponent#loadReportHeader`
    ahora usa `BusinessService#getDisplayName()`, que devuelve solo el
    nombre de fantasía (o la razón social si no tiene), nunca CUIT ni
    datos fiscales.
  - Un WAITER/KITCHEN que le pegue directo a esas APIs ahora recibe 403
    (`RestAccessDeniedHandler`), no los datos.

Prueba rápida (con el backend corriendo y un usuario WAITER creado):

```bash
TOKEN_WAITER=...   # login de un WAITER
curl -s -o /dev/null -w '%{http_code}\n' localhost:3000/api/v1/businesses/me -H "Authorization: Bearer $TOKEN_WAITER"               # 403
curl -s localhost:3000/api/v1/businesses/me/display-name -H "Authorization: Bearer $TOKEN_WAITER"                                     # {"displayName":"..."}
curl -s -o /dev/null -w '%{http_code}\n' localhost:3000/api/v1/users -H "Authorization: Bearer $TOKEN_WAITER"                        # 403
curl -s -o /dev/null -w '%{http_code}\n' localhost:3000/api/v1/locations -H "Authorization: Bearer $TOKEN_WAITER"                    # 200
```

## 14. Impresión térmica (Etapa 3 — cierra la etapa)

Migración `V7__location_receipt_paper_width.sql`. Fran eligió el alcance
respondiendo preguntas puntuales antes de escribir el código:

- **Desde el navegador, no con un agente local**: los tickets se imprimen
  con la ticketera instalada en Windows, sin ningún programa extra en la
  PC del café. `TicketPrintService` arma un documento HTML angosto, lo
  carga en un iframe invisible y llama a `print()` sobre ese iframe (nunca
  `window.print()` de la página), así sale solo el ticket y el reporte A4
  de Caja sigue funcionando igual que antes.
- **Tres tickets** (plantillas en `core/services/ticket-templates.ts`,
  funciones puras):
  - **Comanda para cocina/barra**: mesa en grande, ítems con cantidades y
    notas, nota del pedido. Sin precios. Botón "Imprimir comanda" en cada
    tarjeta del KDS — visible para todos los roles, KITCHEN incluido.
  - **Ticket para el cliente**: encabezado del negocio y local, ítems con
    precio unitario y subtotal, total, pagos cargados y saldo pendiente
    (o "PAGADO"). Dice "Comprobante no válido como factura" al pie — la
    factura electrónica es la Etapa 4. Botón "Imprimir ticket" en el KDS,
    solo para quienes pueden cobrar (mismo grupo que "Cobrar"). Como el
    KDS saca los pedidos entregados del tablero, se imprime antes de
    "Marcar entregado".
  - **Cierre de caja**: los mismos números del reporte A4 (ventas por
    método, movimientos, efectivo esperado/contado, faltante/sobrante,
    conteo por billete, lote Postnet), sin el detalle línea por línea de
    cobros. Botón "Imprimir en ticketera" en el reporte de cierre, al lado
    del "Imprimir / exportar a PDF" de siempre.
- **Ancho configurable por local**: `locations.receipt_paper_width_mm`
  (58 u 80, default 80 — todo local existente queda en 80 sin tocar
  nada). Se edita en la pantalla de Locales (OWNER/ADMIN). El ticket usa
  el ancho IMPRIMIBLE real del rollo (72 mm en uno de 80, 48 mm en uno de
  58), no el ancho del papel, para que no se corte el texto de la derecha.
- **Comanda con botón, nunca automática** — decisión de Fran.
- **Sin cola local propia, a propósito**: con impresión por navegador el
  spooler de Windows ya encola los trabajos (si la ticketera está apagada,
  quedan esperando). Una cola propia solo tendría sentido con un agente
  ESC/POS, que quedó fuera de alcance.
- **Formato argentino** en los tickets (`$ 1.234,50`, `22/09/2026 14:32`)
  vía `Intl` del navegador, sin registrar el locale de Angular.

### Configuración de la PC del café (una sola vez)

1. Instalar el driver de la ticketera en Windows y, en sus preferencias,
   elegir el papel de rollo (80 mm o 58 mm, según la impresora).
2. En Locales, poner el mismo ancho en el local correspondiente.
3. La primera vez que se imprime, en el diálogo de Chrome/Edge: elegir la
   ticketera, **Márgenes: Ninguno** y destildar **Encabezados y pies de
   página**. El navegador recuerda esas opciones.
4. Opcional, para que no aparezca el diálogo: abrir Chrome con
   `--kiosk-printing` (ej. un acceso directo a
   `chrome.exe --kiosk-printing http://tu-servidor`). Ojo: en ese modo
   TODO se imprime directo en la impresora predeterminada, incluido el
   reporte A4 de Caja — conviene usarlo solo en la PC de la barra/cocina.

**`angular.json`**: se subió el presupuesto de CSS por componente
(`anyComponentStyle`: aviso 6 kB, error 10 kB; antes 2/4 kB). La build de
producción (`npm run build`) ya fallaba antes de esta ronda porque los
estilos de Caja y Meseras pasaban los 4 kB — no es algo que haya roto la
impresión.

## 15. Rediseño del frontend (más lindo y más fácil de aprender)

Sin cambios en el backend: todo es frontend Angular. Fran pidió "todo de
una", para celular, tablet, compu y TV de cocina, con modo claro y oscuro,
y tres formas de ayuda para quien entra por primera vez.

**Base visual**
- Sistema de diseño en `src/styles.css`: colores cálidos (café/crema)
  como variables, con **modo claro y oscuro** (`data-theme` en `<html>`).
  `ThemeService` guarda la elección por dispositivo; por defecto sigue la
  configuración del celular/compu. Un script en `index.html` aplica el
  tema antes de que arranque Angular (sin "parpadeo").
- Clases compartidas: `.btn` (+ `-primary`, `-secondary`, `-ghost`,
  `-success`, `-danger`, `-lg`, `-sm`, `-icon`), `.card`, `.field`,
  `.chip`, `.segmented`, `.badge` (+ colores por estado de pedido
  `status-*`), `.alert`, `.stepper`, `.stat`, `.bar-track`, `.hint-box`.
  Todo lo que se toca mide al menos 44 px (dedo).
- Componentes nuevos en `shared/components`: `app-icon` (íconos SVG
  propios, sin librerías), `app-modal` (ventana centrada en la compu,
  "hoja" que sube desde abajo en el celular), `app-empty-state`,
  `app-toast-host` + `ToastService` (avisos cortos), `app-tour`,
  `app-payment-dialog`.
- Formato argentino en toda la app (`LOCALE_ID = 'es-AR'`): `$ 1.234,50`.

**Menú según el dispositivo** (`ShellComponent`)
- Compu: menú lateral agrupado (Día a día / Carta / Administración).
- Tablet: menú lateral angosto, solo íconos con el nombre abajo.
- Celular: barra de 4 accesos abajo (al alcance del pulgar) + "Más".
- Cada rol ve solo lo suyo.

**Ayuda para aprender**
- **Tour de bienvenida** (`TourService` + `TourComponent`): la primera
  vez que alguien entra, le señala las partes principales según su rol.
  Se vuelve a ver con el botón "Ayuda". "Ya lo vio" se guarda por usuario
  en ese dispositivo.
- **Pasos guiados**: Pedidos (¿Dónde? → Productos → Revisar y enviar),
  cierre de Caja (Contar billetes → Postnet → Confirmar), Registro.
- **Pantallas vacías que explican** qué va ahí y con el botón para
  hacerlo (sin mesas, sin productos, sin categorías, sin stock…).
- **Primeros pasos** en Inicio (gerencia): checklist con datos reales
  (categorías, productos, mesas, equipo, primera caja).

**Flujos más simples**
- **Pedidos**: pestañas "Nuevo pedido" y "Pedidos abiertos". Las mesas
  con un pedido abierto se marcan (el backend no deja cargar otro). Los
  productos se suman tocándolos, con +/− en la misma tarjeta. En
  "Pedidos abiertos" se **cobra, imprime y entrega** sin ir a Cocina.
- **Cobro** (`PaymentDialogComponent`, compartido por Pedidos y
  Cocina): se elige el método con un toque, el monto ya viene con lo que
  falta, y en efectivo sugiere billetes y calcula el **vuelto**. Pago
  dividido: la ventana queda abierta con el resto. Si la caja está
  cerrada, ofrece ir a abrirla.
- **Cocina**: un botón grande por pedido con el siguiente paso, tiempo
  de espera por color (amarillo +10 min, rojo +20 min), pedidos nuevos
  resaltados, pestañas en el celular y **modo pantalla completa** para
  un TV (`LayoutService.immersive`).
- **Caja**: estado claro (abierta/cerrada), fondo inicial con sugerencias,
  "Sacar / Poner efectivo" con motivos frecuentes, conteo de billetes
  con +/−, y el sistema dice si falta o sobra antes de cerrar.
- **Mesas** (pantalla nueva, `/mesas`): se cargan varias de una vez
  ("de la 1 a la 12"), con nombre y lugares. Antes solo por API.
- **Registro** (pantalla nueva, `/registro`): alta de un café nuevo en
  dos pasos. Antes solo por API.
- Cocina entra directo a su pantalla al iniciar sesión.

**Servicios nuevos que ordenan el código**: `LocationResolverService`
(el "local actual", antes repetido en 4 pantallas) y `PrintContextService`
(encabezado de tickets, cacheado por local), además de los de tema, avisos,
tour y pantalla completa.

**`angular.json`**: presupuesto de CSS por componente subido a 12 kB
(aviso) / 20 kB (error) — las pantallas con más estados (Pedidos, Caja)
pasaban el límite anterior.

**Verificado acá**: `ng build` (desarrollo y producción) sin errores y
`ng test` 2/2, más capturas de todas las pantallas en celular y compu, en
claro y oscuro, contra un backend de prueba. No se probó contra el backend
Spring real (el sandbox no lo corre).

## 16. Hoja de ruta para hacerla vendible

Lista acordada con Fran después del rediseño (sección 15). Se encara paso
a paso, en este orden. Cada paso se marca acá cuando queda hecho.

### 1. Imprescindible antes del primer cliente

- [x] **Agregar productos a un pedido abierto.** ✅ Hecho — ver
  "Paso 1" más abajo.
- [x] **Prueba exhaustiva antes de salir** (carga, cosas a la vez,
  seguridad y todas las pantallas). ✅ Hecha — ver
  [docs/PRUEBAS-OCTUBRE-2026.md](docs/PRUEBAS-OCTUBRE-2026.md).
- [ ] **Ponerla en internet.** Servidor en la nube, dominio con HTTPS
  (`wss://` para el tiempo real) y la base MySQL con **backups
  automáticos**. 🧰 **El kit del servidor ya está listo** (Docker Compose,
  HTTPS automático, copias nocturnas afuera, actualizaciones seguras,
  avisos al celular): ver [docs/SERVIDOR.md](docs/SERVIDOR.md). Falta
  alquilar el servidor (2 GB de RAM mínimo) y el dominio, y correr
  `deploy/scripts/instalar.sh`.
- [x] **Cuentas: lo básico.** "Olvidé mi contraseña" (con mail),
  cambiar la contraseña desde la app, y que la sesión venza después de un
  tiempo. ✅ Hecho — ver "Paso 1.3" más abajo.

### 2. Muy importante para que no te dejen

- [x] **Tests del backend.** Como mínimo: cobro, caja (apertura,
  movimientos, cierre y diferencia), stock (descuento por venta y
  reposición al cancelar) y aislamiento entre negocios. ✅ Hecho — ver
  "Paso 2.1" más abajo.
- [x] **Pagos con QR de Mercado Pago integrados.** ✅ Hecho — ver
  "Paso 2.2" más abajo.
- [x] **Variantes y agregados** de producto: tamaño chico/grande, leche
  de almendra, extra shot… sin tener que crear un producto por
  combinación. ✅ Hecho — ver "Paso 2.3" más abajo.
- [x] **Descuentos** sobre un ítem o sobre el pedido (el campo
  `discount_total` ya existe en la base desde la Etapa 1, sin uso).
  ✅ Hecho — ver "Paso 2.4" más abajo.
- [x] **Dividir la cuenta** entre los que están en la mesa. ✅ Hecho —
  ver "Paso 2.5" más abajo.
- [x] **Exportar reportes a Excel** (ventas, cierres de caja) para el
  contador. ✅ Hecho — ver "Paso 2.6" más abajo.

### 3. Para manejarlo como negocio

- [x] **Panel de superadmin** (para Fran): ver todos los cafés
  clientes, activar/suspender, cambiar de plan. ✅ Hecho — ver "Paso 3.1"
  más abajo.
- [x] **Cobro de la suscripción**: débito automático (Mercado Pago) y
  período de prueba gratis. ✅ Hecho — ver "Paso 3.2" más abajo.
- [x] **Papeles**: términos y condiciones y política de privacidad (se
  guardan datos de clientes y empleados — Ley 25.326 de datos
  personales). ✅ Hecho — ver "Paso 3.3" más abajo. **Falta completar tus
  datos y que lo revise un abogado.**
- [x] **Una sola versión**: seguir solo con esta (Java/Angular), que es
  la más completa. ✅ Hecho — ver "Paso 3.4" más abajo.

### Opcional (según el café)

- [ ] **Facturación electrónica ARCA (ex AFIP)** — Etapa 4 del roadmap. **Opcional por negocio**: no todos los cafés la van a
  necesitar (algunos ya facturan con otro sistema o no están obligados).
  Tiene que poder activarse o no en cada café. Qué comprobante le toca a
  cada uno lo define su contador.

- [ ] **Apps de delivery (PedidosYa, Rappi y otras)** — **Fase A (modo
  manual) ✅ hecha**, ver "Paso: apps de delivery, Fase A" más abajo. Que los pedidos de
  las apps lleguen solos a la cocina, sin perder ninguno si falla una app.
  **Opcional por café y por local**: cada café prende solo las apps que usa
  (ninguna, una o varias), en modo manual o automático. Plan completo,
  paso a paso: [docs/INTEGRACION-APPS-DELIVERY.md](docs/INTEGRACION-APPS-DELIVERY.md).
  La parte automática necesita antes "Ponerla en internet".

### Fuera de alcance, a propósito

- **Propinas**: decisión de Fran — es un manejo propio de cada mesero,
  el sistema no se mete.

### Estrategia

Con los tres ítems de la parte 1 resueltos, darla **gratis a 1 o 2 cafés
de Mar del Plata como piloto** durante un mes. Lo que pidan define el
orden de la parte 2. Un café contento sirve de referencia para vender al
resto.

### Paso 1 hecho: agregar productos a un pedido abierto

Migración `V8__order_item_batches.sql` (columna `order_items.batch`).

- **Backend**: `POST /orders/{id}/items` (`OrderService#addItems`,
  roles WAITER/SUPERVISOR/ADMIN/OWNER). Solo sobre pedidos abiertos. Los
  ítems nuevos entran como una **tanda** nueva (`batch` 2, 3…), con el
  mismo snapshot de nombre y precio que al crear el pedido, descuentan
  stock igual, y el total se recalcula sumando todos los ítems. El saldo
  a cobrar sube solo. Publica `order:updated` por WebSocket.
- **Decisión de Fran**: si el pedido estaba En preparación o Listo,
  **vuelve a "Nuevo"** en Cocina para que nadie se lo pierda.
- **Cocina**: lo agregado se marca "+ Agregado" y lo que ya se había
  preparado se ve tachado y atenuado. La comanda impresa trae **solo lo
  último agregado**, con el título "COMANDA · AGREGADO".
- **Pedidos**: botón "+" en cada pedido de "Pedidos abiertos", y al tocar
  una mesa ocupada aparece "Agregar a su pedido". Se eligen los productos
  igual que en un pedido nuevo y el botón final dice "Agregar al pedido".
- **Sin cola offline para esto, a propósito**: sumar a un pedido que
  mientras tanto pudo cobrarse o cerrarse necesita la respuesta del
  servidor en el momento. Sin internet, avisa y no suma.
- **Simplificación aceptada**: sin lock sobre el pedido (dos personas
  sumando a la misma mesa en el mismo segundo podrían pisar el total; el
  próximo agregado o cobro lo corrige). Ver el Javadoc de `addItems`.
- **Verificado acá**: build de producción del frontend y `ng test` 2/2,
  y el flujo completo con capturas contra un backend de prueba. El
  backend Java no se pudo compilar en el sandbox (Maven Central bloqueado):
  **compilarlo y probarlo en la PC es lo primero**.

### Paso 1.3 hecho: cuentas (contraseñas y sesiones)

Migración `V9__accounts_security.sql`: columna `users.token_version` y
tabla `password_reset_tokens`. Dependencia nueva: `spring-boot-starter-mail`.

- **La sesión se corta en el momento.** Cada token lleva la "versión de
  sesión" del usuario (`ver`). Cambiar la contraseña, que te la cambie
  un admin o que te den de baja sube esa versión, y todos los tokens
  viejos dejan de valer en el próximo pedido al servidor (también el
  WebSocket). Lo chequea `SessionValidator` en cada request. El token
  sigue venciendo solo a las 8 h (`JWT_EXPIRES_IN`). Los tokens de antes
  de la V9 cuentan como versión 0: nadie queda afuera al actualizar.
- **Frontend**: si el servidor corta la sesión, vuelve al login con el
  aviso "Tu sesión se cerró…".
- **Freno a la fuerza bruta**: 5 contraseñas mal seguidas para un mismo
  mail lo bloquean 15 minutos (respuesta 429). Se guarda en memoria
  (`LoginAttemptService`): al reiniciar el backend se limpia, y con más
  de un servidor cada uno cuenta por su lado — suficiente para un solo
  servidor.
- **"¿La olvidaste?"** en el login (`/recuperar`): se pide el mail y
  llega un link a `/restablecer?token=…`, válido **1 hora** y **una sola
  vez**. En la base se guarda solo el hash SHA-256 del token, nunca el
  token. La respuesta es siempre la misma exista o no el mail (así nadie
  averigua qué mails están registrados), y hay un máximo de 3 pedidos por
  hora por mail. Al restablecer se cierran todas las sesiones abiertas.
- **Sin servidor de correo configurado, el mail se escribe en el log del
  backend** (con el link). Sirve para probar. Para mandarlo de verdad se
  completan las propiedades `spring.mail.*` (ejemplo con Gmail en
  `.env.example`) y `FRONTEND_URL` con el dominio real.
- **Mi cuenta** (`/cuenta`, cualquier rol, desde la tarjeta del usuario
  en el menú): datos propios, tema claro u oscuro y cambiar la
  contraseña (pide la actual). Al cambiarla, esta pantalla sigue
  adentro con un token nuevo y las demás sesiones se cierran.
- **Equipo**: botón "Contraseña" para ponerle una nueva a un empleado
  que se la olvidó (se la decís en persona). Mismas reglas que para
  editar: un ADMIN no toca a un OWNER ni a otro ADMIN, y la tuya propia
  se cambia desde Mi cuenta.
- **Verificado acá**: build de producción del frontend y las pantallas
  con capturas contra un backend de prueba. El backend Java no se pudo
  compilar en el sandbox (Maven Central bloqueado).

### Paso 2.1 hecho: tests del backend

45 tests de integración en `backend/src/test` (104 contando los de
Mercado Pago del paso 2.2, los de opciones del paso 2.3, los de descuentos
del paso 2.4, los de dividir la cuenta del paso 2.5, los del reporte en
Excel del paso 2.6, los del panel de superadmin del paso 3.1 y los del
cobro de la suscripción del paso 3.2 y los de los papeles del paso 3.3). Levantan la aplicación
entera (seguridad, filtro multi-tenant, servicios y MySQL) y le pegan a la
API igual que el frontend. Cada test se arma su propio café de cero (mail y
CUIT al azar), así ninguno depende de otro.

| Archivo | Qué cuida |
|---|---|
| `order/OrderPaymentTest` | Total del pedido, cobro dividido, no pagar de más, no entregar sin cobrar todo, precio congelado, agregar productos (tanda 2, vuelve a Nuevo), pedido reenviado sin duplicar, una mesa = un pedido abierto, qué rol mueve cada estado, producto dado de baja |
| `cash/CashSessionTest` | Una sola caja abierta por local, cierre completo (esperado = apertura + ventas en efectivo + ingresos − egresos, y la diferencia), conteo por billete, billetes inválidos, caja cerrada congelada, cada cobro en su turno, Cocina sin caja |
| `stock/StockTest` | Stock directo y por receta: baja al vender y al agregar, vuelve al cancelar, no vuelve al entregar; alerta de mínimo; historial de ajustes; productos sin control; el mozo ve pero no ajusta |
| `tenant/TenantIsolationTest` | El café B no puede leer, listar, filtrar, crear con, cobrar, cancelar ni modificar nada del café A, aunque conozca los ids (siempre 404) |
| `mercadopago/MercadoPagoQrTest` | (Paso 2.2) Conectar la cuenta (token validado y guardado cifrado), configurar el local, cobro con QR en pantalla e impreso, cobro parcial, cuenta dividida por productos con QR (paso 2.5), vencido, cancelado, bloqueos mientras el QR espera, confirmación por proceso de fondo y por webhook, un solo registro aunque lleguen avisos juntos, aislamiento entre cafés |
| `option/ProductOptionsTest` | (Paso 2.3) Grupos reutilizables asignados a varios productos, reglas imposibles rechazadas, orden de los grupos, precio con extras, elecciones inválidas (falta una obligatoria, de más, repetida, de otro producto, "no hay"), lo vendido no cambia aunque se edite o borre el grupo, insumos de las opciones que bajan y vuelven al cancelar, aislamiento entre cafés |
| `order/OrderDiscountTest` | (Paso 2.4) Lista de descuentos (solo el dueño la edita, sin nombres repetidos ni más del 100%), porcentaje sobre el pedido que se recalcula al agregar, monto fijo sobre un ítem con tope, ítem y pedido a la vez, reemplazar y quitar, permisos (mozo la lista, supervisor lo demás), motivo obligatorio a mano, nunca por debajo de lo cobrado, pedido cerrado, descuento desactivado, aislamiento entre cafés |
| `order/OrderSplitTest` | (Paso 2.5) Cobrar por productos (quedan marcados, el último paga justo el saldo), no cobrar dos veces lo mismo ni productos de otro pedido, descuento sobre el pedido repartido, centavos de redondeo, mezclado con cobros comunes, partes iguales y lo agregado después |
| `report/ReportTest` | (Paso 2.6) El Excel trae las cinco hojas con los datos del período (solo pedidos entregados), fórmulas de totales y textos con caracteres especiales; solo dueño/administrador/supervisor; fechas inválidas; no mezcla datos de otro café |
| `platform/PlatformAdminTest` | (Paso 3.1) Login del superadmin, lista de cafés con sus números, el superadmin no entra a pantallas de un café y ningún usuario de un café entra al panel; suspender corta las sesiones al instante y el login avisa el motivo, reactivar deja entrar de nuevo, dar de baja también bloquea; plan, notas e historial; contraseña mala; su email no se puede usar para registrar un café |
| `billing/BillingTest` | (Paso 3.2) Prueba de 14 días, sin precio no se puede suscribir, suscribirse (primer débito al terminar la prueba), débito aprobado = un mes pago (sin contarlo dos veces), vencido con 7 días de aviso y después suspendido solo, el dueño entra solo a pagar y al pagar se reactiva, tarjeta rechazada y reintento, suscribirse de nuevo con otra tarjeta, cancelar, "Sin cobro" y sumar días de prueba, una suspensión a mano no la levanta un pago, avisos inventados |
| `legal/LegalTest` | (Paso 3.3) Sin la casilla no se registra y con la casilla queda la constancia (versión, quién, IP); versión nueva: solo el dueño la acepta y no se acepta una vieja; botón de baja sin entrar (código al instante, corta el débito, mail al que lo pidió y a Fran, aparece en el panel); si el CUIT y el email no coinciden no toca nada; si Mercado Pago falla el pedido se guarda igual; datos inválidos y freno de 5 pedidos por hora |
| `auth/AuthSecurityTest` | Login, bloqueo a los 5 intentos, cambiar contraseña, baja y reseteo por el dueño (sesión cortada al instante), límites del ADMIN y del mozo, "Olvidé mi contraseña" completo, links inventados o reusados, freno a pedir muchos links |

**Base de test, aparte de la real.** Los tests usan `cofe_app_test` en el
mismo MySQL, con el usuario y la contraseña del `.env`. **La borran y la
rearman con Flyway cada vez que corren** (de paso prueban que V1…V10 arman
el esquema que espera Hibernate). Por seguridad, se niegan a arrancar si
la base no tiene "test" en el nombre: nunca pueden tocar `cofe_app`. Los
mails no salen nunca: en los tests se guardan en memoria.

Preparación, **una sola vez** (en MySQL Workbench, como root; si en el
`.env` usás otro usuario, cambialo). Primero fijate con qué "host" está
creado el usuario:

```sql
SELECT user, host FROM mysql.user WHERE user = 'cofe_user';
```

y usá ese mismo host (`localhost` o `%`) en el GRANT:

```sql
CREATE DATABASE IF NOT EXISTS cofe_app_test CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON cofe_app_test.* TO 'cofe_user'@'localhost';
FLUSH PRIVILEGES;
```

Si los tests dicen `Access denied for user ... to database 'cofe_app_test'`,
es que falta este GRANT (o se hizo con otro host).

Para correrlos: en IntelliJ, clic derecho sobre `backend/src/test/java` →
**Run 'All Tests'**, o en la pestaña Maven → Lifecycle → `test`. Tardan
un par de minutos (cada contraseña se cifra con BCrypt, que es lento a
propósito). Conviene correrlos antes de cada cambio grande y,
más adelante, antes de cada subida al servidor.

**La primera corrida encontró un error real de seguridad**, y ya está
corregido: el filtro que separa los datos de cada café se aplicaba sobre
una conexión equivocada, así que las listas (locales, pedidos, productos,
caja…) traían datos de todos los negocios. Con un solo café en la base no
se notaba. Se arregló en `TenantFilterInterceptor` (ahora usa la conexión
real de la request y, si no la encuentra, corta con error en vez de
seguir sin filtro) y en `WebMvcConfig` (orden del interceptor). Después
del arreglo pasan los 45.

### Paso 2.2 hecho: cobros con QR de Mercado Pago

Desde la ventana de cobro (Pedidos y Cocina) aparece **"QR Mercado
Pago"**. La plata entra directo a la cuenta de Mercado Pago de cada café, y
el cobro queda registrado solo en el pedido y en la caja (método QR)
cuando el cliente paga. No hace falta mirar el celular del cliente ni
cargar nada a mano.

**Dos formas de cobrar, a elegir por local:**

- **QR en pantalla**: se genera un QR nuevo por cada cobro y se muestra en
  la tablet o el celular. Ideal para el mozo que cobra en la mesa.
- **QR impreso**: un QR fijo pegado en el mostrador; al cobrar, el sistema
  le "carga" el monto y el cliente lo escanea. Mercado Pago admite un solo
  cobro a la vez por QR impreso.

**Puesta en marcha (dueño o administrador), menú "Cobros con QR":**

1. En Mercado Pago → [Tus integraciones](https://www.mercadopago.com.ar/developers/panel/app)
   crear una aplicación (pagos presenciales / código QR) con la cuenta
   donde se quiere recibir la plata.
2. Copiar el **Access Token de producción** (empieza con `APP_USR-`) y
   pegarlo en "Cobros con QR". Se valida con Mercado Pago y se guarda
   **cifrado**; nunca se vuelve a mostrar. Para probar sin plata real sirve
   un token de prueba (`TEST-…`): la pantalla lo avisa.
3. En cada local, elegir QR en pantalla o impreso y, **la primera vez**,
   completar la dirección y la ubicación en el mapa (Mercado Pago las pide
   para crear la sucursal y la caja). Con QR impreso aparece el link para
   imprimirlo.

**Cómo se entera el sistema de que el cliente pagó** (siempre
preguntándole a Mercado Pago, nunca creyendo un aviso a ciegas):

- La ventana de cobro pregunta cada 3 segundos mientras muestra el QR.
- Un proceso de fondo revisa cada 15 segundos los cobros que siguen
  esperando (por si se cerró la ventana o se cortó la conexión de la
  tablet). Si el cliente paga con la ventana cerrada, el cobro igual se
  registra.
- Aviso de Mercado Pago (webhook) en `POST /api/v1/webhooks/mercadopago`.
  Hoy no llega porque la app no está en internet; cuando se publique, se
  configura en el panel de Mercado Pago (evento "Order").

Aunque las tres cosas pasen a la vez, el cobro se registra **una sola
vez** (la fila del cobro se bloquea mientras se procesa; hay un test que
lo prueba con seis avisos simultáneos).

**Reglas:**

- Hace falta la caja abierta, igual que para cualquier cobro.
- Se puede cobrar todo lo que falta o una parte (el resto, con otro
  método).
- Mientras un QR espera el pago de un pedido, ese pedido no acepta otros
  cobros ni se puede cancelar, y la caja del local no se puede cerrar
  (para que un pago tardío nunca quede sin registrar). Se destraba
  cancelando el QR desde la misma ventana.
- Si se cierra la ventana con el QR esperando y se vuelve a abrir, al
  elegir "QR Mercado Pago" aparece el mismo QR (no se crea otro).
- El QR vence a los 15 minutos (configurable, máximo 15).
- Si Mercado Pago cobró pero el sistema no pudo registrarlo (caso raro:
  por ejemplo, el pedido ya estaba cerrado), la ventana lo avisa para
  registrarlo a mano.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| `V10__mercadopago_qr.sql` | Tablas `mp_accounts` (cuenta y token cifrado por negocio) y `mp_qr_charges` (cada cobro con QR); columnas `mp_*` en `locations` (tipo de QR, sucursal y caja de MP) |
| `backend/.../mercadopago/` | Conexión de la cuenta, configuración de cada local, cobro con QR, proceso de fondo, webhook y el cliente de la API de Mercado Pago (Orders API, `POST /v1/orders` tipo `qr`) |
| `common/crypto/SecretCipher` | Cifrado AES-256-GCM del token |
| `OrderService` / `CashSessionService` | Registro automático del pago confirmado y los bloqueos mientras un QR espera |
| Frontend | Pantalla "Cobros con QR" (solo dueño/administrador) y el QR dentro de la ventana de cobro. Nueva librería `qrcode` para dibujar el QR |

**Variables nuevas del `.env`** (todas tienen un valor por defecto para
desarrollo):

- `MP_TOKEN_ENCRYPTION_KEY`: frase con la que se cifran los tokens (mínimo
  16 caracteres). **En producción poné una propia, larga y al azar.** Si se
  cambia, cada café tiene que volver a conectar Mercado Pago.
- `MP_QR_EXPIRATION_MINUTES` (15), `MP_SYNC_INTERVAL_MS` (15000) y
  `MP_API_BASE_URL` (la API real de Mercado Pago).

**Para usarlo después de actualizar:** en `frontend`, correr una vez
`npm install` (por la librería nueva `qrcode`). El backend aplica la
migración V10 solo al arrancar.

**Pendiente para cuando la app esté en internet:** botón "Conectar con
Mercado Pago" (OAuth) en lugar de pegar el token, y el webhook configurado
en el panel de Mercado Pago. El resto no cambia.

### Paso 2.3 hecho: variantes y agregados (tamaño, leche, extras)

Un latte chico con leche de almendra y un extra shot ya no necesita un
producto propio: se carga **un** producto "Latte" y los **grupos de
opciones** que usa. Al tomarle el pedido, la app pregunta.

**Decisiones de Fran:**

- Los grupos son **reutilizables**: "Leche: Entera / Descremada / Almendra
  +$300" se crea una vez y se asigna a todos los cafés que la usan. Si
  cambia el precio de la almendra, se cambia en un solo lugar.
- Cada opción puede, si se quiere, **descontar un insumo** del stock
  (Almendra: 150 ml de leche de almendra; Extra shot: 9 g de café). Se
  suma a la receta del producto. Si la opción no tiene insumo, solo suma
  al precio.

**Cómo se usa (dueño o administrador), menú "Opciones":**

1. Crear un grupo (hay tres ejemplos listos para arrancar: Tamaño, Leche y
   Extras) con su nombre, si es **opcional u obligatorio**, si se elige
   **una sola o varias** (con máximo o sin límite) y sus opciones: nombre,
   cuánto suma al precio, si viene **marcada de entrada**, si **hay** y,
   opcional, qué insumo descuenta.
2. Marcar en qué productos se pregunta (se puede marcar una categoría
   entera de una vez). También desde **Productos → Opciones** de cada
   producto.
3. Ordenar los grupos con las flechas: es el orden en que se preguntan
   (Tamaño, después Leche, después Extras).

**Al tomar el pedido:** tocar un producto con opciones abre una ventana con
lo "de entrada" ya marcado (en el caso común es un solo toque en
"Agregar"). Muestra el precio final y no deja agregar si falta elegir algo
obligatorio. Un latte chico y uno grande quedan como dos líneas del
pedido. Cocina ve cada opción debajo del producto, y la comanda y el
ticket la imprimen.

**Reglas:**

- El precio lo calcula siempre el backend con las opciones guardadas
  (nunca confía en el que manda la pantalla).
- Lo vendido queda como se vendió: nombre, precio extra e insumo de cada
  opción se copian al pedido. Editar o borrar el grupo después no cambia
  pedidos viejos, y cancelar un pedido repone exactamente lo que se
  descontó.
- "No hay" (destildar **Hay**) saca la opción de la ventana hasta que se
  vuelva a marcar. Si en un grupo obligatorio no queda ninguna, deja de
  ser obligatorio: no se traba la venta (mismo criterio que el stock
  negativo).
- No se puede guardar un grupo imposible de completar: máximo menor que el
  mínimo, más marcadas de entrada que el máximo, opciones repetidas,
  insumo sin cantidad.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| `V11__product_options.sql` | Tablas `option_groups`, `option_items`, `product_option_groups` (qué grupos usa cada producto) y `order_item_options` (lo elegido en cada ítem, copiado al vender) |
| `backend/.../option/` | Grupos de opciones (`/option-groups`, `PUT /option-groups/order`, `PUT /products/{id}/option-groups`) y `OptionSelector`, que valida lo elegido al tomar un pedido |
| `OrderService` / `StockService` | Precio con extras, guardado de las opciones del ítem, descuento y reposición de los insumos de las opciones |
| Frontend | Pantalla "Opciones", botón "Opciones" en cada producto, ventana para elegir al tomar el pedido, y las opciones en Cocina, comanda y ticket |

**Para usarlo después de actualizar:** nada especial. El backend aplica la
migración V11 solo al arrancar. Lecturas: cualquier rol; crear o editar
grupos: dueño o administrador.

### Paso 2.4 hecho: descuentos

Desde la ventana de **Cobrar** (en Pedidos o en Cocina) aparece
**"Aplicar descuento"**: a todo el pedido o a un producto, de la lista con
un toque o puesto a mano.

**Decisiones de Fran:**

- **Lista fija + libre**: el dueño arma la lista en el menú
  **"Descuentos"** ("Jubilados 10%", "Empleados 20%", "Cortesía $1000"; hay
  ejemplos para arrancar) y además se puede poner uno a mano en el momento
  (porcentaje o monto) con un **motivo obligatorio**.
- **Permisos**: el mozo usa los de la lista, salvo los marcados **"pide
  autorización"**. Los descuentos a mano y los que piden autorización, solo
  supervisor, administrador o dueño. Lo que puso un supervisor, solo lo
  quita o lo cambia un supervisor.

**Cómo calcula:**

- Cada producto: precio × cantidad − su descuento. Un monto fijo nunca
  descuenta más de lo que vale la línea.
- El pedido: suma de los productos − el descuento sobre todo el pedido. Un
  porcentaje sobre el pedido se recalcula solo si después se agregan
  productos.
- Un descuento por producto y uno para todo el pedido, como máximo:
  aplicar otro en el mismo lugar lo reemplaza.
- Nunca deja el total por debajo de lo que ya se cobró (avisa y pide uno
  menor). Solo en pedidos abiertos, y no mientras un QR de Mercado Pago
  espera el pago.
- Queda registrado quién lo aplicó y, si fue a mano, el motivo. El ticket
  del cliente muestra cada descuento con su nombre.
- Desactivar o cambiar un descuento de la lista no cambia los pedidos donde
  ya se usó.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| `V12__order_discounts.sql` | Tabla `order_discounts` (lo aplicado a cada pedido, con quién y por qué) y controles de valor en `discounts` (la tabla de la V1, ahora en uso). Los montos van en las columnas que ya existían: `order_items.discount_amount` y `orders.discount_total` |
| `backend/.../discount/` | La lista de descuentos (`/discounts`) |
| `OrderService` | `POST /orders/{id}/discounts` y `DELETE /orders/{id}/discounts/{id}`: permisos, recálculo de totales y tope por lo cobrado |
| Frontend | Pantalla "Descuentos" (dueño/administrador), sección de descuentos en la ventana de cobro y el ticket con los descuentos |

**Arreglo encontrado por los tests en la PC de Fran (V13):** en Windows,
varios productos del mismo pedido podían quedar guardados con el mismo
instante, y entonces se mostraban en distinto orden entre una lectura y
otra (ticket, Cocina, ventana de cobro). Ahora cada renglón del pedido
tiene su número (`order_items.line_number`) y siempre salen en el orden en
que se cargaron.

**Para usarlo después de actualizar:** nada especial; el backend aplica las
migraciones V12 y V13 solo al arrancar.

### Paso 2.5 hecho: dividir la cuenta

En la ventana de **Cobrar** aparece **"¿Cómo pagan?"** con tres opciones:

- **Todo junto**: como siempre.
- **Partes iguales**: elegís entre cuántos (2, 3, 4…) y la app carga lo que
  paga cada uno. Después de cada cobro baja sola a "quedan N", y el último
  paga lo que falte (así no quedan centavos colgados). Cada uno puede
  pagar con un método distinto.
- **Por productos** (decisión de Fran: la app se acuerda de lo pagado):
  tocás los productos de esa persona (de "2 cafés con leche" se puede
  elegir uno) y la app calcula su parte. Los ya pagados quedan tildados y
  no se pueden cobrar de nuevo. Cada persona puede llevarse **su propio
  ticket** con lo suyo ("Su ticket", y al final "Ticket de cada persona").

**Cómo calcula "por productos":** cada unidad vale lo que vale en el
pedido, con su descuento; si hay un descuento sobre todo el pedido, se
reparte en proporción. Se redondea al centavo y, cuando con un cobro queda
todo pagado, se cobra exactamente el saldo. Nunca cobra más que el saldo
(por ejemplo, si antes se cobró una parte "todo junto").

**Con QR de Mercado Pago también:** en "Por productos" se puede elegir "QR
Mercado Pago"; el QR sale por la parte de esa persona y, cuando Mercado
Pago confirma (aunque sea por el proceso de fondo, con la ventana
cerrada), sus productos quedan marcados como pagados igual que en un
cobro común.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| `V14__order_payment_items.sql` | Tabla `order_payment_items`: qué productos (y cuántas unidades) pagó cada cobro |
| `OrderService#addPayment` | Acepta `items` en lugar de `amount` y calcula el monto; cada producto del pedido informa `paidQuantity` y cada cobro sus `items` |
| `QrChargeService` / `mp_qr_charges.paid_items` | El cobro con QR también acepta `items`: guarda qué productos paga y los marca cuando Mercado Pago confirma |
| Frontend | "¿Cómo pagan?" en la ventana de cobro y el ticket de cada persona |

**Para usarlo después de actualizar:** nada especial; el backend aplica la
migración V14 solo al arrancar.

### Paso 2.6 hecho: reporte en Excel para el contador

Menú nuevo **"Reportes"** (dueño, administrador y supervisor): elegís el
período (hoy, ayer, esta semana, este mes, mes pasado u otras fechas) y el
local (o todos), y tocás **"Descargar Excel"**. Baja un solo archivo con
cinco hojas:

| Hoja | Qué trae |
|---|---|
| Resumen | Período, local, ventas, cantidad de pedidos, ticket promedio, descuentos y lo cobrado con cada método |
| Ventas | Un renglón por pedido entregado: fecha, local, mesa, quién lo atendió, ítems, subtotal, descuentos, total y lo cobrado en efectivo, débito, crédito, transferencia, QR y otro |
| Productos | Unidades y plata de cada producto, de mayor a menor |
| Cierres de caja | Cada turno cerrado: quién abrió y cerró, inicial, ventas por método, ingresos, egresos, esperado, contado, diferencia y notas |
| Descuentos | Cada descuento: pedido, a qué se aplicó, cuánto, quién lo puso y el motivo |

Cada hoja tiene el encabezado fijo al bajar, filtros, y una fila de
totales con fórmulas (si el contador corrige un número, el total se
actualiza). Las fechas salen en la hora del café.

**Criterio:** cuenta como venta cada pedido **entregado** en el período
(cerrado y cobrado), igual que el panel de Inicio. En "Productos", los
descuentos sobre todo el pedido no se reparten entre los productos (sí los
de cada producto). No es un comprobante fiscal.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| `backend/.../common/excel/XlsxWriter` | Arma el .xlsx sin librerías externas (un .xlsx es un .zip con XML adentro): no hubo que sumar dependencias al `pom.xml` |
| `backend/.../report/` | `GET /api/v1/reports/excel?from=…&to=…&locationId=…&tz=…` |
| Frontend | Pantalla "Reportes" |

**Para usarlo después de actualizar:** nada especial (no hay migración
nueva).

### Paso 3.1 hecho: panel de superadmin

Una cuenta aparte para Fran (no es usuario de ningún café). Entra por el
mismo login de siempre y cae en **"Cafés clientes"**:

- Arriba, cuántos cafés hay: total, activos, en prueba y suspendidos
  (tocando uno, filtra).
- Buscador por nombre, CUIT o email del dueño, y filtro por estado.
- Un renglón por café: nombre y CUIT, dueño, fecha de alta, plan, estado,
  locales, usuarios activos, pedidos de los últimos 30 días y cuándo fue el
  último pedido (en naranja si pasaron más de 2 semanas: vale la pena
  llamar). No se ven montos de venta: son del café.
- Tocando un café: su equipo, sus locales, **estado** (en prueba / activo
  / suspendido / dado de baja), **plan**, **notas internas** (solo las ve
  Fran) y el **historial** de cambios (qué, cuándo y quién).

**Suspender (o dar de baja) un café:** hay que escribir el motivo. Al
guardar, nadie de ese café puede entrar: las sesiones abiertas se cortan al
instante (en la próxima pantalla que abran vuelven al login) y el login les
muestra, por ejemplo: *"Este café está suspendido (Falta de pago de
septiembre). Comunicate con Fran por WhatsApp al 223 555-0000."*. Los datos
no se borran: al reactivarlo, vuelven a entrar con sus usuarios de siempre
y está todo como estaba.

**El plan** (Prueba / Básico / Pro / Empresa) por ahora es solo una
etiqueta: no limita nada. Los límites y precios llegan con el cobro de la
suscripción (paso 3.2).

**Seguridad:** el superadmin solo puede usar `/api/v1/platform/**` (no
puede abrir pedidos, caja, etc. de ningún café), y ningún usuario de un
café — ni el dueño — puede usar el panel. El email del superadmin queda
reservado: no se puede registrar un café con él.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| Migración `V15__platform_admin.sql` | Tabla `platform_admins` (la cuenta de Fran), `businesses.status_reason` y `admin_notes`, y `platform_actions` (historial) |
| `backend/.../platform/` | `GET /platform/me`, `GET /platform/businesses`, `GET /platform/businesses/{id}`, `PATCH /platform/businesses/{id}` (estado + motivo, plan, notas) |
| `backend/.../common/security/` | Token de plataforma aparte; cada request de un café suspendido se rechaza con el motivo |
| Frontend | Pantalla `/superadmin` (fuera del menú de un café) y aviso en el login cuando cortan la sesión por suspensión |

**Variables nuevas del `.env`:**

- `SUPERADMIN_EMAIL` y `SUPERADMIN_PASSWORD` (mínimo 10 caracteres): al
  arrancar, el backend crea la cuenta (o le actualiza la contraseña si la
  cambiaste, cerrando sus sesiones). Si cambiás el email, la cuenta
  anterior deja de funcionar. Sin estas dos variables no hay panel (el
  resto anda igual). **No uses el mismo email que tu usuario de dueño de un
  café.**
- `SUPPORT_CONTACT` (opcional): a quién llamar, se muestra en el aviso de
  café suspendido. Ej: `Fran por WhatsApp al 223 555-0000`.

**Para usarlo después de actualizar:** agregá esas variables al `.env` y
reiniciá el backend (aplica la migración V15 solo). Entrá con ese email y
contraseña en el login de siempre.

### Paso 3.2 hecho: cobro de la suscripción

**Cómo es para el café:**

- Al registrarse tiene **14 días de prueba gratis**, sin tarjeta.
- En el menú **Administración → Suscripción** (dueño y administrador) ve
  el precio, cuántos días le quedan y sus pagos. El **dueño** toca
  "Suscribirme con Mercado Pago", confirma en la página de Mercado Pago
  con la tarjeta que quiera y listo: **débito automático todos los meses**.
  El primer débito es el día que termina la prueba (no pierde días).
- 3 días antes de que termine la prueba, al dueño y al administrador les
  aparece un aviso arriba de todo.
- **Si no paga** (no se suscribió, o le rechazan la tarjeta): tiene **7
  días** con un aviso ("Tenés X días para pagar"). Después **el café se
  suspende solo**: nadie del equipo puede entrar... **salvo el dueño, que
  entra solo a la pantalla de Suscripción para pagar**. Apenas se acredita
  el pago, se reactiva solo, con todo como estaba.
- Si le rechazan la tarjeta, Mercado Pago reintenta solo. Si la tarjeta no
  sirve más, puede suscribirse de nuevo con otra (se cancela la anterior
  para que no se cobre dos veces).
- Puede cancelar el débito automático cuando quiera: sigue usando lo que ya
  pagó y después corren los 7 días de siempre.

**Cómo es para vos (panel de superadmin):**

- Arriba: **precio por mes** (uno solo para todos los cafés), días de
  prueba y días para pagar. Tocás "Editar" para cambiarlos. Precio vacío =
  nadie puede suscribirse todavía (la prueba anda igual). Si cambiás el
  precio, vale para las suscripciones nuevas.
- Columna **"Cobro"** en la lista: en prueba hasta tal día, pago hasta tal
  día, "se suspende el…", suspendido por falta de pago o sin cobro.
- En cada café: sus pagos, **"Sin cobro (cortesía)"** (nunca se le cobra
  ni se suspende solo) y **"Sumar días de prueba"** (por ejemplo, si te
  pide unos días más; si estaba suspendido por falta de pago, vuelve a
  entrar).
- El historial anota también lo que hace el sistema solo ("Suspendido
  automáticamente", "Reactivado automáticamente: pago recibido").
- Una suspensión que hacés vos a mano no la levanta un pago.

**Los cafés que ya existían** al actualizar arrancan con 14 días de prueba
desde ese día. Los que ya tenías en "Activo" quedan en "Sin cobro" (lo
cambiás desde el panel).

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| Migración `V16__subscription_billing.sql` | `platform_settings` (precio, días de prueba y de gracia), fechas y estado del cobro en `businesses`, `subscription_payments` (cada débito) |
| `backend/.../billing/` | `GET /subscription`, `POST /subscription/checkout`, `/cancel`, `/refresh`; aviso de Mercado Pago en `POST /webhooks/mercadopago-suscripciones`; proceso de fondo cada hora (se pone al día con Mercado Pago y suspende a los vencidos); `GET/PUT /platform/settings` |
| `backend/.../mercadopago/client/` | Suscripciones de Mercado Pago (preapproval) y sus débitos |
| Seguridad | Café suspendido por falta de pago: el dueño entra, pero solo a `/subscription` (lo demás responde 402 y la app lo manda a Suscripción) |
| Frontend | Pantalla "Suscripción", aviso arriba de todo, y precio + columna "Cobro" + sección "Cobro" en el panel |

**Para cobrar de verdad** (una vez, cuando la app esté en internet):

1. En tu cuenta de Mercado Pago (la tuya, donde querés recibir la plata):
   [Tus integraciones](https://www.mercadopago.com.ar/developers/panel/app)
   → crear una aplicación → **Credenciales de producción** → copiar el
   **Access Token** (`APP_USR-…`).
2. En el `.env`: `BILLING_MP_ACCESS_TOKEN=APP_USR-…` y reiniciar el
   backend. En el panel tiene que decir "Mercado Pago conectado".
3. En esa misma aplicación de Mercado Pago → **Webhooks** → URL
   `https://<tu-dominio>/api/v1/webhooks/mercadopago-suscripciones`, eventos
   **"Planes y suscripciones"**. (Sin esto también funciona: el proceso de
   fondo revisa cada hora; con el aviso es al instante.)
4. `FRONTEND_URL` en el `.env` con tu dominio real: es adonde vuelve el
   dueño después de confirmar en Mercado Pago. Mercado Pago puede no
   aceptar volver a `localhost`, así que la suscripción de punta a punta
   conviene probarla con la app publicada (primero con las credenciales
   **de prueba** y usuarios de prueba de Mercado Pago).
5. Cargar el precio en el panel.

**Pendiente para después:** mails de aviso ("tu prueba termina en 3 días",
"no pudimos cobrarte") — hoy el aviso es dentro de la app. Y factura por
cada abono (va con ARCA, el opcional de abajo).

**Para usarlo después de actualizar:** el backend aplica la migración V16
solo al arrancar. Nada más (sin `BILLING_MP_ACCESS_TOKEN` todo anda, pero
nadie puede suscribirse todavía).

### Paso 3.3 hecho: los papeles (términos, privacidad y botones de baja)

> **Importante:** son borradores armados con lo que la app hace de verdad,
> pero **no son asesoramiento legal**. Antes de cobrarle a un cliente
> real: completá tus datos y hacelos revisar por un abogado.

**Qué hay:**

- **Términos y condiciones** (`/terminos`) y **Política de privacidad**
  (`/privacidad`): páginas públicas, en castellano simple, con un
  resumen "En pocas palabras" arriba. La de privacidad sigue la Ley 25.326
  e incluye las leyendas obligatorias sobre el derecho de acceso y la
  Agencia de Acceso a la Información Pública.
- **Casilla obligatoria al registrarse** ("Leí y acepto…"). Se guarda la
  constancia: qué versión, qué usuario, cuándo, desde qué IP y navegador.
- **Aceptar de nuevo si cambian:** si publicás una versión nueva, al dueño
  le aparece un cartel al entrar que no se puede saltear. Los cafés que ya
  existían lo ven la primera vez que el dueño entra después de actualizar.
- **Botón de arrepentimiento** (`/arrepentimiento`) y **Botón de baja**
  (`/baja`): links visibles en el login, el registro y "Mi cuenta", sin
  tener que entrar. Dan un **código de trámite al instante** (y por mail).
  Si el CUIT y el email coinciden con los del dueño de un café, **se corta
  solo el débito automático**. Te llega un mail y aparece en tu panel, en
  **"Pedidos de baja"**, donde lo marcás como atendido y dejás una nota
  (por ejemplo, "le devolví la plata por Mercado Pago"). El reintegro de un
  arrepentimiento lo hacés vos desde Mercado Pago.

**Tus datos (completalos antes de publicar):** en
`frontend/src/app/features/legal/legal-info.ts` → nombre o razón social,
CUIT, domicilio, email de contacto, ciudad de los tribunales y dónde está
alojada la app. Mientras digan "[COMPLETAR…]" se ven resaltados en
naranja en las páginas.

**Cosas que decidí en el texto y conviene que revises con el abogado:**

- Aviso de **30 días** antes de cambiar el precio.
- **Arrepentimiento** dentro de los 10 días corridos de suscribirse, con
  devolución de lo cobrado (Defensa del Consumidor).
- Tope de responsabilidad: lo pagado en los **últimos 12 meses**.
- Los datos no se borran solos al dar de baja: se borran **a pedido**
  (por email).
- Si el hosting queda fuera de Argentina, la transferencia internacional
  de datos.
- Si tenés que **inscribir la base de datos en la AAIP** (Registro Nacional
  de Bases de Datos, trámite online por TAD). Con clientes reales,
  preguntalo.

**Si cambiás el texto de forma importante:** subí la versión en
`legal-info.ts` (`version` y `versionLabel`) **y** en el backend
(`LEGAL_VERSION` en el `.env`, o `app.legal.version` en
`application.yml`) — las dos iguales. A cada dueño le aparece para
aceptarla de nuevo.

**Qué se agregó:**

| Dónde | Qué |
|---|---|
| Migración `V17__legal.sql` | `legal_acceptances` (cada aceptación), versión aceptada en `businesses`, `service_requests` (pedidos de arrepentimiento y baja) |
| `backend/.../legal/` | `GET /legal/version` y `POST /legal/requests` (públicos), `GET /legal/status`, `POST /legal/accept` (dueño); `GET/PATCH /platform/requests` en el panel |
| Frontend | Páginas `/terminos`, `/privacidad`, `/arrepentimiento`, `/baja`; casilla en el registro; cartel para aceptar la versión nueva; links en login, registro y "Mi cuenta"; "Pedidos de baja" en el panel |

**Para usarlo después de actualizar:** el backend aplica la migración V17
solo al arrancar. Al entrar, cada dueño va a ver el cartel para aceptar
los términos (una sola vez).

### Paso 3.4 hecho: una sola versión

Desde ahora esta (Java + Angular) es **la única versión** de COFFE_APP; la
de NestJS + React quedó descartada.

- **README** reordenado: arriba, qué hace la app, la tecnología, cómo
  correrla y todas las variables del `.env`; abajo, el historial de cada
  cosa que se fue agregando.
- **Comentarios del código**: se sacaron las referencias a la versión vieja
  ("igual que en el original", "del backend NestJS", "schema.prisma",
  "apiClient.ts"…) en 46 archivos del backend y 12 del frontend, dejando
  las explicaciones de por qué el código hace lo que hace. Solo cambiaron
  comentarios: se verificó que el código compilado del backend y el build
  de producción del frontend quedaron **idénticos** byte por byte, y pasan
  los 104 tests.

**Opcional, cuando quieras:** renombrar la carpeta de arriba
(`cofe-app-migracion-java-spring-angular`) a algo como `coffe-app`, con
IntelliJ cerrado. No cambia nada del proyecto.


### Mejora: plano del salón y botón de soporte

**Plano del salón** (Mesas → "Plano"). El café dibuja su salón como es de
verdad y el equipo elige la mesa tocándola en el dibujo.

- **Zonas** (Salón, Vereda, Planta alta…): cada una es un plano aparte, en
  pestañas. Al crear la primera, las mesas se acomodan solas en filas.
- **Mesas**: se arrastran con el mouse o el dedo; se elige la forma
  (redonda, cuadrada o rectangular) y el tamaño (botones o la esquina);
  las rectangulares se pueden girar. "Sacar del plano" la deja sin ubicar
  (no la borra) y se puede poner en otra zona.
- **Referencias**: barra, puerta, pared, ventana, baños y cocina, con
  texto opcional, para ubicarse de un vistazo.
- **Se guarda solo**, un instante después de cada cambio ("Guardado ✓").
- Lo arman el dueño y el administrador; el supervisor lo ve sin cambiarlo.
- **En Pedidos** (paso "¿Dónde?"): botones "Plano / Lista" (se recuerda).
  Libre = verde; ocupada = minutos desde que se sentaron y el total; rojo
  si pasaron más de 45 minutos. Tocar una libre toma el pedido; tocar una
  ocupada le suma productos. Las mesas sin ubicar aparecen abajo. En el
  celular el plano se desliza de costado para que las mesas no queden
  chiquitas.
- Backend: migración `V18__floor_plan_and_support.sql` (tablas
  `floor_areas` y `floor_items`, y la posición de cada mesa en `tables`),
  `GET /floor`, `POST/PATCH/DELETE /floor/areas`,
  `PUT /floor/areas/{id}/layout`. Cada zona mide 100 x 75 unidades y no
  deja guardar nada afuera del plano.

**Botón "Soporte"** (para todos, siempre a mano: en el menú de la
izquierda, arriba en el celular, flotante en la pantalla completa de
Cocina, y "¿Problemas para entrar?" en el login).

1. Primero ofrece **Recargar la app** (arregla la mayoría de las trabas;
   los pedidos guardados sin internet no se pierden).
2. **Escribir por WhatsApp**: abre WhatsApp con el mensaje ya escrito:
   café, quién (y su rol), pantalla, hora, qué pasó, **el último error que
   vio la app**, si hay internet y el navegador. Es un link común: funciona
   aunque el servidor no conteste.
3. Además deja un **pedido de ayuda** en tu panel de superadmin ("Pedidos
   de ayuda", con lo mismo) y te llega por mail si está configurado el
   correo. Máximo 10 por hora por persona.
4. Si la app directamente **no carga** (pantalla en blanco), a los 15
   segundos aparece un aviso con "Recargar" y "Escribir a soporte por
   WhatsApp" que no depende de Angular.

**Para que funcione el WhatsApp:** en el panel de superadmin → Suscripción
→ Editar → "WhatsApp de soporte", con 54, 9 y el código de área, sin el 0
ni el 15 (ej: `5492235550000`). Si lo dejás vacío, el botón igual te deja
el aviso en el panel y muestra el contacto de `SUPPORT_CONTACT`.

Se agregó a la Política de privacidad que se guardan los pedidos de ayuda
y lo que el botón guarda en el navegador. Tests nuevos: `FloorPlanTest`
(zonas, guardado, límites, otro café, roles) y `SupportRequestTest`
(WhatsApp público, pedido y panel, freno de 10 por hora, dueño suspendido).

### Paso: apps de delivery, Fase A (modo manual)

Primera fase del plan [docs/INTEGRACION-APPS-DELIVERY.md](docs/INTEGRACION-APPS-DELIVERY.md):
no depende de que ninguna app apruebe nada, y sirve desde el primer día.

- **Administración → Apps de delivery** (dueño y administrador): una
  tarjeta para PedidosYa, Rappi y Uber Eats, y "Otras apps" con nombre libre
  (una app del barrio, el WhatsApp del café…). Cada una: **Apagada /
  Manual** ("Automático" figura como "próximamente") y la **comisión** que
  cobra. Todo arranca apagado.
- **Pedidos → "¿Vino por una app?"** (solo aparece si hay alguna prendida):
  se elige la app, se copia el número del pedido de la tablet (cliente y
  hora de retiro opcionales) y si **lo cobró la app** o **lo cobramos acá**.
  Después, productos y "Enviar a cocina" como siempre.
- El pedido sale con el cartel de la app y su número ("PedidosYa #4521") en
  Cocina, en Pedidos abiertos y en las comandas impresas, con la hora de
  retiro y el cliente.
- **No se puede cargar dos veces** el mismo número de la misma app.
- **La plata:** si lo cobró la app, el pedido queda pagado con el método
  "App de delivery", que **no suma al efectivo** de la caja (en el cierre
  aparece aparte). Si lo cobra el café, se cobra como cualquier pedido.
- **Excel:** "Ventas por canal" en el Resumen, columnas de app y comisión en
  Ventas, y una hoja "Apps de delivery" con pedidos, ventas, comisión y lo
  que le queda al café. Un café sin apps ve el Excel igual que antes.
- Backend: migración `V19__sales_channels.sql`, `GET/POST/PATCH
  /sales-channels`, bloque `channel` opcional en `POST /orders`. Test nuevo:
  `SalesChannelTest` (118 tests en total).

**Siguiente fase:** la B ("Ponerla en internet"), obligatoria antes del
modo automático.

### Paso: prueba exhaustiva antes de salir a internet

Informe completo, con los números y qué se arregló:
[docs/PRUEBAS-OCTUBRE-2026.md](docs/PRUEBAS-OCTUBRE-2026.md). Los scripts para
repetirla están en [docs/pruebas/](docs/pruebas/LEEME.md).

- **Aguante:** 50 cafés en hora pico (la meta) sin errores y respondiendo en
  centésimas de segundo; también 100 y 200 cafés a la vez. Recién con 7,5
  veces más pedidos que la meta se pone lento (1–2 s), sin perder pedidos.
- **9 problemas encontrados y arreglados**, entre ellos: se podía **cobrar
  dos veces** el mismo pedido con dos clics a la vez, dos mozos podían abrir
  pedido en la **misma mesa**, el **stock** descontaba de menos con ventas
  simultáneas, un pedido podía quedar **cancelado y cobrado**, y "Mi cuenta"
  se cortaba en el celular.
- **Cambio de regla:** un pedido que ya tiene cobros no se puede cancelar
  (si hay que devolver plata, va como egreso en Caja). Los cobrados por una
  app sí.
- **Seguridad:** barrido automático de las 110 direcciones del servidor
  (sin sesión, cada rol, otro café, datos basura): ningún café ve ni toca
  datos de otro.
- **Más rápido:** "Pedidos abiertos" pasó de 0,23 s a 0,03 s con 100 cafés.
- Tests nuevos: `ConcurrencyTest` y `EndpointSweepTest`.

### Paso: kit para el servidor (antes de salir a internet)

Todo lo necesario para que, cuando la usen cafés de verdad, no haya
problemas. Guía paso a paso: [docs/SERVIDOR.md](docs/SERVIDOR.md).

- **Servidor de 2 GB de RAM como mínimo** (Spring Boot y MySQL juntos no
  entran cómodos en 1 GB): unos USD 5 a 15 por mes.
- **Arranca solo:** MySQL, backend y web (Caddy) con Docker Compose y
  `restart: unless-stopped`. Java limitado a `-Xmx768m`.
- **HTTPS automático** con Caddy (pide y renueva el certificado solo).
- **Copias de seguridad cada noche, afuera del servidor** (rclone a
  Backblaze B2, Cloudflare R2 o Google Drive), 14 días, y una **prueba de
  restauración automática por mes**.
- **Actualizaciones seguras:** solo noches de lunes a jueves (nunca
  viernes), solo después de probar en una copia (staging), con copia de
  seguridad antes, y la versión anterior guardada: `scripts/volver.sh`
  vuelve con un solo comando (y si la nueva no arranca, vuelve sola).
- **Logs con tope** y aviso al celular (app ntfy) si el disco pasa el 80 %,
  si la app no responde o si falla una copia.
- **Endpoint de salud:** `/api/v1/actuator/health` (Spring Boot Actuator),
  para Docker y para un monitor externo como UptimeRobot.
- En producción la app y la API van por el mismo dominio: `environment.ts`
  usa direcciones relativas.
