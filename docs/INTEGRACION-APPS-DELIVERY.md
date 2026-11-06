# Integración con apps de delivery (PedidosYa, Rappi y otras)

> **Estado:** **Fase A hecha** (modo Manual, migración V19). Lo demás sigue
> siendo plan. Este documento es la guía para hacerlo paso a paso; cada fase
> se marca acá cuando queda hecha.
>
> Investigado en octubre de 2026. Las apps cambian sus reglas: antes de
> arrancar cada fase, volver a mirar los links de "Fuentes" del final.

## 1. Qué queremos lograr

Que los pedidos que entran por las apps de delivery **lleguen a la cocina
como cualquier otra comanda**, sin que el café tenga que pasarlos a mano, y
que **nunca se pierda un pedido** aunque falle la app, internet o COFFE_APP.

## 2. La regla más importante: es opcional y a medida de cada café

No sabemos qué apps usa cada café, y cambia de un café a otro:

- algunos no usan **ninguna**;
- otros usan **una sola** (por ejemplo, solo PedidosYa);
- otros usan **varias** (PedidosYa + Rappi, y quizás Uber Eats).

Por eso:

1. **Todo arranca apagado.** Un café que no usa apps no ve nada nuevo: ni
   botones, ni filtros, ni avisos. Su COFFE_APP queda igual que hoy.
2. **Cada app se prende por separado, en cada local.** El mismo café
   puede tener PedidosYa en un local y no en otro.
3. **Cada app tiene tres modos** (el café elige y lo puede cambiar cuando
   quiera):

   | Modo | Qué hace | Para quién |
   |---|---|---|
   | **Apagado** | No aparece en ningún lado. | Cafés que no usan esa app. |
   | **Manual** | El pedido se sigue atendiendo en la tablet de la app; en COFFE_APP se carga en segundos con un botón "Pedido de PedidosYa", con su número, y sale la comanda. | Cualquier café, desde el día 1. No depende de ninguna aprobación. |
   | **Automático** | El pedido entra solo a COFFE_APP y sale la comanda sin tocar nada. | Cuando COFFE_APP ya esté aprobado como integrador por esa app. |

4. **"Otra app" en modo manual** sirve para cualquier app que no tengamos
   integrada (una app local, Mercado Libre, WhatsApp del café…): mismo botón,
   con el nombre que el café le ponga.
5. Lo que **aparece solo si hay al menos una app prendida**: el botón
   "Pausar delivery", el semáforo de conexión, el filtro por app en Cocina y
   en Reportes, y "A cobrar de apps" en Caja.

## 3. Cómo funciona cada app hoy (octubre de 2026)

### PedidosYa

- Tiene integración oficial para sistemas de punto de venta. **Hay que
  anotarse como integrador** con un formulario, probar en su entorno de
  pruebas y que lo aprueben. Las credenciales llegan cifradas por mail.
- **PedidosYa le manda el pedido a COFFE_APP** apenas entra (COFFE_APP no
  tiene que preguntar). Por eso COFFE_APP tiene que estar en internet.
- Tiene dos formas de trabajar:
  - **Indirecta:** el café acepta el pedido en la tablet de PedidosYa (como
    hoy) y recién ahí el pedido pasa a COFFE_APP. **Si COFFE_APP falla, el
    pedido igual está en la tablet.**
  - **Directa:** el pedido va directo a COFFE_APP. Si COFFE_APP no lo
    recibe, después de varios intentos **PedidosYa lo cancela solo**.
- Permite: aceptar/rechazar, marcar "listo para retirar", subir la carta,
  abrir/cerrar el local y pausar productos.
- Exige avisar los mantenimientos con 24 horas y manejar bien los errores;
  si la integración falla mucho, **la desactivan**.

### Rappi

- También hay que ser **integrador aprobado** (credenciales por cadena de
  locales; vencen cada semana y se renuevan solas).
- **COFFE_APP tiene que ir a preguntar** si hay pedidos nuevos (cada 30
  segundos, como recomiendan). No avisa cuando el cliente cancela: hay que
  ir a mirar.
- **El café tiene 4 minutos para aceptar cada pedido.** Si no, Rappi lo
  cancela, **el local queda apagado en la app** y hay que llamar a Rappi
  para que lo vuelvan a prender. Es el punto más delicado.
- Cada cambio de carta **pasa por aprobación de Rappi** antes de verse.
- No acepta pedidos programados por la integración.

