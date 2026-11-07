"""
Simulador de hora pico para COFFE_APP.

N cafés, cada uno con: 3 mozos (cargan pedidos, consultan pedidos abiertos
cada 20 s, tiempo real), cocina (tiempo real + consulta cada 20 s, pasa los
pedidos a "En preparación" y "Listo"), caja (cobra y entrega) y dueño (panel
de Inicio cada 60 s). Un pedido nuevo cada PERIOD segundos por café.

Uso: python3 loadtest.py --cafes 50 --period 15 --duration 300 [--setup-only] [--reuse state.json]
"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "resultados")
os.makedirs(OUT_DIR, exist_ok=True)
import argparse, asyncio, json, random, statistics, time, uuid, sys, os
from collections import defaultdict
import aiohttp

API = os.environ.get("API", "http://localhost:3000/api/v1")
WS = API.replace("http", "ws", 1)
PASSWORD = "clave-de-prueba-1"

# ---------------------------------------------------------------- métricas
lat = defaultdict(list)          # label -> [ms]
errors = defaultdict(int)        # (label, status) -> n
expected = defaultdict(int)      # (label, status) -> n  (409 esperados, p.ej. mesa ocupada)
error_samples = []
ws_delay = []                    # ms desde la respuesta del POST hasta el evento en cocina
counters = defaultdict(int)


async def call(s, method, path, token=None, body=None, label=None, ok=(200, 201, 204), soft=()):
    label = label or f"{method} {path.split('?')[0]}"
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    t0 = time.perf_counter()
    try:
        async with s.request(method, API + path, json=body, headers=headers) as r:
            text = await r.text()
            ms = (time.perf_counter() - t0) * 1000
            lat[label].append(ms)
            if r.status in ok:
                return r.status, (json.loads(text) if text else None)
            if r.status in soft:
                expected[(label, r.status)] += 1
                return r.status, (json.loads(text) if text else None)
            errors[(label, r.status)] += 1
            if len(error_samples) < 60:
                error_samples.append(f"{label} -> {r.status}: {text[:200]}")
            return r.status, None
    except Exception as e:  # timeouts, conexión cortada
        ms = (time.perf_counter() - t0) * 1000
        lat[label].append(ms)
        errors[(label, type(e).__name__)] += 1
        if len(error_samples) < 60:
            error_samples.append(f"{label} -> {type(e).__name__}: {e}")
        return 0, None


def pct(values, p):
    if not values:
        return 0
    v = sorted(values)
    return v[min(len(v) - 1, int(round(p / 100 * (len(v) - 1))))]


# ---------------------------------------------------------------- armado
async def setup_cafe(s, i):
    suffix = uuid.uuid4().hex[:8]
    email = f"carga-{suffix}@test.local"
    body = {
        "legalName": f"Café de carga {i}", "tradeName": f"Café {i}", "taxId": "30" + "".join(random.choice("0123456789") for _ in range(9)),
        "fiscalCondition": "MONOTRIBUTO", "ownerEmail": email, "ownerPassword": PASSWORD,
        "ownerFirstName": "Dueño", "ownerLastName": str(i), "acceptTerms": True,
    }
    st, auth = await call(s, "POST", "/auth/register-business", body=body, label="SETUP register", ok=(201,))
    if not auth:
        raise RuntimeError(f"no se pudo registrar el café {i}")
    owner = auth["accessToken"]
    _, locs = await call(s, "GET", "/locations", owner, label="SETUP locations")
    loc = locs[0]["id"]
    staff = {}
    emails = {"owner": email, "WAITER": [], "KITCHEN": []}
    for role, n in (("WAITER", 3), ("KITCHEN", 1)):
        for k in range(n):
            se = f"{role.lower()}{k}-{suffix}@test.local"
            emails[role].append(se)
            await call(s, "POST", "/users", owner, {"email": se, "password": PASSWORD, "firstName": role, "lastName": str(k), "role": role, "locationId": loc}, label="SETUP user", ok=(201,))
            _, a = await call(s, "POST", "/auth/login", body={"email": se, "password": PASSWORD}, label="SETUP login")
            staff.setdefault(role, []).append(a["accessToken"])
    _, cat = await call(s, "POST", "/categories", owner, {"name": "Cafetería"}, label="SETUP category", ok=(201,))
    products = []
    for name, price in (("Café", 2500), ("Cortado", 2800), ("Latte", 3500), ("Medialuna", 900), ("Tostado", 4500), ("Jugo", 3000), ("Torta", 4200), ("Agua", 1800)):
        _, p = await call(s, "POST", "/products", owner, {"categoryId": cat["id"], "name": name, "price": price}, label="SETUP product", ok=(201,))
        products.append(p["id"])
    tables = []
    for n in range(1, 21):
        _, t = await call(s, "POST", "/tables", owner, {"locationId": loc, "number": n}, label="SETUP table", ok=(201,))
        tables.append(t["id"])
    _, ch = await call(s, "POST", "/sales-channels", owner, {"locationId": loc, "kind": "PEDIDOSYA", "mode": "MANUAL", "commissionPct": 25}, label="SETUP channel", ok=(201,))
    await call(s, "POST", "/cash-sessions/open", owner, {"locationId": loc, "openingAmount": 10000}, label="SETUP cash", ok=(201,))
    return {"i": i, "emails": emails, "owner": owner, "loc": loc, "waiters": staff["WAITER"], "kitchen": staff["KITCHEN"][0],
            "products": products, "tables": tables, "channel": ch["id"]}


# ---------------------------------------------------------------- tiempo real (STOMP)
def stomp(command, headers, body=""):
    return command + "\n" + "".join(f"{k}:{v}\n" for k, v in headers.items()) + "\n" + body + "\0"


async def realtime(s, cafe, token, on_event, stop):
    url = f"{WS}/ws/orders?token={token}"
    while not stop.is_set():
        try:
            async with s.ws_connect(url, heartbeat=None) as ws:
                await ws.send_str(stomp("CONNECT", {"accept-version": "1.1,1.2", "heart-beat": "0,0"}))
                counters["ws_connected"] += 1
                async for msg in ws:
                    if msg.type != aiohttp.WSMsgType.TEXT:
                        continue
                    data = msg.data
                    if data.startswith("CONNECTED"):
                        await ws.send_str(stomp("SUBSCRIBE", {"id": "sub-0", "destination": f"/topic/locations/{cafe['loc']}/orders"}))
                    elif data.startswith("MESSAGE"):
                        body = data.split("\n\n", 1)[1].rstrip("\0")
                        try:
                            await on_event(json.loads(body))
                        except Exception as e:
                            counters["ws_handler_error"] += 1
                    elif data.startswith("ERROR"):
                        counters["ws_error_frame"] += 1
                        if len(error_samples) < 60:
                            error_samples.append("WS ERROR frame: " + data[:200])
                    if stop.is_set():
                        break
        except Exception as e:
            counters["ws_disconnect"] += 1
            await asyncio.sleep(4)


# ---------------------------------------------------------------- un café
async def run_cafe(s, cafe, period, stop, start_at):
    open_tables = set()             # mesas con pedido abierto (según nosotros)
    created_at = {}                 # order id -> perf_counter al recibir la respuesta del POST
    pending_ready = asyncio.Queue()  # pedidos "Listo" para cobrar
    tasks = []

    async def kitchen_event(evt):
        data = evt.get("data") or {}
        oid = data.get("id")
        if evt.get("event") == "order:created" and oid:
            t = created_at.pop(data.get("idempotencyKey"), None)
            if t is not None:
                ws_delay.append((time.perf_counter() - t) * 1000)
            counters["ws_events_kitchen"] += 1
            tasks.append(asyncio.create_task(cook(oid)))

    async def cook(oid):
        await asyncio.sleep(random.uniform(15, 45))
        if stop.is_set():
            return
        await call(s, "PATCH", f"/orders/{oid}/status", cafe["kitchen"], {"status": "IN_PREPARATION"}, label="cocina: en preparación")
        await asyncio.sleep(random.uniform(15, 45))
        if stop.is_set():
            return
        # 409 esperado: si el mozo agregó algo mientras se preparaba, el pedido vuelve a "Nuevo" (a propósito).
        st, _ = await call(s, "PATCH", f"/orders/{oid}/status", cafe["kitchen"], {"status": "READY"}, label="cocina: listo", soft=(409,))
        if st == 200:
            await pending_ready.put(oid)

    async def cashier():
        while not stop.is_set():
            try:
                oid = await asyncio.wait_for(pending_ready.get(), timeout=2)
            except asyncio.TimeoutError:
                continue
            # La mesa se queda un rato antes de pagar.
            await asyncio.sleep(random.uniform(20, 90))
            if stop.is_set():
                return
            _, order = await call(s, "GET", f"/orders/{oid}", cafe["owner"], label="caja: ver pedido")
            if order and float(order["balanceDue"]) > 0:
                await call(s, "POST", f"/orders/{oid}/payments", cafe["owner"],
                           {"paymentMethod": random.choice(["CASH", "DEBIT_CARD", "QR" if False else "TRANSFER"]), "amount": order["balanceDue"]},
                           label="caja: cobrar", ok=(201,))
            st, done = await call(s, "PATCH", f"/orders/{oid}/status", cafe["owner"], {"status": "DELIVERED"}, label="caja: entregar")
            if done and done.get("tableId"):
                open_tables.discard(done["tableId"])
            counters["orders_delivered"] += 1

    async def waiter_poll(token):
        await asyncio.sleep(random.uniform(0, 20))
        while not stop.is_set():
            await call(s, "GET", f"/orders/active?locationId={cafe['loc']}", token, label="mozo: pedidos abiertos (cada 20 s)")
            await asyncio.sleep(20)

    async def waiter_start(token):
        # Lo que carga la pantalla de Pedidos al abrirse.
        for path, label in ((f"/tables?locationId={cafe['loc']}", "inicio: mesas"), ("/categories", "inicio: categorías"),
                            ("/products", "inicio: productos"), ("/option-groups", "inicio: opciones"),
                            (f"/floor?locationId={cafe['loc']}", "inicio: plano"), (f"/sales-channels?locationId={cafe['loc']}", "inicio: apps")):
            await call(s, "GET", path, token, label=label)

    async def order_generator():
        await asyncio.sleep(random.uniform(0, period))
        while not stop.is_set():
            token = random.choice(cafe["waiters"])
            items = [{"productId": random.choice(cafe["products"]), "quantity": random.randint(1, 3)} for _ in range(random.randint(1, 4))]
            body = {"locationId": cafe["loc"], "idempotencyKey": str(uuid.uuid4()), "clientTimestamp": "2026-10-03T12:00:00Z", "items": items}
            r = random.random()
            label = "mozo: pedido para llevar"
            if r < 0.55:
                free = [t for t in cafe["tables"] if t not in open_tables]
                if free:
                    t = random.choice(free)
                    body["tableId"] = t
                    open_tables.add(t)
                    label = "mozo: pedido en mesa"
            elif r < 0.70:
                body["channel"] = {"channelId": cafe["channel"], "orderRef": uuid.uuid4().hex[:6], "paidByApp": True}
                label = "mozo: pedido de app"
            created_at[body["idempotencyKey"]] = time.perf_counter()  # desde que el mozo toca "Enviar"
            st, order = await call(s, "POST", "/orders", token, body, label=label, ok=(201,), soft=(409,))
            if order and st == 201:
                counters["orders_created"] += 1
                # A veces la mesa pide algo más.
                if order.get("tableId") and random.random() < 0.15:
                    async def add_later(oid=order["id"], tok=token):
                        await asyncio.sleep(random.uniform(5, 30))
                        if not stop.is_set():
                            await call(s, "POST", f"/orders/{oid}/items", tok, {"items": [{"productId": random.choice(cafe["products"]), "quantity": 1}]},
                                       label="mozo: agregar al pedido", soft=(409,))
                    tasks.append(asyncio.create_task(add_later()))
            elif st == 409 and body.get("tableId"):
                pass
            await asyncio.sleep(random.uniform(period * 0.5, period * 1.5))

    async def owner_dashboard():
        await asyncio.sleep(random.uniform(0, 60))
        while not stop.is_set():
            await call(s, "GET", f"/dashboard/summary?locationId={cafe['loc']}&from=2026-10-03T03:00:00Z&to=2026-10-04T03:00:00Z",
                       cafe["owner"], label="dueño: Inicio (cada 60 s)")
            await call(s, "GET", f"/cash-sessions/current?locationId={cafe['loc']}", cafe["owner"], label="dueño: caja actual")
            await asyncio.sleep(60)

    async def kitchen_poll():
        await asyncio.sleep(random.uniform(0, 20))
        while not stop.is_set():
            await call(s, "GET", f"/orders/active?locationId={cafe['loc']}", cafe["kitchen"], label="cocina: pedidos (cada 20 s)")
            await asyncio.sleep(20)

    await asyncio.sleep(max(0, start_at - time.time()))
    for tok in cafe["waiters"]:
        await waiter_start(tok)
    loops = [realtime(s, cafe, cafe["kitchen"], kitchen_event, stop), kitchen_poll(), cashier(), order_generator(), owner_dashboard()]
    for tok in cafe["waiters"]:
        loops.append(realtime(s, cafe, tok, lambda e: asyncio.sleep(0), stop))
        loops.append(waiter_poll(tok))
    await asyncio.gather(*loops, return_exceptions=True)
    for t in tasks:
        t.cancel()


# ---------------------------------------------------------------- recursos
async def sample_resources(stop, out):
    """% de CPU real (de 1 núcleo) de java y mysqld cada 5 s, y memoria. Solo en Linux (en Windows se saltea)."""
    if not os.path.exists("/proc/self/stat"):
        return
    import subprocess
    tck = os.sysconf("SC_CLK_TCK")
    def pids():
        r = subprocess.run("ps -C java -o pid=; ps -C mysqld -o pid=", shell=True, capture_output=True, text=True).stdout.split()
        return {"java": int(r[0]), "mysqld": int(r[-1])} if len(r) >= 2 else {}
    def ticks(pid):
        f = open(f"/proc/{pid}/stat").read().rsplit(")", 1)[1].split()
        return int(f[11]) + int(f[12])
    def rss(pid):
        for line in open(f"/proc/{pid}/status"):
            if line.startswith("VmRSS"):
                return int(line.split()[1]) // 1024
    p = pids(); prev = {k: ticks(v) for k, v in p.items()}; t_prev = time.time()
    while not stop.is_set():
        await asyncio.sleep(5)
        now = time.time(); cur = {k: ticks(v) for k, v in p.items()}
        out.append({k: {"cpu": round((cur[k] - prev[k]) / tck / (now - t_prev) * 100), "mb": rss(p[k])} for k in p})
        prev, t_prev = cur, now


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cafes", type=int, default=50)
    ap.add_argument("--period", type=float, default=15)
    ap.add_argument("--duration", type=int, default=300)
    ap.add_argument("--state", default=os.path.join(OUT_DIR, "state.json"))
    ap.add_argument("--setup-only", action="store_true")
    ap.add_argument("--reuse", action="store_true")
    ap.add_argument("--out", default=os.path.join(OUT_DIR, "result.json"))
    a = ap.parse_args()

    conn = aiohttp.TCPConnector(limit=0)
    timeout = aiohttp.ClientTimeout(total=30)
    async with aiohttp.ClientSession(connector=conn, timeout=timeout) as s:
        cafes = []
        if a.reuse and os.path.exists(a.state):
            # Las sesiones vencen: se vuelve a iniciar sesión con cada usuario guardado.
            cafes = [c for c in json.load(open(a.state)) if "emails" in c][: a.cafes]
            async def relogin(c):
                async def tok(email):
                    _, r = await call(s, "POST", "/auth/login", body={"email": email, "password": PASSWORD}, label="SETUP login")
                    return r["accessToken"]
                c["owner"] = await tok(c["emails"]["owner"])
                c["waiters"] = [await tok(e) for e in c["emails"]["WAITER"]]
                c["kitchen"] = await tok(c["emails"]["KITCHEN"][0])
            sem0 = asyncio.Semaphore(8)
            async def guarded(c):
                async with sem0:
                    await relogin(c)
            await asyncio.gather(*(guarded(c) for c in cafes))
        if len(cafes) < a.cafes:
            t0 = time.time()
            sem = asyncio.Semaphore(4)
            async def one(i):
                async with sem:
                    return await setup_cafe(s, i)
            cafes += await asyncio.gather(*(one(i) for i in range(len(cafes), a.cafes)))
            json.dump(cafes, open(a.state, "w"))
            print(f"armado: {a.cafes} cafés en {time.time() - t0:.0f} s", flush=True)
        if a.setup_only:
            return
        cafes = cafes[: a.cafes]
        lat.clear(); errors.clear(); expected.clear(); error_samples.clear(); ws_delay.clear(); counters.clear()

        stop = asyncio.Event()
        res = []
        start_at = time.time() + 2
        runners = [asyncio.create_task(run_cafe(s, c, a.period, stop, start_at + random.uniform(0, 10))) for c in cafes]
        sampler = asyncio.create_task(sample_resources(stop, res))
        await asyncio.sleep(a.duration)
        stop.set()
        await asyncio.wait(runners, timeout=40)
        sampler.cancel()

    total_req = sum(len(v) for v in lat.values())
    report = {
        "cafes": a.cafes, "period": a.period, "duration": a.duration,
        "requests": total_req, "rps": round(total_req / a.duration, 1),
        "counters": dict(counters),
        "errors": {f"{k[0]} | {k[1]}": v for k, v in errors.items()},
        "expected_conflicts": {f"{k[0]} | {k[1]}": v for k, v in expected.items()},
        "error_samples": error_samples[:30],
        "ws_delay_ms (desde Enviar hasta que aparece en cocina)": {"n": len(ws_delay), "p50": round(pct(ws_delay, 50)), "p95": round(pct(ws_delay, 95)), "max": round(max(ws_delay or [0]))},
        "endpoints": {k: {"n": len(v), "p50": round(pct(v, 50)), "p95": round(pct(v, 95)), "p99": round(pct(v, 99)), "max": round(max(v))}
                      for k, v in sorted(lat.items()) if not k.startswith("SETUP")},
        "resources": res,
    }
    json.dump(report, open(a.out, "w"), indent=1, ensure_ascii=False)
    print(json.dumps({k: report[k] for k in ("cafes", "requests", "rps", "counters", "errors", "expected_conflicts", "ws_delay_ms (desde Enviar hasta que aparece en cocina)")}, indent=1, ensure_ascii=False))
    print("\nendpoint                                   n    p50   p95   p99   max (ms)")
    for k, v in report["endpoints"].items():
        print(f"{k[:40]:40} {v['n']:6} {v['p50']:5} {v['p95']:5} {v['p99']:5} {v['max']:6}")
    if res:
        for k in res[0]:
            c = [x[k]["cpu"] for x in res]
            print(f"\nCPU {k}: promedio {statistics.mean(c):.0f}% · máximo {max(c)}% (100% = 1 núcleo de 2) · memoria {res[-1][k]['mb']} MB", end="")
        print()
    if error_samples:
        print("\nejemplos de errores:")
        for e in error_samples[:15]:
            print(" ", e)


if __name__ == "__main__":
    asyncio.run(main())
