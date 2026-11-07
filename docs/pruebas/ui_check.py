import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "resultados")
os.makedirs(OUT_DIR, exist_ok=True)
import asyncio, sys, json
from playwright.async_api import async_playwright
sys.path.insert(0, HERE)
APP = "http://localhost:4200"
PASSWORD = "clave-de-prueba-1"


async def login(pg, email):
    await pg.goto(f"{APP}/login")
    await pg.fill("#email", email)
    await pg.fill("#password", PASSWORD)
    await pg.click("button[type=submit]")
    await pg.wait_for_timeout(2000)
    uid = await pg.evaluate("JSON.parse(localStorage.getItem('cofe_app_session_v1')).user.id")
    await pg.evaluate(f"localStorage.setItem('cofe_app_tour_done_{uid}','1')")
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        ctx = await b.new_context(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
        pg = await ctx.new_page()
        email = json.load(open(os.path.join(OUT_DIR, "ui_owner.json")))["email"]
        print("login...", flush=True); await login(pg, email); print("ok", pg.url, flush=True)
        bad = []
        for r in ["", "pedidos", "pedidos?tab=open", "kds", "caja", "productos", "opciones", "descuentos", "categorias", "insumos", "mesas",
                  "reportes", "users", "locations", "business", "cobros-qr", "apps-delivery", "suscripcion", "cuenta"]:
            await pg.goto(f"{APP}/{r}", wait_until="domcontentloaded"); await pg.wait_for_timeout(1500); print(r, flush=True)
            # ¿Algún elemento visible que se sale de la pantalla por la derecha?
            out = await pg.evaluate("""() => {
              const w = window.innerWidth; const res = [];
              const all = document.querySelectorAll('main *'); for (let i = 0; i < Math.min(all.length, 3000); i++) { const el = all[i];
                const r = el.getBoundingClientRect();
                if (r.width > 0 && r.right > w + 1 && getComputedStyle(el).visibility !== 'hidden') {
                  let p = el.parentElement, scrolls = false;
                  while (p) { const o = getComputedStyle(p).overflowX; if (o === 'auto' || o === 'scroll') { scrolls = true; break; } p = p.parentElement; }
                  if (!scrolls) res.push((el.className || el.tagName).toString().slice(0, 40) + ' +' + Math.round(r.right - w));
                }
              }
              return res.slice(0, 3);
            }""")
            if out: bad.append((r or 'inicio', out))
            if r == "cuenta": await pg.screenshot(path=os.path.join(OUT_DIR, "cuenta-celu.png"))
        print("pantallas con cosas que se salen:", bad if bad else "ninguna")
        await b.close()
asyncio.run(main())
