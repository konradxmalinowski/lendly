import { RenderMode, ServerRoute } from '@angular/ssr';

// Stable public pages are prerendered for crawlers; everything with live or private data renders in the browser.
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Prerender },
  { path: 'items', renderMode: RenderMode.Prerender },
  { path: '**', renderMode: RenderMode.Client }
];
