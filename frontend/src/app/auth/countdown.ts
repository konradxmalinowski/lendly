import { DestroyRef, computed, signal } from '@angular/core';

/** Whole-second countdown driven by a deadline, so background tabs do not drift. Inactive on the server. */
export class Countdown {
  private readonly endsAt = signal(0);
  private readonly now = signal(Date.now());
  private timer: ReturnType<typeof setInterval> | null = null;

  readonly remaining = computed(() => Math.max(0, Math.ceil((this.endsAt() - this.now()) / 1000)));
  readonly active = computed(() => this.remaining() > 0);

  constructor(destroyRef: DestroyRef, private readonly onDone: () => void = () => undefined) {
    destroyRef.onDestroy(() => this.stop());
  }

  start(seconds: number): void {
    this.now.set(Date.now());
    this.endsAt.set(this.now() + seconds * 1000);
    if (this.timer || typeof window === 'undefined') {
      return;
    }
    this.timer = setInterval(() => {
      this.now.set(Date.now());
      if (!this.active()) {
        this.stop();
        this.onDone();
      }
    }, 1000);
  }

  private stop(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }
}
