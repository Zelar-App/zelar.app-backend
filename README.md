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

O endpoint é somente de leitura. Fotografias permanecem em sua tarefa; as atualizações de prioridade e status possuem endpoints próprios, descritos abaixo. Os testes `OcorrenciaConsultaIntegrationTest` cobrem consulta pelo autor e por administrador, tentativa de acesso de outro cidadão, UUID inválido, recurso ausente, conta removida e uso do perfil atual mesmo com token administrativo ainda válido.

## Atualização de prioridade

`PATCH /api/v1/ocorrencias/{id}/prioridade` exige `Authorization: Bearer <accessToken>` e um corpo JSON como `{"prioridade":"ALTA"}`. A operação é restrita ao perfil `ADMINISTRADOR` atual da conta no banco; um cidadão não pode alterar prioridade, mesmo sendo o autor da ocorrência. O UUID do usuário é obtido do token.

Prioridades aceitas: `BAIXA`, `MEDIA` e `ALTA`. Uma ocorrência continua sendo criada sem prioridade. O administrador pode atribuir ou trocar a prioridade em qualquer status, inclusive em uma ocorrência resolvida; essa operação não altera seu andamento. Enviar `null` ou omitir a prioridade retorna 400, sem remover o valor existente. Repetir a mesma prioridade retorna sucesso sem modificar `dataAtualizacao`.

Uma mudança retorna `200 OK` com `OcorrenciaResponse` e atualiza `dataAtualizacao`. Autor, categoria, descrição, localização, status e data de criação são preservados; campos extras enviados no JSON não alteram esses atributos. A prioridade pode ser consultada na listagem e na consulta por ID, respeitando suas regras de visibilidade, e nos filtros de listagem já existentes. Não há dados pessoais do autor na resposta.

A operação é transacional e bloqueia o registro durante a leitura. O método de leitura com bloqueio é compartilhado com a atualização de status, evitando que as duas operações sobrescrevam outros campos em requisições concorrentes. Atualizações concorrentes de prioridade são serializadas; entre duas prioridades diferentes, prevalece a última alteração efetivamente aplicada.

Respostas de erro no formato `ApiError`: 400 para UUID ou corpo inválido; 401 para autenticação inválida ou conta removida; 403 para cidadão, antes de buscar a ocorrência; 404 para ocorrência inexistente quando consultada por administrador. Valores fora do domínio são recusados. A operação não exige migration, pois a coluna e as constraints de prioridade já fazem parte do schema.

Os testes de domínio cobrem atribuição, todas as combinações de troca e repetição e recusa de prioridade nula. Os testes do serviço conferem autorização antes da busca, leitura bloqueada e recurso ausente. `OcorrenciaPrioridadeIntegrationTest` cobre persistência com Flyway e Hibernate validate, reflexo na consulta/listagem, todos os status, entrada inválida, conta removida, perfil atualizado no banco e preservação dos demais atributos. A suíte padrão usa H2; a validação de PostgreSQL deve ocorrer em banco separado, conferindo também concorrência e persistência após reinício.

Validação da BE-14: 42 casos novos (23 de integração, 13 de domínio e 6 de serviço), com 201 testes aprovados na suíte completa. O fluxo também foi conferido em PostgreSQL 17 isolado, incluindo duas requisições concorrentes, preservação dos campos e reinício da aplicação. Execução local em Java 24, compilando para Java 21; Java 21 e imagem Docker permanecem para validação no CI.

## Atualização de status

O fluxo do MVP é `ENVIADA → EM_ANALISE → RESOLVIDA`, sem salto de etapas, retorno, reabertura ou repetição do mesmo estado. A regra está registrada na DOC-01 do repositório de documentação. Somente administrador pode executar a mudança; o serviço confirma o perfil atual no banco.

`PATCH /api/v1/ocorrencias/{id}/status` exige Bearer e um corpo JSON como `{"status":"EM_ANALISE"}`. Retorna `200 OK` com `OcorrenciaResponse`, atualizando `dataAtualizacao` e preservando os demais dados. Autor, prioridade, descrição e demais campos extras no JSON não alteram os atributos da ocorrência.

