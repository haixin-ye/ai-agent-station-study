(() => {
  const logic = window.RecallEvaluationLogic;
  const params = new URLSearchParams(location.search);
  const API_BASE = (params.get('api') || location.origin).replace(/\/$/, '');
  const API = `${API_BASE}/api/v1/dev/recall-evaluations`;
  const state = {
    tab: 'overview', datasets: [], datasetId: params.get('datasetId'), corpus: [], cases: [], runs: [],
    runId: params.get('runId'), runDetail: null, comparison: null, importTarget: null, polling: false
  };
  const els = {
    view: document.getElementById('view'), datasetList: document.getElementById('datasetList'),
    datasetTitle: document.getElementById('datasetTitle'), datasetDescription: document.getElementById('datasetDescription'),
    headerDataset: document.getElementById('headerDataset'), headerCorpus: document.getElementById('headerCorpus'),
    headerCases: document.getElementById('headerCases'), headerRun: document.getElementById('headerRun'),
    deleteDatasetBtn: document.getElementById('deleteDatasetBtn'), datasetModal: document.getElementById('datasetModal'),
    importModal: document.getElementById('importModal'), importModalTitle: document.getElementById('importModalTitle'),
    importText: document.getElementById('importText'), importFormat: document.getElementById('importFormat'),
    importHint: document.getElementById('importHint'), filePicker: document.getElementById('filePicker'),
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
    const timeout = window.setTimeout(() => controller.abort(), 12000);
    try {
      const init = { ...options, signal: options.signal || controller.signal, headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(options.headers || {}) } };
      const response = await fetch(`${API}${path}`, init);
      const payload = await response.json().catch(() => null);
      if (!response.ok || !payload || payload.code !== '0000') throw new Error(payload?.info || `HTTP ${response.status}`);
      return payload.data;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('后端接口连接超时，请确认 dev 服务已启动。');
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

  async function loadDatasetData() {
    if (!state.datasetId) return;
    const id = encodeURIComponent(state.datasetId);
    [state.corpus, state.cases, state.runs] = await Promise.all([
      request(`/datasets/${id}/corpus?limit=1000`),
      request(`/datasets/${id}/cases?limit=1000`),
      request(`/runs?datasetId=${id}&limit=100`)
    ]);
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
      <button class="dataset-card ${item.datasetId === state.datasetId ? 'active' : ''}" data-dataset-id="${esc(item.datasetId)}">
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
      els.view.innerHTML = '<div class="panel hero-panel"><div class="empty"><div><h3>从一个隔离的数据集开始</h3><p>导入真实 RAG / 长期记忆，建立期望标签，然后批量测量召回质量。</p></div></div></div>';
      return;
    }
    const renderers = { overview: renderOverview, corpus: renderCorpus, cases: renderCases, experiment: renderExperiment, results: renderResults };
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
    return `<div class="grid">
      <section class="panel"><div class="panel-head"><div><h3>评测语料</h3><p>真实写入 RAG / Memory 存储，并以 evalDatasetId 隔离。</p></div><div class="actions"><button class="btn" id="uploadFilesBtn">上传文档</button><button class="btn primary" data-open-import="corpus">JSONL / CSV 导入</button></div></div>
      <div class="panel-body">${state.corpus.length ? `<table class="table"><thead><tr><th>语料</th><th>类型</th><th>真实来源</th><th>状态</th><th>操作</th></tr></thead><tbody>${state.corpus.map(corpusRow).join('')}</tbody></table>` : '<div class="empty">尚未导入语料。支持文本/Markdown 文件，或结构化批量导入。</div>'}</div></section>
      <section class="panel"><div class="panel-head"><div><h3>批量字段约定</h3><p>不要提交原始数据库 ID；externalId 是标注和 A/B 对比的稳定键。</p></div></div><div class="panel-body"><code class="hint">{"externalId":"refund-policy","type":"RAG_DOCUMENT","title":"退款规则","content":"...","tags":["policy"]}</code><br><br><code class="hint">type: RAG_DOCUMENT | LONG_TERM_MEMORY | USER_PREFERENCE</code></div></section>
    </div>`;
  }

  function corpusRow(item) {
    return `<tr><td><div class="primary-text">${esc(item.title || item.externalId)}</div><div class="secondary-text">${esc(item.externalId)}${item.failureMessage ? ` · ${esc(item.failureCode)}: ${esc(item.failureMessage)}` : ''}</div></td><td><span class="tag">${esc(item.itemType)}</span></td><td><div class="primary-text">${esc(item.sourceId || '—')}</div><div class="secondary-text">${number(item.sourceRefs?.length)} vector refs</div></td><td>${statePill(item.status)}</td><td><div class="actions"><button class="btn small" data-reindex="${esc(item.corpusItemId)}">重建索引</button><button class="btn small danger" data-disable-corpus="${esc(item.corpusItemId)}">停用</button></div></td></tr>`;
  }

  function renderCases() {
    return `<div class="grid"><section class="panel"><div class="panel-head"><div><h3>问题与期望标签</h3><p>标签可按精确 source 或 RAG 父文档匹配，grade 取 1–3。</p></div><button class="btn primary" data-open-import="cases">JSONL / CSV 导入</button></div><div class="panel-body">
      ${state.cases.length ? `<table class="table"><thead><tr><th>问题</th><th>范围</th><th>期望命中</th><th>标签</th><th>状态</th></tr></thead><tbody>${state.cases.map(caseRow).join('')}</tbody></table>` : '<div class="empty">还没有评测问题。导入问题并标注期望命中的 externalId。</div>'}
      </div></section><section class="panel"><div class="panel-head"><div><h3>标注示例</h3><p>expected.externalId 会在运行时解析为该数据集内的真实 sourceId。</p></div></div><div class="panel-body"><code class="hint">{"externalId":"q-refund","query":"退款需要多久？","sourceScope":"RAG","expected":[{"externalId":"refund-policy","grade":3,"matchMode":"PARENT_DOCUMENT"}]}</code></div></section></div>`;
  }

  function caseRow(item) {
    const expected = (item.expected || []).map(value => value.externalId || value.sourceId).filter(Boolean);
    return `<tr><td><div class="primary-text">${esc(item.query)}</div><div class="secondary-text">${esc(item.externalId)}</div></td><td><span class="tag">${esc(item.sourceScope || 'MIXED')}</span></td><td>${expected.map(value => `<span class="tag">${esc(value)}</span>`).join(' ') || '—'}</td><td>${(item.tags || []).map(value => `<span class="tag">${esc(value)}</span>`).join(' ')}</td><td>${statePill(item.status)}</td></tr>`;
  }

  function renderExperiment() {
    return `<div class="grid two"><form class="panel" id="runForm"><div class="panel-head"><div><h3>召回参数</h3><p>参数会作为不可变快照保存在 Run 中。</p></div></div><div class="panel-body form-grid">
      <div class="field wide"><label>实验名称</label><input class="input" name="name" value="baseline-${new Date().toISOString().slice(0,10)}" required></div>
      <div class="field"><label>召回范围</label><select name="sourceScope"><option>MIXED</option><option>RAG</option><option>MEMORY</option></select></div>
      <div class="field"><label>召回模式</label><select name="retrievalMode"><option>HYBRID</option><option>VECTOR</option></select></div>
      <div class="field"><label>Top K</label><input class="input" name="topK" type="number" min="1" max="200" value="10"></div>
      <div class="field"><label>最低相似度</label><input class="input" name="minScore" type="number" min="-1" max="1" step="0.01" value="0.20"></div>
      <div class="field"><label>问题上限</label><input class="input" name="caseLimit" type="number" min="1" value="1000"></div>
      <div class="field"><label>单例超时（ms）</label><input class="input" name="caseTimeoutMs" type="number" min="100" value="30000"></div>
      <div class="field wide"><label>向量集合（逗号分隔；留空使用范围默认值）</label><input class="input" name="collectionTypes" placeholder="RAG_CHUNK,LONG_TERM_MEMORY"></div>
      <div class="field wide"><label><input type="checkbox" name="plannerEnabled"> 启用 Context Planner 复选（随后立即停止）</label></div>
      <div class="field"><label>Planner Model</label><input class="input" name="plannerModelCode" placeholder="留空使用组件默认模型"></div>
      <div class="field"><label>Temperature</label><input class="input" name="plannerTemperature" type="number" min="0" max="2" step="0.1" value="0.1"></div>
      <div class="field wide"><button class="btn primary" type="submit" ${state.cases.length ? '' : 'disabled'}>开始批量评测</button><span class="hint"> ${state.cases.length ? `将运行 ${state.cases.length} 个问题` : '请先导入问题和标签'}</span></div>
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
    return `<div class="grid">
      <section class="panel"><div class="panel-head"><div><h3>${esc(run.name || run.evaluationRunId)}</h3><p>${esc(run.evaluationRunId)} · ${esc(run.config?.sourceScope)} / ${esc(run.config?.retrievalMode)}</p></div><div class="actions">${statePill(run.status)}${['RUNNING','PENDING'].includes(run.status) ? `<button class="btn small danger" data-cancel-run="${esc(run.evaluationRunId)}">取消</button>` : ''}</div></div><div class="panel-body"><div class="metric-grid">${metric('Hit Rate@K', pct(metrics?.hitRateAtK))}${metric('Precision@K', pct(metrics?.precisionAtK))}${metric('Recall@K', pct(metrics?.recallAtK))}${metric('MRR', Number(metrics?.meanReciprocalRank || 0).toFixed(3))}${metric('nDCG@K', Number(metrics?.ndcgAtK || 0).toFixed(3))}${metric('MAP@K', Number(metrics?.mapAtK || 0).toFixed(3))}${metric('No Hit', pct(metrics?.noHitRate))}${metric('P95 Latency', `${number(metrics?.retrievalLatencyP95Ms)} ms`)}</div></div></section>
      <section class="panel"><div class="panel-head"><div><h3>A/B 对比</h3><p>选择同一数据集中的另一次运行作为左侧基线。</p></div><div class="actions"><select class="input" id="compareLeft">${state.runs.filter(item => item.evaluationRunId !== run.evaluationRunId).map(item => `<option value="${esc(item.evaluationRunId)}">${esc(item.name || item.evaluationRunId)}</option>`).join('')}</select><button class="btn" id="compareBtn" ${state.runs.length < 2 ? 'disabled' : ''}>对比</button></div></div>${state.comparison ? comparisonBody(state.comparison) : ''}</section>
      <section class="panel"><div class="panel-head"><div><h3>逐问题诊断</h3><p>展开问题查看每个候选的真实来源、通道、分数和 Planner 选择。</p></div><span class="tag">${grouped.length} cases</span></div><div class="panel-body grid">${grouped.length ? grouped.map(resultCard).join('') : '<div class="empty">运行中，结果会在不打断当前操作的情况下更新。</div>'}</div></section>
    </div>`;
  }

  function resultCard(item) {
    const testCase = state.cases.find(value => value.caseId === item.caseId);
    return `<details class="result-card"><summary><span>${item.hit ? '✓' : '—'}</span><div><div class="primary-text">${esc(testCase?.query || item.caseId)}</div><div class="secondary-text">${esc(item.status)}${item.failureCode ? ` · ${esc(item.failureCode)}` : ''}</div></div><div><span class="hint">P@K</span><br>${Number(item.precisionAtK || 0).toFixed(3)}</div><div><span class="hint">R@K</span><br>${Number(item.recallAtK || 0).toFixed(3)}</div><div><span class="hint">Latency</span><br>${number(item.retrievalLatencyMs)} ms</div></summary><div class="result-body">${item.failureMessage ? `<p class="state failed">${esc(item.failureMessage)}</p>` : ''}${item.hits.length ? item.hits.map(hitRow).join('') : '<div class="hint">没有候选命中</div>'}</div></details>`;
  }

  function hitRow(hit) {
    return `<div class="hit-row"><span class="rank">#${esc(hit.rankNo)}</span><span class="tag">${esc(hit.retrievalChannel)}</span><div><div class="primary-text">${esc(hit.sourceId)}</div><div class="secondary-text">${esc(hit.collectionType)} · parent ${esc(hit.parentSourceId || '—')}</div></div><span>${Number(hit.score || 0).toFixed(4)}</span><span>${hit.expectedGrade ? `<span class="state ready">grade ${hit.expectedGrade}</span>` : hit.selectedByPlanner ? '<span class="state running">planner</span>' : '—'}</span></div>`;
  }

  function comparisonBody(value) {
    const fields = [['Hit Rate','hitRateAtK'],['Precision','precisionAtK'],['Recall','recallAtK'],['MRR','meanReciprocalRank'],['nDCG','ndcgAtK'],['MAP','mapAtK'],['No Hit','noHitRate']];
    return `<div class="panel-body"><div class="metric-grid">${fields.map(([label,key]) => { const delta = value.metricDeltas?.[key] ?? logic.metricDelta(value.leftMetrics?.[key], value.rightMetrics?.[key]); return `<div class="metric"><label>${label} Δ</label><strong class="delta ${delta > 0 ? 'up' : delta < 0 ? 'down' : ''}">${delta > 0 ? '+' : ''}${delta.toFixed(3)}</strong><small>${Number(value.leftMetrics?.[key] || 0).toFixed(3)} → ${Number(value.rightMetrics?.[key] || 0).toFixed(3)}</small></div>`; }).join('')}</div></div>`;
  }

  async function selectDataset(datasetId) {
    state.datasetId = datasetId; state.runId = null; state.runDetail = null; state.comparison = null;
    await loadDatasetData(); updateUrl(); renderAll();
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
  function openImport(target) {
    state.importTarget = target; els.importText.value = '';
    els.importModalTitle.textContent = target === 'corpus' ? '批量导入评测语料' : '批量导入问题与标签';
    els.importHint.textContent = target === 'corpus' ? '必填：externalId、type、content。CSV 的 tags 用 | 分隔。' : '必填：externalId、query、expected。CSV 的 expected 填 JSON 数组。';
    openModal(els.importModal);
  }

  function normalizeImported(raw) {
    const items = els.importFormat.value === 'jsonl' ? logic.parseJsonl(raw) : { items: logic.parseCsv(raw), errors: [] };
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
    const result = await request(`/datasets/${encodeURIComponent(state.datasetId)}/${path}/batch`, { method:'POST', body: JSON.stringify({ items }) });
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
      plannerEnabled: data.get('plannerEnabled') === 'on', plannerModelCode: data.get('plannerModelCode') || null,
      plannerTemperature: Number(data.get('plannerTemperature')), plannerMaxOutputTokens: 3000
    };
    const run = await request('/runs', { method:'POST', body: JSON.stringify(payload) });
    state.runId = run.evaluationRunId; state.runs.unshift(run); state.tab = 'results';
    await loadRunDetail(state.runId); updateUrl(); renderAll(); showToast('评测任务已进入执行队列');
  }

  async function refreshSelected() { await loadDatasets(); await loadDatasetData(); renderAll(); }
  function updateUrl() { const next = new URL(location.href); state.datasetId ? next.searchParams.set('datasetId', state.datasetId) : next.searchParams.delete('datasetId'); state.runId ? next.searchParams.set('runId', state.runId) : next.searchParams.delete('runId'); history.replaceState(null, '', next); }
  let toastTimer;
  function showToast(message, type = '') { clearTimeout(toastTimer); els.toast.textContent = message; els.toast.className = `toast show ${type}`; toastTimer = setTimeout(() => els.toast.className = 'toast', 3200); }
  function showError(error) { showToast(error?.message || String(error), 'error'); }

  document.addEventListener('click', async event => {
    const dataset = event.target.closest('[data-dataset-id]'); if (dataset) return selectDataset(dataset.datasetId).catch(showError);
    const tab = event.target.closest('[data-tab]'); if (tab) return setTab(tab.dataset.tab);
    const goTab = event.target.closest('[data-go-tab]'); if (goTab) return setTab(goTab.dataset.goTab);
    const importButton = event.target.closest('[data-open-import]'); if (importButton) return openImport(importButton.dataset.openImport);
    const run = event.target.closest('[data-run-id]'); if (run) { state.runId = run.dataset.runId; state.tab = 'results'; updateUrl(); await loadRunDetail(state.runId); return renderAll(); }
    const reindex = event.target.closest('[data-reindex]'); if (reindex) { await request(`/datasets/${encodeURIComponent(state.datasetId)}/corpus/${encodeURIComponent(reindex.dataset.reindex)}/reindex`, {method:'POST'}); showToast('索引已重建'); return refreshSelected(); }
    const disable = event.target.closest('[data-disable-corpus]'); if (disable && confirm('停用这条评测语料及其向量索引？')) { await request(`/datasets/${encodeURIComponent(state.datasetId)}/corpus/${encodeURIComponent(disable.dataset.disableCorpus)}`, {method:'DELETE'}); return refreshSelected(); }
    const cancel = event.target.closest('[data-cancel-run]'); if (cancel) { await request(`/runs/${encodeURIComponent(cancel.dataset.cancelRun)}/cancel`, {method:'POST'}); showToast('已请求取消'); return refreshSelected(); }
    if (event.target.closest('#uploadFilesBtn')) return els.filePicker.click();
    if (event.target.closest('#compareBtn')) { const left = document.getElementById('compareLeft')?.value; if (left) { state.comparison = await request(`/compare?leftRunId=${encodeURIComponent(left)}&rightRunId=${encodeURIComponent(state.runId)}`); return renderView(); } }
    if (event.target.closest('[data-close-modal]') || (event.target.classList.contains('modal'))) return closeModals();
  });

  document.addEventListener('submit', event => {
    if (event.target.id === 'datasetForm') { event.preventDefault(); const data = new FormData(event.target); request('/datasets', {method:'POST', body:JSON.stringify({name:data.get('name'),description:data.get('description')})}).then(async dataset => { closeModals(); await loadDatasets(); await selectDataset(dataset.datasetId); showToast('数据集已创建'); }).catch(showError); }
    if (event.target.id === 'importForm') submitImport(event).catch(showError);
    if (event.target.id === 'runForm') { event.preventDefault(); startRun(event.target).catch(showError); }
  });

  els.filePicker.addEventListener('change', async () => {
    if (!els.filePicker.files.length) return;
    const form = new FormData(); [...els.filePicker.files].forEach(file => form.append('files', file));
    try { const result = await request(`/datasets/${encodeURIComponent(state.datasetId)}/corpus/files`, {method:'POST', body:form}); showToast(`已导入 ${result.acceptedCount} 个文档`, result.failedCount ? 'error' : ''); await refreshSelected(); } catch (error) { showError(error); } finally { els.filePicker.value = ''; }
  });

  document.getElementById('newDatasetBtn').addEventListener('click', () => openModal(els.datasetModal));
  document.getElementById('railAddBtn').addEventListener('click', () => openModal(els.datasetModal));
  document.getElementById('refreshBtn').addEventListener('click', () => refreshSelected().then(() => showToast('数据已刷新')).catch(showError));
  els.deleteDatasetBtn.addEventListener('click', async () => { if (!selectedDataset() || !confirm('停用整个评测数据集，并禁用其全部向量索引？')) return; try { await request(`/datasets/${encodeURIComponent(state.datasetId)}`, {method:'DELETE'}); state.datasetId = null; state.runId = null; await loadDatasets(); renderAll(); } catch (error) { showError(error); } });

  bootstrap().catch(showError);
})();
