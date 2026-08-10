import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const dir = path.dirname(fileURLToPath(import.meta.url));
const readJsonl = name => fs.readFileSync(path.join(dir, name), 'utf8').trim().split(/\r?\n/).map((line, index) => {
  try { return JSON.parse(line); } catch (error) { throw new Error(`${name}:${index + 1} JSON解析失败: ${error.message}`); }
});
const splitLikeProduction = text => text.trim().split(/\n\s*\n/).flatMap(paragraph => {
  const chunks = [];
  let start = 0;
  while (start < paragraph.length) {
    const end = Math.min(start + 512, paragraph.length);
    chunks.push(paragraph.slice(start, end).trim());
    if (end >= paragraph.length) break;
    start = Math.max(end - 100, start + 1);
  }
  return chunks;
});

const rag = readJsonl('rag.jsonl');
const memories = readJsonl('long-term-memory.jsonl');
const preferences = readJsonl('user-preference.jsonl');
const corpusFile = readJsonl('corpus.jsonl');
const cases = readJsonl('cases.jsonl');
const corpus = [...rag, ...memories, ...preferences];
const failures = [];
const corpusIds = new Set(corpus.map(item => item.externalId));
const caseIds = new Set(cases.map(item => item.externalId));

if (rag.length !== 100 || memories.length !== 100 || preferences.length !== 100 || cases.length !== 200) failures.push('数量必须为 RAG/记忆/偏好/问题 = 100/100/100/200');
if (corpusFile.length !== 300) failures.push('corpus.jsonl 必须包含300条');
if (corpusIds.size !== 300 || caseIds.size !== 200) failures.push('存在重复ID');
for (const item of [...corpus, ...cases]) if (!/^\d{5,12}$/.test(item.externalId)) failures.push(`ID不是5-12位数字字符串: ${item.externalId}`);
for (const item of corpus) {
  if (!item.title || !item.summary || !item.content || !Array.isArray(item.tags)) failures.push(`语料字段缺失: ${item.externalId}`);
  if (/锛|绔|璇|鐢|馃/.test(`${item.title}${item.summary}${item.content}`)) failures.push(`疑似乱码: ${item.externalId}`);
}
for (const item of rag) {
  const chunks = splitLikeProduction(item.content);
  const paragraphs = item.content.split(/\n\s*\n/);
  if (item.type !== 'RAG_CHUNK') failures.push(`RAG类型必须为RAG_CHUNK: ${item.externalId}`);
  if (item.content.length < 260 || item.content.length > 512) failures.push(`RAG Chunk长度应为260-512字(${item.content.length}): ${item.externalId}`);
  if (paragraphs.length !== 1) failures.push(`RAG Chunk必须只有一个自然段: ${item.externalId}`);
  if (chunks.length !== 1) failures.push(`RAG记录必须恰好形成1个chunk，实际为${chunks.length}: ${item.externalId}`);
}
for (const item of memories) if (item.content.length < 380) failures.push(`长期记忆过短(${item.content.length}): ${item.externalId}`);
for (const item of preferences) if (item.content.length < 360) failures.push(`用户偏好过短(${item.content.length}): ${item.externalId}`);
for (const item of cases) {
  if (!item.query || item.query.length < 38 || !Array.isArray(item.expected) || item.expected.length === 0) failures.push(`测试问题无效: ${item.externalId}`);
  for (const label of item.expected || []) {
    if (!corpusIds.has(label.externalId)) failures.push(`问题${item.externalId}引用不存在的ID ${label.externalId}`);
    if (![2, 3].includes(label.grade)) failures.push(`问题${item.externalId} grade无效`);
    if (Number(label.externalId) >= 11001 && Number(label.externalId) <= 11100 && label.matchMode !== 'EXACT_SOURCE') failures.push(`问题${item.externalId}的RAG标签必须使用EXACT_SOURCE`);
  }
}

const expectedCoverage = new Map([...corpusIds].map(id => [id, 0]));
for (const item of cases) for (const label of item.expected) expectedCoverage.set(label.externalId, expectedCoverage.get(label.externalId) + 1);
const uncovered = [...expectedCoverage].filter(([, count]) => count === 0).map(([id]) => id);
if (uncovered.length) failures.push(`未被任何问题覆盖的语料: ${uncovered.join(',')}`);

const scenarios = new Map();
const scopes = new Map();
for (const item of cases) {
  scenarios.set(item.tags[0], (scenarios.get(item.tags[0]) || 0) + 1);
  scopes.set(item.sourceScope, (scopes.get(item.sourceScope) || 0) + 1);
}
if (scenarios.size !== 10 || [...scenarios.values()].some(count => count !== 20)) failures.push('场景分布必须为10 x 20');
if (scopes.get('RAG') !== 70 || scopes.get('MEMORY') !== 80 || scopes.get('MIXED') !== 50) failures.push('范围分布必须为RAG 70、MEMORY 80、MIXED 50');

if (failures.length) {
  console.error(failures.join('\n'));
  process.exit(1);
}

const lengths = rows => ({ min: Math.min(...rows.map(item => item.content.length)), max: Math.max(...rows.map(item => item.content.length)), avg: Math.round(rows.reduce((sum, item) => sum + item.content.length, 0) / rows.length) });
const chunkCounts = rag.map(item => splitLikeProduction(item.content).length);
console.log(JSON.stringify({
  counts: { rag: rag.length, memories: memories.length, preferences: preferences.length, cases: cases.length },
  scopes: Object.fromEntries(scopes),
  scenarios: Object.fromEntries(scenarios),
  lengths: { rag: lengths(rag), memories: lengths(memories), preferences: lengths(preferences) },
  chunks: { min: Math.min(...chunkCounts), max: Math.max(...chunkCounts), avg: Number((chunkCounts.reduce((a, b) => a + b, 0) / chunkCounts.length).toFixed(2)) },
  expectedCoverage: { covered: expectedCoverage.size, uncovered: 0 }
}, null, 2));
