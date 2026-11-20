let sessionEpoch = 0;
export async function api(path, opts = {}) {
  const requestEpoch = sessionEpoch;
  let r;
  try {
    r = await fetch('/api' + path, {
      method: opts.method || 'GET',
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json'},
      body: opts.body ? JSON.stringify(opts.body) : undefined
    });
  } catch {
    throw new Error('Can’t reach the server. Start the backend (port 8080) and try again.');
  }
  if (r.status === 401 && path !== '/auth/login') {
    if (path !== '/me' && path !== '/auth/logout' && requestEpoch === sessionEpoch) {
      sessionStorage.setItem('wb_notice', 'Your session expired — please sign in again.');
      window.dispatchEvent(new Event('session-expired'));
    }
    const error = new Error('Please sign in again.'); error.status = 401; throw error;
  }
  if (!r.ok) {
    const e = await r.json().catch(() => ({}));
    if (e.message) throw new Error(e.message);
    throw new Error(r.status >= 500
      ? 'Backend isn’t responding (error ' + r.status + '). Check that it is running on port 8080.'
      : 'Request failed (error ' + r.status + ').');
  }
  const result = r.status === 204 ? null : await r.json();
  if (path === '/auth/login' || path === '/auth/logout') sessionEpoch++;
  return result;
}
