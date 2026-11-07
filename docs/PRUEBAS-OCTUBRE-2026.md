# Prueba exhaustiva antes de salir a internet (octubre de 2026)

> **Para qué:** confirmar que COFFE_APP aguanta muchos cafés trabajando a la
> vez, que no tiene errores en ninguna pantalla y que un café nunca puede ver
> ni tocar los datos de otro, antes de subirla a un servidor para que la
> prueben los primeros clientes.
>
> **Resultado:** aguanta **4 veces** la meta del primer año sin errores. Se
> encontraron y arreglaron **9 problemas reales** (el más grave: se podía
> cobrar dos veces el mismo pedido). Quedaron **10 tests nuevos** en el backend
> para que no vuelvan a aparecer.

## 1. Cómo se probó

- Una copia idéntica de COFFE_APP (backend, base de datos MySQL y la app),
  con una base de datos aparte solo para la prueba.
- **Un simulador de hora pico**: cada café simulado tiene 3 mozos, cocina,
  caja y dueño, usando la app como lo haría la gente de verdad:
  - los mozos toman pedidos (55% en mesa, 30% para llevar, 15% de apps de
    delivery) y a veces le suman cosas a una mesa;
  - la cocina recibe los pedidos **en tiempo real** y los pasa a "En
    preparación" y "Listo";
  - la caja cobra (efectivo, débito o transferencia) y entrega;
  - todas las pantallas consultan los pedidos abiertos cada 20 segundos,
    como hace la app, y el dueño mira Inicio cada minuto.
- **La meta acordada:** 50 cafés a la vez, un pedido cada 15 segundos por
  café en hora pico. También se probó el doble, el cuádruple y hasta que se
  pusiera lento.
- **Ojo con los números:** todo corrió en una máquina chica de **2
  núcleos**, compartida entre el servidor, la base de datos y el propio
  simulador. Un servidor de verdad con la misma potencia va a andar igual o
  mejor.

## 2. Aguante (carga)

| Prueba | Pedidos | Errores | Respuesta típica | El 95% respondió en menos de |
|---|---|---|---|---|
| **50 cafés** (la meta) | 964 en 5 min | 0 | 0,02 s | 0,08 s |
| **100 cafés** (el doble), antes de mejorar | 1.740 en 5 min | 0 | 0,06 s | 0,23 s |
| **100 cafés**, después de mejorar | 1.951 en 5 min | 0 | 0,01 s | 0,05 s |
| **200 cafés** (4 veces la meta, 800 dispositivos conectados) | 2.283 en 3 min | 0 | 0,01 s | 0,08 s |
| 100 cafés con un pedido cada 5 s (3 veces más pedidos) | 2.854 en 2,5 min | 0 | 0,02 s | 0,10 s |
| 100 cafés con un pedido cada 2 s (7,5 veces más) | 6.361 en 2,5 min | 0 | 0,1 s | 1–1,8 s ⚠️ |

- **Cocina en tiempo real:** desde que el mozo toca "Enviar" hasta que el
  pedido aparece en la pantalla de cocina, **0,02 segundos** (el 95%, en menos
  de 0,05 s) con 50 y 100 cafés.
- **Dónde se pone lento:** recién con 100 cafés haciendo un pedido cada 2
  segundos (unos 3.000 pedidos por minuto, 7,5 veces la meta). Ahí la
  máquina de prueba estaba al 100% y las respuestas tardaban hasta 1–2
  segundos, pero **no se perdió ni falló ningún pedido**.
- **Mejora hecha:** la lista de "Pedidos abiertos" (la que piden todos los
  mozos y la cocina cada 20 s) consultaba la base varias veces por cada
  pedido. Ahora la arma con 6 consultas en total: con 100 cafés pasó de
  0,23 s a 0,03 s.
- **Uso de memoria:** el servidor usó unos 650 MB y la base de datos unos
  600 MB con 200 cafés.

**Prueba de resistencia (20 minutos sin parar, 50 cafés):** ver el punto 7.

### Qué servidor alcanza

Para el primer año (hasta 100–200 cafés) alcanza un servidor de **2 núcleos
y 4 GB de memoria** con la base de datos adentro, o 2 núcleos / 2 GB más una
base de datos administrada aparte. Recién con varios cientos de cafés hay que
pensar en algo más grande.

## 3. Cosas que pasan a la vez (16 casos)

En un café de verdad, dos personas tocan cosas al mismo tiempo. Se probó
cada caso largando las acciones exactamente juntas.

