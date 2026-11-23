import { api } from '../api.js';
import { $, $$, LABELS, badge, busy, esc, fmtDateTime, head, label, showError, toast } from '../ui.js';

/** Homologação: agentes em análise (GET /agents?status=IN_REVIEW) e decisão (POST /agents/{id}/review). */
export default {
  title: 'Homologação',
  nav: 'review',
  role: 'MANAGER',

  async render(el, [selectedId], ctx) {
    const page = await api.get('/agents?status=IN_REVIEW&size=100&sort=submittedAt,asc');
    const pending = page.content;

    if (!pending.length) {
      el.innerHTML = head('APROVAÇÃO E CONFIANÇA', 'Homologação', 'Avalie agentes antes de disponibilizá-los.') + `
        <div class="panel empty">
          <h2>Tudo em dia.</h2>
          <p>Não há agentes aguardando avaliação.</p>
          <a class="btn" href="#/agents/new">Cadastrar novo agente</a>
        </div>`;
      return;
    }

    const agent = pending.find((a) => a.id === selectedId) || pending[0];
    const history = await api.get(`/agents/${agent.id}/reviews`);
    const ownSubmission = agent.submittedBy?.id === ctx.user.id;

    el.innerHTML = head('APROVAÇÃO E CONFIANÇA', 'Homologação de agentes',
      'Revise finalidade, permissões e dados permitidos antes de decidir.', badge('IN_REVIEW')) + `
      <div class="toolbar">
        <label class="sr-only" for="pending">Selecionar agente pendente</label>
        <select id="pending">
          ${pending.map((a) => `<option value="${esc(a.id)}" ${a.id === agent.id ? 'selected' : ''}>${esc(a.name)} • v${esc(a.releaseVersion)}</option>`).join('')}
        </select>
        <span class="muted sm">${pending.length} aguardando decisão</span>
      </div>
      <div class="split">
        <div>
          <div class="panel">
            <div class="row">
              <span class="agent-icon" aria-hidden="true">${esc(agent.icon || '◈')}</span>
              <div><h2 class="m0">${esc(agent.name)}</h2><span class="hint">Versão ${esc(agent.releaseVersion)} · ${esc(label('category', agent.category))}</span></div>
            </div>
            <p>${esc(agent.purpose)}</p>
            <p class="hint">${esc(agent.description)}</p>
            <div class="detail">
              <div><small>Responsável</small>${esc(agent.owner?.name)}</div>
              <div><small>Submetido por</small>${esc(agent.submittedBy?.name)} · ${esc(fmtDateTime(agent.submittedAt))}</div>
              <div><small>Dados permitidos</small>${esc(label('data', agent.dataClassification))}</div>
              <div><small>Ferramenta</small>${esc(label('tool', agent.toolAccess))}</div>
              <div><small>Modelo / provedor</small>${esc(agent.model || 'Não informado')}</div>
            </div>
          </div>
          <div class="panel">
            <h2>Checklist de homologação</h2>
            <p class="hint">Todos os critérios são obrigatórios para aprovar.</p>
            ${Object.entries(LABELS.checklist).map(([value, text]) =>
              `<label class="check"><input type="checkbox" name="checklist" value="${value}" ${ownSubmission ? 'disabled' : ''}>${esc(text)}</label>`).join('')}
          </div>
          ${history.length ? `
            <div class="panel">
              <h2>Pareceres anteriores</h2>
              <div class="tablewrap"><table>
                <thead><tr><th>Data</th><th>Versão</th><th>Avaliador</th><th>Decisão</th><th>Justificativa</th></tr></thead>
                <tbody>${history.map((r) => `<tr>
                  <td>${esc(fmtDateTime(r.reviewedAt))}</td><td>v${esc(r.reviewedVersion)}</td><td>${esc(r.reviewer?.name)}</td>
                  <td>${badge(r.decision)}</td><td>${esc(r.justification)}</td></tr>`).join('')}</tbody>
              </table></div>
            </div>` : ''}
        </div>
        <aside>
          <form class="panel" id="decision" novalidate>
            <h2>Decisão da avaliação</h2>
            <p class="hint">Avaliador: <b>${esc(ctx.user.name)}</b></p>
            ${ownSubmission ? '<div class="notice bad mb">Você submeteu esta versão. A homologação precisa ser feita por outra pessoa da governança.</div>' : ''}
            <label for="justification">Parecer / justificativa *</label>
            <textarea id="justification" name="justification" maxlength="1000" placeholder="Registre a justificativa da decisão..." ${ownSubmission ? 'disabled' : ''}></textarea>
            <div class="stack mt">
              <button class="btn block" type="submit" value="APPROVED" id="approve" disabled>✓ Aprovar e disponibilizar</button>
              <button class="btn light block" type="submit" value="REJECTED" id="reject" disabled>Solicitar ajustes / rejeitar</button>
            </div>
            <p class="hint">A decisão é registrada na auditoria e atualiza o catálogo.</p>
          </form>
          <div class="notice">◇ Homologação representa uma aprovação interna. Não elimina todos os riscos de uso.</div>
        </aside>
      </div>`;

    $('#pending', el).addEventListener('change', (event) => ctx.navigate(`#/review/${event.target.value}`));

    const form = $('#decision', el);
    const checks = $$('input[name="checklist"]', el);
    const justification = $('#justification', el);
    const update = () => {
      if (ownSubmission) return;
      const hasReason = justification.value.trim().length > 0;
      $('#approve', el).disabled = !hasReason || !checks.every((c) => c.checked);
      $('#reject', el).disabled = !hasReason;
    };
    checks.forEach((c) => c.addEventListener('change', update));
    justification.addEventListener('input', update);

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const decision = event.submitter?.value;
      try {
        await busy(event.submitter, () => api.post(`/agents/${agent.id}/review`, {
          decision,
          justification: justification.value.trim(),
          checklist: checks.filter((c) => c.checked).map((c) => c.value),
        }));
        toast(decision === 'APPROVED'
          ? 'Agente homologado e disponível no catálogo.'
          : 'Avaliação registrada. O agente voltou para ajustes.');
        ctx.refreshPendingCount();
        ctx.navigate('#/review');
      } catch (error) {
        showError(error);
      }
    });
  },
};
