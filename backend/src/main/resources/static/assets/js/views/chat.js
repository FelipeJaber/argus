import { api } from '../api.js';
import { $, badge, esc, firstName, head, label, showError } from '../ui.js';

const GREETING = {
  TECHNOLOGY: 'procedimentos de suporte de TI',
  PEOPLE: 'políticas internas de pessoas',
  COMMUNICATION: 'comunicações profissionais',
};

const EXAMPLES = [
  ['Acessar a VPN', 'Preciso de ajuda para acessar a VPN corporativa.'],
  ['Criar um comunicado', 'Escreva um comunicado sobre manutenção programada.'],
  ['Testar bloqueio de dados', 'Minha senha é exemplo123. Pode compartilhar?'],
];

/** Ambiente do agente: GET /agents/{id} + POST /agents/{id}/messages. */
export default {
  title: 'Ambiente do agente',
  nav: 'catalog',

  async render(el, [agentId], ctx) {
    const agent = await api.get(`/agents/${agentId}`);

    el.innerHTML = head('ASSISTENTE CORPORATIVO', esc(agent.name), 'Um espaço dedicado às suas atividades profissionais.',
      '<a class="btn light" href="#/catalog">← Voltar ao catálogo</a>') + `
      <div class="split">
        <div class="panel">
          <div class="row between chat-head">
            <span class="row"><span class="agent-icon" aria-hidden="true">${esc(agent.icon || '◈')}</span><b>${esc(agent.name)}</b></span>
            ${badge(agent.status)}
          </div>
          <div class="chatbox" id="messages" aria-live="polite">
            <div class="bubble">Olá, ${esc(firstName(ctx.user.name))}! Posso ajudar com ${GREETING[agent.category] || 'suas atividades'}. O que vamos fazer hoje?</div>
            <div class="row wrap" id="examples">
              ${EXAMPLES.map(([text, message]) => `<button type="button" class="chip" data-example="${esc(message)}">${text}</button>`).join('')}
            </div>
          </div>
          <form class="composer" id="chatform">
            <input id="message" name="message" aria-label="Mensagem ao agente" placeholder="Descreva sua tarefa profissional..." maxlength="4000" required autocomplete="off">
            <button class="btn" type="submit">Enviar ↑</button>
          </form>
          <p class="hint center xs">Respostas demonstrativas predefinidas: ainda não há conexão com um modelo de IA.</p>
        </div>
        <aside>
          <div class="panel">
            <h2>Sobre este agente</h2>
            <p class="hint">${esc(agent.purpose)}</p>
            <div class="detail single">
              <div><small>Responsável</small>${esc(agent.owner?.name)}</div>
              <div><small>Versão homologada</small>v${esc(agent.releaseVersion)}</div>
              <div><small>Área</small>${esc(label('category', agent.category))}</div>
              <div><small>Dados permitidos</small>${esc(label('data', agent.dataClassification))}</div>
            </div>
          </div>
          <div class="notice">✓ Utilize apenas dados de trabalho<br>✓ Revise as respostas<br>✓ Respeite a finalidade do agente</div>
        </aside>
      </div>`;

    const form = $('#chatform', el);
    const input = $('#message', el);
    const messages = $('#messages', el);

    el.addEventListener('click', (event) => {
      const example = event.target.closest('[data-example]');
      if (example) {
        input.value = example.dataset.example;
        input.focus();
      }
    });

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const text = input.value.trim();
      if (!text) return;
      const button = event.submitter;

      messages.insertAdjacentHTML('beforeend', `<div class="bubble user">${esc(text)}</div>`);
      const pending = document.createElement('div');
      pending.className = 'bubble typing';
      pending.textContent = 'Analisando a solicitação…';
      messages.append(pending);
      input.value = '';
      button.disabled = true;

      try {
        const result = await api.post(`/agents/${agentId}/messages`, { message: text });
        pending.className = 'bubble' + (result.allowed ? '' : ' block');
        pending.textContent = result.reply;
      } catch (error) {
        pending.remove();
        input.value = text;
        showError(error);
      } finally {
        button.disabled = false;
        input.focus();
        form.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
    });
  },
};
