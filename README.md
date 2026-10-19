# cidade-conectada

## Domínio de ocorrências — BE-06

Inclui entidade, localização, categorias, status, prioridade, DTOs e repository. A criação inicia com status `ENVIADA`, sem prioridade atribuída.

O mapeamento depende das migrations `V2__criar_tabela_ocorrencia.sql` e `V3__criar_indices_ocorrencia.sql` do [PR #64](https://github.com/Zelar-App/zelar.app-backend/pull/64). Esse PR deve ser integrado antes desta entrega para que o Hibernate valide o schema na inicialização. As migrations não são duplicadas nesta branch.