Transição fora do fluxo retorna 409. Recurso ausente retorna 404. Cidadão recebe 403 antes da busca da ocorrência. Autenticação inválida ou conta removida retorna 401. Status ausente, desconhecido, JSON malformado ou identificador inválido retorna 400. Os erros seguem `ApiError`.

A atualização é transacional e utiliza bloqueio da ocorrência durante a leitura para impedir que alterações concorrentes validem o mesmo estado antigo. O novo status é refletido nas consultas autorizadas, inclusive na listagem filtrada. A criação de uma conta administrativa segue o procedimento de provisionamento descrito acima; o cadastro público continua criando apenas cidadãos.

Os testes de domínio verificam todas as transições entre os três estados. OcorrenciaStatusIntegrationTest cobre autorização, entrada inválida, persistência e reflexo na listagem. Para validação adicional em PostgreSQL separado, conferir também duas requisições simultâneas com o mesmo destino: apenas uma deve aplicar a transição, e a outra deve receber 409. As credenciais e a conta administrativa usadas como fixtures de validação não devem ser publicadas.

## Indicadores administrativos

`GET /api/v1/ocorrencias/indicadores` exige `Authorization: Bearer <accessToken>` e o perfil `ADMINISTRADOR` atual da conta no banco. Retorna `200 OK` com as contagens globais de ocorrências por status:

```json
{"enviadas":0,"emAnalise":0,"resolvidas":0}
```

Cada campo é um contador inteiro de 64 bits, inclusive quando o valor é zero. `enviadas` corresponde a `ENVIADA`, `emAnalise` a `EM_ANALISE` e `resolvidas` a `RESOLVIDA`. A consulta inclui todos os autores, conforme a visibilidade administrativa, e não retorna ocorrências nem dados pessoais.

Este endpoint não recebe filtros ou paginação. Parâmetros da listagem, como `status`, `prioridade`, `usuarioId`, `pagina` ou `tamanho`, não alteram as contagens globais. Os filtros existentes continuam disponíveis em `GET /api/v1/ocorrencias`. O caminho literal `/indicadores` não é interpretado como UUID pela consulta individual.

O serviço verifica a conta e sua autorização antes de executar uma única consulta agregada no banco, em transação somente de leitura. Não carrega as ocorrências em memória nem faz três consultas separadas. Novas consultas refletem criações e alterações de status já persistidas; não há cache de contagens nem necessidade de migration.

Erros no formato `ApiError`: 401 para autenticação inválida ou conta removida; 403 para cidadão, inclusive se o banco estiver vazio. O perfil atual no banco prevalece sobre o perfil antigo de um JWT ainda válido. O endpoint não altera registros.

`OcorrenciaIndicadoresIntegrationTest` cobre banco vazio, cada status isolado, múltiplos autores e status, criação via API e alterações de status persistidas como fixtures de teste, resposta sem dados pessoais e autorização. O teste de alterações persistidas complementa os testes próprios do fluxo HTTP de status da BE-10. Os testes do serviço verificam autorização antes da consulta e preservação de contadores de 64 bits. A suíte padrão usa H2 com Flyway e Hibernate validate nos testes de integração; a consulta agregada também deve ser conferida em PostgreSQL separado e após reinício.

Validação da BE-15: 15 testes novos (12 de integração e 3 de serviço), com 174 testes aprovados na suíte completa desta branch. PostgreSQL 17 isolado validou banco vazio, criação via API, mudanças de status persistidas como fixtures, múltiplos autores, escopo global, autorização e reinício. Execução local em Java 24, compilando para Java 21; Java 21 e imagem Docker permanecem para validação no CI.

## Fotografias de ocorrências — preparação da BE-13

Esta branch prepara validação, autorização e acesso ao Supabase Storage, sem migration, entidade de anexo ou endpoint de fotografias. A BE-13 permanece em andamento. O contrato abaixo é uma proposta para alinhamento com a equipe responsável pela DB-09; a integração completa depende desse alinhamento e da migration aprovada.

### Componentes preparados

