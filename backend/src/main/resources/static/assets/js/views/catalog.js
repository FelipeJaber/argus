import { api } from '../api.js';
import { $, badge, debounce, esc, firstName, head, info, label, showError } from '../ui.js';

const CATEGORIES = [['', 'Todos'], ['TECHNOLOGY', 'Tecnologia'], ['COMMUNICATION', 'Comunicação'], ['PEOPLE', 'Pessoas']];

/** Catálogo de agentes homologados (GET /agents/catalog). */
export default {
  title: 'Agentes disponíveis',
  nav: 'catalog',

  async render(el, _params, ctx) {
    const filter = { category: '', q: '' };

    el.innerHTML = head('SEU ESPAÇO DE TRABALHO', `Olá, ${esc(firstName(ctx.user.name))}. Vamos trabalhar?`,
      'Escolha um agente aprovado para apoiar suas atividades.') + `
      <div class="hero">
        <div>
          <span class="eyebrow">IA COM PROPÓSITO</span>
          <h2>O agente certo para cada tarefa.</h2>
          <p>Explore ferramentas homologadas e siga as orientações de uso. Evite compartilhar informações sensíveis.</p>
        </div>
        <span class="hero-symbol" aria-hidden="true">◈</span>
      </div>
      <div class="row between">
        <h2 class="m0">Seu catálogo de agentes</h2>
        <span class="muted sm" id="count"></span>
      </div>
      <div class="toolbar">
        <input class="search" type="search" id="search" aria-label="Buscar agentes" placeholder="⌕  Buscar por nome ou finalidade...">
        <div class="chips" role="group" aria-label="Filtrar por área">
          ${CATEGORIES.map(([value, text]) =>
            `<button type="button" class="chip ${value === '' ? 'active' : ''}" data-category="${value}" aria-pressed="${value === ''}">${text}</button>`).join('')}
        </div>
      </div>
      <div id="cards" class="grid" aria-live="polite"></div>
      <div class="notice mt">◇ <b>Não encontrou o agente que precisa?</b> Agentes em análise aparecem no catálogo após a aprovação da governança.
        <button class="textbtn" type="button" data-action="help">Como funciona →</button>
      </div>`;

    const load = async () => {
      const params = new URLSearchParams();
      if (filter.category) params.set('category', filter.category);
      if (filter.q) params.set('q', filter.q);
      try {
        draw(el, await api.get('/agents/catalog?' + params));
      } catch (error) {
        showError(error);
      }
    };

    $('#search', el).addEventListener('input', debounce((event) => {
      filter.q = event.target.value.trim();
      load();
    }));

    el.addEventListener('click', (event) => {
      const chip = event.target.closest('[data-category]');
      if (chip) {
        filter.category = chip.dataset.category;
        el.querySelectorAll('[data-category]').forEach((c) => {
          c.classList.toggle('active', c === chip);
          c.setAttribute('aria-pressed', String(c === chip));
        });
        load();
      }
      if (event.target.closest('[data-action="help"]')) {
        info('Da aprovação ao seu catálogo', `
          <p>Um responsável cadastra o agente e define sua finalidade. A governança revisa a versão, as permissões
          e os dados permitidos. Somente depois da homologação o agente fica disponível aqui.</p>
          <p class="hint">Antes de chegar ao agente, cada mensagem passa pelas políticas de uso (dados sensíveis e
          finalidade profissional). O conteúdo das mensagens não é registrado na auditoria.</p>`);
      }
    });

    const items = await api.get('/agents/catalog');
    draw(el, items);
  },
};

function draw(el, items) {
  $('#count', el).textContent = `${items.length} ${items.length === 1 ? 'disponível' : 'disponíveis'}`;
  $('#cards', el).innerHTML = items.length
    ? items.map((a) => `
        <article class="card">
          <div class="row between"><span class="agent-icon" aria-hidden="true">${esc(a.icon || '◈')}</span>${badge('APPROVED')}</div>
          <h3>${esc(a.name)}</h3>
          <p>${esc(a.description)}</p>
          <div class="cardmeta">
            <span>◷ v${esc(a.releaseVersion)}</span>
            <span>▧ ${esc(label('category', a.category))}</span>
            <span>♙ ${esc(a.ownerName)}</span>
          </div>
          <a class="btn light" href="#/agents/${esc(a.id)}/chat">Iniciar conversa <span aria-hidden="true">↗</span></a>
        </article>`).join('')
    : '<div class="empty full">Nenhum agente encontrado para este filtro.</div>';
}
