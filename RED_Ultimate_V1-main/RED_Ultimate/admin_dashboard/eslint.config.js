import tsParser from '@typescript-eslint/parser';
import reactHooks from 'eslint-plugin-react-hooks';

// ESLint 9 flat configuration. Type errors are checked separately by `npm run check`.
// Start with high-signal correctness rules; generated router code is not hand-written.
export default [
  { ignores: ['dist/**', 'src/routeTree.gen.ts'] },
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      parser: tsParser,
      parserOptions: { ecmaVersion: 'latest', sourceType: 'module', ecmaFeatures: { jsx: true } },
    },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      'no-debugger': 'error',
      'no-constant-binary-expression': 'error',
      'no-unsafe-optional-chaining': 'error',
      'react-hooks/rules-of-hooks': 'error',
    },
  },
];
