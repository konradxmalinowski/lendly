export type ReportReason = 'SCAM' | 'DANGEROUS' | 'INAPPROPRIATE' | 'SPAM' | 'OTHER';
export type ReportTargetType = 'LISTING' | 'USER';
export type ReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';
export type ModerationAction = 'RESOLVE' | 'DISMISS' | 'HIDE_LISTING' | 'RESTORE_LISTING' | 'SUSPEND_USER' | 'REINSTATE_USER';

export const reportReasonLabels: Record<ReportReason, string> = {
  SCAM: 'Oszustwo lub wyłudzenie',
  DANGEROUS: 'Niebezpieczny lub zakazany przedmiot',
  INAPPROPRIATE: 'Treść obraźliwa lub nieodpowiednia',
  SPAM: 'Spam lub fałszywe ogłoszenie',
  OTHER: 'Inny powód'
};

export const reportStatusLabels: Record<ReportStatus, string> = {
  OPEN: 'Otwarte',
  RESOLVED: 'Rozstrzygnięte',
  DISMISSED: 'Odrzucone'
};

export const historyLabels: Partial<Record<string, string>> = {
  HIDE_LISTING: 'Ukryto ogłoszenie',
  RESTORE_LISTING: 'Przywrócono ogłoszenie',
  SUSPEND_USER: 'Zawieszono konto',
  REINSTATE_USER: 'Przywrócono konto',
  RESOLVE_REPORT: 'Rozstrzygnięto zgłoszenie',
  DISMISS_REPORT: 'Odrzucono zgłoszenie'
};

export interface PersonRef {
  id: string;
  displayName: string;
  role: string;
  status: string;
}

export interface ReportTarget {
  type: ReportTargetType;
  id: string;
  title: string;
  status: string;
  moderationReason: string | null;
  user: PersonRef;
}

export interface ReportSummary {
  id: string;
  status: ReportStatus;
  reason: ReportReason;
  details: string | null;
  createdAt: string;
  reporter: PersonRef;
  target: ReportTarget;
  openReportsOnTarget: number;
}

export interface RelatedReport {
  id: string;
  status: ReportStatus;
  reason: ReportReason;
  details: string | null;
  createdAt: string;
  reporterName: string;
}

export interface HistoryEntry {
  action: string;
  moderatorName: string;
  reason: string | null;
  createdAt: string;
}

export interface ReportDetail extends Omit<ReportSummary, 'openReportsOnTarget'> {
  resolutionNote: string | null;
  resolvedAt: string | null;
  moderatorName: string | null;
  relatedReports: RelatedReport[];
  history: HistoryEntry[];
  allowedActions: ModerationAction[];
}
