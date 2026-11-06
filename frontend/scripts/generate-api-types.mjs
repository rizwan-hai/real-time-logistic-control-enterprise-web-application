import { access, mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const outputPath = resolve(scriptDir, '../src/types/api.generated.ts');
const schemaUrl = process.env.API_SCHEMA_URL ?? 'http://localhost:8080/v3/api-docs';
const schemaFile = process.env.API_SCHEMA_FILE;
const required = process.argv.includes('--required');
try {
  let schema;
  if (schemaFile) schema = JSON.parse(await readFile(resolve(process.cwd(), schemaFile), 'utf8'));
  else {
    const response = await fetch(schemaUrl);
    if (!response.ok) throw new Error(`OpenAPI schema fetch failed: ${response.status} ${response.statusText} (${schemaUrl})`);
    schema = await response.json();
  }
  const ast = await openapiTS(schema);
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, astToString(ast), 'utf8');
  console.info(`Generated API types from ${schemaFile ?? schemaUrl} at ${outputPath}`);
} catch (error) {
  if (required) throw error;
  try { await access(outputPath); }
  catch { throw error; }
  console.warn(`OpenAPI endpoint unavailable; using the existing generated contract at ${outputPath}.`);
}
