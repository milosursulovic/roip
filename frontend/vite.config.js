import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import vueDevTools from "vite-plugin-vue-devtools";
import tailwindcss from "@tailwindcss/vite";
import fs from "fs";
import dotenv from "dotenv";

dotenv.config();

const HOST = process.env.VITE_HOST;
const PORT = process.env.VITE_PORT;
const CERT_KEY_PATH = process.env.VITE_CERT_KEY_PATH;
const CERT_PATH = process.env.VITE_CERT_PATH;

export default defineConfig({
  plugins: [vue(), vueDevTools(), tailwindcss()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    https: {
      key: fs.readFileSync(CERT_KEY_PATH),
      cert: fs.readFileSync(CERT_PATH),
    },
    port: PORT,
    host: HOST,
  },
});
