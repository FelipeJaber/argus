import { auth } from '../api.js';
import { $, brand, busy, showFieldErrors } from '../ui.js';

/** Tela de login (painel institucional à esquerda, formulário à direita). */
export default function renderLogin(root, { onLogin }) {
  root.innerHTML = `
    <div class="login">
      <section class="login-left" aria-hidden="true">
        ${brand}
        <div>
          <span class="eyebrow">INTELIGÊNCIA COM RESPONSABILIDADE</span>
          <h1>O potencial da IA.<br>O controle da<br>sua empresa.</h1>
          <p>Um ambiente para conectar pessoas a agentes aprovados, com propósito e governança.</p>
          <div class="orbit">
            <span class="brand-mark">◈</span>
            <span class="sat s1">⌘</span><span class="sat s2">✎</span><span class="sat s3">✓</span>
          </div>
        </div>
        <div class="row">
          <span class="pill on-dark">✓ Agentes homologados</span>
          <span class="login-tag">Acesso controlado. Uso rastreável.</span>
        </div>
      </section>

      <section class="login-right">
        <div class="login-box">
          <div class="mobilebrand">${brand}</div>
          <span class="pill">GOVERNANÇA DE IA</span>
          <h1>Bem-vindo de volta.</h1>
          <p class="muted">Acesse seu ambiente de inteligência corporativa.</p>
          <form id="loginform" novalidate>
            <label for="email">E-mail corporativo</label>
            <input id="email" name="email" type="email" required autocomplete="username" autofocus>
            <label for="password">Senha</label>
            <input id="password" name="password" type="password" required autocomplete="current-password">
            <div id="login-error" class="form-error" role="alert"></div>
            <button class="btn" type="submit">Entrar no ambiente <span aria-hidden="true">→</span></button>
          </form>
          <p class="hint">Após várias tentativas sem sucesso, o acesso fica bloqueado por alguns minutos.</p>
          <div class="login-foot">
            PROJETO ACADÊMICO • ENGENHARIA DE SOFTWARE<br>
            Breno Cardoso Vieira · Karen Quézia Oliveira<br>
            Felipe Jaber Andrade · Marya Laura Menezes
          </div>
        </div>
      </section>
    </div>`;

  const form = $('#loginform', root);
  const errorBox = $('#login-error', root);

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    errorBox.textContent = '';
    showFieldErrors(form, {});
    if (!form.reportValidity()) return;

    try {
      const user = await busy(event.submitter, () => auth.login(form.email.value.trim(), form.password.value));
      onLogin(user);
    } catch (error) {
      form.password.value = '';
      errorBox.textContent = error.message;
      if (error.errors) showFieldErrors(form, error.errors);
      form.password.focus();
    }
  });
}
