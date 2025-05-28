# Sistema de API de Empréstimo Pessoal (Personal Loan System)

## Visão Geral

Este projeto implementa uma API de Empréstimo Pessoal para uma instituição financeira digital. O sistema permite que clientes solicitem empréstimos, avalia o risco de crédito em tempo real, aprova ou rejeita solicitações e publica eventos para outros serviços da plataforma. A arquitetura é baseada em microsserviços, focando em escalabilidade, resiliência e observabilidade.

**Tecnologias e Conceitos Explorados:**
*   Java 21 (com Virtual Threads, Switch Pattern Matching)
*   Spring Boot 3.2.x (utilizando Spring Web/MVC)
*   Spring AOP
*   Spring Actuator (para monitoramento)
*   Spring Security (para segurança da API)
*   Spring Data MongoDB (persistência)
*   Spring Data Redis (caching)
*   Apache Kafka (mensageria assíncrona)
*   WireMock (simulação de APIs externas)
*   Micrometer (métricas e tracing distribuído)
*   Prometheus (coleta de métricas)
*   Grafana (visualização de métricas e logs)
*   Zipkin (visualização de traces distribuídos)
*   Docker & Docker Compose (infraestrutura local)
*   Design Patterns: Strategy, Chain of Responsibility

## Arquitetura de Microsserviços

O sistema é composto pelos seguintes microsserviços:

1.  **`loan-application-service`**: Ponto de entrada para solicitações de empréstimo.
2.  **`credit-assessment-service`**: Realiza a análise de risco de crédito.
3.  **`loan-decision-engine`**: Toma a decisão final sobre o empréstimo.
4.  **`anti-fraud-service`**: Consome eventos para análise de fraude (inicialmente simulado via WireMock para o fluxo principal).
5.  **`contract-notification-service`**: Consome eventos para geração de contratos e notificações.

### Fluxo Geral da Solicitação de Empréstimo

1.  Cliente envia uma solicitação de empréstimo via API REST para o `loan-application-service`.
2.  `loan-application-service`:
    *   Valida os dados da solicitação.
    *   Persiste a solicitação no MongoDB com status `PENDING_ASSESSMENT`.
    *   Publica um evento `LoanApplicationReceivedEvent` no tópico Kafka `LoanApplicationReceivedEventTopic`.
3.  `credit-assessment-service`:
    *   Consome o `LoanApplicationReceivedEvent`.
    *   Consulta dados internos do cliente (pode usar cache Redis).
    *   Consulta bureaus de crédito (simulado via WireMock em `http://wiremock:8080/bureau/score/{cpf}`).
    *   Consulta score antifraude (simulado via WireMock em `http://wiremock:8080/antifraud/check/{cpf}`).
    *   Aplica regras de crédito e calcula um parecer de risco (utilizando Strategy e Chain of Responsibility).
    *   Publica um evento `CreditAssessmentCompletedEvent` no tópico Kafka `CreditAssessmentCompletedEventTopic` com o parecer.
4.  `loan-decision-engine`:
    *   Consome o `CreditAssessmentCompletedEvent`.
    *   Aplica regras finais de decisão para aprovar, rejeitar ou enviar para revisão manual.
    *   Chama uma API no `loan-application-service` para atualizar o status da solicitação no MongoDB.
    *   Publica um evento `LoanDecisionMadeEvent` no tópico Kafka `LoanDecisionMadeEventTopic` com a decisão final e os termos (se aprovado).
5.  `contract-notification-service`:
    *   Consome o `LoanDecisionMadeEvent`.
    *   Se aprovado, simula a geração de contrato e envia uma notificação ao cliente (log).
    *   Se rejeitado, envia uma notificação de rejeição ao cliente (log).
6.  `anti-fraud-service` (atuação assíncrona/secundária):
    *   Consome o `LoanApplicationReceivedEvent` (ou `LoanDecisionMadeEvent`).
    *   Simula uma análise de fraude mais profunda e registra o resultado (log).

---

## Regras de Negócio por Microsserviço

### 1. `loan-application-service`