| Caso | Antes | Ahora |
|---|---|---|
| Dos cobros a la vez del total del mismo pedido (doble clic en Cobrar o dos cajeros) | ❌ **Se cobraba dos veces** | ✅ Se cobra una vez; el segundo avisa que ya está cobrado |
| Cuatro mozos eligen la misma mesa a la vez | ❌ Quedaban dos pedidos en la misma mesa | ✅ Uno solo; los otros ven "esa mesa ya tiene un pedido" |
| Doble clic en "Enviar a cocina" | ⚠️ Se creaba un pedido, pero los otros clics daban error (sin internet, la cola lo mostraba como "no se pudo enviar") | ✅ Todos reciben el mismo pedido, sin error |
| Seis mozos le suman productos al mismo pedido a la vez | ❌ El total quedaba mal (menos de lo que sumaban los productos) | ✅ El total siempre es la suma exacta |
| 20 ventas a la vez del mismo producto con stock | ❌ El stock descontaba de menos (quedaba más de lo real) | ✅ Descuenta exacto |
| 30 ventas a la vez con dos productos con stock, en distinto orden | (el primer arreglo del stock hacía que algunas fallaran; se corrigió antes de terminar) | ✅ Todas bien, stock exacto |
| Cancelar 10 pedidos a la vez | ✅ | ✅ El stock vuelve exacto |
| Cancelar, empezar a preparar y cobrar el mismo pedido a la vez | ❌ Podía quedar **cancelado y cobrado** | ✅ Nunca |
| Tres descuentos "a todo el pedido" a la vez | ❌ Quedaban los tres guardados | ✅ Queda uno solo |
| Mismo pedido de PedidosYa cargado por 4 personas | ✅ | ✅ Uno solo |
| Abrir la caja 3 veces a la vez | ✅ | ✅ Una sola caja |
| Cerrar la caja justo mientras se cobra | ✅ | ✅ El cierre cuadra |
| Registrar el mismo email 3 veces a la vez | ✅ | ✅ Una cuenta |
| Crear el mismo mozo / la misma mesa 3 veces a la vez | ✅ | ✅ Uno solo |
| Ningún "error interno" en ningún caso | ❌ | ✅ |

**Un cambio de regla que tenés que conocer:** un pedido que **ya tiene
cobros** ya no se puede cancelar (antes se podía, y la plata quedaba en la
caja de un pedido anulado y descuadraba el cierre). Si hay que devolver la
plata, se registra como egreso en Caja. Los pedidos de app cobrados por la
app sí se pueden cancelar (esa plata no está en la caja). Si preferís otra
regla, se cambia.

## 4. Seguridad

Un test nuevo le pide al propio servidor la lista de **todas sus
direcciones (110)** y las ataca una por una. Si mañana se agrega una
dirección nueva, queda cubierta sola.

| Prueba | Resultado |
|---|---|
| Entrar sin iniciar sesión | ✅ Ninguna dirección privada deja entrar |
| Cada rol (dueño, administrador, supervisor, mozo, cocina) contra cada dirección | ✅ Nadie hace algo que su rol no permite |
| El superadmin contra las pantallas de un café, y un café contra el panel de superadmin | ✅ Separados |
| Desde un café, con los ids y el local de **otro café**, en todas las direcciones | ✅ Nunca entra, nunca ve un dato del otro, y después de todo el ataque los datos del otro café **quedaron idénticos** |
| Datos basura: textos de 5.000 letras, números negativos enormes, JSON roto | ✅ Siempre un aviso claro, nunca un error interno |
| Sesión falsificada: firma cambiada, café o rol cambiados a mano, sin firma, vencida | ✅ Todas rechazadas |
| Adivinar contraseñas probando muchas | ✅ Se frena solo después de varios intentos |

