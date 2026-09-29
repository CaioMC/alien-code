# Alien Code

Assistente de codificação local: sobe um ambiente efêmero (a **Toca**, um container Docker) com o
projeto existente ou um projeto novo, executa o agente **opencode** com modelos abertos do **Ollama**
e mostra cada passo da execução ao vivo — no estilo do Manus e do Claude Code Web.

É a V2 do estudo iniciado em `poc-websocket-demo` (assistente) e `coding-agent` (agente codificador).

## Estado atual: marco M0 (esqueleto)

| Marco | Situação |
|---|---|
| **M0 · Esqueleto** — imagem da Toca com opencode; o Alien Server cria, semeia e destrói Tocas | ✅ |
| M1 · Timeline ao vivo — eventos do opencode → WebSocket → UI | próximo |
| M2 · Entrega segura · M3 · Grafo · M4 · Multi-repo + DAG · M5 · AI-DLC completo | planejado |

O que já funciona:

- **Imagem `alien/toca`** ([`toca/`](toca/)): JDK 21, Maven, Node, git e **opencode 1.18.33** em modo
  servidor, usuário sem privilégios (`alien`, uid 1000), senha obrigatória.
- **Alien Server** ([`alien-server/`](alien-server/)), Java 21 + Spring Boot, em Clean Architecture:
  - cria a Toca com limites de CPU, memória e processos, sem capabilities, com `no-new-privileges`
    e a porta do opencode publicada só em `127.0.0.1`;
  - semeia `/workspace` com cópias (`git clone --local`, sem remote) de um ou mais repositórios
    locais, ou com um projeto novo (`git init`). O repositório original nunca é montado;
  - espera o opencode responder (`GET /global/health`) antes de declarar a Toca pronta;
  - descarta a Toca ao pedido, ao vencer o TTL (60 min) ou, se sobrar container órfão, quando o
    servidor sobe de novo.

## Como rodar

Pré-requisitos: Docker, Java 21, Maven e git.

```bash
# 1. imagem da Toca (uma vez)
docker build -t alien/toca:0.1 toca/

# 2. servidor (escuta só em 127.0.0.1:8080)
cd alien-server && mvn spring-boot:run
```

Criar uma Toca com um repositório local (precisa estar dentro de `alien.workspace.allowed-roots`,
por padrão a sua home):

```bash
curl -s -X POST localhost:8080/api/tocas -H 'Content-Type: application/json' -d '{
  "missionId": "m-1",
  "seed": {"type": "existing", "repositories": [{"path": "'$HOME'/projetos/minha-api", "ref": "main"}]}
}'
# → {"id":"toca-3f9a1c2e","status":"READY","agentUrl":"http://127.0.0.1:32771","workspace":["/workspace/minha-api"],...}
```

Projeto novo: `{"seed": {"type": "new", "name": "meu-projeto"}}`.

| Rota | O que faz |
|---|---|
| `POST /api/tocas` | Cria, semeia e espera a Toca ficar pronta (201) |
| `GET /api/tocas` · `GET /api/tocas/{id}` | Lista / detalha |
| `DELETE /api/tocas/{id}` | Descarta (remove o container) |

A senha do opencode de cada Toca nunca aparece na API.

## Organização do código

```
alien-server/src/main/java/dev/aliencode/
├── core/toca/                        regras de negócio, sem framework de infraestrutura
│   ├── domain/model/                 Toca, TocaId, TocaStatus, TocaEndpoint, Seed, RepositorySeed
│   ├── domain/exception/             TocaNotFoundException, TocaProvisioningException
│   ├── port/sandbox/                 SandboxPort + SandboxRequest, SandboxHandle, ExecResult, ManagedSandbox
│   ├── port/workspace/               WorkspaceSnapshotPort
│   ├── port/harness/                 AgentHarnessPort, HarnessNotReadyException
│   ├── port/repository/              TocaRepository
│   ├── usecase/ (+ command/)         interfaces dos casos de uso e ProvisionTocaCommand
│   └── application/                  implementação dos casos de uso, TocaSettings
└── adapters/toca/                    tecnologia: tudo que o core não conhece
    ├── web/controller/               TocaController (só traduz e delega)
    ├── web/request/                  ProvisionTocaRequest, SeedRequest, RepositoryRequest (só dados)
    ├── web/response/                 TocaResponse (só dados; sem senha)
    ├── web/mapper/                   TocaWebMapper: DTO ⇄ domínio
    ├── web/handler/                  ApiExceptionHandler (ProblemDetail)
    ├── docker/ · git/ · opencode/    implementações das portas
    ├── persistence/ · scheduling/    repositório em memória, faxina agendada
    └── config/                       properties → TocaSettings, DockerClient, Clock
```

Cada fluxo (criar, semear, falhar, descartar, faxina, órfãos...) está desenhado classe a classe em
[`docs/sequencias.md`](docs/sequencias.md).

## Testes

```bash
cd alien-server
mvn test     # unitários (domínio, casos de uso, adaptadores com git e HTTP reais)
mvn verify   # + integração: sobe Tocas reais no Docker (precisa da imagem alien/toca:0.1)
```

## Especificação

A especificação completa, com diagramas, está em
[`docs/alien-code-especificacao.pdf`](docs/alien-code-especificacao.pdf). O PDF é gerado por código:

```bash
python -m venv .venv && .venv/bin/pip install reportlab
.venv/bin/python docs/pdf/gerar_especificacao.py
```

[`docs/referencias/opencode-1.18.33-openapi.json`](docs/referencias/opencode-1.18.33-openapi.json)
é o contrato real da API do opencode fixado na imagem, extraído de `GET /doc`. Ele é a fonte para o
adaptador de eventos do M1: os nomes de evento do opencode 1.18 (`session.next.tool.called`,
`permission.v2.asked`, `session.idle`…) diferem dos citados na seção 8.3 da especificação, que será
atualizada junto com o M1.

## Diferenças conscientes em relação à especificação (M0)

- **Rede da Toca:** a rede `alien-net` ainda é uma bridge comum. O bloqueio de saída (só Ollama e
  proxy de pacotes) chega no M2, junto com o proxy — uma rede Docker `internal` não permite publicar
  a porta do opencode para o servidor no host.
- **Limite de processos:** 512 em vez de 256; opencode + Node + Maven passam de 256 com folga.
- **Persistência:** Tocas em memória; o Event Store em SQLite chega com as missões (M1).
- **Alterações não commitadas** do repositório original ainda não são levadas para a Toca.

## Referências

- [awslabs/aidlc-workflows](https://github.com/awslabs/aidlc-workflows): metodologia e motor de
  workflow (perfis, portões de aprovação, sensores, auditoria), com suporte nativo ao opencode.
- [Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify): grafo de dependências local
  (tree-sitter + MCP), usado para ordenar tarefas dependentes entre repositórios.
