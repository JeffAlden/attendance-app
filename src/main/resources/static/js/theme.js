document.documentElement.setAttribute('data-bs-theme', localStorage.getItem('theme') || 'light');
function toggleTheme() {
  const t = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'light' : 'dark';
  document.documentElement.setAttribute('data-bs-theme', t); localStorage.setItem('theme', t);
}
