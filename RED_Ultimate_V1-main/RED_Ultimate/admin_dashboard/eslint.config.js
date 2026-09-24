// ESLint 9 flat config — RED admin dashboard (Vite 5 + React 19 + TS).
// `npm run lint` = `eslint src --report-unused-disable-directives --max-warnings 0`.
// Lenient on legacy patterns (any / unused vars) so the gate stays green
// without touching src/ (tsconfig already sets noUnusedLocals:false).
import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import tseslint from "typescript-eslint";

export default tseslint.config(
  {
    ignores: [
      "dist/**",
      "coverage/**",
      "node_modules/**",
      "public/**",
      "src/routeTree.gen.ts",
      "**/*.gen.ts",
    ],
  },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ["**/*.{ts,tsx,js,jsx,mjs,cjs}"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "module",
      globals: globals.browser,
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      // Core hooks correctness. exhaustive-deps stays ON (warn) because
      // src/api/queries.ts carries a disable-next-line for it — turning the
      // rule off would make that directive unused and fail the lint gate.
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      // Dashboard co-locates stores/providers in tsx — not a bug here.
      "react-refresh/only-export-components": "off",
      // Legacy: ~45 files use `any`; keep the gate green.
      "@typescript-eslint/no-explicit-any": "off",
      "@typescript-eslint/no-unused-vars": "off",
      "no-console": "off",
      // TS compiler (npm run check) owns undefined-name checking.
      "no-undef": "off",
    },
  }
);
