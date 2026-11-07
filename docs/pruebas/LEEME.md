# Scripts de la prueba exhaustiva (octubre de 2026)

Los resultados y qué se encontró están en [../PRUEBAS-OCTUBRE-2026.md](../PRUEBAS-OCTUBRE-2026.md).
Sirven para repetir la prueba antes de cada cambio grande o antes de subir
una versión nueva al servidor.

**Importante:** correrlos contra una base de datos **de prueba**, nunca
contra la de un café real: crean cafés, pedidos y cobros de mentira.

Necesitan Python 3 con `aiohttp` (y `playwright` para los de pantallas):

```
pip install aiohttp playwright
python -m playwright install chromium
```

Por defecto le hablan al backend en `http://localhost:3000/api/v1` (se cambia
con la variable `API`). Lo que generan queda en la carpeta `resultados/`.

| Script | Qué hace | Ejemplo |
|---|---|---|
| `loadtest.py` | Simulador de hora pico: N cafés con mozos, cocina en tiempo real, caja y dueño | `python loadtest.py --cafes 50 --period 15 --duration 300` |
| `race.py` | 16 casos de cosas que pasan a la vez (doble cobro, misma mesa, stock…) | `python race.py` |
| `tokens.py` | Sesiones falsificadas y freno de contraseñas | `python tokens.py` |
| `ui_tour.py` | Recorre todas las pantallas con cada rol en compu, tablet y celular (necesita la app en `http://localhost:4200`) | `python ui_tour.py` |
| `ui_check.py` | Revisa que nada se salga de la pantalla en el celular | `python ui_check.py` |

`loadtest.py`: `--cafes` cuántos cafés, `--period` cada cuántos segundos
hace un pedido cada café, `--duration` cuántos segundos dura. La primera vez
arma los cafés (tarda unos minutos); con `--reuse` usa los ya armados.

Los tests que quedaron fijos en el backend (`ConcurrencyTest` y
`EndpointSweepTest`) corren solos con el resto de los tests.
