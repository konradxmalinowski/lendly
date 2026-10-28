import { ListingDetail } from '../catalog/catalog.models';
import { SITE_ORIGIN, SeoPage } from './seo.service';

const CONDITIONS: Record<ListingDetail['condition'], string> = {
  NEW: 'https://schema.org/NewCondition',
  VERY_GOOD: 'https://schema.org/UsedCondition',
  GOOD: 'https://schema.org/UsedCondition',
  FAIR: 'https://schema.org/UsedCondition'
};

function absolute(url: string): string {
  return url.startsWith('http') ? url : SITE_ORIGIN + (url.startsWith('/') ? url : `/${url}`);
}

export function listingDescription(text: string, limit = 155): string {
  const flat = text.replace(/\s+/g, ' ').trim();
  if (flat.length <= limit) {
    return flat;
  }
  const cut = flat.slice(0, limit - 1);
  return `${cut.slice(0, cut.lastIndexOf(' ') > 80 ? cut.lastIndexOf(' ') : cut.length)}…`;
}

// Only fields the public listing API returns. No availability or rating: the API does not provide them here.
export function listingSeo(listing: ListingDetail, categoryName: string): SeoPage {
  const path = `/items/${listing.id}`;
  const images = listing.images.map((image) => absolute(image.url));
  const cover = listing.images[0];
  const description = listingDescription(listing.description);
  const price = listing.pricingType === 'FREE' || listing.dailyPrice === null ? 0 : listing.dailyPrice;

  return {
    title: `${listing.title} | Lendly`,
    description,
    path,
    image: cover
      ? { url: absolute(cover.url), width: cover.width, height: cover.height, alt: `Zdjęcie: ${listing.title}` }
      : undefined,
    jsonLd: {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: listing.title,
      description: listing.description,
      url: SITE_ORIGIN + path,
      ...(images.length ? { image: images } : {}),
      ...(categoryName ? { category: categoryName } : {}),
      itemCondition: CONDITIONS[listing.condition],
      offers: {
        '@type': 'Offer',
        url: SITE_ORIGIN + path,
        price,
        priceCurrency: listing.currency,
        businessFunction: 'http://purl.org/goodrelations/v1#LeaseOut',
        priceSpecification: {
          '@type': 'UnitPriceSpecification',
          price,
          priceCurrency: listing.currency,
          referenceQuantity: { '@type': 'QuantitativeValue', value: 1, unitCode: 'DAY' }
        }
      }
    }
  };
}
