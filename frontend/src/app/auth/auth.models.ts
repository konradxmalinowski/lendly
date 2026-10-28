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
  mfaEnabled: boolean;
  mfaRequired: boolean;
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

export interface AuthFeatures {
  registration: boolean;
  googleSignIn: boolean;
  // Google creates new accounts only when this is on; otherwise it signs in to existing ones.
  googleSignUp: boolean;
  mfa: boolean;
}

export interface HealthResponse {
  features?: Partial<AuthFeatures>;
}

export interface MfaChallengeResponse {
  mfaRequired: true;
}

export type SignInResult =
  | { status: 'signed-in'; user: PrivateProfile }
  | { status: 'mfa-required' };

export interface MfaStatus {
  enabled: boolean;
  required: boolean;
  recoveryCodesRemaining: number;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
  qrImage: string;
}

export interface RecoveryCodesResponse {
  recoveryCodes: string[];
}
