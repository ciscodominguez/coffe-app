import { ApplicationConfig, ErrorHandler, LOCALE_ID, provideZoneChangeDetection } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEsAr from '@angular/common/locales/es-AR';
import { provideHttpClient, withInterceptors, withXhr } from '@angular/common/http';
import { provideRouter, withInMemoryScrolling } from '@angular/router';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { DiagnosticsErrorHandler, diagnosticsInterceptor } from './core/services/diagnostics.service';

// Formato argentino en toda la app: "$ 1.234,50", "24/09/2026 14:32".
registerLocaleData(localeEsAr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'top' })),
    provideHttpClient(withXhr(), withInterceptors([authInterceptor, diagnosticsInterceptor])),
    // V18 — anota el último error para el botón de soporte.
    { provide: ErrorHandler, useClass: DiagnosticsErrorHandler },
    { provide: LOCALE_ID, useValue: 'es-AR' },
  ],
};
