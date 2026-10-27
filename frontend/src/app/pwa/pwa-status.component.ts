import { DOCUMENT } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SwUpdate } from '@angular/service-worker';
import { filter } from 'rxjs';

@Component({
  selector: 'lendly-pwa-status',
  standalone: true,
  template: `
    <div class="pwa-status" role="status">
      @if (!online()) {
        <p class="pwa-card">Jesteś offline. Ogłoszenia, rezerwacje i konto wymagają połączenia z internetem.</p>
      }
      @if (updateReady()) {
        <div class="pwa-card">
          <p>Dostępna nowa wersja</p>
          <div class="pwa-actions">
            <button class="button-primary" type="button" (click)="applyUpdate()">Odśwież teraz</button>
            <button class="button-secondary" type="button" (click)="updateReady.set(false)">Później</button>
          </div>
        </div>
      }
      @if (broken()) {
        <div class="pwa-card">
          <p>Wersja aplikacji na tym urządzeniu jest uszkodzona. Odśwież stronę, aby ją naprawić.</p>
          <div class="pwa-actions">
            <button class="button-primary" type="button" (click)="reload()">Odśwież stronę</button>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    :host { position: fixed; z-index: 10; right: 0.75rem; bottom: calc(0.75rem + env(safe-area-inset-bottom));
      left: 0.75rem; pointer-events: none; }
    .pwa-status { display: grid; gap: 0.5rem; max-width: 520px; margin-inline: auto; }
    .pwa-card { margin: 0; padding: 0.85rem 1rem; border: 1px solid var(--border-strong); border-radius: var(--radius-small);
      background: var(--surface); color: var(--text-primary); line-height: 1.5; pointer-events: auto; }
    .pwa-card p { margin: 0; }
    .pwa-actions { display: flex; flex-wrap: wrap; gap: 0.5rem; margin-top: 0.75rem; }
  `
})
export class PwaStatusComponent {
  private readonly document = inject(DOCUMENT);
  private readonly updates = inject(SwUpdate);
  private readonly destroyRef = inject(DestroyRef);

  readonly online = signal(true);
  readonly updateReady = signal(false);
  readonly broken = signal(false);

  constructor() {
    const view = this.document.defaultView;
    if (view) {
      this.online.set(view.navigator.onLine);
      const goOnline = () => this.online.set(true);
      const goOffline = () => this.online.set(false);
      const recheck = () => {
        if (this.document.visibilityState === 'visible' && this.updates.isEnabled) {
          void this.updates.checkForUpdate().catch(() => false);
        }
      };
      view.addEventListener('online', goOnline);
      view.addEventListener('offline', goOffline);
      this.document.addEventListener('visibilitychange', recheck);
      this.destroyRef.onDestroy(() => {
        view.removeEventListener('online', goOnline);
        view.removeEventListener('offline', goOffline);
        this.document.removeEventListener('visibilitychange', recheck);
      });
    }

    if (this.updates.isEnabled) {
      this.updates.versionUpdates.pipe(
        filter((event) => event.type === 'VERSION_READY'),
        takeUntilDestroyed()
      ).subscribe(() => this.updateReady.set(true));
      this.updates.unrecoverable.pipe(takeUntilDestroyed()).subscribe(() => this.broken.set(true));
    }
  }

  applyUpdate(): void {
    this.updates.activateUpdate().catch(() => false).finally(() => this.reload());
  }

  reload(): void {
    this.document.defaultView?.location.reload();
  }
}