*   **Validação da Solicitação (Entrada):**
    *   **R1.0.1:** Todos os campos obrigatórios da solicitação devem estar presentes (CPF, valor solicitado, número de parcelas, renda declarada, etc.).
    *   **R1.0.2:** Formato do CPF deve ser válido.
    *   **R1.0.3:** Valor do empréstimo solicitado deve estar dentro de um range mínimo e máximo permitido pela instituição (ex: R$ 500 - R$ 100.000).
    *   **R1.0.4:** Número de parcelas deve estar dentro de um range permitido (ex: 3 - 48 parcelas).
*   **Processamento da Solicitação:**
    *   **R1.1.1:** Ao receber uma solicitação válida, o serviço deve persistir os dados no MongoDB com o status inicial `PENDING_ASSESSMENT`.
    *   **R1.1.2:** Um identificador único de solicitação (`applicationId`) deve ser gerado.
    *   **R1.1.3:** Publicar um evento `LoanApplicationReceivedEvent` contendo o `applicationId` e os dados relevantes da solicitação para o tópico Kafka `LoanApplicationReceivedEventTopic`.
*   **Atualização de Status (via API interna):**
    *   **R1.2.1:** Deve expor um endpoint seguro para que o `loan-decision-engine` possa atualizar o status final da solicitação (`APPROVED`, `REJECTED`, `PENDING_MANUAL_REVIEW`) e os termos do empréstimo, se aprovado.

### 2. `credit-assessment-service`

*   **Consumo de Evento:**
    *   **R2.0.1:** Deve consumir mensagens do tópico `LoanApplicationReceivedEventTopic`.
*   **Elegibilidade Básica (Primeiros elos da Chain of Responsibility):**
    *   **R2.1.1 (Idade):** Solicitante deve ter entre 18 e 75 anos (informação pode vir no evento ou ser buscada). Se não, gerar parecer `INELIGIBLE_AGE`.
    *   **R2.1.2 (CPF Regular):** Simular consulta à Receita Federal (via WireMock). Se irregular, parecer `INELIGIBLE_CPF_STATUS`.
    *   **R2.1.3 (Conta Ativa):** Simular verificação interna. Se não ativa, parecer `INELIGIBLE_NO_ACTIVE_ACCOUNT`.
    *   **R2.1.4 (Renda Mínima):** Renda mensal declarada deve ser >= R$ 1.200,00. Se menor, parecer `INELIGIBLE_LOW_INCOME`.
    *   **R2.1.5 (Restrições Internas Graves):** Simular verificação interna. Se houver, parecer `INELIGIBLE_INTERNAL_RESTRICTION`.
*   **Análise de Risco de Crédito (Elos subsequentes da Chain of Responsibility e Strategies):**
    *   **R2.2.1 (Consulta Bureau de Crédito - Simulado via WireMock):**
        *   Chamar `GET http://wiremock:8080/bureau/score/{cpf}`.
        *   Resultado esperado: JSON com `{"documentNumber": "cpf", "creditScore": NNN, "positiveHistory": true/false}`.
        *   **Cache Redis:** Cachear o resultado por CPF por 24 horas para evitar chamadas repetidas.
        *   Se score < 300, parecer `HIGH_RISK_SCORE`.
    *   **R2.2.2 (Consulta Antifraude - Simulado via WireMock):**
        *   Chamar `POST http://wiremock:8080/antifraud/check/{cpf}` (corpo pode ser vazio ou com dados da solicitação).
        *   Resultado esperado: JSON com `{"customerId": "cpf", "fraudScore": NNN, "recommendation": "APPROVE"|"REVIEW"|"REJECT"}`.
        *   **Cache Redis:** Cachear o resultado por CPF por 1 hora.
        *   Se `recommendation` for `REJECT` ou `fraudScore` muito alto (>700), parecer `HIGH_RISK_FRAUD`.
    *   **R2.2.3 (Comprometimento de Renda - Strategy pode ser usada para diferentes formas de cálculo):**
        *   Calcular: `(Valor da Parcela Estimada + Outras Dívidas conhecidas) / Renda Mensal Declarada`.
        *   (Valor da Parcela Estimada pode ser uma simulação simples ou uma chamada a uma calculadora interna).
        *   Se comprometimento > 40%, parecer `HIGH_DEBT_TO_INCOME_RATIO`.
        *   Se comprometimento entre 30%-40%, parecer `MEDIUM_DEBT_TO_INCOME_RATIO`.
    *   **R2.2.4 (Relacionamento com Instituição - Strategy):**
        *   Analisar tempo de conta, movimentação (simulado). Pode gerar um score de relacionamento interno.
