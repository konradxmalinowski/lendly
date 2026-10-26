import { Meta, Title } from '@angular/platform-browser';
import { inject } from '@angular/core';

export function setPrivatePageMetadata(title: string, description: string): void {
  inject(Title).setTitle(title);
  const meta = inject(Meta);
  meta.updateTag({ name: 'description', content: description });
  meta.updateTag({ name: 'robots', content: 'noindex, nofollow' });
}
