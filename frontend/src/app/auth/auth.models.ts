export interface PrivateProfile {
  id: string;
  email: string;
  emailVerified: boolean;
  displayName: string;
  avatarUrl: string | null;
  areaLabel: string | null;
  role: 'USER' | 'MODERATOR' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED';
  registeredAt: string;
}

export interface AuthResponse {
  user: PrivateProfile;
  csrfToken: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors: Record<string, string> | null;
  correlationId: string;
}

export interface CsrfResponse {
  csrfToken: string;
}

export interface GoogleAuthorizationResponse {
  authorizationUrl: string;
}
