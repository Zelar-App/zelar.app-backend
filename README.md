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

## Conta administrativa

O cadastro público (`POST /api/v1/usuarios`) sempre cria `CIDADAO`; o corpo não aceita `perfil` e campos extras são ignorados. A conta `ADMINISTRADOR` só é criada pelo provisionamento no startup, quando as variáveis abaixo estão definidas:

| Variável | Uso | Padrão |
| --- | --- | --- |
| `ADMIN_EMAIL` | E-mail da conta administrativa | Obrigatória para provisionar |
| `ADMIN_PASSWORD` | Senha em texto, entre 8 e 72 caracteres; armazenada com BCrypt | Obrigatória para provisionar |
| `ADMIN_NOME` | Nome de exibição da conta | `Administrador` |

Comportamento do provisionamento:

- Sem `ADMIN_EMAIL` e `ADMIN_PASSWORD`, a aplicação inicia normalmente e nenhuma conta é criada — não há credenciais padrão em nenhum ambiente.
- Com ambas definidas, a conta é criada como `ADMINISTRADOR` na inicialização e o procedimento é registrado em log apenas com o e-mail — a senha nunca aparece.
- Execução repetida é segura: se o e-mail já existir, nada é criado nem alterado. Um e-mail já cadastrado como `CIDADAO` não é promovido — nesse caso o log avisa e é preciso escolher outro e-mail ou ajustar o banco manualmente.
- Definir apenas uma das duas variáveis interrompe a inicialização com erro explícito, evitando configuração parcial silenciosa.
- O e-mail é validado com as mesmas regras do login (formato válido, até 150 caracteres) antes de qualquer gravação. Configuração inválida interrompe a inicialização sem persistir usuário e sem expor a senha.
- Após a conta ser provisionada, remova `ADMIN_EMAIL`, `ADMIN_PASSWORD` e `ADMIN_NOME` do ambiente: a conta já existe e manter a senha exportada só aumenta a superfície de vazamento.

Para validar o fluxo administrativo: configure as três variáveis no ambiente, suba a aplicação, autentique em `POST /api/v1/auth/login` com as credenciais provisionadas e use o `accessToken` retornado nas rotas protegidas.

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

## Listagem de ocorrências

`GET /api/v1/ocorrencias` exige autenticação. Cidadãos consultam somente suas próprias ocorrências; administradores consultam todas. O serviço confirma a existência da conta e usa seu perfil atual no banco para aplicar a regra. O autor da consulta vem do token, não de um parâmetro `usuarioId`.

| Parâmetro | Uso | Padrão e limites |
| --- | --- | --- |
| `pagina` | Índice da página, começando em zero | 0; inteiro não negativo |
| `tamanho` | Quantidade máxima por página | 20; de 1 a 100 |
| `status` | Filtro opcional | `ENVIADA`, `EM_ANALISE` ou `RESOLVIDA` |
| `prioridade` | Filtro opcional | `BAIXA`, `MEDIA` ou `ALTA` |

Os filtros são combinados por igualdade e respeitam a visibilidade do usuário. A ordenação é fixa: `dataCriacao` decrescente, com desempate por `id` decrescente. Não há parâmetro de ordenação arbitrária nesta versão.

Exemplo: `GET /api/v1/ocorrencias?pagina=0&tamanho=10&status=ENVIADA`.

Resposta `200 OK`:

