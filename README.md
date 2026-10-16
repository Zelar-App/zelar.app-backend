# Zelar.app — Backend

API REST do Zelar.app para cadastro de cidadãos, autenticação e evolução do gerenciamento de ocorrências urbanas.

## Tecnologias

- Java 21 e Spring Boot 4.0.8
- Maven Wrapper
- Spring MVC, Data JPA, Security e Bean Validation
- PostgreSQL e Flyway
- JWT com Spring Security OAuth2 Resource Server
- JUnit, Mockito e H2 para testes

O código é organizado por domínio, mantendo controller, service, repository, entity e DTO separados dentro de cada módulo.

## Estado atual

- Cadastro: `POST /api/v1/usuarios`.
- Login: `POST /api/v1/auth/login`.
- Usuário autenticado: `GET /api/v1/auth/me`.
- Ocorrências e funcionalidades administrativas ainda estão em desenvolvimento.

Veja os contratos e regras em [Autenticação](docs/autenticacao.md).

## Configuração local

Pré-requisitos: JDK 21 e PostgreSQL acessível, com banco previamente criado. O Flyway cria as tabelas na inicialização; o Hibernate valida o schema com `ddl-auto=validate`.

Configure as variáveis abaixo no ambiente do processo. No IntelliJ, use **Run → Edit Configurations → Environment variables** e selecione o JDK 21 para o projeto e para o Maven.

| Variável | Obrigatória | Valor padrão / finalidade |
| --- | --- | --- |
| `DB_URL` | Sim | URL JDBC do PostgreSQL |
| `DB_USERNAME` | Sim | Usuário do banco |
| `DB_PASSWORD` | Sim | Senha do banco |
| `JWT_SECRET` | Sim | Chave aleatória de pelo menos 32 bytes, codificada em Base64 |
| `JWT_ISSUER` | Não | `zelar.app` |
| `JWT_TOKEN_DURATION` | Não | `PT1H` (uma hora), duração ISO-8601 de pelo menos um segundo |
| `SERVER_PORT` | Não | `8080` |

O arquivo [.env.example](.env.example) é um modelo sem credenciais. O Spring Boot não carrega `.env` automaticamente: informe as variáveis na configuração de execução ou no terminal. Não versione `.env`, senhas ou tokens.

Para gerar uma chave local no PowerShell:

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Copie o resultado para `JWT_SECRET`. Use uma chave exclusiva por ambiente e preserve-a entre reinicializações; a troca invalida os tokens já emitidos.

Com as variáveis configuradas, execute a classe `app.zelar.ZelarAppBackendApplication` no IntelliJ ou:

```powershell
.\mvnw.cmd spring-boot:run
```

## Testes e build

```powershell
.\mvnw.cmd clean verify
```

Em Linux/macOS, use `./mvnw clean verify`. Os testes usam H2 e uma chave exclusiva de testes, sem exigir PostgreSQL nem credenciais reais. A integração de autenticação usa Flyway e `ddl-auto=validate`, além de tokens assinados reais. Rotas administrativas de teste existem somente no código de testes.

## Docker

O Compose atual constrói e inicia **somente o backend**. Ele requer um PostgreSQL externo e as mesmas variáveis, podendo ler `.env` pelo próprio Docker Compose.

```powershell
docker compose up --build
```

No Docker Desktop, para um PostgreSQL executando no computador, utilize `host.docker.internal` no lugar de `localhost` na URL JDBC. A integração de um serviço PostgreSQL no Compose continua pendente.

## Desenvolvimento

- `main`: versão estável; `stage`: homologação; `develop`: integração.
- Novas entregas partem da `develop` em `feature/*` ou `fix/*`.
- Faça commits pequenos e coesos, execute os testes e revise o diff antes de integrar.
- Nesta colaboração, commits são locais e **todo push é executado manualmente pelo responsável pelo projeto**.
- Abra PR para `develop` após o push manual. Não encerre uma issue como entregue apenas porque existe implementação local.
