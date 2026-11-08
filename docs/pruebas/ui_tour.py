"""
Recorrido de TODAS las pantallas con cada rol, en compu, tablet y celular.
Busca: errores de JavaScript, pedidos al servidor que fallan, pantallas que se
salen de costado (scroll horizontal) y redirecciones raras.
"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "resultados")
os.makedirs(OUT_DIR, exist_ok=True)
import asyncio, json, os, random, sys, uuid
import aiohttp
from playwright.async_api import async_playwright

sys.path.insert(0, HERE)
from loadtest import API, PASSWORD, setup_cafe

APP = "http://localhost:4200"
OUT = os.path.join(OUT_DIR, "capturas")
os.makedirs(OUT, exist_ok=True)

ROUTES = ["", "pedidos", "pedidos?tab=open", "kds", "caja", "productos", "opciones", "descuentos", "categorias", "insumos", "mesas",
          "reportes", "users", "locations", "business", "cobros-qr", "apps-delivery", "suscripcion", "cuenta"]
PUBLIC = ["login", "registro", "recuperar", "terminos", "privacidad", "arrepentimiento", "baja"]
VIEWPORTS = {"compu": (1280, 800), "tablet": (820, 1180), "celu": (390, 844)}


async def req(s, m, p, t=None, b=None):
    async with s.request(m, API + p, json=b, headers={"Authorization": f"Bearer {t}"} if t else {}) as r:
        txt = await r.text()
        return r.status, (json.loads(txt) if txt and txt[0] in "[{" else txt)


async def build():
    """Un café de prueba completo: carta con opciones, stock, mesas en el plano, apps, caja, pedidos en todos los estados."""
    async with aiohttp.ClientSession() as s:
        cafe = await setup_cafe(s, 777)
        o = cafe["owner"]
        emails = {}
        for role in ("ADMIN", "SUPERVISOR"):
            e = f"{role.lower()}-{uuid.uuid4().hex[:6]}@test.local"
            await req(s, "POST", "/users", o, {"email": e, "password": PASSWORD, "firstName": role.title(), "lastName": "Prueba", "role": role, "locationId": cafe["loc"]})
            emails[role] = e
        for role in ("WAITER", "KITCHEN"):
            e = f"{role.lower()}-{uuid.uuid4().hex[:6]}@test.local"
            await req(s, "POST", "/users", o, {"email": e, "password": PASSWORD, "firstName": role.title(), "lastName": "Prueba", "role": role, "locationId": cafe["loc"]})
            emails[role] = e
        _, me = await req(s, "GET", "/auth/me", o)
        emails["OWNER"] = me["email"] if isinstance(me, dict) and "email" in me else None
        # Opciones, stock y descuento
        await req(s, "POST", "/option-groups", o, {"name": "Leche", "minSelect": 1, "maxSelect": 1, "options": [
            {"name": "Entera", "priceDelta": 0, "isDefault": True}, {"name": "Almendra", "priceDelta": 600}], "productIds": [cafe["products"][2]]})
        await req(s, "PATCH", f"/stock/products/{cafe['products'][3]}/config", o, {"stockMode": "DIRECT", "minStock": 20})
        await req(s, "POST", f"/stock/products/{cafe['products'][3]}/adjust", o, {"locationId": cafe["loc"], "newQuantity": 12})
        await req(s, "POST", "/stock/ingredients", o, {"name": "Leche", "unit": "ml", "minStock": 2000})
        await req(s, "POST", "/discounts", o, {"name": "Jubilados", "type": "PERCENTAGE", "value": 10})
        # Plano
        _, area = await req(s, "POST", "/floor/areas", o, {"locationId": cafe["loc"], "name": "Salón"})
        tables = [{"id": t, "x": 5 + (i % 6) * 15, "y": 5 + (i // 6) * 18, "width": 10, "height": 10, "shape": "ROUND" if i % 2 else "SQUARE"} for i, t in enumerate(cafe["tables"][:12])]
        await req(s, "PUT", f"/floor/areas/{area['id']}/layout", o, {"tables": tables, "items": [{"kind": "BAR", "label": "Barra", "x": 0, "y": 65, "width": 40, "height": 8}]})
        # Pedidos en todos los estados
        def body(table=None, items=None, channel=None):
            b = {"locationId": cafe["loc"], "idempotencyKey": str(uuid.uuid4()), "clientTimestamp": "2026-10-03T12:00:00Z",
                 "items": items or [{"productId": cafe["products"][0], "quantity": 2}, {"productId": cafe["products"][4], "quantity": 1}]}
            if table: b["tableId"] = table
            if channel: b["channel"] = channel
            return b
        w = cafe["waiters"][0]; k = cafe["kitchen"]
        _, a = await req(s, "POST", "/orders", w, body(cafe["tables"][0]))
        _, b = await req(s, "POST", "/orders", w, body(cafe["tables"][1]))
        await req(s, "PATCH", f"/orders/{b['id']}/status", k, {"status": "IN_PREPARATION"})
        _, c = await req(s, "POST", "/orders", w, body())
        await req(s, "PATCH", f"/orders/{c['id']}/status", k, {"status": "IN_PREPARATION"})
        await req(s, "PATCH", f"/orders/{c['id']}/status", k, {"status": "READY"})
        await req(s, "POST", "/orders", w, body(channel={"channelId": cafe["channel"], "orderRef": "4521", "customerName": "Martina", "paidByApp": True}))
        _, d = await req(s, "POST", "/orders", w, body(cafe["tables"][2]))
        await req(s, "POST", f"/orders/{d['id']}/discounts", o, {"type": "PERCENTAGE", "value": 10, "reason": "Cliente frecuente"})
        await req(s, "POST", f"/orders/{d['id']}/payments", o, {"paymentMethod": "CASH", "amount": 2000})
        for i in range(3):
            _, e = await req(s, "POST", "/orders", w, body())
            for st in ("IN_PREPARATION", "READY"):
                await req(s, "PATCH", f"/orders/{e['id']}/status", k, {"status": st})
            await req(s, "POST", f"/orders/{e['id']}/payments", o, {"paymentMethod": random.choice(["CASH", "DEBIT_CARD"]), "amount": e["total"]})
            await req(s, "PATCH", f"/orders/{e['id']}/status", o, {"status": "DELIVERED"})
        return emails


async def login(pg, email, password=PASSWORD):
    await pg.goto(f"{APP}/login")
    await pg.fill("#email", email)
    await pg.fill("#password", password)
    await pg.click("button[type=submit]")
    await pg.wait_for_timeout(2000)
    try:
        uid = await pg.evaluate("JSON.parse(localStorage.getItem('cofe_app_session_v1')).user.id")
        await pg.evaluate(f"localStorage.setItem('cofe_app_tour_done_{uid}','1')")
    except Exception:
        pass


async def visit(pg, url, tag, problems, shot=None):
    errs, bad = [], []
    def on_console(m):
        if m.type == "error" and "favicon" not in m.text:
            errs.append(m.text[:200])
    def on_resp(r):
        if r.status >= 500 or (r.status >= 400 and r.status not in (401, 403, 404, 409) and "/api/v1/" in r.url):
            bad.append(f"{r.status} {r.request.method} {r.url.split('/api/v1')[-1][:80]}")
    pg.on("console", on_console)
    pg.on("pageerror", lambda e: errs.append("JS: " + str(e)[:200]))
    pg.on("response", on_resp)
    await pg.goto(url)
    await pg.wait_for_timeout(1800)
    overflow = await pg.evaluate("document.documentElement.scrollWidth - window.innerWidth")
    final = pg.url.replace(APP, "")
    if shot:
        await pg.screenshot(path=shot)
    pg.remove_listener("console", on_console)
    pg.remove_listener("response", on_resp)
    # Los 4xx esperables del navegador ("Failed to load resource") se cuentan aparte.
    real_errs = [e for e in errs if "Failed to load resource" not in e]
    if real_errs:
        problems.append(f"{tag}: errores de JS → {real_errs[:2]}")
    if bad:
        problems.append(f"{tag}: pedidos al servidor que fallaron → {bad[:3]}")
    if overflow > 2:
        problems.append(f"{tag}: la pantalla se sale {overflow}px de costado")
    return final


async def main():
    emails = await build()
    problems, visited = [], 0
    async with async_playwright() as p:
        b = await p.chromium.launch()
        # Pantallas públicas
        for vp, (w, h) in VIEWPORTS.items():
            pg = await b.new_page(viewport={"width": w, "height": h})
            for r in PUBLIC:
                await visit(pg, f"{APP}/{r}", f"[público/{vp}] /{r}", problems, f"{OUT}/publico-{r}-{vp}.png" if vp == "celu" else None)
                visited += 1
            await pg.close()
        # Cada rol del café
        redirects = {}
        for role in ("OWNER", "ADMIN", "SUPERVISOR", "WAITER", "KITCHEN"):
            for vp, (w, h) in VIEWPORTS.items():
                ctx = await b.new_context(viewport={"width": w, "height": h}, is_mobile=(vp == "celu"), has_touch=(vp != "compu"))
                pg = await ctx.new_page()
                await login(pg, emails[role])
                for r in ROUTES:
                    name = r.replace("?", "_").replace("=", "-") or "inicio"
                    shot = f"{OUT}/{role.lower()}-{name}-{vp}.png" if (vp == "celu" or role == "OWNER") else None
                    final = await visit(pg, f"{APP}/{r}", f"[{role}/{vp}] /{r}", problems, shot)
                    visited += 1
                    if vp == "compu":
                        redirects[(role, r)] = final
                await ctx.close()
        # Superadmin
        for vp, (w, h) in VIEWPORTS.items():
            ctx = await b.new_context(viewport={"width": w, "height": h})
            pg = await ctx.new_page()
            await login(pg, "fran@coffeapp.test", "clave-superadmin-1")
            await visit(pg, f"{APP}/superadmin", f"[SUPERADMIN/{vp}] /superadmin", problems, f"{OUT}/superadmin-{vp}.png")
            final = await visit(pg, f"{APP}/pedidos", f"[SUPERADMIN/{vp}] /pedidos", problems)
            if vp == "compu":
                redirects[("SUPERADMIN", "pedidos")] = final
            visited += 2
            await ctx.close()
        await b.close()
    json.dump({"problems": problems, "redirects": {f"{k[0]} {k[1]}": v for k, v in redirects.items()}}, open(os.path.join(OUT_DIR, "ui-result.json"), "w"), indent=1, ensure_ascii=False)
    print(f"pantallas visitadas: {visited}")
    print("adónde terminó cada rol (compu):")
    for (role, r), final in redirects.items():
        if final.split("?")[0] != "/" + r.split("?")[0]:
            print(f"  {role:10} /{r:18} → {final}")
    print(f"\nproblemas: {len(problems)}")
    for x in problems:
        print(" -", x)


asyncio.run(main())
