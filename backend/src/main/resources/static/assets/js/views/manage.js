import { api } from '../api.js';
import { $, badge, busy, dialog, esc, fmtDateTime, head, label, showError, toast } from '../ui.js';

const FILTERS = [['', 'Todos'], ['DRAFT', 'Rascunhos'], ['IN_REVIEW', 'Em análise'], ['APPROVED', 'Homologados'],
  ['REJECTED', 'Rejeitados'], ['SUSPENDED', 'Suspensos']];

/** Gestão de agentes: indicadores (GET /agents/stats) e ciclo de vida (GET /agents, submit, suspend). */
export default {
  title: 'Gestão de agentes',
  nav: 'manage',
  role: 'MANAGER',

  async render(el, _params, ctx) {
    let status = '';

    el.innerHTML = head('CONTROLE E ORGANIZAÇÃO', 'Gestão de agentes', 'Acompanhe o ciclo de vida dos agentes da organização.',
      '<a class="btn" href="#/agents/new">+ Cadastrar agente</a>') + `
      <div class="stats" id="stats"></div>
      <div class="panel">
        <div class="toolbar flush">
          <h2 class="m0">Agentes cadastrados</h2>
          <div class="chips" role="group" aria-label="Filtrar por status">
            ${FILTERS.map(([value, text]) =>
              `<button type="button" class="chip ${value === '' ? 'active' : ''}" data-status="${value}" aria-pressed="${value === ''}">${text}</button>`).join('')}
          </div>
        </div>
        <div class="tablewrap" id="table"></div>
      </div>`;

    const load = async () => {
      const [stats, page] = await Promise.all([
        api.get('/agents/stats'),
        api.get('/agents?size=100' + (status ? '&status=' + status : '')),
      ]);
      drawStats(el, stats);
      drawTable(el, page.content);
    };

    el.addEventListener('click', async (event) => {
      const chip = event.target.closest('[data-status]');
      if (chip) {
        status = chip.dataset.status;
        el.querySelectorAll('[data-status]').forEach((c) => {
          c.classList.toggle('active', c === chip);
          c.setAttribute('aria-pressed', String(c === chip));
        });
        load().catch(showError);
        return;
      }

      const button = event.target.closest('[data-action]');
      if (!button) return;
      const { action, id, name } = button.dataset;

      if (action === 'submit') {
        try {
          await busy(button, () => api.post(`/agents/${id}/submit`));
          toast('Agente enviado para homologação. Outra pessoa da governança deverá avaliá-lo.');
          ctx.refreshPendingCount();
          await load();
        } catch (error) {
          showError(error);
        }
      }

      if (action === 'suspend') {
        const done = await dialog({
          title: `Suspender “${esc(name)}”?`,
          body: `<p>O agente sai do catálogo e novas execuções ficam bloqueadas até uma nova homologação.</p>
                 <label for="reason">Motivo <small>(opcional, vai para a auditoria)</small></label>
                 <input id="reason" name="reason" maxlength="255">`,
          submitLabel: 'Suspender agente',
          danger: true,
          onSubmit: (form) => api.post(`/agents/${id}/suspend`, { reason: form.reason.value.trim() || null }),
        });
        if (done) {
          toast('Agente suspenso. Novas execuções estão bloqueadas.');
          await load().catch(showError);
        }
      }
    });

    await load();
  },
};

function drawStats(el, stats) {
  const by = stats.byStatus;
  const others = (by.DRAFT || 0) + (by.REJECTED || 0) + (by.SUSPENDED || 0);
  const cards = [
    ['Total de agentes', stats.total, 'No ambiente'],
    ['Homologados', by.APPROVED || 0, 'Disponíveis no catálogo'],
    ['Em análise', by.IN_REVIEW || 0, 'Aguardando decisão'],
    ['Outros estados', others, 'Rascunhos, rejeitados e suspensos'],
  ];
  $('#stats', el).innerHTML = cards.map(([title, value, hint]) =>
    `<div class="stat"><span class="muted">${title}</span><strong>${value}</strong><small>${hint}</small></div>`).join('');
}

function drawTable(el, agents) {
  if (!agents.length) {
    $('#table', el).innerHTML = '<div class="empty">Nenhum agente neste filtro. <a class="textbtn" href="#/agents/new">Cadastrar agente</a></div>';
    return;
  }
  $('#table', el).innerHTML = `
    <table>
      <thead><tr><th>Agente</th><th>Responsável</th><th>Versão</th><th>Status</th><th>Atualizado</th><th>Ação</th></tr></thead>
      <tbody>${agents.map(row).join('')}</tbody>
    </table>`;
}

function row(a) {
  const id = esc(a.id);
  const name = esc(a.name);
  let actions;
  switch (a.status) {
    case 'APPROVED':
      actions = `<button class="textbtn danger" data-action="suspend" data-id="${id}" data-name="${name}">Suspender</button>`;
      break;
    case 'IN_REVIEW':
      actions = `<a class="textbtn" href="#/review/${id}">Avaliar →</a>`;
      break;
    default: // DRAFT, REJECTED, SUSPENDED: editáveis e submetíveis
      actions = `<a class="textbtn" href="#/agents/${id}/edit">Editar</a>
                 <button class="textbtn" data-action="submit" data-id="${id}">Submeter</button>`;
  }
  return `<tr>
      <td><strong>${name}</strong><br><span class="muted">${esc(label('category', a.category))}</span></td>
      <td>${esc(a.owner?.name)}</td>
      <td>v${esc(a.releaseVersion)}</td>
      <td>${badge(a.status)}</td>
      <td>${esc(fmtDateTime(a.updatedAt))}</td>
      <td>${actions}</td>
    </tr>`;
}
