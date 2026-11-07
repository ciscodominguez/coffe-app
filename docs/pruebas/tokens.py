import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "resultados")
os.makedirs(OUT_DIR, exist_ok=True)
import asyncio, aiohttp, base64, json, sys, uuid
sys.path.insert(0, HERE)
from loadtest import API, PASSWORD, setup_cafe
def b64(d): return base64.urlsafe_b64encode(json.dumps(d).encode()).rstrip(b"=").decode()
async def st(s, path, token):
    async with s.get(API + path, headers={"Authorization": f"Bearer {token}"} if token else {}) as r:
        return r.status
async def main():
    ok = []
    async with aiohttp.ClientSession() as s:
        cafe = await setup_cafe(s, 8801); good = cafe["owner"]; h, p, sig = good.split(".")
        payload = json.loads(base64.urlsafe_b64decode(p + "==")); 
        other = await setup_cafe(s, 8802)
        # 1. firma cambiada
        ok.append(("Token con la firma cambiada", await st(s, "/products", f"{h}.{p}.{sig[:-4]}AAAA") == 401))
        # 2. contenido cambiado (otro café) con la firma original
        forged = dict(payload); forged["businessId"] = json.loads(base64.urlsafe_b64decode(other["owner"].split(".")[1] + "=="))["businessId"] if "businessId" in payload else "x"
        ok.append(("Token con el café cambiado a mano", await st(s, "/products", f"{h}.{b64(forged)}.{sig}") == 401))
        # 3. rol cambiado a mano
        forged = dict(payload)
        for k in ("role", "roles", "authorities"):
            if k in forged: forged[k] = "SUPERADMIN" if k == "role" else ["SUPERADMIN"]
        ok.append(("Token con el rol cambiado a SUPERADMIN", await st(s, "/platform/businesses", f"{h}.{b64(forged)}.{sig}") in (401, 403)))
        # 4. algoritmo 'none'
        ok.append(("Token sin firma (alg none)", await st(s, "/products", f"{b64({'alg': 'none', 'typ': 'JWT'})}.{p}.") == 401))
        # 5. token vencido
        expired = dict(payload); expired["exp"] = 1000000000
        ok.append(("Token vencido (aunque se le cambie la fecha, la firma no cierra)", await st(s, "/products", f"{h}.{b64(expired)}.{sig}") == 401))
        # 6. basura
        ok.append(("Token basura", await st(s, "/products", "hola.que.tal") == 401))
        # 7. Contraseña adivinada: se frena
        email = f"bruto-{uuid.uuid4().hex[:6]}@test.local"
        codes = []
        for i in range(12):
            async with s.post(API + "/auth/login", json={"email": email, "password": f"mala{i}"}) as r:
                codes.append(r.status)
        ok.append((f"Muchos intentos de contraseña: se frena (códigos {sorted(set(codes))})", 429 in codes or 423 in codes or codes[-1] != 401))
        print("payload keys:", list(payload.keys()))
    for name, good_ in ok:
        print(("OK   " if good_ else "FALLA") + " " + name)
asyncio.run(main())
