import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';

export const SITE_ORIGIN = 'https://lendly.com.pl';
export const SITE_NAME = 'Lendly';
export const DEFAULT_IMAGE: SeoImage = { url: `${SITE_ORIGIN}/og-image.png`, width: 1200, height: 630, alt: 'Lendly: pożyczaj rzeczy od sąsiadów' };

export interface SeoImage {
  url: string;
  width?: number;
  height?: number;
  alt?: string;
}

export interface SeoPage {
  title: string;
  description: string;
  // Path on the public origin; ignored for noindex pages, which carry no canonical.
  path?: string;
  noindex?: boolean;
  // Keep link following for noindex pages that only exist as filtered views of an indexable page.
  follow?: boolean;
  image?: SeoImage;
  jsonLd?: object | null;
}

const JSON_LD_ID = 'lendly-jsonld';

@Injectable({ providedIn: 'root' })
export class SeoService {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);

  apply(page: SeoPage): void {
    const image = page.image ?? DEFAULT_IMAGE;
    const canonical = page.noindex || page.path === undefined ? null : SITE_ORIGIN + page.path;

    this.title.setTitle(page.title);
    this.name('description', page.description);
    this.name('robots', page.noindex ? (page.follow ? 'noindex, follow' : 'noindex, nofollow') : 'index, follow');
    this.setCanonical(canonical);

    this.property('og:type', 'website');
    this.property('og:site_name', SITE_NAME);
    this.property('og:locale', 'pl_PL');
    this.property('og:title', page.title);
    this.property('og:description', page.description);
    this.property('og:url', canonical);
    this.property('og:image', image.url);
    this.property('og:image:width', image.width?.toString() ?? null);
    this.property('og:image:height', image.height?.toString() ?? null);
    this.property('og:image:alt', image.alt ?? null);

    this.name('twitter:card', 'summary_large_image');
    this.name('twitter:title', page.title);
    this.name('twitter:description', page.description);
    this.name('twitter:image', image.url);

    this.setJsonLd(page.jsonLd ?? null);
  }

  private name(name: string, content: string): void {
    this.meta.updateTag({ name, content });
  }

  private property(property: string, content: string | null): void {
    const selector = `property='${property}'`;
    if (content === null) {
      this.meta.removeTag(selector);
    } else {
      this.meta.updateTag({ property, content }, selector);
    }
  }

  private setCanonical(href: string | null): void {
    const head = this.document.head;
    let link = head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (href === null) {
      link?.remove();
      return;
    }
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      head.appendChild(link);
    }
    link.setAttribute('href', href);
  }

  private setJsonLd(data: object | null): void {
    const head = this.document.head;
    let script = head.querySelector<HTMLScriptElement>(`script#${JSON_LD_ID}`);
    if (data === null) {
      script?.remove();
      return;
    }
    if (!script) {
      script = this.document.createElement('script');
      script.id = JSON_LD_ID;
      script.type = 'application/ld+json';
      head.appendChild(script);
    }
    // Escaping "<" keeps user-written listing text from closing the script element.
    script.textContent = JSON.stringify(data).replace(/</g, '\\u003c');
  }
}
