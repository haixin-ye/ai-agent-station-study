const {
  parseJsonl,
  parseCsv,
  metricDelta,
  groupRunResults,
  preserveWorkbenchState
} = require('./recall_evaluation_logic');
const fs = require('fs');

const workbenchSource = fs.readFileSync(require.resolve('./recall_evaluation.js'), 'utf8');
if (!workbenchSource.includes('selectDataset(datasetCard.dataset.datasetId)')) {
  throw new Error('dataset card must read datasetId from HTMLElement.dataset');
}
if (!workbenchSource.includes('/api/v1/rag/knowledge/files')
  || !workbenchSource.includes('new File([')
  || !workbenchSource.includes('/corpus/rag/attachments')) {
  throw new Error('RAG evaluation import must use the production multipart upload endpoint before attachment');
}
if (!workbenchSource.includes('uploadSingleRagDocument(item)')
  || !workbenchSource.includes('再次导入同一文件会自动跳过已完成的大文件')
  || !workbenchSource.includes('renderRagImportStatus()')) {
  throw new Error('RAG import must upload and attach parent documents incrementally with visible resumable progress');
}

const jsonl = parseJsonl('{"externalId":"q1","query":"hello"}\nnot-json\n{"externalId":"q2","query":"world"}');
if (jsonl.items.length !== 2 || jsonl.errors.length !== 1 || jsonl.errors[0].line !== 2) {
  throw new Error('JSONL line isolation failed');
}

const csv = parseCsv('externalId,query,tags\r\nq1,"hello, world","a,b"\r\nq2,test,c');
if (csv.length !== 2 || csv[0].query !== 'hello, world' || csv[0].tags !== 'a,b') {
  throw new Error('quoted CSV parsing failed');
}

if (metricDelta(0, 0.25) !== 0.25 || metricDelta(0.4, 0) !== -0.4) {
  throw new Error('zero-safe metric delta failed');
}

const grouped = groupRunResults(
  [{ caseId: 'c1', status: 'COMPLETED' }, { caseId: 'c2', status: 'FAILED' }],
  [{ caseId: 'c1', rankNo: 2 }, { caseId: 'c1', rankNo: 1 }]
);
if (grouped[0].hits[0].rankNo !== 1 || grouped[1].hits.length !== 0) {
  throw new Error('run-result grouping failed');
}

const preserved = preserveWorkbenchState({ tab: 'results', datasetId: 'd1', runId: 'r1', scrollTop: 321 },
  { tab: 'corpus', datasetId: 'd2', runId: 'r2' });
if (preserved.tab !== 'results' || preserved.datasetId !== 'd1' || preserved.runId !== 'r1' || preserved.scrollTop !== 321) {
  throw new Error('poll refresh state preservation failed');
}

console.log('recall evaluation logic projections pass');
