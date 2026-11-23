# Roteiro de demonstração (MVP)

Cerca de 10 minutos. Mostra o ciclo completo: **uso com políticas → governança → auditoria**.

## Antes de começar

1. JDK 25 instalado (`java -version`).
2. Na pasta `backend`:
   - Windows (PowerShell): `.\mvnw.cmd spring-boot:run`
   - Linux/macOS/Git Bash: `./mvnw spring-boot:run`
3. Espere a linha `Started ArgusApplication` (cerca de 15 s) e abra **http://localhost:8080**.

Senha de todos os usuários de demonstração: `Argus@2026`.

| Usuário | Perfil | Usar para |
|---|---|---|
| `breno@empresa.exemplo` | Colaborador | catálogo e chat |
| `karen@empresa.exemplo` | Gestora de governança | homologação, cadastro, auditoria |
| `felipe@empresa.exemplo` | Administrador | pessoas e acessos, segunda aprovação |

> O banco é em memória: **reiniciar a aplicação volta tudo ao estado inicial**. Use isso para ensaiar.

## 1. Colaborador: usar IA com proteção (Breno)

1. Entre como Breno. Mostre que o menu só tem **Meus agentes**: o perfil define o que aparece.
2. **Catálogo:** 2 agentes homologados. Use o filtro **Tecnologia** e a busca.
3. Abra **Suporte de TI → Iniciar conversa**:
   - clique **Acessar a VPN** e envie → resposta do agente;
   - clique **Testar bloqueio de dados** e envie → **bloqueado pela política de dados** (nada vai ao provedor);
   - digite `Me indica um filme para hoje?` → **bloqueado pela política de finalidade**.
4. Ponto para a banca: o conteúdo das mensagens **não é gravado**, só o resultado (ver auditoria adiante).

## 2. Governança: homologar com segregação de funções (Karen)

1. **Sair do ambiente** e entre como Karen. O menu agora tem a área de **Governança**.
2. **Gestão de agentes:** indicadores e o "Assistente de RH" **em análise**.
3. **Homologação:** o agente foi submetido pelo Felipe, então a Karen pode avaliar.
   - Mostre que **Aprovar** fica travado até marcar os 5 itens do checklist **e** escrever a justificativa.
   - Aprove → o agente vai para o catálogo e o contador do menu zera.
4. **Cadastrar agente:** crie, por exemplo, "Assistente de projetos" e clique **Enviar para homologação**.
5. Volte em **Homologação**: como a própria Karen submeteu, a tela **não deixa ela aprovar**
   ("A homologação precisa ser feita por outra pessoa da governança"). A API também bloqueia.

## 3. Administração e segunda aprovação (Felipe)

1. Entre como Felipe → **Homologação** → aprove o "Assistente de projetos" da Karen.
2. **Pessoas e acessos:** o administrador muda perfis e ativa/desativa contas (as sessões da pessoa
   caem na hora). Mostre que ele não consegue alterar a própria conta.
3. **Auditoria:** quem fez o quê e quando, com filtros por evento e resultado: logins, senha errada,
   bloqueios de política, submissões e aprovações.

## 4. Mostrar o banco de dados (console H2)

Abra **http://localhost:8080/h2-console** em outra aba e preencha:

| Campo | Valor |
|---|---|
| JDBC URL | `jdbc:h2:mem:argus` (troque o `jdbc:h2:~/test` que vem preenchido) |
| User Name | `sa` |
| Password | (vazio) |

Clique **Connect**, cole uma consulta no campo de SQL e use **Run** (ou Ctrl+Enter):

```sql
-- Senhas: só o hash BCrypt, cada um com salt diferente (nunca a senha em texto)
SELECT name, email, role, password_hash FROM users;

-- Sessões de login: o refresh token fica só como hash SHA-256; logout/revogação ficam registrados
SELECT user_id, refresh_token_hash, created_at, expires_at, revoked_at, revoked_reason FROM auth_sessions;

-- Auditoria: metadados do que aconteceu, sem o texto das mensagens enviadas aos agentes
SELECT occurred_at, action, outcome, actor_name, agent_name, detail FROM audit_events ORDER BY occurred_at DESC;

-- Ciclo de vida dos agentes e pareceres de homologação
SELECT name, status, release_version FROM agents;
SELECT reviewed_at, decision, justification, reviewed_version FROM agent_reviews;

-- Schema versionado pelo Flyway
SELECT version, description, installed_on FROM flyway_schema_history;
```

Faça isso **depois** das partes 1 a 3, para as tabelas de sessões e auditoria já terem dados.

> O console só existe no perfil de desenvolvimento e só aceita acesso do próprio computador.
> Em produção (PostgreSQL) ele fica desligado.

## 5. Fechamento (opcional)

- Entre de novo como Breno: o catálogo agora tem os agentes aprovados durante a demonstração.
- **Minha conta:** dispositivos conectados e "sair de todos os dispositivos".
- Recarregue a página (F5): a sessão continua, sem token guardado no navegador.
- Swagger da API: http://localhost:8080/swagger-ui.html

## Se algo der errado

| Sintoma | O que fazer |
|---|---|
| `Port 8080 was already in use` | Feche a outra instância ou rode com `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"` e abra a porta 8081 |
| "Muitas tentativas sem sucesso" no login | A conta bloqueia após 5 senhas erradas; no perfil de desenvolvimento libera em 1 minuto |
| Fontes diferentes do layout | Sem internet o navegador usa a fonte padrão; o sistema funciona normalmente |
| Quer recomeçar do zero | Pare (Ctrl+C) e rode de novo: o banco em memória é recriado com os dados iniciais |

## O que é MVP (para responder à banca)

- As respostas dos agentes são **demonstrativas**: ainda não há conexão com um modelo de IA. A arquitetura já
  tem o ponto de extensão (`AgentGateway`), e as políticas rodam **antes** de qualquer chamada ao provedor.
- As políticas de uso são regras simples (palavras-chave e padrão de CPF), substituíveis por um classificador.
- Uma organização só, sem permissão por agente/setor e sem recuperação de senha por e-mail.
