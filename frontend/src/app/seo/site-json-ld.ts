import { DEFAULT_IMAGE, SITE_NAME, SITE_ORIGIN } from './seo.service';

export const siteJsonLd = {
  '@context': 'https://schema.org',
  '@graph': [
    {
      '@type': 'Organization',
      '@id': `${SITE_ORIGIN}/#organization`,
      name: SITE_NAME,
      url: `${SITE_ORIGIN}/`,
      logo: DEFAULT_IMAGE.url
    },
    {
      '@type': 'WebSite',
      '@id': `${SITE_ORIGIN}/#website`,
      name: SITE_NAME,
      url: `${SITE_ORIGIN}/`,
      inLanguage: 'pl-PL',
      publisher: { '@id': `${SITE_ORIGIN}/#organization` }
    }
  ]
};
