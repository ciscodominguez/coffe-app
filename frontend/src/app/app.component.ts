import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ThemeService } from './core/services/theme.service';
import { ToastHostComponent } from './shared/components/toast-host/toast-host.component';

@Component({
    selector: 'app-root',
    imports: [RouterOutlet, ToastHostComponent],
    templateUrl: './app.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'COFE_APP';

  // Se inyecta acá para que el tema se aplique (y siga al sistema) desde el arranque, también en el login.
  private readonly theme = inject(ThemeService);
}
