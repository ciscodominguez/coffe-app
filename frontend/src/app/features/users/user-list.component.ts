import { Component, OnInit, computed, inject, signal, ChangeDetectionStrategy } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { UserRole, USER_ROLE_LABELS } from '../../core/models/auth.model';
import { UserResponse } from '../../core/models/user.model';
import { UserService } from '../../core/services/user.service';
import { extractErrorMessage } from '../../core/services/error.util';
import { ToastService } from '../../core/services/toast.service';
import { IconComponent, IconName } from '../../shared/components/icon/icon.component';
import { ModalComponent } from '../../shared/components/modal/modal.component';

const ALL_ROLES: UserRole[] = ['OWNER', 'ADMIN', 'SUPERVISOR', 'WAITER', 'KITCHEN'];
const ADMIN_ASSIGNABLE_ROLES: UserRole[] = ['SUPERVISOR', 'WAITER', 'KITCHEN'];

/** Qué puede hacer cada rol, en palabras simples — se muestra al elegir el rol de alguien nuevo. */
const ROLE_INFO: Readonly<Record<UserRole, { icon: IconName; text: string }>> = {
  OWNER: { icon: 'building', text: 'Todo, incluidos los datos fiscales del negocio.' },
  ADMIN: { icon: 'settings', text: 'Todo menos los datos fiscales: carta, mesas, equipo y reportes.' },
  SUPERVISOR: { icon: 'chart', text: 'Atiende, cobra, ve los reportes y ajusta el stock.' },
  WAITER: { icon: 'receipt', text: 'Toma pedidos, cobra y usa la caja.' },
  KITCHEN: { icon: 'flame', text: 'Ve y prepara los pedidos. No maneja plata.' },
};

/**
 * Equipo: quién trabaja en el café y con qué rol. Alta en una ventana con
 * la explicación de cada rol, así quien administra sabe qué va a poder
 * hacer cada persona. Alta/baja: OWNER/ADMIN (un ADMIN no puede crear
 * OWNER/ADMIN — mismo criterio que el backend).
 */
@Component({
    selector: 'app-user-list',
    imports: [ReactiveFormsModule, IconComponent, ModalComponent],
    templateUrl: './user-list.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './user-list.component.css'
})
export class UserListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastService);

  readonly roleInfo = ROLE_INFO;
  readonly users = signal<UserResponse[]>([]);
  readonly loading = signal(true);
  readonly includeInactive = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly creating = signal(false);
  readonly showForm = signal(false);
  readonly confirmDeactivate = signal<UserResponse | null>(null);
  readonly busyId = signal<string | null>(null);

  // ---- V9: cambiarle la contraseña a alguien del equipo ----
  readonly passwordFor = signal<UserResponse | null>(null);
  readonly newPassword = signal('');
  readonly passwordError = signal<string | null>(null);
  readonly savingPassword = signal(false);

  readonly canManage = computed(() => {
    const role = this.authService.user()?.role;
    return role === 'OWNER' || role === 'ADMIN';
  });

  readonly assignableRoles = computed<UserRole[]>(() =>
    this.authService.user()?.role === 'OWNER' ? ALL_ROLES : ADMIN_ASSIGNABLE_ROLES,
  );

  readonly sortedUsers = computed(() =>
    [...this.users()].sort(
      (a, b) =>
        Number(b.isActive) - Number(a.isActive) ||
        ALL_ROLES.indexOf(a.role as UserRole) - ALL_ROLES.indexOf(b.role as UserRole) ||
        a.firstName.localeCompare(b.firstName),
    ),
  );

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required]],
    lastName: ['', [Validators.required]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
    role: ['WAITER' as UserRole, [Validators.required]],
  });

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  async reload(): Promise<void> {
    this.errorMessage.set(null);
    try {
      this.users.set(await this.userService.findAll(this.includeInactive()));
    } catch (error) {
      this.errorMessage.set(extractErrorMessage(error, 'No se pudo cargar el equipo.'));
    } finally {
      this.loading.set(false);
    }
  }

  async toggleIncludeInactive(): Promise<void> {
    this.includeInactive.set(!this.includeInactive());
    await this.reload();
  }

  openForm(): void {
    this.form.reset({ firstName: '', lastName: '', email: '', password: '', role: 'WAITER' });
    this.formError.set(null);
    this.showForm.set(true);
  }

  async onCreate(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.creating()) return;
    this.creating.set(true);
    this.formError.set(null);
    try {
      const created = await this.userService.create(this.form.getRawValue());
      this.showForm.set(false);
      await this.reload();
      this.toast.success(`${created.firstName} ya puede entrar con su email y contraseña.`);
    } catch (error) {
      this.formError.set(extractErrorMessage(error, 'No se pudo crear el usuario.'));
    } finally {
      this.creating.set(false);
    }
  }

  async deactivate(user: UserResponse): Promise<void> {
    this.confirmDeactivate.set(null);
    if (this.isSelf(user)) return; // el backend igual lo rechaza.
    this.busyId.set(user.id);
    try {
      await this.userService.deactivate(user.id);
      await this.reload();
      this.toast.info(`${user.firstName} ya no puede entrar.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo dar de baja.'));
    } finally {
      this.busyId.set(null);
    }
  }

  async reactivate(user: UserResponse): Promise<void> {
    this.busyId.set(user.id);
    try {
      await this.userService.update(user.id, { isActive: true });
      await this.reload();
      this.toast.success(`${user.firstName} puede volver a entrar.`);
    } catch (error) {
      this.toast.error(extractErrorMessage(error, 'No se pudo reactivar.'));
    } finally {
      this.busyId.set(null);
    }
  }

  /**
   * Mismo criterio que el backend (UserService#update): la propia se cambia
   * en "Mi cuenta", y un ADMIN no puede tocar a un OWNER/ADMIN.
   */
  canManageUser(user: UserResponse): boolean {
    if (!this.canManage() || this.isSelf(user)) return false;
    if (this.authService.user()?.role === 'OWNER') return true;
    return user.role !== 'OWNER' && user.role !== 'ADMIN';
  }

  openPassword(user: UserResponse): void {
    this.passwordFor.set(user);
    this.newPassword.set('');
    this.passwordError.set(null);
  }

  async savePassword(): Promise<void> {
    const user = this.passwordFor();
    if (!user || this.savingPassword()) return;
    if (this.newPassword().length < 8) {
      this.passwordError.set('Tiene que tener al menos 8 caracteres.');
      return;
    }
    this.savingPassword.set(true);
    this.passwordError.set(null);
    try {
      await this.userService.update(user.id, { password: this.newPassword() });
      this.passwordFor.set(null);
      this.toast.success(`Listo. ${user.firstName} tiene que volver a entrar con la contraseña nueva.`);
    } catch (error) {
      this.passwordError.set(extractErrorMessage(error, 'No se pudo cambiar la contraseña.'));
    } finally {
      this.savingPassword.set(false);
    }
  }

  isSelf(user: UserResponse): boolean {
    return user.id === this.authService.user()?.id;
  }

  roleLabel(role: string): string {
    return USER_ROLE_LABELS[role as UserRole] ?? role;
  }

  initials(user: UserResponse): string {
    return `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.toUpperCase();
  }
}