### Uber Eats y otras

- Uber Eats **volvió a Argentina en 2026** (no está claro en qué ciudades).
- Uber anunció en 2026 que compra Delivery Hero, la dueña de PedidosYa;
  **falta la aprobación** de los gobiernos. Por ahora no cambia nada para
  los comercios, pero a futuro la integración de PedidosYa puede cambiar.
- Conclusión: cada app tiene que ser **un "enchufe" separado** dentro de
  COFFE_APP, para poder sumar, cambiar o sacar una sin tocar el resto.

## 4. Cómo se arma por dentro (general para todas las apps)

### Una sola forma de pedido

Todo pedido que viene de una app se convierte en un **pedido normal de
COFFE_APP** (el mismo de mostrador), con estos datos extra:

- de qué app vino y su número de pedido en la app ("PEDIDOSYA #1234");
- hora de retiro del repartidor, nombre del cliente y notas;
- si ya está pagado en la app o se cobra al entregar;
- la comisión de la app (para los reportes).

Así la cocina, el stock, los tickets y los reportes funcionan igual que
siempre, sin código especial por app.

### Las piezas comunes (se hacen una sola vez)

- **Canales del local:** qué apps tiene prendidas cada local, en qué modo,
  la comisión de cada una y sus datos de conexión (guardados cifrados, como
  ya se hace con Mercado Pago).
- **Bandeja de entrada:** todo lo que llega de una app se guarda **primero
  tal cual** y recién después se convierte en comanda. Cada pedido de app se
  guarda una sola vez (por app + número de pedido), así **no hay
  duplicados** aunque la app lo mande dos veces.
- **Vínculo de la carta:** qué producto de la app corresponde a qué
  producto (y opciones) de COFFE_APP.
- **Registro de salud:** cuándo fue la última vez que cada app respondió
  bien, para el semáforo.

### El "enchufe" de cada app

Cada app es una pieza aparte que sabe hacer lo mismo, cada una a su manera:

- recibir o ir a buscar pedidos nuevos;
- aceptar y rechazar;
- avisar "listo para retirar";
- abrir/cerrar el local y pausar productos;
- subir la carta;
- decir si la conexión está bien.

El modo **Manual** también es un enchufe (el que "recibe" es la persona que
carga el pedido). Para probar sin depender de las apps se hace un enchufe
de **"App de prueba"** que simula pedidos, cancelaciones y caídas.

## 5. Reglas para que nunca se pierda un pedido

1. **La tablet de la app es el respaldo.** En PedidosYa se arranca en
   **modo indirecto**: el café acepta en la tablet y COFFE_APP suma la
   comanda automática. Si COFFE_APP falla, el pedido igual está en la
   tablet.
2. **Guardar primero, procesar después.** Al llegar un pedido, COFFE_APP lo
   guarda y contesta "recibido" al instante; armar la comanda viene después.
   Si algo falla en el medio, el pedido queda guardado y se reintenta.
3. **La comanda nunca se traba.** Si un producto no está vinculado, la
   comanda sale igual con el nombre como viene de la app y una marca
   "revisar". No se descuenta stock de lo no vinculado.
4. **Semáforo por app** arriba en la pantalla: verde (anda bien), amarillo
   (hace un rato que no responde), rojo con sonido y el cartel **"Atendé
   PedidosYa desde la tablet"** si se cortó.
5. **Aceptar solo si la cocina lo recibió** (Rappi, o PedidosYa directo):
   COFFE_APP acepta solo cuando confirma que la comanda llegó a la pantalla
   de Cocina o a la impresora. Si no llegó, alarma fuerte para aceptarlo a
   mano antes de los 4 minutos, así no se apaga el local.
6. **Cancelaciones bien visibles:** si el cliente cancela, la comanda se
   tacha en Cocina con aviso sonoro, y el stock vuelve.
7. **Control al final del día:** reporte "pedidos según la app" contra
   "pedidos en COFFE_APP", con los que falten de un lado o del otro.
8. **Si se cae COFFE_APP entero** (el servidor): el semáforo no se puede ver,
   así que el café sigue con las tablets como hoy. Por eso, en modo
   automático, **las tablets siempre quedan prendidas y con sonido**.

## 6. Cómo lo vive el café

### Ponerlo en marcha ("sin quilombo")

En **Negocio → Apps de delivery**, una tarjeta por app (PedidosYa, Rappi,
Uber Eats, Otra app), todas en "Apagado". Al prender una:

