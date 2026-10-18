// Backend base URL. Local development sets VITE_API_BASE_URL in .env.development;
// production builds without it fall back to the deployed API.
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL || 'https://invtracker-s5ain.ondigitalocean.app';
