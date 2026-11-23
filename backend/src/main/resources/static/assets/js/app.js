// Ponto de entrada: restaura a sessão, desenha o shell e roteia por hash (#/catalog, #/manage...).

import { api, auth } from './api.js';
import { $, brand, esc, errorPanel, hasRole, initials, label, showError, toast } from './ui.js';
import renderLogin from './views/login.js';
import catalog from './views/catalog.js';
import chat from './views/chat.js';
import manage from './views/manage.js';
import agentForm from './views/agent-form.js';
import review from './views/review.js';
import people from './views/people.js';
import audit from './views/audit.js';
import account from './views/account.js';

const UUID = '([0-9a-f-]{36})';
const routes = [
  { pattern: /^\/catalog$/, view: catalog },
  { pattern: new RegExp(`^/agents/${UUID}/chat$`), view: chat },
  { pattern: /^\/manage$/, view: manage },
  { pattern: /^\/agents\/new$/, view: agentForm },
  { pattern: new RegExp(`^/agents/${UUID}/edit$`), view: agentForm },
  { pattern: new RegExp(`^/review(?:/${UUID})?$`), view: review },
  { pattern: /^\/people$/, view: people },
  { pattern: /^\/audit$/, view: audit },
  { pattern: /^\/account$/, view: account },
];

const state = { user: null, navigation: 0 };

const ctx = {
  get user() { return state.user; },
  navigate(hash) {
    if (location.hash === hash) route(); else location.hash = hash;
  },
  refreshPendingCount,
  updateUser(user) {
    state.user = user;
    renderShell();
    route();
  },
};

// --- inicialização ---

auth.onSessionExpired(() => {
  if (!state.user) return;
  state.user = null;
  showLogin();
  toast('Sua sessão expirou. Entre novamente.', 'error');
});

window.addEventListener('hashchange', () => route());

boot();

async function boot() {
  const session = await auth.restore();
  if (session) start(session.user); else showLogin();
}

function showLogin() {
  document.title = 'Entrar — Argus';
  renderLogin($('#app'), { onLogin: start });
}

function start(user) {
  state.user = user;
  renderShell();
  if (!location.hash || location.hash === '#/') history.replaceState(null, '', '#/catalog');
  route();
}

// --- shell (sidebar + topbar) ---

function renderShell() {
  const user = state.user;
  const governance = hasRole(user, 'MANAGER');
  const nav = (id, href, icon, text, extra = '') =>
    `<a class="nav" href="${href}" data-nav="${id}"><span aria-hidden="true">${icon}</span>${text}${extra}</a>`;

  $('#app').innerHTML = `
    <aside class="sidebar">
      ${brand}
      <div class="workspace">
        <div class="row">
          <span class="avatar square">${esc(initials(user.sector || 'Argus'))}</span>
          <div><b>${esc(user.sector || 'Sua organização')}</b><div class="hint xs">${esc(label('role', user.role))}</div></div>
        </div>
      </div>
      <nav aria-label="Navegação principal">
        <div class="navlabel">ESPAÇO DE TRABALHO</div>
        ${nav('catalog', '#/catalog', '▦', 'Meus agentes')}
        ${governance ? `
          <div class="navlabel">GOVERNANÇA</div>
          ${nav('manage', '#/manage', '◈', 'Gestão de agentes')}
          ${nav('review', '#/review', '◇', 'Homologação', '<span class="count hidden" id="pending-count"></span>')}
          ${nav('people', '#/people', '♙', 'Pessoas e acessos')}
          ${nav('audit', '#/audit', '≡', 'Auditoria')}` : ''}
      </nav>
      <div class="sidebottom">
        <a class="usercard" href="#/account" data-nav="account">
          <span class="avatar">${esc(initials(user.name))}</span>
          <span><span class="name">${esc(user.name)}</span><span class="hint xs">Minha conta</span></span>
        </a>
        <button class="nav" type="button" data-action="logout">↪ Sair do ambiente</button>
      </div>
    </aside>
    <main class="main">
      <header class="topbar">
        <div class="crumbs">Workspace <span class="sep">/</span><span class="current" id="page-title"></span></div>
        <div class="row">
          <span class="pill">${esc(label('roleShort', user.role))}</span>
          <a class="avatar" href="#/account" title="Minha conta">${esc(initials(user.name))}</a>
        </div>
      </header>
      <div class="content" id="content"></div>
      <footer class="foot">
        <span>ARGUS · Governança de agentes de inteligência artificial</span>
        <span>Projeto acadêmico · Engenharia de Software</span>
      </footer>
    </main>`;

  $('[data-action="logout"]').addEventListener('click', async () => {
    const user = state.user;
    state.user = null;
    await auth.logout();
    // Quem entrar depois começa do catálogo, não na última tela de quem saiu.
    history.replaceState(null, '', '#/catalog');
    showLogin();
    toast(`Até logo, ${user.name.split(' ')[0]}.`);
  });

  refreshPendingCount();
}

/** Contador de agentes aguardando homologação no menu. */
export async function refreshPendingCount() {
  const badge = $('#pending-count');
  if (!badge) return;
  try {
    const stats = await api.get('/agents/stats');
    const pending = stats.byStatus.IN_REVIEW || 0;
    badge.textContent = pending;
    badge.classList.toggle('hidden', pending === 0);
  } catch {
    badge.classList.add('hidden');
  }
}

// --- roteamento ---

async function route() {
  if (!state.user || !$('#content')) return;
  const path = location.hash.replace(/^#/, '') || '/catalog';
  let match = null;
  for (const r of routes) {
    const m = path.match(r.pattern);
    if (m) { match = { view: r.view, params: m.slice(1).filter(Boolean) }; break; }
  }
  if (!match) { location.replace('#/catalog'); return; }

  const { view, params } = match;
  if (view.role && !hasRole(state.user, view.role)) {
    toast('Seu perfil não tem acesso a esta área.', 'error');
    location.replace('#/catalog');
    return;
  }

  document.querySelectorAll('[data-nav]').forEach((el) => {
    const active = el.dataset.nav === view.nav;
    el.classList.toggle('active', active);
    if (active) el.setAttribute('aria-current', 'page'); else el.removeAttribute('aria-current');
  });
  const title = typeof view.title === 'function' ? view.title(params) : view.title;
  $('#page-title').textContent = title;
  document.title = `${title} — Argus`;

  // Cada navegação ganha um contêiner novo: listeners da tela anterior somem junto com ela.
  const navigation = ++state.navigation;
  const content = document.createElement('div');
  content.className = 'content';
  content.id = 'content';
  content.innerHTML = '<div class="loading">Carregando…</div>';
  $('#content').replaceWith(content);
  window.scrollTo(0, 0);

  try {
    await view.render(content, params, ctx);
  } catch (error) {
    if (navigation !== state.navigation || error?.code === 'SESSION_EXPIRED') return;
    content.innerHTML = errorPanel(error);
    if (error?.status !== 404) showError(error);
  }
}
