// Cliente da API do Argus.
//
// - O access token fica SÓ em memória (variável deste módulo), nunca em localStorage.
// - O refresh token é um cookie HttpOnly que o navegador envia sozinho para /api/v1/auth/*.
// - Ao recarregar a página, a sessão é restaurada com POST /auth/refresh.
// - Se uma chamada voltar 401 (token expirado), renova uma única vez e repete a chamada.

const BASE = '/api/v1';

let accessToken = null;
let refreshInFlight = null;
let onExpired = () => {};

export class ApiError extends Error {
  constructor(status, code, message, errors) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors || {};
  }
}

async function parse(response) {
  const type = response.headers.get('content-type') || '';
  return type.includes('json') ? response.json() : null;
}

function toError(response, body) {
  return new ApiError(
    response.status,
    body?.code || 'HTTP_' + response.status,
    body?.detail || 'Não foi possível concluir a operação.',
    body?.errors,
  );
}

async function doRefresh() {
  for (let attempt = 0; attempt < 2; attempt++) {
    let response;
    try {
      response = await fetch(BASE + '/auth/refresh', { method: 'POST', credentials: 'same-origin' });
    } catch {
      return null;
    }
    if (response.ok) {
      const body = await response.json();
      accessToken = body.accessToken;
      return body;
    }
    // 409: outra aba acabou de renovar e o cookie novo já chegou; tenta de novo uma vez.
    if (response.status !== 409) break;
  }
  accessToken = null;
  return null;
}

/** Garante uma única renovação por vez, mesmo com várias chamadas falhando juntas. */
function refresh() {
  refreshInFlight ??= doRefresh().finally(() => { refreshInFlight = null; });
  return refreshInFlight;
}

async function request(method, path, body, retry = true) {
  const headers = { Accept: 'application/json' };
  if (accessToken) headers.Authorization = 'Bearer ' + accessToken;
  const init = { method, headers, credentials: 'same-origin' };
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(BASE + path, init);
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Não foi possível conectar ao servidor.');
  }

  if (response.status === 401 && retry) {
    if (await refresh()) return request(method, path, body, false);
    accessToken = null;
    onExpired();
    throw new ApiError(401, 'SESSION_EXPIRED', 'Sua sessão expirou. Entre novamente.');
  }

  const data = response.status === 204 ? null : await parse(response);
  if (!response.ok) throw toError(response, data);
  return data;
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body),
  put: (path, body) => request('PUT', path, body),
  patch: (path, body) => request('PATCH', path, body),
  delete: (path) => request('DELETE', path),
};

export const auth = {
  /** Chamado quando a sessão não pode mais ser renovada (expirada ou revogada). */
  onSessionExpired(handler) {
    onExpired = handler;
  },

  /** Tenta restaurar a sessão a partir do cookie. Retorna {user} ou null. */
  async restore() {
    const body = await refresh();
    return body ? { user: body.user } : null;
  },

  async login(email, password) {
    let response;
    try {
      response = await fetch(BASE + '/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ email, password }),
        credentials: 'same-origin',
      });
    } catch {
      throw new ApiError(0, 'NETWORK_ERROR', 'Não foi possível conectar ao servidor.');
    }
    const body = await parse(response);
    if (!response.ok) throw toError(response, body);
    accessToken = body.accessToken;
    return body.user;
  },

  async logout() {
    try {
      await fetch(BASE + '/auth/logout', { method: 'POST', credentials: 'same-origin' });
    } finally {
      accessToken = null;
    }
  },

  async logoutAll() {
    try {
      await request('POST', '/auth/logout-all');
    } finally {
      accessToken = null;
    }
  },
};
