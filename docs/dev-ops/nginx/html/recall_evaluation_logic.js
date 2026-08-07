(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  root.RecallEvaluationLogic = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  function parseJsonl(text) {
    const items = [];
    const errors = [];
    String(text || '').split(/\r?\n/).forEach((line, index) => {
      if (!line.trim()) return;
      try {
        items.push(JSON.parse(line));
      } catch (error) {
        errors.push({ line: index + 1, message: error.message, preview: line.slice(0, 160) });
      }
    });
    return { items, errors };
  }

  function parseCsv(text) {
    const rows = [];
    let row = [];
    let value = '';
    let quoted = false;
    const input = String(text || '');
    for (let index = 0; index < input.length; index += 1) {
      const char = input[index];
      if (char === '"') {
        if (quoted && input[index + 1] === '"') {
          value += '"';
          index += 1;
        } else {
          quoted = !quoted;
        }
      } else if (char === ',' && !quoted) {
        row.push(value);
        value = '';
      } else if ((char === '\n' || char === '\r') && !quoted) {
        if (char === '\r' && input[index + 1] === '\n') index += 1;
        row.push(value);
        if (row.some(cell => cell !== '')) rows.push(row);
        row = [];
        value = '';
      } else {
        value += char;
      }
    }
    row.push(value);
    if (row.some(cell => cell !== '')) rows.push(row);
    if (!rows.length) return [];
    const headers = rows[0].map(header => header.trim());
    return rows.slice(1).map(cells => Object.fromEntries(headers.map((header, index) => [header, cells[index] ?? ''])));
  }

  function metricDelta(left, right) {
    const before = Number(left ?? 0);
    const after = Number(right ?? 0);
    return after - before;
  }

  function groupRunResults(results, hits) {
    const byCase = new Map();
    (hits || []).forEach(hit => {
      if (!byCase.has(hit.caseId)) byCase.set(hit.caseId, []);
      byCase.get(hit.caseId).push(hit);
    });
    byCase.forEach(values => values.sort((a, b) => Number(a.rankNo || 0) - Number(b.rankNo || 0)));
    return (results || []).map(result => ({ ...result, hits: byCase.get(result.caseId) || [] }));
  }

  function preserveWorkbenchState(previous, incoming) {
    return { ...(incoming || {}), ...(previous || {}) };
  }

  return { parseJsonl, parseCsv, metricDelta, groupRunResults, preserveWorkbenchState };
});
