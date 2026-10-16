# Autenticação — BE-04 / BE-05

## Cadastro público

`POST /api/v1/usuarios` mantém o contrato existente. Nome, e-mail e senha são obrigatórios; telefone é opcional. O perfil é sempre `CIDADAO`, definido no servidor. A senha é persistida como hash BCrypt e não aparece nas respostas.

O cadastro não permite criar administradores. O provisionamento de administradores deve ser definido em uma entrega própria, sem expor seleção de perfil no cadastro público.

## Login

`POST /api/v1/auth/login` é público e recebe JSON:

```json
{
  "email": "cidadao@example.com",
  "senha": "senha-do-usuario"
}
```

O e-mail é normalizado com a mesma regra do cadastro. A senha é comparada com o hash armazenado. Conta inexistente e senha incorreta recebem a mesma mensagem; uma comparação de hash também é executada para contas inexistentes.

Resposta `200 OK`, com `Cache-Control: no-store`:

```json
{
  "accessToken": "<JWT assinado>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "usuario": {
    "id": "a0af53ab-0e31-4f12-95fd-d21d1d1dc5e4",
    "nome": "Cidadão",
    "email": "cidadao@example.com",
    "perfil": "CIDADAO",
    "telefone": null,
    "criadoEm": "2026-10-16T10:00:00"
  }
}
```

`expiresIn` é a validade configurada, em segundos. Dados pessoais retornados no login pertencem ao próprio usuário autenticado. O token contém apenas `iss`, `sub` (UUID), `iat`, `exp` e `perfil`; não contém nome, e-mail, telefone nem senha.

## Requisições autenticadas

Envie o token no cabeçalho:

```http
Authorization: Bearer <accessToken>
```

`GET /api/v1/auth/me` retorna o mesmo formato de usuário, consultando o cadastro pelo UUID do token. Usuário removido recebe `401`. A resposta usa `Cache-Control: no-store`.

Somente os métodos `POST` de cadastro e login são públicos. As demais rotas exigem autenticação. A API não cria sessão de login: cada requisição protegida deve enviar seu próprio Bearer token.

## Validação e autorização

- Assinatura HS256 com chave configurada externamente, sem chave padrão de produção.
- Validação de emissor, expiração obrigatória, validade temporal, UUID do usuário e perfil conhecido.
- Expiração sem tolerância adicional de relógio; mantenha os relógios dos servidores sincronizados.
- `CIDADAO` e `ADMINISTRADOR` são convertidos em `ROLE_CIDADAO` e `ROLE_ADMINISTRADOR` no contexto de segurança.
- As próximas operações administrativas devem declarar autorização, por exemplo `@PreAuthorize("hasRole('ADMINISTRADOR')")`. Autenticação isolada não restringe uma operação ao administrador.
- As regras de acesso a ocorrências de outros usuários continuam dependendo da definição de visibilidade do produto.

O perfil do JWT reflete o momento do login. Alterações de perfil só afetam tokens novos ou a expiração dos anteriores. Esta entrega não inclui refresh token, logout com revogação ou alteração de perfil. Para sair no cliente, descarte o token; o token emitido continua válido até expirar ou até a chave ser trocada. Use HTTPS fora do ambiente local.

## Erros

As respostas seguem o `ApiError` existente: `status`, `erro`, `mensagem`, `caminho` e `timestamp`.

| HTTP | Situação |
| --- | --- |
| `400` | Corpo ausente, JSON inválido ou campos inválidos |
| `401` | Credenciais incorretas, token ausente/inválido/expirado ou usuário removido na consulta `/me` |
| `403` | Usuário autenticado sem a permissão exigida |

Falhas de login usam a mensagem `E-mail ou senha inválidos.`. Respostas `401` incluem `WWW-Authenticate: Bearer`.

## Configuração

As variáveis, os comandos de execução e a geração de chave estão no [README](../README.md). Configuração ausente, chave inválida/curta ou duração inferior a um segundo impedem a inicialização.

## Validação desta entrega

- 51 testes aprovados com `mvnw.cmd clean verify` em 16/10/2026, usando o Java 24 instalado e compilação com alvo Java 21.
- Testes de integração cobrem cadastro, hash real de senha, login, JWT real, consulta do usuário, respostas 400/401/403 e permissões de cidadão/administrador.
- Testes de tokens cobrem assinatura com outra chave, expiração, ausência de expiração, emissor incorreto, UUID/perfil inválidos e configuração inválida.
- Flyway e Hibernate `validate` exercitados em H2; esta execução não comprova compatibilidade com PostgreSQL.
- Docker estava indisponível durante a validação local. Execução em PostgreSQL e no runtime Java 21 ainda precisa ser confirmada antes da integração final.
