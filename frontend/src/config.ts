export const config = {
  devBypass: (import.meta.env.VITE_DEV_BYPASS ?? 'true') === 'true',
  azureAd: {
    clientId: import.meta.env.VITE_AZURE_AD_CLIENT_ID ?? '',
    tenantId: import.meta.env.VITE_AZURE_AD_TENANT_ID ?? '',
    redirectUri: import.meta.env.VITE_AZURE_AD_REDIRECT_URI ?? 'http://localhost:9211/auth/callback',
    apiScope: import.meta.env.VITE_AZURE_AD_API_SCOPE ?? '',
  },
} as const;
