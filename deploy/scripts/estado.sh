#!/usr/bin/env bash
# Cómo está todo, de un vistazo.   Uso: scripts/estado.sh
source "$(dirname "$0")/comun.sh"

hace() { local s="${1:-0}"; [ "$s" -gt 0 ] || { echo "nunca"; return; }; echo "hace $(( ($(date +%s) - s) / 3600 )) h"; }

echo
echo "  Versión puesta ........ $(version_actual)   (base en V$(version_base dc))"
echo "  En prueba ............. $(cat "$ESTADO/candidata" 2>/dev/null || echo "ninguna")$([ -f "$ESTADO/candidata-probada" ] && echo "  (probada: lista para actualizar.sh)")"
echo "  Responde .............. $(salud_ok prod && echo "sí" || echo "NO")"
echo "  Disco ................. $(df -h / | awk 'NR == 2 { print $5 " usado, " $4 " libres" }')"
echo "  Memoria ............... $(free -m | awk '/^Mem:/ { print $3 " MB usados de " $2 " MB" }')"
echo "  Última copia buena .... $(hace "$(cat "$ESTADO/ultimo-backup-ok" 2>/dev/null)")"
echo "  Última prueba de restauración OK ... $(hace "$(cat "$ESTADO/ultima-prueba-restauracion-ok" 2>/dev/null)")"
echo "  Horario para actualizar: $(texto_ventana) — ahora: $(en_ventana && echo "SÍ" || echo "no")"
echo
dc ps --format 'table {{.Service}}\t{{.Status}}'
echo
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}' | grep -E 'NAME|cofe-' || true
echo
