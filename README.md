# JamOrder

Aplicação interna para registrar requisições de compra. O projeto usa Spring Boot, JDBC, PostgreSQL e migrations Flyway.

## Requisitos e execução local

- Java 21
- Docker (para o PostgreSQL local e para os testes)

```bash
docker compose up -d      # PostgreSQL 17 com as variáveis do .env
./mvnw spring-boot:run    # aplica as migrations e sobe em http://localhost:8081
```

## Testes

```bash
./mvnw test
```

Os testes sobem um PostgreSQL 17 descartável com Testcontainers, aplicam as migrations nele e o removem ao final. Eles não usam o banco local do `.env`: o perfil `test` aponta o datasource para um endereço inválido, então todo `@SpringBootTest` precisa de `@Import(TestcontainersConfiguration.class)`. Use apenas dados fictícios nos testes.

## Integração contínua

O workflow `.github/workflows/ci.yml` roda em todo push e pull request:

- `./mvnw test`, com o Docker do runner;
- gitleaks em todo o histórico do Git, para barrar segredos commitados.

## Regras de status

Uma requisição só pode ser cancelada enquanto é rascunho (`DRAFT`) ou aguarda entrega (`OPEN`). Ao cancelar, os itens ainda pendentes passam a `CANCELLED`. Requisições parcialmente recebidas ou concluídas não podem ser canceladas.

## Backup manual do banco local

Gere o dump fora do repositório (os dados reais nunca entram no Git):

```bash
docker exec jamorder-postgres sh -c 'pg_dump -Fc -U "$POSTGRES_USER" "$POSTGRES_DB"' > /caminho/fora/do/repo/jamorder-AAAAMMDD-HHMM.dump
```

Para conferir um dump, restaure-o num container PostgreSQL novo e descartável, nunca no `jamorder-postgres`.

## Primeiro ADMIN de desenvolvimento

O sistema não usa a senha temporária do Spring Boot. Na primeira inicialização, quando ainda não houver usuários na tabela `app_users`, configure estas variáveis no arquivo local `.env` antes de iniciar a aplicação:

```properties
APP_INITIAL_ADMIN_USERNAME=admin
APP_INITIAL_ADMIN_PASSWORD=uma-senha-local-segura
APP_INITIAL_ADMIN_DISPLAY_NAME=Administrador local
```

O `.env` já é ignorado pelo Git e agora é carregado explicitamente na inicialização. O usuário é criado uma única vez com a senha protegida por BCrypt. Depois disso, as variáveis não são usadas para alterar a conta existente.

## Login e logout

1. Abra `http://localhost:8081/login.html` (ou a porta definida em `SERVER_PORT`).
2. Entre com o usuário inicial configurado.
3. A página principal mostra o usuário e a role autenticados.
4. Use **Sair** para encerrar a sessão.

O frontend obtém um token CSRF em `/api/csrf` e o envia no login, logout e criação de requisição. Requisições que mudam estado sem esse token são rejeitadas.
