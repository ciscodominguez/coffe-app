"""Casos simultáneos: cosas que pasan a la vez en un café de verdad."""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "resultados")
os.makedirs(OUT_DIR, exist_ok=True)
import asyncio, json, random, uuid, sys
import aiohttp
sys.path.insert(0, HERE)
from loadtest import API, PASSWORD, setup_cafe

results = []
internal = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("OK   " if ok else "FALLA") + f" {name}" + (f" — {detail}" if detail else ""), flush=True)


async def req(s, method, path, token=None, body=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    async with s.request(method, API + path, json=body, headers=headers) as r:
        t = await r.text()
        if r.status >= 500:
            internal.append(f"{method} {path} -> {r.status}")
        try:
            return r.status, json.loads(t) if t else None
        except Exception:
            return r.status, t


def order_body(cafe, table=None, items=None, key=None, channel=None):
    b = {"locationId": cafe["loc"], "idempotencyKey": key or str(uuid.uuid4()), "clientTimestamp": "2026-10-03T12:00:00Z",
         "items": items or [{"productId": cafe["products"][0], "quantity": 1}]}
    if table:
        b["tableId"] = table
    if channel:
        b["channel"] = channel
    return b


async def main():
    async with aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=60)) as s:
        cafe = await setup_cafe(s, 9001)
        owner, w = cafe["owner"], cafe["waiters"]

        # 1. Dos (o más) mozos eligen la misma mesa al mismo tiempo.
        fails = 0
        for t in cafe["tables"][:8]:
            rs = await asyncio.gather(*(req(s, "POST", "/orders", w[i % 3], order_body(cafe, table=t)) for i in range(4)))
            codes = sorted(c for c, _ in rs)
            if codes.count(201) != 1 or any(c not in (201, 409) for c in codes):
                fails += 1
        check("Misma mesa, 4 mozos a la vez (x8 mesas): un solo pedido, el resto 'mesa ocupada'", fails == 0, f"{fails} mesas mal")

        # 2. Doble clic en "Enviar": la misma clave 5 veces a la vez.
        key = str(uuid.uuid4())
        rs = await asyncio.gather(*(req(s, "POST", "/orders", w[0], order_body(cafe, key=key)) for _ in range(5)))
        ids = {b["id"] for c, b in rs if c == 201 and isinstance(b, dict)}
        check("Doble clic en Enviar (5 a la vez, misma clave): un solo pedido", len(ids) == 1 and all(c == 201 for c, _ in rs), f"códigos {[c for c, _ in rs]}, pedidos {len(ids)}")

        # 3. Dos cobros por el total al mismo tiempo (dos cajeros, o doble clic en Cobrar).
        bad = []
        for _ in range(6):
            _, o = await req(s, "POST", "/orders", w[0], order_body(cafe))
            amount = o["total"]
            rs = await asyncio.gather(*(req(s, "POST", f"/orders/{o['id']}/payments", owner, {"paymentMethod": "CASH", "amount": amount}) for _ in range(2)))
            _, after = await req(s, "GET", f"/orders/{o['id']}", owner)
            if float(after["paidTotal"]) > float(after["total"]) + 0.001:
                bad.append(f"cobrado {after['paidTotal']} de {after['total']}")
        check("Cobrar dos veces a la vez el mismo pedido: nunca se cobra de más", not bad, "; ".join(bad[:3]))

        # 4. Varios mozos agregan productos al mismo pedido a la vez: el total tiene que coincidir con los ítems.
        bad = []
        for _ in range(4):
            _, o = await req(s, "POST", "/orders", w[0], order_body(cafe))
            await asyncio.gather(*(req(s, "POST", f"/orders/{o['id']}/items", w[i % 3], {"items": [{"productId": cafe["products"][1], "quantity": 1}]}) for i in range(6)))
            _, after = await req(s, "GET", f"/orders/{o['id']}", owner)
            items_total = sum(float(i["subtotal"]) for i in after["items"])
            if abs(items_total - float(after["subtotal"])) > 0.01 or len(after["items"]) != 7:
                bad.append(f"ítems {len(after['items'])} suman {items_total} pero el pedido dice {after['subtotal']}")
        check("6 mozos agregan al mismo pedido a la vez: total = suma de los productos", not bad, "; ".join(bad[:3]))

        # 5. Pedido repetido de app cargado a la vez por dos personas.
        ref = uuid.uuid4().hex[:6]
        ch = {"channelId": cafe["channel"], "orderRef": ref, "paidByApp": True}
        rs = await asyncio.gather(*(req(s, "POST", "/orders", w[i % 3], order_body(cafe, channel=ch)) for i in range(4)))
        codes = sorted(c for c, _ in rs)
        check("Mismo pedido de PedidosYa cargado por 4 personas a la vez: uno solo, sin errores internos", codes.count(201) == 1 and all(c in (201, 409) for c in codes), f"códigos {codes}")

        # 6. Abrir la caja dos veces a la vez (ya hay una abierta: probamos cerrar y abrir 3 a la vez).
        _, cur = await req(s, "GET", f"/cash-sessions/current?locationId={cafe['loc']}", owner)
        await req(s, "POST", f"/cash-sessions/{cur['id']}/close", owner, {"countedCashAmount": 0})
        rs = await asyncio.gather(*(req(s, "POST", "/cash-sessions/open", owner, {"locationId": cafe["loc"], "openingAmount": 1000}) for _ in range(3)))
        codes = sorted(c for c, _ in rs)
        check("Abrir la caja 3 veces a la vez: una sola caja abierta, sin errores internos", codes.count(201) == 1 and all(c in (201, 409) for c in codes), f"códigos {codes}")

        # 7. Cerrar la caja mientras se cobra.
        _, cur = await req(s, "GET", f"/cash-sessions/current?locationId={cafe['loc']}", owner)
        _, o = await req(s, "POST", "/orders", w[0], order_body(cafe))
        rs = await asyncio.gather(req(s, "POST", f"/orders/{o['id']}/payments", owner, {"paymentMethod": "CASH", "amount": o["total"]}),
                                  req(s, "POST", f"/cash-sessions/{cur['id']}/close", owner, {"countedCashAmount": 1000}))
        codes = [c for c, _ in rs]
        _, closed = await req(s, "GET", f"/cash-sessions/{cur['id']}", owner)
        exp = float(closed.get("expectedCashAmount") or 0)
        paid_in_session = codes[0] == 201
        consistent = (paid_in_session and abs(exp - (1000 + float(o["total"]))) < 0.01) or (not paid_in_session and abs(exp - 1000) < 0.01)
        check("Cerrar la caja justo mientras se cobra: el cierre cuadra con lo cobrado", all(c < 500 for c in codes) and consistent, f"códigos {codes}, esperado {exp}")
        await req(s, "POST", "/cash-sessions/open", owner, {"locationId": cafe["loc"], "openingAmount": 0})

        # 8. Stock: 20 pedidos a la vez del mismo producto con stock 10.
        pid = cafe["products"][2]
        c1, b1 = await req(s, "PATCH", f"/stock/products/{pid}/config", owner, {"stockMode": "DIRECT", "minStock": 0})
        c2, b2 = await req(s, "POST", f"/stock/products/{pid}/adjust", owner, {"locationId": cafe["loc"], "newQuantity": 10, "reason": "prueba"})
        rs = await asyncio.gather(*(req(s, "POST", "/orders", w[i % 3], order_body(cafe, items=[{"productId": pid, "quantity": 1}])) for i in range(20)))
        created = sum(1 for c, _ in rs if c == 201)
        _, levels = await req(s, "GET", f"/stock/products/levels?locationId={cafe['loc']}", owner)
        lvl = next((l for l in (levels or []) if l.get("productId") == pid), None)
        qty = float(lvl["currentStock"]) if lvl else None
        check("Stock: 20 pedidos a la vez del mismo producto (había 10): se venden todos y el stock descuenta exacto", created == 20 and qty is not None and abs(qty - (10 - created)) < 0.001,
              f"config {c1}/{c2}, pedidos creados {created}, stock final {qty} (debería ser {10 - created})")

        # 9. Mismo email registrado dos veces a la vez.
        email = f"doble-{uuid.uuid4().hex[:6]}@test.local"
        def reg(tax):
            return {"legalName": "Doble", "taxId": tax, "fiscalCondition": "MONOTRIBUTO", "ownerEmail": email, "ownerPassword": PASSWORD,
                    "ownerFirstName": "A", "ownerLastName": "B", "acceptTerms": True}
        rs = await asyncio.gather(*(req(s, "POST", "/auth/register-business", None, reg("30" + str(random.randint(100000000, 999999999)))) for _ in range(3)))
        codes = sorted(c for c, _ in rs)
        check("Registrar el mismo email 3 veces a la vez: una cuenta, sin errores internos", codes.count(201) == 1 and all(c in (201, 409) for c in codes), f"códigos {codes}")

        # 10. Mismo usuario del equipo creado dos veces a la vez.
        email = f"mozo-doble-{uuid.uuid4().hex[:6]}@test.local"
        body = {"email": email, "password": PASSWORD, "firstName": "M", "lastName": "D", "role": "WAITER", "locationId": cafe["loc"]}
        rs = await asyncio.gather(*(req(s, "POST", "/users", owner, body) for _ in range(3)))
        codes = sorted(c for c, _ in rs)
        check("Crear el mismo mozo 3 veces a la vez: uno solo, sin errores internos", codes.count(201) == 1 and all(c in (201, 409) for c in codes), f"códigos {codes}")

        # 11. Misma mesa creada dos veces a la vez.
        rs = await asyncio.gather(*(req(s, "POST", "/tables", owner, {"locationId": cafe["loc"], "number": 99}) for _ in range(3)))
        codes = sorted(c for c, _ in rs)
        check("Crear la mesa 99 tres veces a la vez: una sola, sin errores internos", codes.count(201) == 1 and all(c in (201, 409) for c in codes), f"códigos {codes}")

        # 12. Cocina y mozo cambian el estado a la vez / cancelar mientras se cobra.
        _, o = await req(s, "POST", "/orders", w[0], order_body(cafe))
        rs = await asyncio.gather(req(s, "PATCH", f"/orders/{o['id']}/status", cafe["kitchen"], {"status": "IN_PREPARATION"}),
                                  req(s, "PATCH", f"/orders/{o['id']}/status", owner, {"status": "CANCELLED"}),
                                  req(s, "POST", f"/orders/{o['id']}/payments", owner, {"paymentMethod": "CASH", "amount": o["total"]}))
        _, after = await req(s, "GET", f"/orders/{o['id']}", owner)
        ok = all(c < 500 for c, _ in rs) and not (after["status"] == "CANCELLED" and float(after["paidTotal"]) > 0)
        check("Cancelar, empezar a preparar y cobrar el mismo pedido a la vez: nunca queda cancelado y cobrado", ok,
              f"códigos {[c for c, _ in rs]}, estado {after['status']}, cobrado {after['paidTotal']}")

        # 13. Dos descuentos a la vez sobre todo el pedido.
        _, o = await req(s, "POST", "/orders", w[0], order_body(cafe, items=[{"productId": cafe["products"][4], "quantity": 2}]))
        rs = await asyncio.gather(*(req(s, "POST", f"/orders/{o['id']}/discounts", owner, {"type": "PERCENTAGE", "value": 10, "reason": "prueba"}) for _ in range(3)))
        _, after = await req(s, "GET", f"/orders/{o['id']}", owner)
        whole = [d for d in after.get("discounts", []) if not d.get("orderItemId")]
        check("3 descuentos 'a todo el pedido' a la vez: queda uno solo y el total es correcto",
              len(whole) == 1 and abs(float(after["total"]) - float(after["subtotal"]) * 0.9) < 0.02 and all(c < 500 for c, _ in rs),
              f"códigos {[c for c, _ in rs]}, descuentos {len(whole)}, subtotal {after['subtotal']} total {after['total']}")

        # 14. Pedidos con dos productos con stock, cargados en orden inverso a la vez (no se tienen que trabar).
        p1, p2 = cafe["products"][3], cafe["products"][5]
        for p in (p1, p2):
            await req(s, "PATCH", f"/stock/products/{p}/config", owner, {"stockMode": "DIRECT", "minStock": 0})
            await req(s, "POST", f"/stock/products/{p}/adjust", owner, {"locationId": cafe["loc"], "newQuantity": 100, "reason": "prueba"})
        bodies = [order_body(cafe, items=[{"productId": a, "quantity": 1}, {"productId": b, "quantity": 1}]) for a, b in [(p1, p2), (p2, p1)] * 15]
        rs = await asyncio.gather(*(req(s, "POST", "/orders", w[i % 3], b) for i, b in enumerate(bodies)))
        created = sum(1 for c, _ in rs if c == 201)
        _, levels = await req(s, "GET", f"/stock/products/levels?locationId={cafe['loc']}", owner)
        q = {l["productId"]: float(l["currentStock"]) for l in levels}
        check("30 pedidos a la vez con dos productos con stock en distinto orden: ninguno falla y el stock cuadra",
              created == 30 and q.get(p1) == 70 and q.get(p2) == 70, f"creados {created}, stock {q.get(p1)} / {q.get(p2)}")

        # 15. Cancelar a la vez 10 pedidos que descontaron stock: vuelve todo.
        rs = await asyncio.gather(*(req(s, "PATCH", f"/orders/{b['id']}/status", owner, {"status": "CANCELLED"}) for c, b in rs[:10] if c == 201))
        _, levels = await req(s, "GET", f"/stock/products/levels?locationId={cafe['loc']}", owner)
        q = {l["productId"]: float(l["currentStock"]) for l in levels}
        check("Cancelar 10 pedidos a la vez: el stock vuelve exacto", all(c == 200 for c, _ in rs) and q.get(p1) == 80 and q.get(p2) == 80, f"stock {q.get(p1)} / {q.get(p2)}")

    check("Ningún error interno del servidor en todos los casos", not internal, "; ".join(internal[:5]))
    bad = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(bad)} de {len(results)} casos bien")
    json.dump(results, open(os.path.join(OUT_DIR, "race-result.json"), "w"), ensure_ascii=False, indent=1)


asyncio.run(main())
