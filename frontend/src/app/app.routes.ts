import { Routes } from '@angular/router';
import { authGuard, superadminGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';
import { ShellComponent } from './shared/components/shell/shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    // V9 — "Olvidé mi contraseña" y la pantalla del link del mail (sin sesión).
    path: 'recuperar',
    loadComponent: () =>
      import('./features/password/forgot-password.component').then((m) => m.ForgotPasswordComponent),
  },
  {
    path: 'restablecer',
    loadComponent: () =>
      import('./features/password/reset-password.component').then((m) => m.ResetPasswordComponent),
  },
  {
    // Alta de un café nuevo (antes solo por API) — dos pasos, y entra directo.
    path: 'registro',
    loadComponent: () => import('./features/register/register.component').then((m) => m.RegisterComponent),
  },
  // V17 — los papeles: públicos (se ven sin entrar, como pide Defensa del Consumidor).
  {
    path: 'terminos',
    loadComponent: () => import('./features/legal/terms.component').then((m) => m.TermsComponent),
  },
  {
    path: 'privacidad',
    loadComponent: () => import('./features/legal/privacy.component').then((m) => m.PrivacyComponent),
  },
  {
    path: 'arrepentimiento',
    data: { type: 'WITHDRAWAL' },
    loadComponent: () => import('./features/legal/service-request.component').then((m) => m.ServiceRequestComponent),
  },
  {
    path: 'baja',
    data: { type: 'CANCELLATION' },
    loadComponent: () => import('./features/legal/service-request.component').then((m) => m.ServiceRequestComponent),
  },
  {
    // V15 — panel de superadmin (Fran): fuera del menú de un café.
    path: 'superadmin',
    canActivate: [superadminGuard],
    loadComponent: () => import('./features/superadmin/superadmin.component').then((m) => m.SuperadminComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
      },
      // Ítem 8 de las mejoras pedidas: Negocio/Usuarios/Locales/Categorías
      // son datos de gestión — visibles solo a OWNER/ADMIN/SUPERVISOR
      // ("dueño y gerentes"), nunca a WAITER/KITCHEN. Mismo grupo de roles
      // en las cuatro; ver también shell.component.ts (oculta los links
      // del nav) y dashboard.component.ts (arma la vía rápida). Productos
      // es la excepción: WAITER/KITCHEN también lo ven (sin guard, más
      // abajo) para poder buscar un producto y consultar sus ingredientes;
      // la alta/edición/baja sigue restringida a OWNER/ADMIN dentro del
      // propio componente (ver ProductListComponent#canManage).
      {
        // V9 — "Mi cuenta": cualquier rol (cambiar la propia contraseña, tema).
        path: 'cuenta',
        loadComponent: () => import('./features/account/account.component').then((m) => m.AccountComponent),
      },
      {
        path: 'business',
        canActivate: [roleGuard(['OWNER', 'ADMIN', 'SUPERVISOR'])],
        loadComponent: () =>
          import('./features/business/business-profile.component').then((m) => m.BusinessProfileComponent),
      },
      {
        // Paso 2.2 — conectar Mercado Pago y configurar el QR de cada local.
        // Solo OWNER/ADMIN: maneja la cuenta donde entra la plata (mismo
        // @PreAuthorize que MercadoPagoController).
        // V19 — apps de delivery (PedidosYa, Rappi…): cada café prende las que usa.
        path: 'apps-delivery',
        canActivate: [roleGuard(['OWNER', 'ADMIN'])],
        loadComponent: () => import('./features/delivery-apps/delivery-apps.component').then((m) => m.DeliveryAppsComponent),
      },
      {
        path: 'cobros-qr',
        canActivate: [roleGuard(['OWNER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/mercadopago/mercadopago-settings.component').then((m) => m.MercadoPagoSettingsComponent),
      },
      {
        // Paso 2.6 — Excel para el contador. Mismos roles que el panel de
        // Inicio y que ReportController: dueño, administrador y supervisor.
        path: 'reportes',
        canActivate: [roleGuard(['OWNER', 'ADMIN', 'SUPERVISOR'])],
        loadComponent: () => import('./features/reports/reports.component').then((m) => m.ReportsComponent),
      },
      {
        // V16 — suscripción a COFFE_APP. Ver: dueño y administrador; pagar o
        // cancelar: solo el dueño (mismo @PreAuthorize que SubscriptionController).
        path: 'suscripcion',
        canActivate: [roleGuard(['OWNER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/subscription/subscription.component').then((m) => m.SubscriptionComponent),
      },
      {
        path: 'users',
        canActivate: [roleGuard(['OWNER', 'ADMIN', 'SUPERVISOR'])],
        loadComponent: () => import('./features/users/user-list.component').then((m) => m.UserListComponent),
      },
      {
        path: 'locations',
        canActivate: [roleGuard(['OWNER', 'ADMIN', 'SUPERVISOR'])],
        loadComponent: () =>
          import('./features/locations/location-list.component').then((m) => m.LocationListComponent),
      },
      {
        path: 'categorias',
        canActivate: [roleGuard(['OWNER', 'ADMIN', 'SUPERVISOR'])],
        loadComponent: () =>
          import('./features/catalog/category-list.component').then((m) => m.CategoryListComponent),
      },
      {
        // Mesas del local: ver para gerencia; alta/edición/baja solo OWNER/ADMIN (mismo @PreAuthorize que TableController).
        path: 'mesas',
        canActivate: [roleGuard(['OWNER', 'ADMIN', 'SUPERVISOR'])],
        loadComponent: () => import('./features/tables/table-list.component').then((m) => m.TableListComponent),
      },
      {
        path: 'productos',
        // Sin roleGuard a propósito: todos los roles autenticados pueden
        // entrar a buscar productos/ingredientes (ver comentario más
        // arriba). El propio ShellComponent (authGuard) ya exige sesión.
        loadComponent: () =>
          import('./features/catalog/product-list.component').then((m) => m.ProductListComponent),
      },
      {
        // V12 — lista de descuentos. Solo OWNER/ADMIN (mismo @PreAuthorize
        // que la escritura en DiscountController).
        path: 'descuentos',
        canActivate: [roleGuard(['OWNER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/catalog/discount-list.component').then((m) => m.DiscountListComponent),
      },
      {
        // V11 — grupos de opciones (tamaño, leche, extras). Solo OWNER/ADMIN,
        // mismo @PreAuthorize que la escritura en OptionGroupController.
        path: 'opciones',
        canActivate: [roleGuard(['OWNER', 'ADMIN'])],
        loadComponent: () =>
          import('./features/catalog/option-group-list.component').then((m) => m.OptionGroupListComponent),
      },
      {
        path: 'insumos',
        // Bloque D (ítems 12/13) — "gerencia carga, todos consultan": sin
        // roleGuard a propósito, mismo criterio que /productos (WAITER/
        // KITCHEN incluidos, para ver qué se está por acabar). El propio
        // componente (IngredientListComponent#canManage) oculta el alta/
        // edición de insumos y los ajustes manuales a quien no sea
        // OWNER/ADMIN/SUPERVISOR.
        loadComponent: () =>
          import('./features/stock/ingredient-list.component').then((m) => m.IngredientListComponent),
      },
      {
        path: 'kds',
        loadComponent: () => import('./features/kds/kds-board.component').then((m) => m.KdsBoardComponent),
      },
      {
        path: 'pedidos',
        // Mismo criterio de roles que POST /orders en el backend (OrderController#create): KITCHEN no carga pedidos, tiene su propia pantalla (KDS).
        canActivate: [roleGuard(['WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER'])],
        loadComponent: () =>
          import('./features/waiter/waiter-order.component').then((m) => m.WaiterOrderComponent),
      },
      {
        path: 'caja',
        // Ítem 14 — mismo grupo de roles que CashSessionController del backend: KITCHEN no maneja dinero.
        canActivate: [roleGuard(['WAITER', 'SUPERVISOR', 'ADMIN', 'OWNER'])],
        loadComponent: () =>
          import('./features/cash/cash-session.component').then((m) => m.CashSessionComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