- `ValidadorFotografia`: aceita JPEG/PNG de até 5 MiB (5.242.880 bytes), verifica o formato pelo conteúdo, exige correspondência com o MIME declarado e decodifica a imagem. Limita cada lado a 8192 pixels e a área a 16 milhões de pixels antes da decodificação. Regrava a imagem sem os metadados e os bytes adicionais do arquivo recebido; o resultado também deve respeitar 5 MiB. O nome original não determina o caminho de armazenamento.
- `FotografiaValidada`: transporta conteúdo, MIME, extensão, tamanho e dimensões; copia o vetor de bytes na entrada e na saída.
- `FotografiaAcessoService`: reutiliza `OcorrenciaService.consultar`, preservando a identidade, o perfil atual e a visibilidade já aplicados às ocorrências. Somente o autor pode enviar; autor e administrador autorizado podem consultar. Ocorrência fora do escopo continua retornando o mesmo erro de recurso ausente.
- `FotografiaStorage` e `SupabaseFotografiaStorage`: isolam envio, leitura privada e exclusão técnica para compensação. O adaptador recebe uma chave UUID com extensão correspondente, conhecida antes do envio, e recusa caminhos arbitrários e sobrescrita. A leitura tem limite de bytes, independentemente do tamanho declarado pelo provedor. Falhas de infraestrutura usam uma mensagem controlada, sem corpo da resposta ou credencial.

O adaptador **ainda não é um bean** e nenhuma chamada de armazenamento está ligada aos fluxos HTTP. Isso permite integrar a preparação sem exigir credenciais e sem mudar a criação/listagem de ocorrências. Os testes HTTP usam servidor simulado; não validam um bucket real. As novas exceções só receberão mapeamento em `ApiError` quando os endpoints forem implementados.

### Proposta de API após a DB-09

A criação existente permanece JSON e aceita ocorrência sem fotografia. Quando houver foto, o cliente cria a ocorrência primeiro e depois envia o anexo:

| Operação proposta | Contrato |
| --- | --- |
| `POST /api/v1/ocorrencias/{id}/fotografia` | Bearer do Zelar; somente autor; `multipart/form-data`, campo `foto`; uma fotografia por ocorrência; sem substituição no MVP; sucesso 201 |
| `GET /api/v1/ocorrencias/{id}/fotografia` | Bearer do Zelar; autor ou administrador; conteúdo binário JPEG/PNG; sucesso 200; sem URL pública do bucket |

Erros previstos para a implementação HTTP: 400 para conteúdo inválido; 401 para autenticação inválida/conta removida; 403 quando um administrador tenta anexar em nome de outro autor; 404 para ocorrência fora do escopo, inexistente ou fotografia ausente; 409 para fotografia já vinculada; 413 para excesso de tamanho na camada multipart; 503 para indisponibilidade do armazenamento. O limite multipart da requisição precisa comportar os cabeçalhos além dos 5 MiB do arquivo. As respostas de leitura devem definir MIME conhecido, `X-Content-Type-Options: nosniff` e cache privado sem armazenamento.

Propõe-se acrescentar `possuiFotografia` às respostas de ocorrência somente quando a persistência estiver disponível. O frontend deve buscar os bytes com autenticação e gerenciar o ciclo de vida da URL temporária do navegador; uma tag de imagem com URL direta não envia o Bearer da aplicação. Se o envio da foto falhar depois da criação, o cliente deve guardar o ID criado e repetir apenas o envio da foto, sem cadastrar a ocorrência novamente. O bloqueio atual de envio com foto permanece até esse fluxo estar completo.

### Proposta para a DB-09

Usar a tabela `anexo`, com uma fotografia por ocorrência no MVP:

| Campo proposto | Regra |
| --- | --- |
| `id` | UUID, chave primária gerada pelo backend |
| `ocorrencia_id` | UUID obrigatório, FK e UNIQUE; impede duas fotografias para a mesma ocorrência |
| `bucket` | VARCHAR(100) obrigatório |
| `chave_objeto` | VARCHAR(255) obrigatório; UUID gerado pelo backend + `.jpg` ou `.png`; UNIQUE junto com `bucket` |
| `tipo_conteudo` | VARCHAR(50), somente `image/jpeg` e `image/png` |
| `tamanho_bytes` | BIGINT, maior que zero e até 5.242.880; refere-se ao conteúdo regravado |
| `largura`, `altura` | Inteiros positivos até 8192, produto até 16.000.000 |
| `data_criacao` | TIMESTAMP obrigatório, seguindo o padrão atual |

