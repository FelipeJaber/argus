import { api } from '../api.js';
import { $, LABELS, busy, esc, head, showError, showFieldErrors, toast } from '../ui.js';

const ICONS = ['◈', '⌘', '✎', '♧', '✓', '▦', '☰', '✦'];

const options = (group, selected) => Object.entries(LABELS[group])
  .map(([value, text]) => `<option value="${value}" ${value === selected ? 'selected' : ''}>${esc(text)}</option>`).join('');

/** Cadastro (POST /agents) e edição (PUT /agents/{id}) de agentes. */
export default {
  title: (params) => (params.length ? 'Editar agente' : 'Cadastrar agente'),
  nav: 'manage',
  role: 'MANAGER',

  async render(el, [agentId], ctx) {
    const [agent, usersPage] = await Promise.all([
      agentId ? api.get(`/agents/${agentId}`) : Promise.resolve(null),
      api.get('/users?size=200'),
    ]);
    const owners = usersPage.content.filter((u) => u.active);
    const a = agent || { category: 'TECHNOLOGY', dataClassification: 'INTERNAL', toolAccess: 'NONE', releaseVersion: '1.0', icon: '◈', owner: { id: ctx.user.id } };

    const title = agent ? `Editar “${esc(agent.name)}”` : 'Da ideia ao uso responsável.';
    const subtitle = agent
      ? 'Ajuste a versão e envie novamente para homologação.'
      : 'Defina a finalidade e as condições de uso antes da homologação.';

    el.innerHTML = head(agent ? 'EDIÇÃO DE AGENTE' : 'NOVO AGENTE', title, subtitle) + `
      <div class="step"><b>1</b> Cadastro <i></i><b>2</b> Homologação <i></i><b>3</b> Disponibilização</div>
      <form id="agentform" novalidate>
        <div class="split">
          <div>
            <div class="panel">
              <h2>Informações do agente</h2>
              <div class="fields">
                <div><label for="name">Nome do agente *</label>
                  <input id="name" name="name" required maxlength="120" placeholder="Ex.: Assistente de projetos" value="${esc(a.name)}"></div>
                <div><label for="category">Área de atuação *</label>
                  <select id="category" name="category">${options('category', a.category)}</select></div>
                <div class="full"><label for="description">Descrição para o catálogo *</label>
                  <textarea id="description" name="description" required maxlength="500" placeholder="Explique como o agente ajuda o colaborador.">${esc(a.description)}</textarea></div>
                <div class="full"><label for="purpose">Finalidade e instruções de uso *</label>
                  <textarea id="purpose" name="purpose" required maxlength="2000" placeholder="Defina as tarefas permitidas e os limites do agente.">${esc(a.purpose)}</textarea></div>
                <div><label for="ownerId">Responsável pelo agente *</label>
                  <select id="ownerId" name="ownerId">
                    ${owners.map((u) => `<option value="${esc(u.id)}" ${u.id === a.owner?.id ? 'selected' : ''}>${esc(u.name)}</option>`).join('')}
                  </select></div>
                <div><label for="model">Modelo / provedor</label>
                  <input id="model" name="model" maxlength="120" placeholder="Ex.: Modelo corporativo" value="${esc(a.model)}"></div>
                <div><label for="releaseVersion">Versão *</label>
                  <input id="releaseVersion" name="releaseVersion" required pattern="\\d{1,3}\\.\\d{1,3}(\\.\\d{1,3})?" placeholder="1.0" value="${esc(a.releaseVersion)}"></div>
                <div><label for="icon">Ícone</label>
                  <select id="icon" name="icon">${ICONS.map((i) => `<option ${i === a.icon ? 'selected' : ''}>${i}</option>`).join('')}</select></div>
              </div>
            </div>
            <div class="panel">
              <h2>Dados, ferramentas e permissões</h2>
              <div class="fields">
                <div><label for="dataClassification">Classificação máxima de dados</label>
                  <select id="dataClassification" name="dataClassification">${options('data', a.dataClassification)}</select></div>
                <div><label for="toolAccess">Ferramenta autorizada</label>
                  <select id="toolAccess" name="toolAccess">${options('tool', a.toolAccess)}</select></div>
              </div>
              <label class="check"><input type="checkbox" name="confirm" required>
                Confirmo que a finalidade, as fontes e as permissões foram revisadas.</label>
            </div>
            <div class="row between wrap">
              <a class="btn light" href="#/manage">Cancelar</a>
              <div class="row wrap">
                <button class="btn light" type="submit" value="draft">Salvar rascunho</button>
                <button class="btn" type="submit" value="review">Enviar para homologação →</button>
              </div>
            </div>
          </div>
          <aside>
            <div class="panel">
              <span class="agent-icon" aria-hidden="true">◇</span>
              <h3>Governança desde o início.</h3>
              <p class="hint">O agente só estará disponível após a aprovação de uma pessoa diferente da que fez a submissão.</p>
              <hr>
              <p class="hint">✓ Finalidade profissional definida<br>✓ Permissões explícitas<br>✓ Dados permitidos delimitados<br>✓ Versão rastreável</p>
            </div>
            ${agent?.status === 'REJECTED' ? '<div class="notice warn">Este agente foi rejeitado. Consulte o parecer na tela de homologação, ajuste e envie novamente.</div>' : ''}
            ${agent?.status === 'SUSPENDED' ? '<div class="notice warn">Este agente está suspenso. Após os ajustes, ele precisa passar por uma nova homologação.</div>' : ''}
          </aside>
        </div>
      </form>`;

    const form = $('#agentform', el);
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      showFieldErrors(form, {});
      if (!form.reportValidity()) return;

      // form.elements: "form.name" devolveria o atributo name do próprio <form>, não o campo.
      const value = (field) => form.elements.namedItem(field).value.trim();
      const submitForReview = event.submitter?.value === 'review';
      const body = {
        name: value('name'),
        description: value('description'),
        purpose: value('purpose'),
        category: value('category'),
        ownerId: value('ownerId'),
        model: value('model') || null,
        dataClassification: value('dataClassification'),
        toolAccess: value('toolAccess'),
        releaseVersion: value('releaseVersion'),
        icon: value('icon'),
        submitForReview,
      };

      try {
        await busy(event.submitter, () => (agentId ? api.put(`/agents/${agentId}`, body) : api.post('/agents', body)));
        toast(submitForReview
          ? 'Agente enviado. Outra pessoa da governança deverá homologar esta submissão.'
          : 'Rascunho salvo.');
        if (submitForReview) ctx.refreshPendingCount();
        ctx.navigate('#/manage');
      } catch (error) {
        showFieldErrors(form, error.errors);
        showError(error);
      }
    });
  },
};
