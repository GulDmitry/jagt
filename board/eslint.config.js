import js from "@eslint/js";
import prettier from "eslint-config-prettier";
import importX from "eslint-plugin-import-x";
import globals from "globals";

export default [
  { ignores: ["node_modules/", "vendor/", "coverage/"] },
  js.configs.recommended,
  prettier,
  {
    files: ["**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "module",
      globals: globals.browser,
    },
    plugins: { "import-x": importX },
    rules: {
      complexity: ["error", 12],
      "max-params": ["error", 7],
      "import-x/no-cycle": "error",
      "import-x/max-dependencies": ["error", { max: 5 }],
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
  {
    files: ["components/**/*.js", "core/**/*.js"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          patterns: [
            {
              regex: "(^|/)ui/",
              message:
                "A component or core module reads no ui/ module: what it needs arrives as a prop or an argument.",
            },
          ],
        },
      ],
    },
  },
  { files: ["eslint.config.js"], languageOptions: { globals: globals.node } },
];
