# Zelar.app — Backend

> "Conectando pessoas, dados e cidades."

Backend da plataforma **Zelar.app**, responsável pelas regras de negócio, autenticação, gerenciamento de ocorrências e disponibilização da API REST.

## O Projeto

O Zelar.app é uma plataforma que conecta cidadãos e responsáveis pela infraestrutura urbana, permitindo o registro, acompanhamento e gerenciamento de ocorrências como:

* Buracos em vias;
* Problemas de iluminação;
* Acúmulo de lixo;
* Alagamentos;
* Problemas em calçadas;
* Sinalização danificada;
* Ocorrências ambientais.

O backend é responsável pelo processamento das informações, controle de usuários, gerenciamento das ocorrências e integração com o banco de dados.

## Stack Tecnológica (MVP)

* **Backend:** Java 17+ com Spring Boot
* **Banco de Dados:** PostgreSQL
* **Segurança:** JWT (JSON Web Token)
* **Ferramentas futuras:** Docker e CI/CD

## Arquitetura e Padrões

* Arquitetura em camadas:

  * Controller
  * Service
  * Repository
  * Entity

* API REST seguindo boas práticas.

* Separação de responsabilidades para facilitar manutenção e escalabilidade.

## Estratégia de Branches

```text
main      → versão estável
stage     → homologação e QA
develop   → integração

feature/* → novas funcionalidades
fix/*     → correções
```

## Qualidade de Software

O projeto busca seguir práticas de qualidade desde o início:

* Controle de versão com Git;
* Desenvolvimento em branches;
* Pull Requests;
* Revisão de código;
* Padronização do código;
* Testes automatizados;
* Documentação contínua.

## Como Executar

As instruções de configuração do ambiente, banco de dados e execução local serão adicionadas conforme o desenvolvimento do projeto.
