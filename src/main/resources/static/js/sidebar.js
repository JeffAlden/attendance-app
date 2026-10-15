document.querySelectorAll('.sidebar-menu .sidebar-link').forEach(link => {
  const path = window.location.pathname.replace(/\/$/, '') || '/dashboard';
  if (path === link.pathname || path.startsWith(link.pathname + '/')) {
    link.setAttribute('aria-current', 'page');
  }
  link.setAttribute('aria-label', link.textContent.trim());
});
document.querySelectorAll('.sidebar-footer .sidebar-link').forEach(button => {
  button.setAttribute('aria-label', button.textContent.trim());
});
function updateThemeLabel() {
  const label = document.getElementById('theme-label');
  if (label) {
    label.textContent = document.documentElement.getAttribute('data-bs-theme') === 'dark' ? 'Light mode' : 'Dark mode';
    label.parentElement.setAttribute('aria-label', label.textContent);
  }
}
new MutationObserver(updateThemeLabel).observe(document.documentElement, { attributes: true, attributeFilter: ['data-bs-theme'] });
updateThemeLabel();
