(() => {
  const logic = window.RecallEvaluationLogic;
  const params = new URLSearchParams(location.search);
  const API_BASE = (params.get('api') || location.origin).replace(/\/$/, '');
  const API = `${API_BASE}/api/v1/dev/recall-evaluations`;
  const state = {
    tab: params.get('tab') || 'corpus', corpusType: params.get('corpusType') || 'RAG_DOCUMENT',
    datasets: [], datasetId: params.get('datasetId'), corpus: [], cases: [], runs: [],
    vectors: { RAG_DOCUMENT: [], LONG_TERM_MEMORY: [], USER_PREFERENCE: [] },
    runId: params.get('runId'), runDetail: null, comparison: null, importTarget: null,
    importCorpusType: null, polling: false
  };
  const els = {
    view: document.getElementById('view'), datasetList: document.getElementById('datasetList'),
    datasetTitle: document.getElementById('datasetTitle'), datasetDescription: document.getElementById('datasetDescription'),
    headerDataset: document.getElementById('headerDataset'), headerCorpus: document.getElementById('headerCorpus'),
    headerCases: document.getElementById('headerCases'), headerRun: document.getElementById('headerRun'),
    deleteDatasetBtn: document.getElementById('deleteDatasetBtn'), datasetModal: document.getElementById('datasetModal'),
    importModal: document.getElementById('importModal'), importModalTitle: document.getElementById('importModalTitle'),
    importText: document.getElementById('importText'), importFormat: document.getElementById('importFormat'),
    importHint: document.getElementById('importHint'), importFilePicker: document.getElementById('importFilePicker'),
    importFileName: document.getElementById('importFileName'),
    toast: document.getElementById('toast')
  };

  const selectedDataset = () => state.datasets.find(item => item.datasetId === state.datasetId);
  const selectedRun = () => state.runs.find(item => item.evaluationRunId === state.runId) || state.runDetail?.run;
  const esc = value => String(value ?? '').replace(/[&<>'"]/g, char => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' })[char]);
  const statusClass = value => String(value || '').toLowerCase();
  const pct = value => `${((Number(value) || 0) * 100).toFixed(1)}%`;
  const number = value => Number(value || 0).toLocaleString('zh-CN');
  const statePill = value => `<span class="state ${statusClass(value)}">${esc(value || 'UNKNOWN')}</span>`;
  const metric = (label, value, note = '') => `<div class="metric"><label>${esc(label)}</label><strong>${esc(value)}</strong>${note ? `<small>${esc(note)}</small>` : ''}</div>`;

  async function request(path, options = {}) {
    const controller = new AbortController();
    const timeoutMs = Number(options.timeoutMs || 12000);
    const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
    try {
      const { timeoutMs: ignoredTimeout, ...fetchOptions } = options;
      const init = { ...fetchOptions, signal: options.signal || controller.signal, headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(options.headers || {}) } };
      const response = await fetch(`${API}${path}`, init);
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload || payload.code !== '0000') throw new Error(payload?.info || `HTTP ${response.status}`);
      return payload.data;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('请求处理超时，请确认后端、Embedding服务和向量数据库状态。');
      throw error;
    } finally {
      window.clearTimeout(timeout);
    }
  }

  async function bootstrap() {
    renderAll();
    await loadDatasets();
    if (!state.datasetId && state.datasets.length) state.datasetId = state.datasets[0].datasetId;
    if (state.datasetId) await loadDatasetData();
    renderAll();
    window.setInterval(pollActiveRun, 1600);
  }

  async function loadDatasets() {
    state.datasets = await request('/datasets');
    if (state.datasetId && !state.datasets.some(item => item.datasetId === state.datasetId)) state.datasetId = null;
  }

  async function loadDatasetData(datasetId = state.datasetId) {
    if (!datasetId) return;
    const id = encodeURIComponent(datasetId);
    const [corpus, cases, runs] = await Promise.all([
      request(`/datasets/${id}/corpus?limit=1000`),
      request(`/datasets/${id}/cases?limit=1000`),
      request(`/runs?datasetId=${id}&limit=100`)
    ]);
    const vectorResults = await Promise.allSettled([
      request(`/datasets/${id}/vectors?itemType=RAG_DOCUMENT&limit=1000`),
      request(`/datasets/${id}/vectors?itemType=LONG_TERM_MEMORY&limit=1000`),
      request(`/datasets/${id}/vectors?itemType=USER_PREFERENCE&limit=1000`)
    ]);
    if (state.datasetId !== datasetId) return;
    state.corpus = corpus;
    state.cases = cases;
    state.runs = runs;
    state.vectors = {
      RAG_DOCUMENT: vectorResults[0].status === 'fulfilled' ? vectorResults[0].value : [],
      LONG_TERM_MEMORY: vectorResults[1].status === 'fulfilled' ? vectorResults[1].value : [],
      USER_PREFERENCE: vectorResults[2].status === 'fulfilled' ? vectorResults[2].value : []
    };
    const vectorFailures = vectorResults.filter(result => result.status === 'rejected');
    if (vectorFailures.length) console.warn(`${vectorFailures.length} vector table request(s) failed`, vectorFailures);
    if (state.runId && !state.runs.some(run => run.evaluationRunId === state.runId)) state.runId = null;
    if (!state.runId && state.runs.length) state.runId = state.runs[0].evaluationRunId;
    if (state.runId && state.tab === 'results') await loadRunDetail(state.runId);
  }

  async function loadRunDetail(runId) {
    state.runDetail = await request(`/runs/${encodeURIComponent(runId)}`);
    const index = state.runs.findIndex(run => run.evaluationRunId === runId);
    if (index >= 0) state.runs[index] = state.runDetail.run;
  }

  function renderAll() {
    renderRail();
    renderHeader();
    renderView();
  }

  function renderRail() {
    els.datasetList.innerHTML = state.datasets.length ? state.datasets.map(item => `
      <button type="button" class="dataset-card ${item.datasetId === state.datasetId ? 'active' : ''}" data-dataset-id="${esc(item.datasetId)}" aria-pressed="${item.datasetId === state.datasetId}">
        <div class="dataset-name">${esc(item.name)}</div>
        <div class="dataset-meta"><span>${number(item.readyCorpusCount)} 语料</span><span>${number(item.caseCount)} 问题</span><span class="dataset-status">${esc(item.status)}</span></div>
      </button>`).join('') : '<div class="empty">还没有数据集<br>点击右上角开始建立基线</div>';
  }

  function renderHeader() {
    const dataset = selectedDataset();
    const run = selectedRun();
    els.datasetTitle.textContent = dataset?.name || '选择或创建一个评测数据集';
    els.datasetDescription.textContent = dataset?.description || '建立独立语料、问题标签与可复现的召回实验。';
    els.headerDataset.textContent = dataset?.name || '未选择';
    els.headerCorpus.textContent = `${number(dataset?.readyCorpusCount)} / ${number(dataset?.corpusCount)}`;
    els.headerCases.textContent = number(dataset?.caseCount);
    els.headerRun.textContent = run ? `${run.name || run.evaluationRunId} · ${run.status}` : '—';
    els.deleteDatasetBtn.disabled = !dataset;
    document.querySelectorAll('.tab').forEach(tab => tab.classList.toggle('active', tab.dataset.tab === state.tab));
  }

  function renderView() {
    if (!state.datasetId) {
      els.view.innerHTML = '<div class="panel hero-panel"><div class="empty"><div><h3>请选择或创建测试数据集</h3><p>导入记忆数据与测试问题，然后配置参数并开始批量召回。</p></div></div></div>';
      return;
    }
    const renderers = { corpus: renderCorpus, cases: renderCases, experiment: renderExperiment, results: renderResults };
    if (!renderers[state.tab]) state.tab = 'corpus';
    els.view.innerHTML = renderers[state.tab]();
  }

  function renderOverview() {
    const dataset = selectedDataset();
    const latest = state.runs[0];
    const readyRate = dataset?.corpusCount ? (dataset.readyCorpusCount || 0) / dataset.corpusCount : 0;
    return `<div class="grid">
      <section class="panel hero-panel"><div class="panel-body"><div class="hero-copy"><h3>把召回链路变成可测量的工程对象</h3><p>每条向量都带有精确的数据集隔离标记。实验只运行 Memory/RAG 召回，以及可选的 Context Planner；不会进入 MainNode，也不会产生正式会话。</p></div><div class="hero-numbers"><div class="number-card"><strong>${pct(readyRate)}</strong><span>语料就绪率</span></div><div class="number-card"><strong>${number(state.cases.length)}</strong><span>已标注问题</span></div><div class="number-card"><strong>${number(state.runs.length)}</strong><span>实验版本</span></div></div></div></section>
      <div class="grid three">
        ${overviewCard('01 · 语料', '导入真实 RAG 文档、长期记忆或用户偏好。', `${dataset.readyCorpusCount || 0} ready`, 'corpus')}
        ${overviewCard('02 · 标签', '为每个问题指定期望语料与相关性等级。', `${state.cases.length} cases`, 'cases')}
        ${overviewCard('03 · 实验', '调整 topK、阈值、向量/混合召回和 Planner。', latest ? latest.status : 'not started', 'experiment')}
      </div>
      ${latest ? `<section class="panel"><div class="panel-head"><div><h3>最近一次实验</h3><p>${esc(latest.name || latest.evaluationRunId)}</p></div>${statePill(latest.status)}</div><div class="panel-body"><div class="metric-grid">${metric('Hit Rate@K', pct(latest.metrics?.hitRateAtK))}${metric('MRR', (latest.metrics?.meanReciprocalRank ?? 0).toFixed(3))}${metric('nDCG@K', (latest.metrics?.ndcgAtK ?? 0).toFixed(3))}${metric('P95 Latency', `${number(latest.metrics?.retrievalLatencyP95Ms)} ms`)}</div></div></section>` : ''}
    </div>`;
  }

  function overviewCard(title, description, value, tab) {
    return `<section class="panel"><div class="panel-body"><div class="eyebrow">${esc(value)}</div><h3>${esc(title)}</h3><p class="hint">${esc(description)}</p><button class="btn small" data-go-tab="${tab}">进入模块 →</button></div></section>`;
  }

  function renderCorpus() {
    const types = [['RAG_DOCUMENT', 'RAG 数据'], ['LONG_TERM_MEMORY', '长期记忆'], ['USER_PREFERENCE', '用户偏好']];
    const type = state.corpusType;
    const items = state.corpus.filter(item => item.itemType === type);
    const vectors = state.vectors[type] || [];
    const label = types.find(item => item[0] === type)?.[1] || type;
    return `<section class="panel">
      <div class="subtabs">${types.map(([value, text]) => `<button class="subtab ${value === type ? 'active' : ''}" data-corpus-type="${value}">${text} <span class="tag">${state.corpus.filter(item => item.itemType === value).length}</span></button>`).join('')}</div>
      <div class="panel-head"><div><h3>${label}表</h3><p>表格同时展示评测记录与 PGVector 中按 evalDatasetId 查询到的真实向量行。</p></div><div class="actions"><button class="btn primary" data-open-import="corpus" data-corpus-type="${type}">导入 ${label}</button></div></div>
      <div class="panel-body">${items.length ? `<table class="table"><thead><tr><th>自定义数字ID</th><th>内容</th><th>真实向量库记录</th><th>状态</th><th>操作</th></tr></thead><tbody>${items.map(item => corpusRow(item, vectors)).join('')}</tbody></table>` : `<div class="empty">尚未导入${label}。点击右上角导入JSONL或CSV。</div>`}</div>
    </section>`;
  }

  function corpusRow(item, vectors) {
    const stored = vectors.filter(record => record.externalId === item.externalId);
    const preview = stored[0]?.content || item.summary || '';
    return `<tr><td><div class="primary-text">${esc(item.externalId)}</div><div class="secondary-text">${esc(item.title || item.itemType)}</div></td><td><div class="primary-text">${esc(item.summary || item.title || '—')}</div><div class="secondary-text" title="${esc(preview)}">${esc(preview || '—')}</div></td><td>${stored.length ? `<details><summary>${stored.length} 条真实向量记录</summary><div class="db-records">${stored.map(vectorRecord).join('')}</div></details>` : '<span class="state failed">向量库无记录</span>'}</td><td>${statePill(item.status)}${item.failureMessage ? `<div class="secondary-text">${esc(item.failureCode)} · ${esc(item.failureMessage)}</div>` : ''}</td><td><div class="actions"><button class="btn small" data-reindex="${esc(item.corpusItemId)}">重建索引</button><button class="btn small danger" data-disable-corpus="${esc(item.corpusItemId)}">停用</button></div></td></tr>`;
  }

  function vectorRecord(item) {
    return `<div class="db-record"><div><strong>${esc(item.collectionType)}</strong> · ${number(item.embeddingDimensions)}维</div><div>sourceId: <code>${esc(item.sourceId)}</code></div><div>vectorId: <code>${esc(item.vectorId)}</code></div></div>`;
  }

  function renderCases() {
    return `<section class="panel"><div class="panel-head"><div><h3>测试问题表</h3><p>问题ID和期望命中ID都使用5–12位数字字符串；导入时会验证期望数据已成功入库。</p></div><button class="btn primary" data-open-import="cases">导入测试问题</button></div><div class="panel-body">
      ${state.cases.length ? `<table class="table"><thead><tr><th>问题ID</th><th>用户问题</th><th>召回范围</th><th>期望命中ID</th><th>场景标签</th><th>状态</th></tr></thead><tbody>${state.cases.map(caseRow).join('')}</tbody></table>` : '<div class="empty">还没有测试问题。请先导入已经标注正确答案的问题集。</div>'}
      </div></section>`;
  }

  function caseRow(item) {
    const expected = (item.expected || []).map(value => value.externalId || value.sourceId).filter(Boolean);
    return `<tr><td><div class="primary-text">${esc(item.externalId)}</div></td><td><div class="primary-text">${esc(item.query)}</div></td><td><span class="tag">${esc(item.sourceScope || 'MIXED')}</span></td><td>${expected.map(value => `<span class="tag">${esc(value)}</span>`).join(' ') || '—'}</td><td>${(item.tags || []).map(value => `<span class="tag">${esc(value)}</span>`).join(' ')}</td><td>${statePill(item.status)}</td></tr>`;
  }

  function renderExperiment() {
    const ready = state.corpus.length > 0 && state.corpus.every(item => item.status === 'READY') && state.cases.length > 0;
    return `<div class="grid two"><form class="panel" id="runForm"><div class="panel-head"><div><h3>召回参数</h3><p>参数会作为不可变快照保存在 Run 中。</p></div></div><div class="panel-body form-grid">
      <div class="field wide"><label>实验名称</label><input class="input" name="name" value="baseline-${new Date().toISOString().slice(0,10)}" required></div>
      <div class="field"><label>召回范围</label><select name="sourceScope"><option>MIXED</option><option>RAG</option><option>MEMORY</option></select></div>
      <div class="field"><label>召回模式</label><select name="retrievalMode"><option>HYBRID</option><option>VECTOR</option></select></div>
      <div class="field"><label>Top K</label><input class="input" name="topK" type="number" min="1" max="200" value="10"></div>
      <div class="field"><label>最低相似度</label><input class="input" name="minScore" type="number" min="-1" max="1" step="0.01" value="0.20"></div>
      <div class="field"><label>问题上限</label><input class="input" name="caseLimit" type="number" min="1" value="1000"></div>
      <div class="field"><label>单例超时（ms）</label><input class="input" name="caseTimeoutMs" type="number" min="100" value="30000"></div>
      <div class="field wide"><label>向量集合（逗号分隔；留空使用范围默认值）</label><input class="input" name="collectionTypes" placeholder="RAG_CHUNK,LONG_TERM_MEMORY"></div>
      <div class="field wide"><label>Context Planner 模式</label><div class="planner-options">
        <label class="planner-option"><strong><input type="radio" name="plannerMode" value="off" checked>关闭 Context Planner</strong><span>只统计原始向量/混合召回结果。</span></label>
        <label class="planner-option"><strong><input type="radio" name="plannerMode" value="on">开启 Context Planner</strong><span>保留原始候选，再统计Planner筛选后的准确率与错误剔除情况。</span></label>
      </div></div>
      <div class="field"><label>Planner Model</label><input class="input" name="plannerModelCode" placeholder="留空使用组件默认模型"></div>
      <div class="field"><label>Temperature</label><input class="input" name="plannerTemperature" type="number" min="0" max="2" step="0.1" value="0.1"></div>
      <div class="field wide"><button class="btn primary" type="submit" ${ready ? '' : 'disabled'}>开始批量测试</button><span class="hint">${ready ? `将运行 ${state.cases.length} 个问题；当前 ${state.corpus.length} 条记忆数据全部READY。` : '请先导入记忆数据和测试问题，并确保全部记忆数据为READY。'}</span></div>
      </div></form>
      <section class="panel"><div class="panel-head"><div><h3>最近运行</h3><p>运行在有界线程池中异步执行，单个问题失败不会中止批次。</p></div></div><div class="panel-body"><div class="run-list">${state.runs.length ? state.runs.slice(0,10).map(runCard).join('') : '<div class="empty">尚未运行实验</div>'}</div></div></section></div>`;
  }

  function runCard(run) {
    const total = Number(run.totalCaseCount || 0), complete = Number(run.completedCaseCount || 0);
    const progress = total ? Math.min(100, complete / total * 100) : 0;
    return `<article class="run-card ${run.evaluationRunId === state.runId ? 'selected' : ''}" data-run-id="${esc(run.evaluationRunId)}"><div><div class="primary-text">${esc(run.name || run.evaluationRunId)}</div><div class="secondary-text">topK=${esc(run.config?.topK)} · ${esc(run.config?.retrievalMode)} · planner=${run.config?.plannerEnabled ? 'on' : 'off'}</div><div class="progress"><i style="width:${progress}%"></i></div></div><div>${statePill(run.status)}<div class="secondary-text">${complete}/${total || '?'}</div></div></article>`;
  }

  function renderResults() {
    const run = selectedRun();
    if (!run) return '<div class="panel"><div class="empty">先运行一个实验，或从历史记录中选择 Run。</div></div>';
    const detail = state.runDetail?.run?.evaluationRunId === run.evaluationRunId ? state.runDetail : null;
    const metrics = detail?.metrics || run.metrics;
    const grouped = detail ? logic.groupRunResults(detail.results, detail.hits) : [];
    const plannerEnabled = Boolean(run.config?.plannerEnabled);
    return `<div class="grid">
      <section class="panel"><div class="panel-head"><div><h3>${esc(run.name || run.evaluationRunId)}</h3><p>${esc(run.evaluationRunId)} · ${esc(run.config?.sourceScope)} / ${esc(run.config?.retrievalMode)} · Context Planner ${plannerEnabled ? '开启' : '关闭'}</p></div><div class="actions">${statePill(run.status)}${['RUNNING','PENDING'].includes(run.status) ? `<button class="btn small danger" data-cancel-run="${esc(run.evaluationRunId)}">取消</button>` : ''}</div></div><div class="panel-body">${plannerEnabled ? plannerMetricComparison(metrics) : `<div class="metric-grid">${rawMetrics(metrics)}</div>`}</div></section>
      ${plannerEnabled ? `<section class="panel"><div class="panel-head"><div><h3>Context Planner 筛选质量</h3><p>用于判断Planner是否保留正确候选、剔除无关候选，以及Prompt是否需要优化。</p></div></div><div class="panel-body"><div class="metric-grid">${metric('正确候选保留率', pct(metrics?.plannerRelevantRetentionRate))}${metric('无关候选剔除率', pct(metrics?.plannerIrrelevantRemovalRate))}${metric('正确候选误删', number(metrics?.plannerRelevantDroppedCount))}${metric('平均保留候选数', Number(metrics?.plannerAverageSelectedCount || 0).toFixed(1))}${metric('Planner失败率', pct(metrics?.plannerFailureRate))}${metric('Planner平均耗时', `${number(metrics?.plannerLatencyAverageMs)} ms`)}</div></div></section>` : ''}
      <section class="panel"><div class="panel-head"><div><h3>逐问题测试结果</h3><p>展开问题，直接对比期望数字ID、原始召回ID和Planner保留/剔除结果。</p></div><span class="tag">${grouped.length} 个问题</span></div><div class="panel-body grid">${grouped.length ? grouped.map(item => resultCard(item, plannerEnabled)).join('') : '<div class="empty">运行中，结果会自动更新。</div>'}</div></section>
    </div>`;
  }

  function rawMetrics(metrics) {
    return `${metric('Hit Rate@K', pct(metrics?.hitRateAtK))}${metric('Precision@K', pct(metrics?.precisionAtK))}${metric('Recall@K', pct(metrics?.recallAtK))}${metric('MRR', Number(metrics?.meanReciprocalRank || 0).toFixed(3))}${metric('nDCG@K', Number(metrics?.ndcgAtK || 0).toFixed(3))}${metric('MAP@K', Number(metrics?.mapAtK || 0).toFixed(3))}${metric('No Hit', pct(metrics?.noHitRate))}${metric('P95耗时', `${number(metrics?.retrievalLatencyP95Ms)} ms`)}`;
  }

  function plannerMetricComparison(metrics) {
    const rows = [
      ['命中率', metrics?.hitRateAtK, metrics?.plannerHitRateAtK, true],
      ['Precision@K', metrics?.precisionAtK, metrics?.plannerPrecision, true],
      ['Recall@K', metrics?.recallAtK, metrics?.plannerRecall, true],
      ['MRR', metrics?.meanReciprocalRank, metrics?.plannerMeanReciprocalRank, false],
      ['nDCG@K', metrics?.ndcgAtK, metrics?.plannerNdcgAtK, false]
    ];
    return `<table class="metric-compare"><thead><tr><th>准确率指标</th><th>原始召回</th><th>Planner筛选后</th><th>变化</th></tr></thead><tbody>${rows.map(([label, raw, planned, percent]) => { const delta = logic.metricDelta(raw, planned); const format = value => percent ? pct(value) : Number(value || 0).toFixed(3); return `<tr><td>${label}</td><td>${format(raw)}</td><td>${format(planned)}</td><td class="delta ${delta > 0 ? 'up' : delta < 0 ? 'down' : ''}">${delta > 0 ? '+' : ''}${percent ? pct(delta) : delta.toFixed(3)}</td></tr>`; }).join('')}</tbody></table>`;
  }

  function resultCard(item, plannerEnabled) {
    const testCase = state.cases.find(value => value.caseId === item.caseId);
    const expected = (testCase?.expected || []).map(value => value.externalId || value.sourceId).filter(Boolean);
    const selected = item.hits.filter(hit => hit.selectedByPlanner);
    const plannerHit = selected.some(hit => hit.expectedGrade);
    return `<details class="result-card"><summary><span>${item.hit ? '✓' : '—'}</span><div><div class="primary-text">${esc(testCase?.query || item.caseId)}</div><div class="secondary-text">问题ID ${esc(testCase?.externalId || item.caseId)} · 期望 ${expected.map(esc).join(', ') || '—'}</div></div><div><span class="hint">原始结果</span><br>${item.hit ? '命中' : '未命中'}</div><div><span class="hint">Planner结果</span><br>${plannerEnabled ? (plannerHit ? '命中' : '未命中') : '未启用'}</div><div><span class="hint">耗时</span><br>${number(item.retrievalLatencyMs)} ms</div></summary><div class="result-body">${item.failureMessage ? `<p class="state failed">${esc(item.failureMessage)}</p>` : ''}<p><strong>期望命中ID：</strong>${expected.map(value => `<span class="tag">${esc(value)}</span>`).join(' ') || '—'}</p><h4>原始召回候选</h4>${item.hits.length ? item.hits.map(hit => hitRow(hit, plannerEnabled)).join('') : '<div class="hint">没有候选命中</div>'}${plannerEnabled ? `<h4>Context Planner 结果</h4><p>${statePill(item.plannerStatus || 'UNKNOWN')} · ${esc(item.plannerReason || '没有返回筛选理由')}</p><p><strong>保留：</strong>${selected.map(hit => `<span class="tag">${esc(hit.externalId || hit.sourceId)}</span>`).join(' ') || '无'}</p><details><summary>查看Planner完整结构化输出</summary><pre>${esc(JSON.stringify(item.plannerOutput || {}, null, 2))}</pre></details>` : ''}</div></details>`;
  }

  function hitRow(hit, plannerEnabled) {
    const plannerState = plannerEnabled ? (hit.selectedByPlanner ? '<span class="candidate-state keep">Planner保留</span>' : '<span class="candidate-state drop">Planner剔除</span>') : '';
    return `<div class="hit-row"><span class="rank">#${esc(hit.rankNo)}</span><span class="tag">${esc(hit.retrievalChannel)}</span><div><div class="primary-text">ID ${esc(hit.externalId || '未映射')} ${hit.expectedGrade ? '<span class="state ready">正确答案</span>' : ''}</div><div class="secondary-text">sourceId ${esc(hit.sourceId)} · ${esc(hit.collectionType)} · parent ${esc(hit.parentSourceId || '—')}</div></div><span>${Number(hit.score || 0).toFixed(4)}</span><span>${plannerState || (hit.expectedGrade ? `grade ${hit.expectedGrade}` : '—')}</span></div>`;
  }

  function comparisonBody(value) {
    const fields = [['Hit Rate','hitRateAtK'],['Precision','precisionAtK'],['Recall','recallAtK'],['MRR','meanReciprocalRank'],['nDCG','ndcgAtK'],['MAP','mapAtK'],['No Hit','noHitRate']];
    return `<div class="panel-body"><div class="metric-grid">${fields.map(([label,key]) => { const delta = value.metricDeltas?.[key] ?? logic.metricDelta(value.leftMetrics?.[key], value.rightMetrics?.[key]); return `<div class="metric"><label>${label} Δ</label><strong class="delta ${delta > 0 ? 'up' : delta < 0 ? 'down' : ''}">${delta > 0 ? '+' : ''}${delta.toFixed(3)}</strong><small>${Number(value.leftMetrics?.[key] || 0).toFixed(3)} → ${Number(value.rightMetrics?.[key] || 0).toFixed(3)}</small></div>`; }).join('')}</div></div>`;
  }

  async function selectDataset(datasetId) {
    state.datasetId = datasetId; state.tab = 'corpus'; state.runId = null; state.runDetail = null; state.comparison = null;
    state.corpus = []; state.cases = []; state.runs = [];
    state.vectors = { RAG_DOCUMENT: [], LONG_TERM_MEMORY: [], USER_PREFERENCE: [] };
    updateUrl(); renderAll();
    await loadDatasetData(datasetId); renderAll();
  }

  function setTab(tab) {
    state.tab = tab; state.comparison = null; updateUrl();
    if (tab === 'results' && state.runId) loadRunDetail(state.runId).then(renderAll).catch(showError);
    renderAll();
  }

  async function pollActiveRun() {
    const run = selectedRun();
    if (!run || !['PENDING','RUNNING'].includes(run.status) || state.polling) return;
    state.polling = true;
    try {
      await loadRunDetail(run.evaluationRunId);
      await loadDatasets();
      const editing = ['INPUT','TEXTAREA','SELECT'].includes(document.activeElement?.tagName) || document.querySelector('.modal.open');
      if (!editing) {
        const scrollTop = els.view.scrollTop;
        renderAll();
        els.view.scrollTop = scrollTop;
      } else {
        renderHeader(); renderRail();
      }
    } catch (error) {
      console.warn('Recall evaluation polling failed', error);
    } finally { state.polling = false; }
  }

  function openModal(modal) { modal.classList.add('open'); modal.setAttribute('aria-hidden', 'false'); }
  function closeModals() { document.querySelectorAll('.modal.open').forEach(modal => { modal.classList.remove('open'); modal.setAttribute('aria-hidden','true'); }); }
  function openImport(target, corpusType = null) {
    state.importTarget = target; els.importText.value = '';
    state.importCorpusType = corpusType;
    els.importFilePicker.value = '';
    els.importFileName.textContent = '尚未选择文件';
    els.importFileName.classList.remove('ready');
    const labels = { RAG_DOCUMENT: 'RAG数据', LONG_TERM_MEMORY: '长期记忆', USER_PREFERENCE: '用户偏好' };
    els.importModalTitle.textContent = target === 'corpus' ? `批量导入${labels[corpusType] || '记忆数据'}` : '批量导入测试问题';
    els.importHint.textContent = target === 'corpus' ? `必填：externalId（5–12位数字字符串）、content。当前表格类型：${corpusType || '由type字段决定'}。` : '必填：externalId、query、expected；问题ID与expected.externalId都使用数字字符串。';
    openModal(els.importModal);
  }

  function normalizeImported(raw) {
    const normalizedRaw = String(raw || '').replace(/^\uFEFF/, '').trim();
    if (!normalizedRaw) throw new Error('请先选择数据文件，或粘贴需要导入的内容。');
    let items;
    if (els.importFormat.value === 'json') {
      const parsed = JSON.parse(normalizedRaw);
      const values = Array.isArray(parsed) ? parsed : parsed?.items;
      if (!Array.isArray(values)) throw new Error('JSON 文件必须是数组，或包含 items 数组。');
      items = { items: values, errors: [] };
    } else {
      items = els.importFormat.value === 'jsonl' ? logic.parseJsonl(normalizedRaw) : { items: logic.parseCsv(normalizedRaw), errors: [] };
    }
    if (items.errors.length) throw new Error(`第 ${items.errors[0].line} 行解析失败：${items.errors[0].message}`);
    return items.items.map(item => ({ ...item,
      tags: Array.isArray(item.tags) ? item.tags : String(item.tags || '').split('|').filter(Boolean),
      expected: Array.isArray(item.expected) ? item.expected : item.expected ? JSON.parse(item.expected) : undefined
    }));
  }

  async function submitImport(event) {
    event.preventDefault();
    const items = normalizeImported(els.importText.value);
    const path = state.importTarget === 'corpus' ? 'corpus' : 'cases';
    if (state.importTarget === 'corpus' && state.importCorpusType) {
      items.forEach(item => { item.type = state.importCorpusType; });
    }
    const result = await request(`/datasets/${encodeURIComponent(state.datasetId)}/${path}/batch`, { method:'POST', body: JSON.stringify({ items }), timeoutMs: 300000 });
    closeModals(); showToast(`已导入 ${result.acceptedCount} 项，失败 ${result.failedCount} 项`, result.failedCount ? 'error' : '');
    await refreshSelected();
  }

  async function startRun(form) {
    const data = new FormData(form);
    const payload = {
      datasetId: state.datasetId, name: data.get('name'), sourceScope: data.get('sourceScope'),
      retrievalMode: data.get('retrievalMode'), topK: Number(data.get('topK')), minScore: Number(data.get('minScore')),
      caseLimit: Number(data.get('caseLimit')), caseTimeoutMs: Number(data.get('caseTimeoutMs')),
      collectionTypes: String(data.get('collectionTypes') || '').split(',').map(value => value.trim()).filter(Boolean),
      plannerEnabled: data.get('plannerMode') === 'on', plannerModelCode: data.get('plannerModelCode') || null,
      plannerTemperature: Number(data.get('plannerTemperature')), plannerMaxOutputTokens: 3000
    };
    const run = await request('/runs', { method:'POST', body: JSON.stringify(payload) });
    state.runId = run.evaluationRunId; state.runs.unshift(run); state.tab = 'results';
    await loadRunDetail(state.runId); updateUrl(); renderAll(); showToast('评测任务已进入执行队列');
  }

  async function refreshSelected() { await loadDatasets(); await loadDatasetData(); renderAll(); }
  function updateUrl() { const next = new URL(location.href); state.datasetId ? next.searchParams.set('datasetId', state.datasetId) : next.searchParams.delete('datasetId'); state.runId ? next.searchParams.set('runId', state.runId) : next.searchParams.delete('runId'); next.searchParams.set('tab', state.tab); next.searchParams.set('corpusType', state.corpusType); history.replaceState(null, '', next); }
  let toastTimer;
  function showToast(message, type = '') { clearTimeout(toastTimer); els.toast.textContent = message; els.toast.className = `toast show ${type}`; toastTimer = setTimeout(() => els.toast.className = 'toast', 3200); }
  function showError(error) { showToast(error?.message || String(error), 'error'); }

  document.addEventListener('click', async event => {
    const dataset = event.target.closest('[data-dataset-id]'); if (dataset) return selectDataset(dataset.datasetId).catch(showError);
    const tab = event.target.closest('[data-tab]'); if (tab) return setTab(tab.dataset.tab);
    const corpusType = event.target.closest('[data-corpus-type]'); if (corpusType && !corpusType.matches('[data-open-import]')) { state.corpusType = corpusType.dataset.corpusType; updateUrl(); return renderView(); }
    const goTab = event.target.closest('[data-go-tab]'); if (goTab) return setTab(goTab.dataset.goTab);
    const importButton = event.target.closest('[data-open-import]'); if (importButton) return openImport(importButton.dataset.openImport, importButton.dataset.corpusType || null);
    const run = event.target.closest('[data-run-id]'); if (run) { state.runId = run.dataset.runId; state.tab = 'results'; updateUrl(); await loadRunDetail(state.runId); return renderAll(); }
    const reindex = event.target.closest('[data-reindex]'); if (reindex) { await request(`/datasets/${encodeURIComponent(state.datasetId)}/corpus/${encodeURIComponent(reindex.dataset.reindex)}/reindex`, {method:'POST'}); showToast('索引已重建'); return refreshSelected(); }
    const disable = event.target.closest('[data-disable-corpus]'); if (disable && confirm('停用这条评测语料及其向量索引？')) { await request(`/datasets/${encodeURIComponent(state.datasetId)}/corpus/${encodeURIComponent(disable.dataset.disableCorpus)}`, {method:'DELETE'}); return refreshSelected(); }
    const cancel = event.target.closest('[data-cancel-run]'); if (cancel) { await request(`/runs/${encodeURIComponent(cancel.dataset.cancelRun)}/cancel`, {method:'POST'}); showToast('已请求取消'); return refreshSelected(); }
    if (event.target.closest('#chooseImportFileBtn')) return els.importFilePicker.click();
    if (event.target.closest('#compareBtn')) { const left = document.getElementById('compareLeft')?.value; if (left) { state.comparison = await request(`/compare?leftRunId=${encodeURIComponent(left)}&rightRunId=${encodeURIComponent(state.runId)}`); return renderView(); } }
    if (event.target.closest('[data-close-modal]') || (event.target.classList.contains('modal'))) return closeModals();
  });

  document.addEventListener('submit', event => {
    if (event.target.id === 'datasetForm') { event.preventDefault(); const data = new FormData(event.target); request('/datasets', {method:'POST', body:JSON.stringify({name:data.get('name'),description:data.get('description')})}).then(async dataset => { closeModals(); await loadDatasets(); await selectDataset(dataset.datasetId); showToast('数据集已创建'); }).catch(showError); }
    if (event.target.id === 'importForm') submitImport(event).catch(showError);
    if (event.target.id === 'runForm') { event.preventDefault(); startRun(event.target).catch(showError); }
  });

  els.importFilePicker.addEventListener('change', async () => {
    const file = els.importFilePicker.files?.[0];
    if (!file) return;
    try {
      if (file.size > 10 * 1024 * 1024) throw new Error('导入文件不能超过 10 MB。');
      const extension = file.name.split('.').pop()?.toLowerCase();
      els.importFormat.value = extension === 'csv' ? 'csv' : extension === 'json' ? 'json' : 'jsonl';
      els.importText.value = await file.text();
      els.importFileName.textContent = `${file.name} · ${(file.size / 1024).toFixed(1)} KB · 已读取`;
      els.importFileName.classList.add('ready');
    } catch (error) {
      els.importFilePicker.value = '';
      els.importFileName.textContent = '文件读取失败';
      els.importFileName.classList.remove('ready');
      showError(error);
    }
  });

  document.getElementById('newDatasetBtn').addEventListener('click', () => openModal(els.datasetModal));
  document.getElementById('railAddBtn').addEventListener('click', () => openModal(els.datasetModal));
  document.getElementById('refreshBtn').addEventListener('click', () => refreshSelected().then(() => showToast('数据已刷新')).catch(showError));
  els.deleteDatasetBtn.addEventListener('click', async () => { if (!selectedDataset() || !confirm('停用整个评测数据集，并禁用其全部向量索引？')) return; try { await request(`/datasets/${encodeURIComponent(state.datasetId)}`, {method:'DELETE'}); state.datasetId = null; state.runId = null; await loadDatasets(); renderAll(); } catch (error) { showError(error); } });

  bootstrap().catch(showError);
})();
