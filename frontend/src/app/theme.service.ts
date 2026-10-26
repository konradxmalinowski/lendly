import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';

type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly currentTheme = signal<Theme>(this.document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light');
  readonly isDark = computed(() => this.currentTheme() === 'dark');

  toggle(): void {
    const nextTheme = this.isDark() ? 'light' : 'dark';
    this.currentTheme.set(nextTheme);
    this.document.documentElement.setAttribute('data-theme', nextTheme);

    try {
      this.document.defaultView?.localStorage.setItem('lendly-theme', nextTheme);
    } catch {
      return;
    }
  }
}
