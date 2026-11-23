// Utilitários de interface compartilhados pelas telas.

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

/** Escapa texto vindo da API antes de inserir em HTML (proteção contra XSS). */
export const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (c) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[c]));

export const brand = `<div class="brand"><span class="brand-mark">◈</span>argus<span class="brand-ai">AI</span></div>`;

// --- rótulos (enums da API → texto da interface) ---

export const LABELS = {
  status: { DRAFT: 'Rascunho', IN_REVIEW: 'Em análise', APPROVED: 'Homologado', REJECTED: 'Rejeitado', SUSPENDED: 'Suspenso' },
  outcome: {
    ALLOWED: 'Permitido', BLOCKED: 'Bloqueado', DRAFT: 'Rascunho', IN_REVIEW: 'Em análise',
    APPROVED: 'Homologado', REJECTED: 'Rejeitado', SUSPENDED: 'Suspenso',
  },
  category: { TECHNOLOGY: 'Tecnologia', COMMUNICATION: 'Comunicação', PEOPLE: 'Pessoas' },
  role: { COLLABORATOR: 'Colaborador', MANAGER: 'Gestor de governança', ADMIN: 'Administrador' },
  roleShort: { COLLABORATOR: 'Colaborador', MANAGER: 'Governança', ADMIN: 'Administrador' },
  data: { PUBLIC: 'Dados públicos', INTERNAL: 'Dados internos', CONFIDENTIAL: 'Dados confidenciais' },
  tool: { NONE: 'Nenhuma ferramenta externa', KNOWLEDGE_BASE_READ_ONLY: 'Consulta à base de conhecimento (somente leitura)' },
  policy: { DATA_POLICY: 'Política de dados', PURPOSE_POLICY: 'Política de finalidade' },
  action: {
    LOGIN: 'Login', LOGOUT: 'Logout', SESSION_REVOKED: 'Sessão encerrada', REFRESH_TOKEN_REUSE: 'Reuso de token detectado',
    PASSWORD_CHANGED: 'Senha alterada', USER_CREATED: 'Usuário criado', USER_UPDATED: 'Usuário alterado',
    AGENT_CREATED: 'Agente cadastrado', AGENT_UPDATED: 'Agente editado', AGENT_SUBMITTED: 'Versão submetida',
    AGENT_REVIEWED: 'Avaliação registrada', AGENT_SUSPENDED: 'Agente suspenso', AGENT_EXECUTION: 'Execução',
  },
  checklist: {
    PURPOSE_AND_LIMITS_DEFINED: 'Finalidade profissional e limites claramente definidos',
    DATA_SOURCES_COMPLIANT: 'Dados e fontes compatíveis com a política',
    LEAST_PRIVILEGE_PERMISSIONS: 'Permissões e ferramentas com acesso mínimo necessário',
    MISUSE_SCENARIOS_REVIEWED: 'Cenários de acesso indevido e bloqueio revisados',
    OWNER_AND_VERSION_IDENTIFIED: 'Responsável e versão identificados',
  },
};

export const label = (group, value) => LABELS[group]?.[value] ?? value ?? '—';

const TONE = {
  APPROVED: 'green', ALLOWED: 'green',
  REJECTED: 'red', BLOCKED: 'red', SUSPENDED: 'red',
  IN_REVIEW: 'amber', DRAFT: 'amber',
};

/** Pill de estado. A cor acompanha sempre o texto (a identidade pede que o estado não dependa só da cor). */
export function badge(value, group = 'status') {
  const prefix = value === 'APPROVED' ? '✓ ' : '';
  return `<span class="pill ${TONE[value] || ''}">${prefix}${esc(label(group, value))}</span>`;
}

// --- perfis ---

const RANK = { COLLABORATOR: 1, MANAGER: 2, ADMIN: 3 };

/** Mesma hierarquia do backend: ADMIN > MANAGER > COLLABORATOR. */
export const hasRole = (user, role) => (RANK[user?.role] || 0) >= (RANK[role] || 99);

// --- formatação ---

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
export const fmtDateTime = (iso) => (iso ? dateTime.format(new Date(iso)) : '—');

