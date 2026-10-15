const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const avatar = r => r.photo
  ? `<img src="${esc(r.photo)}" class="rounded-circle me-3" width="48" height="48" style="object-fit:cover">`
  : `<div class="rounded-circle bg-secondary text-white d-flex align-items-center justify-content-center me-3" style="width:48px;height:48px">${esc(r.name[0])}</div>`;
const badge = r => r.status !== 'OK'
  ? `<span class="badge text-bg-danger" title="${esc(r.status)}">${esc(r.status)}</span>`
  : `<span class="badge text-bg-${r.direction === 'IN' ? 'success' : 'warning'}" title="${esc(r.direction)}">${esc(r.direction)}</span>`;
const scanState = r => r.status !== 'OK' ? 'denied' : r.direction === 'IN' ? 'in' : 'out';
const item = r => `<div class="list-group-item scan-item scan-${scanState(r)} d-flex align-items-center">${avatar(r)}
  <div class="flex-grow-1"><div class="fw-semibold">${esc(r.name)}</div><div class="small text-muted">${esc(r.code)} · ${esc(r.dept)}</div></div>
  <div class="scan-status text-center">${badge(r)}<div class="small text-muted">${esc(r.time)}</div></div></div>`;

let seenScans = null;
const scanKey = r => r.id != null ? `id:${r.id}` : JSON.stringify([r.code, r.time, r.direction, r.status]);
let loading = false;
let reloadPending = false;
async function load() {
  if (loading) { reloadPending = true; return; }
  loading = true;
  try {
  const response = await fetch('/dashboard/data', { cache: 'no-store' });
  if (!response.ok) throw new Error(`Dashboard request failed: ${response.status}`);
  const d = await response.json();
  document.getElementById('entry-only-notice').hidden = !d.entryOnly;
  document.getElementById('out-card').hidden = d.entryOnly;
  document.getElementById('occupancy-label').textContent = d.entryOnly ? 'Employees entered today' : 'Currently inside';
  document.getElementById('inside-heading').textContent = d.entryOnly ? 'Employees entered today' : 'Who is inside';
  ['inside', 'totalIn', 'totalOut', 'denied'].forEach(k => document.getElementById('c-' + k).textContent = d[k]);
  const feed = document.getElementById('feed');
  const existing = new Map(Array.from(feed.children, node => [node.dataset.scanKey, node]));
  const rows = d.recent.map(r => {
    const key = scanKey(r);
    let row = existing.get(key);
    if (!row) {
      const template = document.createElement('template');
      template.innerHTML = item(r);
      row = template.content.firstElementChild;
      row.dataset.scanKey = key;
    }
    if (seenScans !== null && !seenScans.has(key)) {
      row.classList.add('scan-new');
      row.addEventListener('animationend', () => row.classList.remove('scan-new'), { once: true });
    }
    return row;
  });
  if (rows.length) {
    // Keep existing nodes in place so refreshes do not interrupt their animation.
    rows.forEach((row, index) => {
      if (feed.children[index] !== row) feed.insertBefore(row, feed.children[index] || null);
    });
    while (feed.children.length > rows.length) feed.lastElementChild.remove();
  } else {
    feed.innerHTML = '<div class="list-group-item">No scans yet</div>';
  }
  seenScans = new Set(d.recent.map(scanKey));
  document.getElementById('inside').innerHTML = d.insideList.map(item).join('') || `<div class="list-group-item">${d.entryOnly ? 'No entries today' : 'Nobody inside'}</div>`;
  } catch (error) {
    console.error('Unable to refresh dashboard', error);
  } finally {
    loading = false;
    if (reloadPending) { reloadPending = false; load(); }
  }
}
load();
new EventSource('/dashboard/stream').addEventListener('scan', load);   // live updates
setInterval(load, 60000);                                              // safety refresh
