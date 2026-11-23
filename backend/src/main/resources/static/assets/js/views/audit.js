import { api } from '../api.js';
import { $, LABELS, badge, esc, fmtDateTime, head, label, showError } from '../ui.js';

const PAGE_SIZE = 20;

/** Auditoria: GET /audit-events (somente metadados; o conteúdo das mensagens não é registrado). */
export default {
  title: 'Auditoria',
  nav: 'audit',
  role: 'MANAGER',

  async render(el) {
    const filter = { outcome: '', action: '', page: 0 };

    el.innerHTML = head('VISIBILIDADE E RASTREABILIDADE', 'Auditoria de uso',
      'Consulte eventos e decisões das políticas, sem expor o conteúdo das mensagens.') + `
      <div class="panel">
        <div class="toolbar flush wrap">
          <h2 class="m0">Eventos recentes</h2>
          <div class="row wrap">
            <label class="sr-only" for="f-action">Filtrar por evento</label>
            <select id="f-action" name="action">
              <option value="">Todos os eventos</option>
              ${Object.entries(LABELS.action).map(([v, t]) => `<option value="${v}">${esc(t)}</option>`).join('')}
            </select>
            <label class="sr-only" for="f-outcome">Filtrar por resultado</label>
            <select id="f-outcome" name="outcome">
              <option value="">Todos os resultados</option>
              ${Object.entries(LABELS.outcome).map(([v, t]) => `<option value="${v}">${esc(t)}</option>`).join('')}
            </select>
          </div>
        </div>
        <div class="tablewrap" id="table"></div>
        <div class="pager" id="pager"></div>
      </div>
      <div class="notice">São registrados apenas metadados: quem, quando, qual agente, o evento e o resultado.</div>`;

    const load = async () => {
      const params = new URLSearchParams({ page: filter.page, size: PAGE_SIZE });
      if (filter.outcome) params.set('outcome', filter.outcome);
      if (filter.action) params.set('action', filter.action);
      draw(el, await api.get('/audit-events?' + params));
    };

    el.addEventListener('change', (event) => {
      if (!['action', 'outcome'].includes(event.target.name)) return;
      filter[event.target.name] = event.target.value;
      filter.page = 0;
      load().catch(showError);
    });
    el.addEventListener('click', (event) => {
      const button = event.target.closest('[data-page]');
      if (!button) return;
      filter.page = Number(button.dataset.page);
      load().catch(showError);
    });

    await load();
  },
};

function describe(e) {
  const action = label('action', e.action);
  if (!e.detail) return esc(action);
  const detail = LABELS.policy[e.detail] ? `${LABELS.policy[e.detail]} aplicada` : e.detail;
  return `${esc(action)}<br><span class="muted">${esc(detail)}</span>`;
}

function draw(el, page) {
  $('#table', el).innerHTML = page.content.length ? `
    <table>
      <thead><tr><th>Horário</th><th>Usuário</th><th>Agente</th><th>Evento</th><th>Resultado</th></tr></thead>
      <tbody>${page.content.map((e) => `<tr>
        <td>${esc(fmtDateTime(e.occurredAt))}</td>
        <td>${esc(e.actorName || '—')}</td>
        <td>${esc(e.agentName || '—')}</td>
        <td>${describe(e)}</td>
        <td>${badge(e.outcome, 'outcome')}</td>
      </tr>`).join('')}</tbody>
    </table>` : '<div class="empty">Nenhum evento encontrado.</div>';

  $('#pager', el).innerHTML = page.totalPages > 1 ? `
    <button class="btn light sm" data-page="${page.page - 1}" ${page.page === 0 ? 'disabled' : ''}>← Anteriores</button>
    <span class="muted sm">Página ${page.page + 1} de ${page.totalPages} · ${page.totalElements} eventos</span>
    <button class="btn light sm" data-page="${page.page + 1}" ${page.page + 1 >= page.totalPages ? 'disabled' : ''}>Próximos →</button>`
    : `<span class="muted sm">${page.totalElements} ${page.totalElements === 1 ? 'evento' : 'eventos'}</span>`;
}