export const initials = (name) => String(name || '?').trim().split(/\s+/).filter(Boolean)
  .map((part) => part[0]).slice(0, 2).join('').toUpperCase();

export const firstName = (name) => String(name || '').trim().split(/\s+/)[0] || '';

export function debounce(fn, wait = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), wait);
  };
}

export function head(kicker, title, description, action = '') {
  return `<div class="pagehead"><div><div class="eyebrow muted">${kicker}</div><h1>${title}</h1><p>${description}</p></div>${action}</div>`;
}

// --- feedback ---

let toastTimer;
export function toast(message, kind = 'info') {
  const el = $('#toast');
  el.textContent = message;
  el.className = 'show' + (kind === 'error' ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = ''; }, 4000);
}

/** Mostra o erro da API ao usuário. Sessão expirada já é tratada pelo app (volta ao login). */
export function showError(error) {
  if (error?.code === 'SESSION_EXPIRED') return;
  // Erros de negócio (4xx) já viram mensagem na tela; só falhas inesperadas vão para o console.
  if (!error?.status || error.status >= 500) console.error(error);
  toast(error?.message || 'Ocorreu um erro inesperado.', 'error');
}

export function errorPanel(error) {
  const notFound = error?.status === 404;
  return `<div class="panel error-panel">
      <h2>${notFound ? 'Não encontrado.' : 'Não foi possível carregar esta tela.'}</h2>
      <p class="muted">${esc(error?.message || 'Tente novamente em instantes.')}</p>
      <a class="btn light" href="#/catalog">Voltar ao catálogo</a>
    </div>`;
}

// --- formulários ---

/** Marca os campos inválidos devolvidos pela API ({campo: mensagem}). */
export function showFieldErrors(form, errors = {}) {
  $$('.field-error', form).forEach((el) => el.remove());
  $$('[aria-invalid]', form).forEach((el) => el.removeAttribute('aria-invalid'));
  for (const [field, message] of Object.entries(errors)) {
    const input = form.elements.namedItem(field);
    if (!input || !input.insertAdjacentHTML) continue;
    input.setAttribute('aria-invalid', 'true');
    input.insertAdjacentHTML('afterend', `<div class="field-error">${esc(message)}</div>`);
  }
}

/** Trava o botão enquanto a requisição acontece. */
export async function busy(button, task) {
  if (!button) return task();
  const original = button.innerHTML;
  button.disabled = true;
  button.textContent = 'Aguarde…';
  try {
    return await task();
  } finally {
    button.disabled = false;
    button.innerHTML = original;
  }
}

// --- diálogos ---

/**
 * Abre um <dialog>. `onSubmit(form)` pode lançar erro para manter o diálogo aberto.
 * Retorna uma Promise resolvida com o valor de onSubmit ou null se cancelado.
 */
export function dialog({ title, body, submitLabel = 'Confirmar', cancelLabel = 'Cancelar', danger = false, onSubmit }) {
  return new Promise((resolve) => {
    const el = document.createElement('dialog');
    el.innerHTML = `<form method="dialog" novalidate>
        <h2>${title}</h2>
        <div class="dialog-body">${body}</div>
        <div class="dialogactions">
          ${cancelLabel ? `<button type="button" class="btn light" data-cancel>${cancelLabel}</button>` : ''}
          <button type="submit" class="btn ${danger ? 'danger' : ''}">${submitLabel}</button>
        </div>
      </form>`;
    document.body.append(el);
    const form = $('form', el);
    let result = null;

    el.addEventListener('close', () => { el.remove(); resolve(result); });
    $('[data-cancel]', el)?.addEventListener('click', () => el.close());
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!form.reportValidity()) return;
      try {
        result = onSubmit ? await busy(event.submitter, () => onSubmit(form)) : true;
        el.close();
      } catch (error) {
        if (error?.errors && Object.keys(error.errors).length) showFieldErrors(form, error.errors);
        showError(error);
      }
    });
    el.showModal();
  });
}

export const info = (title, body) => dialog({ title, body, submitLabel: 'Entendi', cancelLabel: '' });
