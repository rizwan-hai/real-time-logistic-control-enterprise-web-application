import { defineConfig, devices } from '@playwright/test';
declare const process: { env: Record<string, string | undefined> };
export default defineConfig({ testDir:'./tests', use:{ baseURL:'http://127.0.0.1:5173', ...devices['Desktop Chrome'] }, webServer:{ command:'npm exec vite -- --host 127.0.0.1', url:'http://127.0.0.1:5173', reuseExistingServer:!process.env.CI } });
