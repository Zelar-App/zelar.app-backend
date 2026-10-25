# Zelar.app Backend

API do Zelar.app com Java 21, Spring Boot, Maven e PostgreSQL.

## Pré-requisitos

- JDK 21 configurado no terminal ou no IntelliJ.
- PostgreSQL acessível com um banco vazio para a primeira execução.
- Maven Wrapper do projeto; não é necessário instalar Maven separadamente.

## Configuração

Configure as variáveis no ambiente do processo ou na configuração de execução do IntelliJ:

| Variável | Uso | Padrão |
| --- | --- | --- |
| `DB_URL` | URL JDBC, por exemplo `jdbc:postgresql://localhost:5432/zelar` | Obrigatória |
| `DB_USERNAME` | Usuário do banco | Obrigatória |
| `DB_PASSWORD` | Senha do banco | Obrigatória |
| `JWT_SECRET` | Chave aleatória de pelo menos 32 bytes codificada em Base64 | Obrigatória |
| `JWT_ISSUER` | Emissor aceito nos tokens | `zelar.app` |
| `JWT_TOKEN_DURATION` | Duração no formato ISO-8601 | `PT1H` |
| `SERVER_PORT` | Porta HTTP | `8080` |

Use `.env.example` como referência. Não versione credenciais reais e não reutilize a chave do perfil de testes. A aplicação iniciada pelo Maven ou IntelliJ não carrega um arquivo `.env` automaticamente.

As migrations Flyway versionadas em `src/main/resources/db/migration` criam o schema. O Hibernate usa `validate`: não substitua essa configuração por geração automática para contornar uma migration ausente.

## Execução e testes

No PowerShell, a partir da raiz do projeto:

```powershell
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Em Linux ou macOS, use `./mvnw`. A suíte padrão usa H2 em modo PostgreSQL; os testes de integração de autenticação validam o schema criado pelo Flyway. Isso não substitui a validação do ambiente com PostgreSQL real.

O Compose atual contém apenas o backend e espera um PostgreSQL acessível externamente. Para usá-lo, configure o `.env` e execute `docker compose up --build`. Dentro do container, `localhost` aponta para o próprio container; no Docker Desktop, `host.docker.internal` pode apontar para o banco no host. O mapeamento atual expõe `8080:8080`; alterar a porta exige alinhar configuração e mapeamento.

## Autenticação

| Método | Caminho | Comportamento |
| --- | --- | --- |
| POST | `/api/v1/usuarios` | Cadastro público de cidadão |
| POST | `/api/v1/auth/login` | Login público por email e senha |
| GET | `/api/v1/auth/me` | Consulta do próprio usuário autenticado |

Exemplo do corpo de login:

```json
{"email":"demo@example.com","senha":"SenhaDemo123"}
```

O login retorna `accessToken`, `tokenType` (`Bearer`), `expiresIn` em segundos e `usuario`, sem senha. Nas rotas protegidas, envie `Authorization: Bearer <accessToken>`.

O token usa HS256 e contém emissor, UUID do usuário, perfil, emissão e expiração. As claims não incluem nome, email, telefone ou senha. O conteúdo é assinado, não criptografado. A API é stateless; estes endpoints não implementam refresh token ou revogação de tokens.

As respostas de erro seguem `ApiError`: 400 para entrada inválida, 401 para autenticação inválida, 403 para falta de permissão, 404 para recurso inexistente e 409 para conflito como email duplicado. Somente os dois POSTs públicos acima dispensam autenticação; os demais métodos e caminhos são protegidos.

## Integração contínua

O GitHub Actions executa `clean verify` com Java 21 e o build da imagem Docker em PRs e pushes para `develop`, `stage` e `main`.

## Criação de ocorrências

`POST /api/v1/ocorrencias` exige `Authorization: Bearer <accessToken>` e um corpo JSON:

```json
{
  "categoria": "BURACO",
  "descricao": "Buraco na via próximo à praça",
  "latitude": -12.97,
  "longitude": -38.50,
  "enderecoAproximado": "Praça central"
}
```

Categorias aceitas: `BURACO`, `ILUMINACAO`, `LIXO`, `ALAGAMENTO`, `CALCADA`, `SINALIZACAO` e `AMBIENTAL`. Categoria e descrição são obrigatórias; latitude deve estar entre -90 e 90 e longitude entre -180 e 180. Endereço é opcional, com até 255 caracteres.

A resposta é `201 Created`, com UUID, `usuarioId`, os dados da ocorrência, status `ENVIADA`, prioridade nula e data de criação. O autor é obtido do token; enviar `usuarioId`, `status` ou `prioridade` no corpo não altera esses valores controlados pelo servidor. A resposta não inclui nome, email, telefone ou senha do autor. Se o usuário do token não existir mais, a API retorna 401 sem persistir a ocorrência.

Para conferir o fluxo com PostgreSQL, use um banco de validação separado, configure as variáveis acima e inicie a aplicação. Confirme a aplicação das migrations e a inicialização com `ddl-auto=validate`; cadastre um cidadão, faça login e crie ocorrências das sete categorias. Verifique 400 para coordenadas inválidas e 401 sem token, além da persistência após reiniciar a aplicação. Não execute limpeza ou geração automática de tabelas em um banco compartilhado.
