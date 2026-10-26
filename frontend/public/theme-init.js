(() => {
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  try {
    document.documentElement.dataset.theme = localStorage.getItem('lendly-theme') ?? (prefersDark ? 'dark' : 'light');
  } catch {
    document.documentElement.dataset.theme = prefersDark ? 'dark' : 'light';
  }
})();