**Encontrado y arreglado:** 12 direcciones respondían "Ocurrió un error
interno" cuando faltaba un dato obligatorio (por ejemplo, el local en
Reportes o en Inicio). No era un riesgo, pero confundía y ensuciaba el
registro de errores. Ahora responden un aviso claro ("Falta el dato
locationId").

## 5. Todas las pantallas, en compu, tablet y celular

Un navegador automático entró **312 veces** a las pantallas: las públicas
(login, registro, términos, privacidad, arrepentimiento, baja), las 19
pantallas del café con cada uno de los 5 roles, y el panel de superadmin,
en compu, tablet y celular, con un café de prueba completo (carta con
opciones, stock bajo, plano, apps de delivery, caja abierta y pedidos en
todos los estados).

- **Errores de la app:** 0. **Pedidos al servidor que fallaron:** 0.
- Cada rol termina donde corresponde (por ejemplo, el mozo que intenta abrir
  Reportes vuelve a Inicio).
- **Encontrado y arreglado:** en el celular, "Mi cuenta" era más ancha que la
  pantalla y se cortaba a la derecha. La causa estaba repetida en 11
  pantallas más que podían cortarse con textos largos: se arreglaron todas.
  Después se revisaron los elementos de las 19 pantallas en el celular: nada
  se sale.

## 6. Lo que se cambió

- **Bloqueo por pedido:** cobrar, sumar productos, descuentos y cambios de
  estado sobre el mismo pedido se hacen de a uno.
- **Base de datos en "READ COMMITTED"** (el modo de casi todas las bases):
  después de esperar su turno, cada operación ve lo último que se guardó.
- **Stock:** cada fila se bloquea mientras se actualiza, siempre en el mismo
  orden (así dos ventas no se traban entre sí).
- **Doble envío del mismo pedido:** devuelve el pedido ya creado.
- **No se cancela un pedido cobrado** (ver el punto 3).
- **Avisos claros** en vez de "error interno" para pedidos mal armados.
- **"Pedidos abiertos" más rápido** (6 consultas en vez de varias por pedido).
- **Pantallas del celular** que se podían cortar.
- **10 tests nuevos en el backend:** `ConcurrencyTest` (7 casos de cosas a
  la vez) y `EndpointSweepTest` (3: roles y sesión, otro café, datos basura,
  sobre las 110 direcciones). Los scripts del simulador de carga quedan en
  `docs/pruebas/` para repetir la prueba antes de cada cambio grande.

## 7. Prueba de resistencia

20 minutos seguidos con 50 cafés trabajando a ritmo normal (lo que pasa en
un sábado al mediodía, pero sin pausa). La idea es ver si algo se "gasta"
con el tiempo: memoria que crece, respuestas que se ponen lentas, errores
que aparecen recién al rato.

| Qué | Resultado |
|---|---|
| Pedidos al servidor | 29.446 (24,5 por segundo) |
| Pedidos de café creados | 3.942 (1.011 llegaron a "entregado") |
| Errores | **0** (solo 32 avisos esperables de "ya estaba listo") |
| Respuesta más lenta habitual (p95) | 29 ms o menos en todas las funciones |
| Comanda que tarda en aparecer en cocina | mitad en 14 ms, casi todas en 25 ms |
| Memoria del backend | 408 → 427 → 431 → 431 → 431 MB: **se estabiliza, no crece** |
| Memoria de la base de datos | 460 → 525 MB, subiendo cada vez menos (es su caché) |
| Uso del procesador | backend 16 % en promedio, base de datos 14 % |

**Conclusión:** no hay pérdidas de memoria ni se pone lento con el tiempo.

## 8. Lo que no se pudo probar desde acá

| Qué | Por qué | Cuándo |
|---|---|---|
| Cobros con QR y la suscripción contra **Mercado Pago real** | Se probó con un Mercado Pago simulado | Con credenciales **de prueba** de Mercado Pago, antes de salir |
| Mails reales ("Olvidé mi contraseña", avisos de baja y de ayuda) | No hay servidor de correo acá | Al configurar el servidor |
| La **impresora térmica**, celulares reales y el Wi-Fi de un café | Necesita el mundo real | Lista de abajo |
| Copias de seguridad de la base y recuperación | Es parte de "Ponerla en internet" | En esa fase |

Dos riesgos chicos que quedaron anotados (no se arreglaron porque casi no
pasan):

- **Dos QR a la vez para el mismo pedido:** si dos personas abren "Cobrar con
  QR" exactamente en el mismo instante, se podrían generar dos QR. El
  cliente paga uno solo y el segundo vence solo.
- **Fotos de productos:** el servidor confía en el tipo de archivo que dice
  el navegador. Solo acepta JPG, PNG y WebP, hasta 5 MB, y las guarda como
  imagen, así que el riesgo es bajo.

## 9. Lista para probar vos (15 minutos)

Con la app abierta en la compu del café, un celular y la impresora:

1. **Impresora:** cargá un pedido y tocá imprimir comanda y ticket. ¿Sale
   bien el ancho, las tildes y el $?
2. **Dos celulares a la vez:** desde los dos, tocá "Cobrar" en el mismo
   pedido al mismo tiempo. Uno tiene que cobrar y el otro avisar que ya
   está cobrado.
3. **Tiempo real:** con Cocina abierta en la compu, cargá un pedido desde el
   celular. Tiene que aparecer en cocina en menos de un segundo.
4. **Sin Wi-Fi:** en el celular, activá el modo avión, cargá un pedido,
   desactivá el modo avión. El pedido tiene que llegar solo a cocina.
5. **Celular chico:** recorré Pedidos, el plano, Caja y "Mi cuenta". Nada
   tendría que cortarse a los costados.
6. **Botón Soporte:** tocalo y probá "Escribir por WhatsApp" (con tu número
   cargado en el panel).

Si algo de esto falla, mandame una captura y lo vemos.