1. **Elegir el modo** (Manual o Automático; si la app todavía no está
   aprobada, solo Manual) y la **comisión** que cobra.
2. **Automático:** ingresar el código del local en la app. Los trámites con
   la app los hace Fran desde el panel de superadmin, no el café.
3. **Vincular la carta casi solo:** COFFE_APP propone qué producto de la app
   corresponde a cuál, por nombre y precio. El café confirma o corrige.
4. **Pedido de prueba** para ver que la comanda sale bien.

### Todos los días

- El pedido entra con el cartel de la app ("PEDIDOSYA #1234"), la hora de
  retiro y las notas. Cuando Cocina toca "Listo", se le avisa a la app para
  que mande al repartidor.
- **"Pausar delivery"**: un botón que pausa todas las apps del local juntas
  (para cuando están a full) y las vuelve a abrir.
- Si se agota un producto en el stock de COFFE_APP, se pausa solo en las
  apps (modo automático).
- Al cerrar la caja se puede cerrar el local en las apps.

### La plata

- Lo de las apps **no entra a la caja del mostrador**: queda como "A cobrar
  de PedidosYa / Rappi" (la app le liquida al café después).
- Si el cliente paga en efectivo al repartidor (cuando la app lo permite),
  se registra como corresponde.
- Reportes con **ventas por canal y comisión** de cada app (también en el
  Excel para el contador).

### Lo que ve Fran (superadmin)

- Qué cafés tienen qué apps prendidas y en qué modo.
- El semáforo de todos, para enterarse antes que el café si una app se cae.
- Las credenciales de integrador de COFFE_APP con cada app (una sola vez,
  no por café).

## 7. Fases (en este orden)

### Fase A — Canales y modo Manual (no depende de nadie) ✅ Hecha

- [x] Canales por local: Apagado / Manual por app, más "Otra app" con
  nombre libre, y comisión. → **Administración → Apps de delivery**.
- [x] Botón para cargar un pedido de app en segundos: app, número, hora de
  retiro, productos, notas, pagado o no. → **Pedidos → "¿Vino por una
  app?"**.
- [x] Cartel de la app en Cocina, tickets y pedidos abiertos.
- [x] Caja: lo cobrado por la app va como "App de delivery", que no suma al
  efectivo esperado.