*   **Parecer Final da Avaliação:**
    *   **R2.3.1:** Consolidar todos os pareceres parciais.
    *   **R2.3.2:** Se qualquer parecer for de "alta criticidade" (ex: `HIGH_RISK_SCORE`, `HIGH_RISK_FRAUD`), o parecer final da avaliação pode ser `DECLINED_ASSESSMENT`.
    *   **R2.3.3:** Se elegível e sem riscos críticos, calcular um limite de crédito e uma faixa de taxa de juros (usando **Strategy Pattern** com base no score de crédito, score de relacionamento, etc.).
        *   Ex: `LowRiskInterestRateStrategy`, `StandardInterestRateStrategy`.
    *   **R2.3.4:** Publicar um evento `CreditAssessmentCompletedEvent` com o `applicationId`, o parecer consolidado (`ELIGIBLE_FOR_DECISION`, `DECLINED_ASSESSMENT`, `REVIEW_MANUALLY_ASSESSMENT`), e, se elegível, o limite de crédito e a faixa de taxa propostos, para o tópico Kafka `CreditAssessmentCompletedEventTopic`.

### 3. `loan-decision-engine`

*   **Consumo de Evento:**
    *   **R3.0.1:** Deve consumir mensagens do tópico `CreditAssessmentCompletedEventTopic`.
*   **Lógica de Decisão Final:**
    *   **R3.1.1:** Se o parecer do `credit-assessment-service` for `DECLINED_ASSESSMENT`, a decisão final é `REJECTED`.
    *   **R3.1.2:** Se o parecer for `REVIEW_MANUALLY_ASSESSMENT`, a decisão final é `PENDING_MANUAL_REVIEW`.
    *   **R3.1.3:** Se o parecer for `ELIGIBLE_FOR_DECISION`:
        *   Verificar se o valor solicitado pelo cliente é <= ao limite de crédito proposto.
        *   Se sim, a decisão final é `APPROVED`. A taxa de juros final é selecionada da faixa proposta (pode ser a menor da faixa para simplificar).
        *   Se não, a decisão final pode ser `REJECTED` ou oferecer um `COUNTER_OFFER` (não implementaremos contraproposta neste escopo, então será `REJECTED` ou `PENDING_MANUAL_REVIEW`).
    *   **R3.1.4 (Política de "Não Concessão Múltipla" - Verificação Final):** Antes de aprovar, verificar no `loan-application-service` (via API) se o cliente já possui um empréstimo pessoal ativo. Se sim, e a política não permitir múltiplos, mudar decisão para `REJECTED`.
*   **Atualização e Publicação:**
    *   **R3.2.1:** Chamar a API interna do `loan-application-service` (`PUT /api/v1/internal/loans/{applicationId}/status`) para atualizar o status final da solicitação e os termos (valor aprovado, taxa, número de parcelas) no MongoDB.
    *   **R3.2.2:** Publicar um evento `LoanDecisionMadeEvent` contendo o `applicationId`, a decisão final (`APPROVED`, `REJECTED`, `PENDING_MANUAL_REVIEW`), e os termos (se `APPROVED`), para o tópico Kafka `LoanDecisionMadeEventTopic`.

### 4. `anti-fraud-service`

*   **Consumo de Evento:**
    *   **R4.0.1:** Deve consumir mensagens do tópico `LoanApplicationReceivedEventTopic`.
*   **Análise de Fraude (Simulada):**
    *   **R4.1.1:** Para cada evento, simular uma análise de fraude mais profunda (ex: verificar padrões, cruzar com listas de restrição internas).
    *   **R4.1.2:** Registrar o resultado da análise (log). Em um sistema real, poderia atualizar um sistema de perfil de risco do cliente ou gerar alertas para uma equipe de fraude.
    *   **Nota:** Conforme o fluxo principal, o `credit-assessment-service` obtém um score de fraude via WireMock para a decisão em tempo real. Este `anti-fraud-service` representa uma análise assíncrona, possivelmente mais detalhada, que não bloqueia o fluxo de aprovação do empréstimo.

### 5. `contract-notification-service`

*   **Consumo de Evento:**
    *   **R5.0.1:** Deve consumir mensagens do tópico `LoanDecisionMadeEventTopic`.
