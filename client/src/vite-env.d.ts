/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the API server for split deployments; unset = same origin. */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
