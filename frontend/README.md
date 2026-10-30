# COFFE_APP — Frontend

Frontend en **Angular 22** (componentes standalone, signals, control flow `@if`/`@for`).

## Requisitos

- Node.js **22.22.3 o superior** (o 24.15+)
- Backend de COFFE_APP corriendo (por defecto en `http://localhost:8080`, ver `src/environments/`)

## Comandos

| Comando | Qué hace |
|---|---|
| `npm install` | Instala dependencias |
| `npm start` | Servidor de desarrollo en `http://localhost:4200` |
| `npm run build` | Build de producción en `dist/frontend/browser` |
| `npm test` | Tests unitarios con Vitest |

## Migración 18 → 22

Se actualizó versión por versión (18 → 19 → 20 → 21 → 22) con `ng update` y sus migraciones oficiales:

- Componentes standalone sin `standalone: true` explícito (default desde v19).
- Control flow nuevo (`@if`, `@for`) y se quitó `CommonModule` donde ya no hacía falta.
- `ChangeDetectionStrategy.Eager` explícito en los componentes (mantiene el comportamiento previo a v22).
- `provideHttpClient(withXhr(), ...)` para conservar el backend XHR.
- Builder nuevo `@angular/build` (esbuild) en lugar de `@angular-devkit/build-angular`.
- Tests: de Karma + Jasmine a **Vitest** (`@angular/build:unit-test`).
- TypeScript 6.0.
