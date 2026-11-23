import { api } from '../api.js';
import { $, LABELS, dialog, esc, fmtDateTime, hasRole, head, showError, toast } from '../ui.js';

/** Pessoas e acessos: GET /users (gestor) · POST /users e PATCH /users/{id} (administrador). */
export default {
  title: 'Pessoas e acessos',
  nav: 'people',
  role: 'MANAGER',

  async render(el, _params, ctx) {
    const isAdmin = hasRole(ctx.user, 'ADMIN');

    el.innerHTML = head('ADMINISTRAÇÃO', 'Pessoas e acessos', 'Organize os perfis e o acesso aos agentes da empresa.',
      isAdmin ? '<button class="btn" type="button" data-action="new">+ Novo usuário</button>' : '') + `
      <div class="notice mb">${isAdmin
        ? 'Ao mudar o perfil ou desativar uma pessoa, as sessões dela são encerradas na hora.'
        : 'Somente administradores podem alterar perfis e acessos. Você pode consultar a equipe.'}</div>
      <div class="panel">
        <h2>Equipe</h2>
        <div class="tablewrap" id="table"></div>
      </div>`;

    const load = async () => {
      const page = await api.get('/users?size=200');
      draw(el, page.content, ctx.user, isAdmin);
    };

    el.addEventListener('change', async (event) => {
      const input = event.target.closest('[data-user]');
      if (!input) return;
      const id = input.dataset.user;
      const body = input.name === 'role' ? { role: input.value } : { active: input.checked };
      input.disabled = true;
      try {
        await api.patch(`/users/${id}`, body);
        toast(input.name === 'role'
          ? 'Perfil atualizado. A pessoa precisará entrar novamente.'
          : input.checked ? 'Acesso reativado.' : 'Acesso desativado e sessões encerradas.');
      } catch (error) {
        showError(error);
      } finally {
        await load().catch(showError);
      }
    });

    el.addEventListener('click', async (event) => {
      if (!event.target.closest('[data-action="new"]')) return;
      const created = await dialog({
        title: 'Novo usuário',
        submitLabel: 'Criar usuário',
        body: `
          <div class="fields">
            <div class="full"><label for="u-name">Nome completo *</label><input id="u-name" name="name" required maxlength="120"></div>
            <div><label for="u-email">E-mail corporativo *</label><input id="u-email" name="email" type="email" required maxlength="160" autocomplete="off"></div>
            <div><label for="u-sector">Setor</label><input id="u-sector" name="sector" maxlength="80"></div>
            <div><label for="u-role">Perfil *</label><select id="u-role" name="role">
              ${Object.entries(LABELS.role).map(([v, t]) => `<option value="${v}">${esc(t)}</option>`).join('')}</select></div>
            <div><label for="u-password">Senha inicial *</label>
              <input id="u-password" name="password" type="password" required minlength="8" maxlength="72" autocomplete="new-password"></div>
          </div>
          <p class="hint">Compartilhe a senha inicial por um canal seguro e peça para a pessoa trocá-la em “Minha conta”.</p>`,
        onSubmit: (form) => {
          const field = (name) => form.elements.namedItem(name).value.trim();
          return api.post('/users', {
            name: field('name'),
            email: field('email'),
            sector: field('sector') || null,
            role: field('role'),
            password: form.elements.namedItem('password').value,
          });
        },
      });
      if (created) {
        toast(`Usuário ${created.name} criado.`);
        await load().catch(showError);
      }
    });

    await load();
  },
};

function draw(el, users, me, isAdmin) {
  $('#table', el).innerHTML = `
    <table>
      <thead><tr><th>Pessoa</th><th>Setor</th><th>Perfil</th><th>Acesso</th><th>Último acesso</th></tr></thead>
      <tbody>${users.map((u) => {
        const editable = isAdmin && u.id !== me.id;
        const disabled = editable ? '' : 'disabled';
        return `<tr>
          <td><strong>${esc(u.name)}</strong>${u.id === me.id ? ' <span class="pill">você</span>' : ''}<br><span class="muted">${esc(u.email)}</span></td>
          <td>${esc(u.sector || '—')}</td>
          <td><select name="role" data-user="${esc(u.id)}" aria-label="Perfil de ${esc(u.name)}" ${disabled}>
            ${Object.entries(LABELS.role).map(([v, t]) => `<option value="${v}" ${v === u.role ? 'selected' : ''}>${esc(t)}</option>`).join('')}
          </select></td>
          <td><label class="check m0"><input type="checkbox" name="active" data-user="${esc(u.id)}" ${u.active ? 'checked' : ''} ${disabled}> Ativo</label></td>
          <td>${esc(fmtDateTime(u.lastLoginAt))}</td>
        </tr>`;
      }).join('')}</tbody>
    </table>`;
}