- [x] Reportes y Excel: ventas por canal y comisiones (hoja "Apps de
  delivery").
- [x] Nada nuevo visible si el café no tiene apps prendidas (con test).

**Cómo quedó por dentro** (para las fases que siguen):

- Migración `V19__sales_channels.sql`: tabla `sales_channels` (app, nombre,
  modo, comisión, por local) y columnas `channel_*` en `orders` (app, número
  en la app, cliente, hora de retiro, si lo cobró la app, comisión copiada).
  El número no se puede repetir en la misma app.
- Cobro nuevo `APP` en `order_payments`: se crea solo si "lo cobró la app".
  Va asociado a la caja abierta si hay una (aparece en el cierre, aparte), y
  si no hay caja abierta queda sin caja (`cash_session_id` ahora puede ser
  nulo solo para esto).
- `POST /orders` acepta un bloque `channel` opcional; `GET/POST/PATCH
  /sales-channels` para configurar. El modo `AUTO` existe en la base pero el
  backend todavía no deja elegirlo.
- Tests: `SalesChannelTest` (configuración, roles y otro café; pagado por la
  app sin caja; cobrado por el café; validaciones; Excel con y sin apps).
- **Listo cuando:** un café sin apps ve todo igual que hoy, y uno con
  PedidosYa en manual carga un pedido en menos de 30 segundos y le sale la
  comanda.

### Fase B — Ponerla en internet

Es el ítem pendiente de la hoja de ruta del README (sección 16).
**Obligatoria antes de lo automático:** las apps no pueden mandarle pedidos
a una compu del local.

### Fase C — Base del modo Automático (todavía sin ninguna app real)

- [ ] Bandeja de entrada sin duplicados, con reintentos.
- [ ] Vínculo de la carta con sugerencias automáticas.
- [ ] Semáforo por app con alarma, y "Pausar delivery".
- [ ] Aceptar solo si la cocina lo recibió.
- [ ] Reporte de control diario.
- [ ] Enchufe de "App de prueba" y tests: pedido repetido, producto sin
  vincular, cancelación, caída de la app, caída de internet.
- **Listo cuando:** con la App de prueba se simulan todos los problemas y
  ningún pedido se pierde ni se duplica.

### Fase D — PedidosYa automático

- [ ] Fran completa el formulario de integrador y consigue el entorno de
  pruebas.
- [ ] Enchufe de PedidosYa en **modo indirecto** (el café acepta en la
  tablet).
- [ ] Subir carta, "listo para retirar", abrir/cerrar, pausar productos.
- [ ] Pasar la aprobación de PedidosYa.
- [ ] Probarlo en un café piloto.
- [ ] Recién después, evaluar el modo directo.

### Fase E — Rappi automático

- [ ] Fran tramita las credenciales de integrador.
- [ ] Enchufe de Rappi: preguntar cada 30 segundos, aceptar solo si la
  cocina lo recibió, alarma antes de los 4 minutos.
- [ ] Carta con su aprobación (mostrar "esperando aprobación de Rappi").
- [ ] Prueba en un café piloto.

### Fase F — Otras apps

Uber Eats si llega a Mar del Plata, u otras que pidan los cafés. Con el
diseño de enchufes, cada una es solo su pieza nueva.

### Plan B si los trámites de integrador se traban

Conectarse a través de un intermediario que ya está integrado (por
ejemplo, **Deliverect**, que trabaja con PedidosYa y Rappi). Funciona igual
para el café, pero **cada café le paga un abono** al intermediario: conviene
solo si las apps tardan mucho en aprobar a COFFE_APP.

## 8. Trámites y preguntas para hacerles a las apps

Antes de la Fase D/E, Fran pregunta (y anota acá las respuestas):

- ¿Qué se necesita para ser integrador? (¿empresa con CUIT, monotributo,
  contrato, costo?)
- ¿Cuánto tarda la aprobación y qué pruebas piden?
- PedidosYa: ¿se puede usar el modo indirecto para todos los locales?
- Rappi: si el café acepta en la tablet de Rappi, ¿COFFE_APP igual recibe
  el pedido? (define si se puede tener el mismo respaldo que en PedidosYa)
- ¿Cómo informan las cancelaciones y los cambios de un pedido?
- ¿Mandan el detalle de la liquidación (comisiones) por la integración?

## 9. Decisiones pendientes de Fran

- [ ] ¿Las apps entran en el abono normal o se cobra aparte (por ejemplo,
  un extra por local con apps automáticas)?
- [ ] Rappi: ¿aceptar automático (si la cocina lo recibió) o seguir
  aceptando en la tablet?
- [ ] ¿Qué cafés piloto prueban cada app?

## 10. Riesgos a tener en cuenta

- Las apps pueden **cambiar sus reglas** o desactivar una integración que
  falla mucho → enchufes separados, semáforo y tablets siempre prendidas.
- **Compra de PedidosYa por Uber** (pendiente): puede cambiar su
  integración más adelante.
- Sin servidor en internet, **no hay modo automático** (Fase B).
- Rappi: un descuido de 4 minutos apaga el local → alarma fuerte y aceptar
  solo cuando Cocina lo recibió.

## Fuentes

- [PedidosYa Integración — cómo funciona](https://integrar.pedidosya.com/es/how-it-works/)
- [PedidosYa Integración — documentación técnica](https://integrar.pedidosya.com/es/documentation/)
- [PedidosYa Integración — soporte y alta de integradores](https://integrar.pedidosya.com/es/support/)
- [Rappi Developer Portal — preguntas frecuentes](https://dev-portal.rappi.com/en/frequently-asked-questions/)
- [Deliverect — integración con Rappi](https://help.deliverect.com/en/articles/7979380-rappi-integration-overview)
- [Deliverect — integración con PedidosYa](https://www.deliverect.com/en/integrations/pedidosya)
- [Madi Digital — integración PedidosYa 2026](https://app.madi-digital.com/blog/integracion-pedidosya-restaurante)
- [iProfesional — Uber y la compra de PedidosYa (julio 2026)](https://www.iprofesional.com/negocios/460184-que-cambia-para-usuarios-y-comercios-tras-la-compra-de-pedidosya-por-parte-de-uber.amp)
- [iProfesional — Uber Eats vuelve a Argentina (enero 2026)](https://www.iprofesional.com/negocios/446682-uber-eats-vuelve-a-argentina-para-competir-con-rappi-y-pedidosya-en-el-delivery)
