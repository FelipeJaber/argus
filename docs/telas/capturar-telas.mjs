// Gera as imagens desta pasta a partir do sistema rodando, usando Edge/Chrome headless pelo
// DevTools Protocol (sem dependências: Node 22+ já traz fetch e WebSocket).
//
// 1. Suba a aplicação com o banco zerado:  cd backend && ./mvnw spring-boot:run
// 2. Rode:                                 node docs/telas/capturar-telas.mjs [pasta-de-saída]
//
// O roteiro altera os dados (aprova o "Assistente de RH"): reinicie a aplicação antes de rodar de novo.
// Outro navegador: defina BROWSER com o caminho do executável do Chrome/Chromium/Edge.
import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';

const OUT = process.argv[2] || dirname(fileURLToPath(import.meta.url));
const BASE = process.env.ARGUS_URL || 'http://localhost:8080/';
const EDGE = process.env.BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const PORT = 9333;
const PASSWORD = 'Argus@2026'; // senha fictícia dos usuários de demonstração (application.properties)
const W = 1440, H = 900;

mkdirSync(OUT, { recursive: true });
const profile = join(tmpdir(), `argus-shots-${Date.now()}`);
const edge = spawn(EDGE, [
  '--headless=new', `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
  '--no-first-run', '--no-default-browser-check', '--disable-extensions', '--hide-scrollbars',
  `--window-size=${W},${H}`, 'about:blank',
], { stdio: 'ignore' });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function pageWs() {
  for (let i = 0; i < 50; i++) {
    try {
      const list = await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json();
      const page = list.find((t) => t.type === 'page');
      if (page) return page.webSocketDebuggerUrl;
    } catch { /* ainda subindo */ }
    await sleep(200);
  }
  throw new Error('Edge não abriu a porta de depuração');
}

const ws = new WebSocket(await pageWs());
await new Promise((r) => ws.addEventListener('open', r, { once: true }));
let seq = 0;
const pending = new Map();
ws.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data);
  if (msg.id && pending.has(msg.id)) {
    const { resolve, reject } = pending.get(msg.id);
    pending.delete(msg.id);
    msg.error ? reject(new Error(msg.error.message)) : resolve(msg.result);
  }
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++seq;
  pending.set(id, { resolve, reject });
  ws.send(JSON.stringify({ id, method, params }));
});

async function js(expression) {
  const r = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
  if (r.exceptionDetails) throw new Error(`${expression}\n→ ${r.exceptionDetails.exception?.description || r.exceptionDetails.text}`);
  return r.result.value;
}
async function waitFor(expression, label, timeout = 10000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) {
    if (await js(`!!(${expression})`)) return;
    await sleep(100);
  }
  throw new Error(`Tempo esgotado esperando: ${label}`);
}
async function settle() {
  await js('document.fonts.ready.then(() => true)');
  await sleep(400);
}
async function shot(name, { full = false } = {}) {
  await waitFor(`!document.querySelector('#toast')?.className`, 'toast sumir', 8000);
  await settle();
  let h = H;
  if (full) {
    h = Math.min(1600, Math.max(H, await js('document.documentElement.scrollHeight')));
    await send('Emulation.setDeviceMetricsOverride', { width: W, height: h, deviceScaleFactor: 1, mobile: false });
    await settle();
  }
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(join(OUT, name), Buffer.from(data, 'base64'));
  if (full) await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false });
  console.log('ok', name, `${W}x${h}`);
}
async function go(hash, readyExpr, label) {
  await js(`location.hash = ${JSON.stringify(hash)}`);
  await waitFor(readyExpr, label);
}
async function login(email) {
  await waitFor(`document.querySelector('#loginform')`, 'tela de login');
  await js(`(() => {
    const f = document.querySelector('#loginform');
    f.elements.namedItem('email').value = ${JSON.stringify(email)};
    f.elements.namedItem('password').value = ${JSON.stringify(PASSWORD)};
    f.requestSubmit();
  })()`);
  await waitFor(`document.querySelector('[data-action="logout"]') && document.querySelector('.card')`, `catálogo de ${email}`);
}
async function logout() {
  await js(`document.querySelector('[data-action="logout"]').click()`);
  await waitFor(`document.querySelector('#loginform')`, 'voltar ao login');
}
async function sendChat(text) {
  const before = await js(`document.querySelectorAll('#messages .bubble').length`);
  await js(`(() => {
    const f = document.querySelector('#chatform');
    f.elements.namedItem('message').value = ${JSON.stringify(text)};
    f.requestSubmit(f.querySelector('button[type="submit"]'));
  })()`);
  await waitFor(`document.querySelectorAll('#messages .bubble').length >= ${before + 2} && !document.querySelector('#messages .typing')`, `resposta para "${text}"`);
}

try {
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: BASE });

  // 1. Login
  await waitFor(`document.querySelector('#loginform')`, 'tela de login');
  await shot('01-login.png');
  await js(`(() => {
    const f = document.querySelector('#loginform');
    f.elements.namedItem('email').value = 'marya@empresa.exemplo';
    f.elements.namedItem('password').value = 'senha-errada';
    f.requestSubmit(f.querySelector('button[type="submit"]'));
  })()`);
  await waitFor(`document.querySelector('#login-error')?.textContent.trim()`, 'erro de login');
  await js(`document.querySelector('#login-error').textContent = ''`);

  // 2-3. Colaborador: catálogo e chat com as políticas de uso
  await login('breno@empresa.exemplo');
  await shot('02-catalogo.png');
  const chatHref = await js(`[...document.querySelectorAll('.card')]
      .find((c) => c.textContent.includes('Suporte de TI'))
      .querySelector('a[href*="/chat"]').getAttribute('href')`);
  await go(chatHref, `document.querySelector('#chatform')`, 'chat');
  await sendChat('Como faço para acessar a VPN da empresa de casa?');
  await sendChat('Meu CPF é 123.456.789-09, pode cadastrar meu acesso?');
  await sendChat('Me indica um filme para hoje?');
  await js(`(() => { const m = document.querySelector('#messages'); m.scrollTop = m.scrollHeight; })()`);
  await shot('03-chat-politicas.png', { full: true });
  await logout();

  // 4-6. Governança (Karen): gestão, homologação e cadastro
  await login('karen@empresa.exemplo');
  await go('#/manage', `document.querySelector('main') && document.body.textContent.includes('Assistente de RH')`, 'gestão');
  await shot('04-gestao-agentes.png');
  await go('#/review', `document.querySelector('#decision')`, 'homologação');
  await js(`(() => {
    document.querySelectorAll('input[name="checklist"]').forEach((c) => { c.checked = true; c.dispatchEvent(new Event('change', { bubbles: true })); });
    const t = document.querySelector('#justification');
    t.value = 'Finalidade clara, sem acesso a dados pessoais e com base de conhecimento somente leitura.';
    t.dispatchEvent(new Event('input', { bubbles: true }));
  })()`);
  await waitFor(`!document.querySelector('#approve').disabled`, 'botão aprovar habilitado');
  await shot('05-homologacao.png', { full: true });
  // Aprova de verdade: entra no catálogo e gera o evento de homologação na auditoria
  await js(`document.querySelector('#decision').requestSubmit(document.querySelector('#approve'))`);
  await waitFor(`!document.body.textContent.includes('Assistente de RH • v1.0')`, 'aprovação concluída');
  await go('#/agents/new', `document.querySelector('#name')`, 'cadastro');
  await js(`(() => {
    const set = (id, v) => { const el = document.querySelector('#' + id); el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); };
    set('name', 'Assistente de projetos');
    set('description', 'Organize cronogramas, atas e status reports dos projetos da sua área.');
    set('purpose', 'Apoiar a gestão de projetos internos: cronogramas, atas e relatórios de andamento. Não acessa dados de clientes.');
    set('model', 'Modelo corporativo');
    set('releaseVersion', '1.0');
  })()`);
  await shot('06-cadastro-agente.png');
  await logout();

  // 7-9. Administrador (Felipe): pessoas, auditoria e conta
  await login('felipe@empresa.exemplo');
  await go('#/people', `document.body.textContent.includes('marya@empresa.exemplo')`, 'pessoas');
  await shot('07-pessoas-acessos.png');
  await go('#/audit', `document.querySelectorAll('tbody tr').length > 3`, 'auditoria');
  await shot('08-auditoria.png', { full: true });
  await go('#/account', `document.body.textContent.includes('felipe@empresa.exemplo')`, 'minha conta');
  await shot('09-minha-conta.png');
} finally {
  ws.close();
  edge.kill();
}
