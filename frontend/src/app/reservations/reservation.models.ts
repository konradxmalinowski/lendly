import { PricingType } from '../catalog/catalog.models';

export type ReservationStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'EXPIRED' | 'ACTIVE' | 'COMPLETED';
export type ReservationAction = 'ACCEPT' | 'REJECT' | 'CANCEL' | 'HANDOVER' | 'RETURN';
export type ReservationRole = 'BORROWER' | 'OWNER';

export interface DateRange {
  startDate: string;
  endDate: string;
}

export interface AvailabilityBlock extends DateRange {
  id: string;
}

export interface ReservationListingRef {
  id: string;
  title: string;
  coverImageUrl: string | null;
  status: string;
}

export interface Counterpart {
  id: string;
  displayName: string;
  areaLabel: string | null;
}

export interface ReservationSummary {
  id: string;
  role: ReservationRole;
  status: ReservationStatus;
  startDate: string;
  endDate: string;
  days: number;
  pricingType: PricingType;
  estimatedCost: number;
  currency: string;
  listing: ReservationListingRef;
  counterpart: Counterpart;
  expiresAt: string;
  createdAt: string;
}

export interface ReservationDetail extends ReservationSummary {
  dailyPrice: number | null;
  note: string | null;
  decisionReason: string | null;
  cancelledBy: 'BORROWER' | 'OWNER' | null;
  approximateLat: number | null;
  approximateLng: number | null;
  handoverLat: number | null;
  handoverLng: number | null;
  allowedActions: ReservationAction[];
  decidedAt: string | null;
  handedOverAt: string | null;
  returnedAt: string | null;
  cancelledAt: string | null;
}
