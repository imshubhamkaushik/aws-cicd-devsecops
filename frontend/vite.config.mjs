import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [
    react({
      include: /\.(jsx|js)$/, // treat .js files as JSX
    }),
  ],
  build: {
    outDir: "build", // keeps 'build/' so the Dockerfile COPY path stays unchanged
  },
  // Local dev uses the same /api contract as the production SPA. Forward it
  // through the gateway so authentication, routing and rate limiting behave
  // the same way when Vite is running on localhost:5173.
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:11000",
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: "./src/setupTests.js",
    // `npm run test:coverage` (used by CI) writes coverage/lcov.info, which the
    // SonarQube scan reads via sonar.javascript.lcov.reportPaths.
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "lcov"],
      reportsDirectory: "coverage",
      include: ["src/**/*.{js,jsx}"],
      exclude: [
        "src/**/*.test.{js,jsx}", 
        "src/setupTests.js", 
        "src/index.jsx"
      ],
    },
  },
});
