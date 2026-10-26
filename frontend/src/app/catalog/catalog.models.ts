export type PricingType = 'FREE' | 'PAID';
export type ItemCondition = 'NEW' | 'VERY_GOOD' | 'GOOD' | 'FAIR';
export type ListingStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED' | 'HIDDEN';
export type SearchSort = 'newest' | 'price_asc' | 'price_desc' | 'distance';

export interface Category {
  key: string;
  displayName: string;
}

export interface ListingImage {
  id: string;
  url: string;
  width: number;
  height: number;
}

export interface ListingSummary {
  id: string;
  title: string;
  categoryKey: string;
  condition: ItemCondition;
  pricingType: PricingType;
  dailyPrice: number | null;
  currency: string;
  coverImageUrl: string | null;
  lat: number;
  lng: number;
  ownerDisplayName: string;
  areaLabel: string | null;
  distanceKm: number | null;
  publishedAt: string;
}

export interface ListingOwner {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  areaLabel: string | null;
  registeredAt: string;
}

export interface ListingDetail {
  id: string;
  title: string;
  description: string;
  categoryKey: string;
  condition: ItemCondition;
  pricingType: PricingType;
  dailyPrice: number | null;
  currency: string;
  lat: number;
  lng: number;
  images: ListingImage[];
  owner: ListingOwner;
  publishedAt: string;
  updatedAt: string;
}

export interface OwnerListing {
  id: string;
  title: string;
  description: string;
  categoryKey: string;
  condition: ItemCondition;
  pricingType: PricingType;
  dailyPrice: number | null;
  currency: string;
  handoverLat: number | null;
  handoverLng: number | null;
  publicLat: number | null;
  publicLng: number | null;
  status: ListingStatus;
  images: ListingImage[];
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
  moderationReason: string | null;
}

export interface ListingRequest {
  title: string;
  description: string;
  categoryKey: string;
  condition: ItemCondition;
  pricingType: PricingType;
  dailyPrice: string | null;
  handoverLat: number | null;
  handoverLng: number | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SearchQuery {
  q?: string;
  category?: string;
  pricing?: PricingType;
  minPrice?: string;
  maxPrice?: string;
  bbox?: string;
  lat?: number;
  lng?: number;
  radiusKm?: number;
  sort?: SearchSort;
  page?: number;
  size?: number;
}

export interface MapPoint {
  lat: number;
  lng: number;
}

export interface MapBounds {
  west: number;
  south: number;
  east: number;
  north: number;
}
