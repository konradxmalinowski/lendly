import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'lendly-mfa-setup-prompt',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="prompt" role="alert">
      <p class="prompt-title">Najpierw włącz weryfikację dwuetapową</p>
      <p>Moderacja wymaga kodu z aplikacji uwierzytelniającej. Włącz weryfikację w profilu, a potem wróć tutaj.</p>
      <a class="button-primary" routerLink="/profile">Przejdź do profilu</a>
    </div>
  `,
  styles: `
    .prompt { margin: 1.5rem 0; padding: 1rem 1.25rem; border: 1px solid var(--border-strong);
      border-radius: var(--radius-small); background: var(--surface); color: var(--text-primary); }
    .prompt p { margin: 0 0 0.75rem; line-height: 1.55; }
    .prompt-title { font-weight: 600; }
    .prompt a { margin-top: 0.25rem; }
  `
})
export class MfaSetupPromptComponent {}
