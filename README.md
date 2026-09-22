# Cidade Conectada 
> "Conectando pessoas, dados e cidades."

Plataforma digital acadêmica para registro, gestão e análise de problemas urbanos, desenvolvida com foco em **Qualidade de Software**.

## O Projeto
O Cidade Conectada atua como uma ponte entre a população e a administração urbana. Os cidadãos podem registrar problemas de infraestrutura (buracos, iluminação, lixo, etc.) via geolocalização, enquanto a administração pública gerencia o ciclo de vida dessas ocorrências através de um painel inteligente.

## Stack Tecnológica (MVP)
* **Backend:** Java 17+ com Spring Boot (REST API)
* **Banco de Dados:** PostgreSQL
* **Segurança:** Autenticação e Autorização via JWT (JSON Web Token)
* **Frontend:** React / Angular / Vue *(A definir)*
* **Infraestrutura futura:** Docker e CI/CD

## Arquitetura e Padrões
* **Arquitetura:** MVC com separação em camadas (Controller, Service, Repository, Entity).
* **Segurança Aplicada:** Senhas com hash criptográfico (BCrypt), sanitização de inputs (Anti-Injection) e validação estrita de rotas.
* **Git Flow Acadêmico:**
  * `main`: Produção (Estável)
  * `stage`: Homologação e QA (Garantia de Qualidade)
  * `develop`: Integração Contínua
  * `feature/*`: Desenvolvimento de tarefas isoladas

## Como Executar (Em breve)
As instruções para rodar a API localmente e configurar as variáveis de ambiente do PostgreSQL serão adicionadas aqui futuramente.
