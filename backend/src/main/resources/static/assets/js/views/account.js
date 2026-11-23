import { api, auth } from '../api.js';
import { $, busy, dialog, esc, fmtDateTime, head, initials, label, showError, showFieldErrors, toast } from '../ui.js';

/** Minha conta: perfil (GET /me), troca de senha (PUT /me/password) e sessões (GET/DELETE /auth/sessions). */
export default {
  title: 'Minha conta',
  nav: 'account',

  async render(el, _params, ctx) {
    const me = await api.get('/me');

    el.innerHTML = head('SEGURANÇA E ACESSO', 'Minha conta', 'Seus dados de acesso e os dispositivos conectados.') + `
      <div class="split">
        <div>
          <div class="panel">
            <h2>Dispositivos conectados</h2>
            <p class="hint">Cada login abre uma sessão. Encerre as que você não reconhece.</p>
            <div class="tablewrap" id="sessions"></div>
            <div class="row mt">
              <button class="btn light" type="button" data-action="logout-all">Sair de todos os dispositivos</button>
            </div>
          </div>
          <form class="panel" id="password-form" novalidate>
            <h2>Alterar senha</h2>
            <div class="fields">
              <div><label for="currentPassword">Senha atual *</label>
                <input id="currentPassword" name="currentPassword" type="password" required autocomplete="current-password"></div>
              <div><label for="newPassword">Nova senha * <small>(8 a 72 caracteres)</small></label>
                <input id="newPassword" name="newPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password"></div>
            </div>
            <p class="hint">Ao trocar a senha, as outras sessões são encerradas. Este dispositivo continua conectado.</p>
            <button class="btn" type="submit">Salvar nova senha</button>
          </form>
        </div>
        <aside>
          <div class="panel">
            <div class="row"><span class="avatar">${esc(initials(me.name))}</span><div><b>${esc(me.name)}</b><div class="hint">${esc(me.email)}</div></div></div>
            <hr>
            <div class="detail single">
              <div><small>Perfil</small>${esc(label('role', me.role))}</div>
              <div><small>Setor</small>${esc(me.sector || '—')}</div>
              <div><small>Último acesso</small>${esc(fmtDateTime(me.lastLoginAt))}</div>
            </div>
          </div>
        </aside>
      </div>`;

    const loadSessions = async () => {
      const sessions = await api.get('/auth/sessions');
      $('#sessions', el).innerHTML = `
        <table>
          <thead><tr><th>Dispositivo</th><th>Endereço</th><th>Início</th><th>Expira</th><th></th></tr></thead>
          <tbody>${sessions.map((s) => `<tr>
            <td><strong>${esc(describeAgent(s.userAgent))}</strong>${s.current ? ' <span class="pill green">este dispositivo</span>' : ''}</td>
            <td>${esc(describeAddress(s.ipAddress))}</td>
            <td>${esc(fmtDateTime(s.createdAt))}</td>
            <td>${esc(fmtDateTime(s.expiresAt))}</td>
            <td>${s.current ? '' : `<button class="textbtn danger" data-session="${esc(s.id)}">Encerrar</button>`}</td>
          </tr>`).join('')}</tbody>
        </table>`;
    };

    el.addEventListener('click', async (event) => {
      const session = event.target.closest('[data-session]');
      if (session) {
        try {
          await busy(session, () => api.delete(`/auth/sessions/${session.dataset.session}`));
          toast('Sessão encerrada.');
          await loadSessions();
        } catch (error) {
          showError(error);
        }
      }

      if (event.target.closest('[data-action="logout-all"]')) {
        const confirmed = await dialog({
          title: 'Sair de todos os dispositivos?',
          body: '<p>Todas as suas sessões serão encerradas, inclusive esta. Você precisará entrar novamente.</p>',
          submitLabel: 'Sair de todos',
          danger: true,
          onSubmit: () => auth.logoutAll(),
        });
        if (confirmed !== null) location.reload();
      }
    });

    const form = $('#password-form', el);
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      showFieldErrors(form, {});
      if (!form.reportValidity()) return;
      try {
        await busy(event.submitter, () => api.put('/me/password', {
          currentPassword: form.currentPassword.value,
          newPassword: form.newPassword.value,
        }));
        form.reset();
        toast('Senha alterada. As outras sessões foram encerradas.');
        await loadSessions();
      } catch (error) {
        showFieldErrors(form, error.errors);
        showError(error);
      }
    });

    await loadSessions();
  },
};

function describeAddress(ip) {
  if (!ip) return '—';
  return ['127.0.0.1', '::1', '0:0:0:0:0:0:0:1'].includes(ip) ? 'localhost' : ip;
}

/** Resumo legível do User-Agent ("Chrome no Windows"). */
function describeAgent(userAgent) {
  if (!userAgent) return 'Dispositivo desconhecido';
  const browser = /Edg\//.test(userAgent) ? 'Edge'
    : /Firefox\//.test(userAgent) ? 'Firefox'
      : /Chrome\//.test(userAgent) ? 'Chrome'
        : /Safari\//.test(userAgent) ? 'Safari' : 'Navegador';
  const system = /Windows/.test(userAgent) ? 'Windows'
    : /Android/.test(userAgent) ? 'Android'
      : /iPhone|iPad/.test(userAgent) ? 'iOS'
        : /Mac OS X/.test(userAgent) ? 'macOS'
          : /Linux/.test(userAgent) ? 'Linux' : '';
  return system ? `${browser} no ${system}` : browser;
}