Não persistir os bytes no PostgreSQL, chave secreta, URL pública/assinada ou caminho fornecido pelo cliente. Recomenda-se FK com exclusão restrita inicialmente, pois exclusão em cascata do metadado não remove o objeto do Storage. O MVP não terá endpoint público de exclusão/substituição da foto.

**Decisão a alinhar antes da implementação:** PostgreSQL e Storage não participam da mesma transação. Recomenda-se reservar o vínculo/chave antes do upload e manter estado durável do envio (`PENDENTE`, `DISPONIVEL` e falha/limpeza pendente), com reconciliação das falhas; somente anexos disponíveis devem aparecer na consulta. A equipe deve confirmar os campos de estado/data de atualização ou estratégia equivalente na DB-09. O método `excluir` prepara a compensação, mas uma tentativa de exclusão isolada não garante ausência de objetos órfãos. Uploads concorrentes e repetição após timeout precisam ser testados na integração, respeitando a unicidade do vínculo e sem substituir objeto existente.

### Supabase Storage privado

Preparar o bucket privado `ocorrencias-fotografias`, com restrição a JPEG/PNG e 5 MiB. A validação do backend continua obrigatória. O JWT do Zelar identifica o usuário somente na API do Zelar; o backend consulta o Storage com uma **secret key do servidor** (`sb_secret_...`), enviada no cabeçalho `apikey`. O adaptador desta etapa suporta esse tipo de chave, não a chave pública nem o JWT do Zelar. Nunca configurar a secret key no frontend, em variável `VITE_*`, em logs ou no repositório.

Variáveis propostas, **ainda não consumidas pela aplicação**:

```dotenv
SUPABASE_STORAGE_URL=https://seu-projeto.supabase.co
SUPABASE_STORAGE_BUCKET=ocorrencias-fotografias
SUPABASE_STORAGE_SECRET_KEY=
```

Na integração, criar configuração própria e um `RestClient.Builder` dedicado, com limites explícitos de conexão/leitura e sem redirecionar credenciais para outro host. Validar configuração sem exibir a chave. Não habilitar tentativas automáticas de upload antes de definir o tratamento de operação com resultado incerto. Não alterar `SUPABASE_PUBLISHABLE_KEY` ou `JWT_SECRET` para usar a chave de Storage.

Referências do fornecedor: [chaves de API](https://supabase.com/docs/guides/getting-started/api-keys), [buckets e restrições](https://supabase.com/docs/guides/storage/buckets/fundamentals), [download privado](https://supabase.com/docs/guides/storage/serving/downloads) e [operações HTTP no SDK oficial](https://github.com/supabase/storage-js/blob/main/src/packages/StorageFileApi.ts).

### Validação e próximos passos

Os testes de validação cobrem arquivos vazios, MIME incompatível, conteúdo falso/truncado, excesso real de bytes, dimensões excessivas e cópia defensiva. Os testes de autorização verificam delegação à visibilidade existente, envio pelo autor, consulta administrativa e propagação dos erros de autenticação/recurso ausente. Os testes do adaptador verificam rotas, cabeçalhos, conteúdo, limites e falhas com HTTP simulado.

Após a DB-09: integrar entidade/repositório, configuração e limites HTTP, orquestração consistente do envio, endpoints e tratamento de erros; testar concorrência, falha de persistência/Storage, compensação e consulta por diferentes perfis; validar um bucket privado real separado; por fim conectar o frontend e retirar o bloqueio atual de fotografia. Nenhuma migration deve ser duplicada nesta branch e a BE-13 só pode ser concluída após esse fluxo completo.

Validação desta preparação: 318 testes aprovados, sem falhas ou erros, incluindo 64 novos (20 de validação, 6 de acesso e 38 de armazenamento simulado). Execução local em Java 24, compilando para Java 21; a suíte usa H2 nos testes de integração existentes. PostgreSQL e bucket privado reais, configuração de rede/credenciais e CI com Java 21 permanecem para a integração. As etapas RED e GREEN estão registradas em commits separados, com um commit próprio para cada classe nova.
