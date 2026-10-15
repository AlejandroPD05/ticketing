import js from '@eslint/js';
import globals from 'globals';

// La web de documentación son <script> clásicos (sin módulos) que se cargan
// en orden desde index.html; el proyecto "real" es Java/Maven.
export default [
  { ignores: ['node_modules/', 'target/'] },
  js.configs.recommended,
  {
    files: ['js/**/*.js', 'script.js'],
    languageOptions: {
      ecmaVersion: 2021,
      sourceType: 'script',
      globals: {
        ...globals.browser,
        // Cargada por CDN en index.html (diagrama ER).
        mermaid: 'readonly',
        // Publicados en window por otros <script> del mismo proyecto.
        FX: 'readonly',
        I18N: 'readonly',
      },
    },
    rules: {
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
    },
  },
];