*   **Processamento da Decisão:**
    *   **R5.1.1:** Se o evento indicar `APPROVED`:
        *   Simular a geração de um documento de contrato com os termos do empréstimo.
        *   Simular o envio de uma notificação (e-mail/SMS) para o cliente informando a aprovação e com link para o contrato (registrar no log).
    *   **R5.1.2:** Se o evento indicar `REJECTED`:
        *   Simular o envio de uma notificação (e-mail/SMS) para o cliente informando a rejeição e o motivo principal (de forma genérica e amigável, registrar no log).
    *   **R5.1.3:** Se o evento indicar `PENDING_MANUAL_REVIEW`:
        *   Simular o envio de uma notificação para o cliente informando que a solicitação está em análise e que entraremos em contato (registrar no log).

---

## Setup e Execução Local (Docker)

1.  **Pré-requisitos:**
    *   Java 21 (JDK)
    *   Maven 3.8+
    *   Docker
    *   Docker Compose

2.  **Estrutura de Diretórios:**
    Certifique-se de que sua workspace tenha a estrutura de diretórios conforme descrito no início desta Fase 0 (cada microsserviço em seu próprio diretório, com `docker-compose.yml` e `prometheus-config/` na raiz).

3.  **Construir os Microsserviços:**
    Para cada microsserviço, navegue até seu diretório e compile:
    ```bash
    # Exemplo para loan-application-service
    cd loan-application-service
    mvn clean package -DskipTests
    cd ..
    # Repita para os outros serviços
    ```

4.  **Configurar WireMock Stubs:**
    *   Crie o diretório `workspace/wiremock/mappings/`.
    *   Adicione os arquivos `anti-fraud-stubs.json` e `credit-bureau-stubs.json` neste diretório com as definições dos endpoints simulados.

5.  **Configurar Prometheus:**
    *   Crie o diretório `workspace/prometheus-config/`.
    *   Adicione o arquivo `prometheus.yml` neste diretório, configurado para fazer scrape dos endpoints `/actuator/prometheus` de todos os seus microsserviços (usando os nomes dos contêineres definidos no `docker-compose.yml`).

6.  **Iniciar a Infraestrutura e os Serviços com Docker Compose:**
    Na raiz da sua workspace (onde está o `docker-compose.yml`):
    ```bash
    docker-compose up --build -d
    ```
    O comando `--build` garantirá que as imagens Docker para seus microsserviços sejam construídas (ou reconstruídas se houver alterações). O `-d` executa em modo detached.

7.  **Verificar os Serviços:**
    *   **Logs:** Verifique os logs de cada contêiner para garantir que iniciaram sem erros:
        ```bash
        docker-compose logs -f loan-application-app
        docker-compose logs -f kafka
        docker-compose logs -f wiremock
        # etc.
        ```
    *   **Kafka Topics:** Se você usou `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"` ou `KAFKA_CREATE_TOPICS`, os tópicos devem ter sido criados. Você pode usar uma ferramenta como `kafkacat` (ou `kcat`) para listar os tópicos ou consumir mensagens se quiser verificar.
    *   **WireMock Admin:** Acesse `http://localhost:8089/__admin/mappings` no seu navegador para ver se os stubs foram carregados corretamente.
    *   **Prometheus:** Acesse `http://localhost:9090`. Vá em "Status" -> "Targets" para verificar se todos os seus microsserviços estão com status "UP".
    *   **Grafana:** Acesse `http://localhost:3000`. Login: `admin`/`admin`.
        *   Configure o Prometheus como Data Source:
            *   Nome: Prometheus (ou como preferir)
            *   HTTP URL: `http://prometheus:9090`
            *   Clique em "Save & Test".
    *   **Zipkin:** Acesse `http://localhost:9411` para visualizar traces distribuídos.
    *   **Endpoints da Aplicação:** Teste os endpoints do Actuator de cada serviço (ex: `http://localhost:8081/actuator/health` para `loan-application-app`).

8.  **Para Parar Tudo:**
    ```bash
    docker-compose down
    ```
    Para remover os volumes (e perder os dados persistidos):
    ```bash
    docker-compose down -v
    ```

---

Este README.md fornece uma base sólida. Você pode adicionar seções sobre como testar a API, exemplos de requests, detalhes de configuração, etc., conforme o projeto evolui.