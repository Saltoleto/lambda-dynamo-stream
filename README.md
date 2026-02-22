# DynamoDB Streams → REST notifier (AWS Lambda) | Java 21 + Spring Boot

Este projeto cria uma AWS Lambda **Java 21** que recebe um evento do **DynamoDB Streams**
e faz **uma chamada REST** para um serviço externo.

## Como funciona

- O handler é: `com.saltinvest.streamlambda.handler.DynamoStreamHandler`
- Ele inicializa um contexto Spring Boot **somente uma vez** (static) e injeta os serviços.
- Converte os registros do DynamoDB Streams em um payload compacto (`DynamoChangeBatch`)
- Faz **1 chamada REST por batch** (mais eficiente do que 1 por item)

## Variáveis de ambiente

Obrigatória:
- `TARGET_URL` → URL do serviço REST

Opcional:
- `TARGET_API_KEY` → enviado como header `X-API-Key`

## Build (uber-jar compatível com Lambda)

> O projeto usa **maven-shade-plugin** para gerar um JAR “flat” (sem dependências em BOOT-INF),
o que é o formato mais simples para o runtime Java do Lambda.

```bash
mvn -DskipTests package
```

Saída:
- `target/dynamodb-stream-lambda-1.0.0-shaded.jar`

## Deploy (exemplo com AWS SAM)

Arquivo: `template.yaml`

```bash
sam build
sam deploy --guided
```

## Otimização de cold start

1) **SnapStart**: o `template.yaml` já vem com SnapStart habilitado (PublishedVersions).
2) **Spring sem servidor web**: `spring.main.web-application-type=none`
3) **Lazy init**: `spring.main.lazy-initialization=true`
4) **Menos auto-config**: não usamos starters pesados como web/tomcat, data, etc.
5) **Timeouts de rede**: evita invocações “presas” em chamadas externas.

### Dica: JAVA_TOOL_OPTIONS (opcional)
No `template.yaml`, existe um exemplo de `JAVA_TOOL_OPTIONS` para priorizar start rápido.
Ajuste conforme benchmark (isso pode reduzir throughput em invocações longas).

## Teste local (payload exemplo)

Payload exemplo em `events/dynamodb-stream-sample.json`.

Você pode testar com o SAM (se quiser) ou escrever um pequeno runner.
Para um teste simples de unidade, veja `src/test`.

---

## Build sem Maven (via Docker)

```bash
./scripts/build-with-docker.sh
```

## Autenticação (Secret Manager + client_credentials)

Para chamar o serviço REST, a Lambda obtém um token via **client_credentials**:

1) Busca `client_id` e `client_secret` no **AWS Secrets Manager** (secret JSON com as chaves `client_id` e `client_secret`).
2) Faz POST no endpoint de token (referenciado como **STS**) usando `grant_type=client_credentials`.
3) Usa `Authorization: Bearer <token>` na chamada ao `TARGET_URL`.

### Variáveis de ambiente

Obrigatórias:
- `CLIENT_CREDENTIALS_SECRET_NAME`
- `STS_TOKEN_URL`
- `TARGET_URL`

Opcional:
- `STS_SCOPE`
- `TARGET_API_KEY`

### Observação sobre SnapStart

A busca do Secret e a obtenção do token são **lazy** (na primeira invocação), para evitar que segredos/tokens fiquem dentro do snapshot por padrão.