```json
{
  "conteudo": [],
  "pagina": 0,
  "tamanho": 10,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

Cada item de `conteudo` usa `OcorrenciaResponse`, com os mesmos campos da resposta de criação. Não há entidade `Usuario` nem seus dados pessoais na listagem. A ausência de resultados retorna uma lista vazia; uma página fora do conjunto mantém os totais e devolve conteúdo vazio.

Parâmetros inválidos retornam 400 no formato `ApiError`. Ausência de autenticação, token inválido ou usuário removido retornam 401. A listagem não implementa indicadores agregados ou alteração de status/prioridade.

Os testes `OcorrenciaListagemIntegrationTest` exercitam tokens reais, visibilidade, filtros, limites, paginação e desempate de ordenação com Flyway e Hibernate validate. A suíte padrão continua usando H2; para PostgreSQL real, repetir o fluxo em banco separado com dois cidadãos e um administrador provisionado de forma controlada, conferindo a visibilidade e os totais de cada perfil.

## Consulta de ocorrência por ID

`GET /api/v1/ocorrencias/{id}` exige `Authorization: Bearer <accessToken>` e um identificador UUID. Retorna `200 OK` com `OcorrenciaResponse`, incluindo identificadores, categoria, descrição, localização, status, prioridade e datas. Não retorna a entidade `Usuario` nem nome, email, telefone ou senha do autor.

Cidadãos consultam somente as próprias ocorrências; administradores consultam todas. O serviço usa o perfil atual da conta no banco e aplica a mesma regra de visibilidade da listagem na consulta SQL. Um parâmetro `usuarioId` enviado pelo cliente não troca a identidade da consulta.

Ocorrência inexistente ou fora do acesso do usuário retorna `404 Not Found` com a mesma mensagem `Ocorrência não encontrada.`, sem revelar se existe um registro de outra conta. Identificador inválido retorna 400 no formato `ApiError`. Ausência de autenticação, token inválido ou usuário removido retorna 401.

O endpoint é somente de leitura. Fotografias e alterações de status permanecem em suas respectivas tarefas; a atualização de prioridade possui endpoint próprio, descrito abaixo. Os testes `OcorrenciaConsultaIntegrationTest` cobrem consulta pelo autor e por administrador, tentativa de acesso de outro cidadão, UUID inválido, recurso ausente, conta removida e uso do perfil atual mesmo com token administrativo ainda válido.

## Atualização de prioridade

`PATCH /api/v1/ocorrencias/{id}/prioridade` exige `Authorization: Bearer <accessToken>` e um corpo JSON como `{"prioridade":"ALTA"}`. A operação é restrita ao perfil `ADMINISTRADOR` atual da conta no banco; um cidadão não pode alterar prioridade, mesmo sendo o autor da ocorrência. O UUID do usuário é obtido do token.

Prioridades aceitas: `BAIXA`, `MEDIA` e `ALTA`. Uma ocorrência continua sendo criada sem prioridade. O administrador pode atribuir ou trocar a prioridade em qualquer status, inclusive em uma ocorrência resolvida; essa operação não altera seu andamento. Enviar `null` ou omitir a prioridade retorna 400, sem remover o valor existente. Repetir a mesma prioridade retorna sucesso sem modificar `dataAtualizacao`.

Uma mudança retorna `200 OK` com `OcorrenciaResponse` e atualiza `dataAtualizacao`. Autor, categoria, descrição, localização, status e data de criação são preservados; campos extras enviados no JSON não alteram esses atributos. A prioridade pode ser consultada na listagem e na consulta por ID, respeitando suas regras de visibilidade, e nos filtros de listagem já existentes. Não há dados pessoais do autor na resposta.

A operação é transacional e bloqueia o registro durante a leitura. O método de leitura com bloqueio é compartilhável com a atualização de status, evitando que as duas operações sobrescrevam outros campos ao serem integradas. Atualizações concorrentes de prioridade são serializadas; entre duas prioridades diferentes, prevalece a última alteração efetivamente aplicada.

Respostas de erro no formato `ApiError`: 400 para UUID ou corpo inválido; 401 para autenticação inválida ou conta removida; 403 para cidadão, antes de buscar a ocorrência; 404 para ocorrência inexistente quando consultada por administrador. Valores fora do domínio são recusados. A operação não exige migration, pois a coluna e as constraints de prioridade já fazem parte do schema.

Os testes de domínio cobrem atribuição, todas as combinações de troca e repetição e recusa de prioridade nula. Os testes do serviço conferem autorização antes da busca, leitura bloqueada e recurso ausente. `OcorrenciaPrioridadeIntegrationTest` cobre persistência com Flyway e Hibernate validate, reflexo na consulta/listagem, todos os status, entrada inválida, conta removida, perfil atualizado no banco e preservação dos demais atributos. A suíte padrão usa H2; a validação de PostgreSQL deve ocorrer em banco separado, conferindo também concorrência e persistência após reinício.

Validação da BE-14: 42 casos novos (23 de integração, 13 de domínio e 6 de serviço), com 201 testes aprovados na suíte completa. O fluxo também foi conferido em PostgreSQL 17 isolado, incluindo duas requisições concorrentes, preservação dos campos e reinício da aplicação. Execução local em Java 24, compilando para Java 21; Java 21 e imagem Docker permanecem para validação no CI.
