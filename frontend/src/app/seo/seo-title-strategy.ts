import { Injectable, inject } from '@angular/core';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { SeoService } from './seo.service';

export interface RouteSeo {
  description: string;
  noindex?: boolean;
  // Query parameters make the page a filtered view: keep it out of the index but let links be followed.
  noindexWithQuery?: boolean;
  // The page component sets its own metadata once its data has loaded.
  dynamic?: boolean;
  jsonLd?: object;
}

export const DEFAULT_TITLE = 'Lendly | Pożyczaj rzeczy od sąsiadów';
const DEFAULT_DESCRIPTION = 'Pożyczaj rzeczy od osób z okolicy zamiast kupować je na krótko.';

@Injectable({ providedIn: 'root' })
export class SeoTitleStrategy extends TitleStrategy {
  private readonly seo = inject(SeoService);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    let route = snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    const data = route.data['seo'] as RouteSeo | undefined;
    if (data?.dynamic) {
      return;
    }

    const [path, query] = snapshot.url.split('#')[0].split('?');
    const filtered = !!data?.noindexWithQuery && !!query;
    this.seo.apply({
      title: this.buildTitle(snapshot) ?? DEFAULT_TITLE,
      description: data?.description ?? DEFAULT_DESCRIPTION,
      path,
      noindex: data?.noindex ?? filtered,
      follow: filtered,
      jsonLd: data?.jsonLd
    });
  }
}
