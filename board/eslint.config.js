import js from "@eslint/js";
import prettier from "eslint-config-prettier";
import globals from "globals";

export default [
  { ignores: ["node_modules/", "vendor/"] },
  js.configs.recommended,
  prettier,
  {
    files: ["**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: globals.browser,
    },
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector:
            ":matches(Program, ExportNamedDeclaration) > VariableDeclaration[kind!='const']",
          message:
            "A module holds no mutable state: keep it in a const state object.",
        },
      ],
    },
  },
  { files: ["eslint.config.js"], languageOptions: { globals: globals.node } },
];
