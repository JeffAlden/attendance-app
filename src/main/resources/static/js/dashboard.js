const esc = s => String(s ?? '').replace(/[&<>"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
const avatar = r => r.photo
  ? `<img src="${esc(r.photo)}" class="rounded-circle me-3" width="48" height="48" style="object-fit:cover">`
  : `<div class="rounded-circle bg-secondary text-white d-flex align-items-center justify-content-center me-3" style="width:48px;height:48px">${esc(r.name[0])}</div>`;
const badge = r => r.status !== 'OK'
  ? `<span class="badge text-bg-danger">${esc(r.status)}</span>`
  : `<span class="badge text-bg-${r.direction === 'IN' ? 'success' : 'warning'}">${esc(r.direction)}</span>`;
const item = r => `<div class="list-group-item d-flex align-items-center">${avatar(r)}
  <div class="flex-grow-1"><div class="fw-semibold">${esc(r.name)}</div><div class="small text-muted">${esc(r.code)} · ${esc(r.dept)}</div></div>
  <div class="text-end">${badge(r)}<div class="small text-muted">${esc(r.time)}</div></div></div>`;

async function load() {
  const d = await (await fetch('/dashboard/data')).json();
  ['inside', 'totalIn', 'totalOut', 'denied'].forEach(k => document.getElementById('c-' + k).textContent = d[k]);
  document.getElementById('feed').innerHTML = d.recent.map(item).join('') || '<div class="list-group-item">No scans yet</div>';
  document.getElementById('inside').innerHTML = d.insideList.map(item).join('') || '<div class="list-group-item">Nobody inside</div>';
}
load();
new EventSource('/dashboard/stream').addEventListener('scan', load);   // live updates
setInterval(load, 60000);                                              // safety refresh
