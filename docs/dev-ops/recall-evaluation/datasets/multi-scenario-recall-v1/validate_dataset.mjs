import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const readJsonl = name => fs.readFileSync(path.join(dir, name), 'utf8').trim().split(/\r?\n/).map(JSON.parse);
const rag = readJsonl('rag.jsonl');
const memory = readJsonl('long-term-memory.jsonl');
const preferences = readJsonl('user-preference.jsonl');
const cases = readJsonl('cases.jsonl');
const corpus = [...rag, ...memory, ...preferences];
const corpusIds = new Set(corpus.map(item => item.externalId));
const caseIds = new Set(cases.map(item => item.externalId));

const failures = [];
if (rag.length !== 60 || memory.length !== 60 || preferences.length !== 60 || cases.length !== 150) failures.push('unexpected counts');
if (corpusIds.size !== corpus.length) failures.push('duplicate corpus IDs');
if (caseIds.size !== cases.length) failures.push('duplicate case IDs');
for (const item of [...corpus, ...cases]) {
  if (!/^\d{5,12}$/.test(item.externalId)) failures.push(`invalid numeric ID ${item.externalId}`);
}
for (const item of cases) {
  if (!item.query || !Array.isArray(item.expected) || !item.expected.length) failures.push(`invalid case ${item.externalId}`);
  for (const label of item.expected || []) {
    if (!corpusIds.has(label.externalId)) failures.push(`missing expected ID ${label.externalId} in case ${item.externalId}`);
  }
}
const scenarios = new Map();
for (const item of cases) {
  const key = item.tags?.[0];
  scenarios.set(key, (scenarios.get(key) || 0) + 1);
}
if (scenarios.size !== 6 || [...scenarios.values()].some(count => count !== 25)) failures.push('scenario distribution is not 6 x 25');

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}
console.log(JSON.stringify({ corpus: corpus.length, cases: cases.length, scenarios: Object.fromEntries(scenarios) }));
