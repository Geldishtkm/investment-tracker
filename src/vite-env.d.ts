/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL; set in .env.development for local work */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
