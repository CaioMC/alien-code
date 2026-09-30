# Alien Code

Assistente de codificação local: sobe um ambiente efêmero (a **Toca**, um container Docker) com o
projeto existente ou um projeto novo, executa o agente **opencode** com modelos abertos do **Ollama**
e mostra cada passo da execução ao vivo — no estilo do Manus e do Claude Code Web.

É a V2 do estudo iniciado em `poc-websocket-demo` (assistente) e `coding-agent` (agente codificador).

## Estado atual: marco M1 (timeline ao vivo)

| Marco | Situação |
|---|---|
| **M0 · Esqueleto** — imagem da Toca com opencode; o Alien Server cria, semeia e destrói Tocas | ✅ |
| **M1 · Timeline ao vivo** — missão com 1 repo e 1 tarefa; eventos do opencode → WebSocket → UI | ✅ |
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
- **Missões (M1)**: `POST /api/missions` abre uma missão com um pedido e um repositório (ou projeto
  novo). O servidor provisiona a Toca, abre uma sessão do opencode com a tarefa e transforma o
  fluxo SSE do opencode em eventos da timeline (raciocínio, texto, tool calls, terminal, diffs,
  tokens), gravados em ordem num Event Store SQLite e entregues por WebSocket. Reconectar com
  `lastSeq` reenvia só o que faltou; **Parar** aborta a sessão; ao fim, a Toca é descartada.
- **Alien Web** ([`alien-web/`](alien-web/)), React + TypeScript + Vite: abre missões, mostra a
  timeline ao vivo (passos, raciocínio recolhível, tool calls como sub-linhas com duração e saída),
  as abas Terminal e Diff, tokens gastos e o botão **Parar** (ou `Esc`). Se a conexão cair, ele
  reconecta sozinho com o último `seq` recebido.

## Como rodar

Pré-requisitos: Docker (com Compose), Java 21, Maven e git.

```bash
# 1. imagem da Toca (uma vez)
docker build -t alien/toca:0.1 toca/

# 2. Ollama e a rede das Tocas (alien-net)
docker compose up -d ollama
docker compose exec ollama ollama pull qwen3:8b

# 3. servidor (escuta só em 127.0.0.1:8080)
cd alien-server && mvn spring-boot:run

# 4. interface (http://127.0.0.1:5173; /api e /ws vão para o servidor pelo proxy do Vite)
cd alien-web && npm install && npm run dev
```

Pela interface: **Nova missão** → descreva a tarefa, informe o caminho do repositório → **Abrir
missão**. Pela linha de comando:

```bash
curl -s -X POST localhost:8080/api/missions -H 'Content-Type: application/json' -d '{
  "prompt": "A função soma em calc.py está errada. Corrija e rode um teste rápido.",
  "seed": {"type": "existing", "repositories": [{"path": "'$HOME'/projetos/calc"}]}
}'
# → {"id":"m-3f9a1c2e","status":"CREATED",...}

websocat 'ws://127.0.0.1:8080/ws/missions/m-3f9a1c2e?lastSeq=0'    # um envelope JSON por evento
```

| Rota | O que faz |
|---|---|
| `POST /api/missions` | Abre a missão (201) e começa a conduzi-la em segundo plano |
| `GET /api/missions` · `GET /api/missions/{id}` | Lista / snapshot com `lastSeq` |
| `POST /api/missions/{id}/stop` | Para a missão (aborta a sessão e descarta a Toca) |
| `WS /ws/missions/{id}?lastSeq=N` | Eventos com seq > N e depois ao vivo; aceita `{"type":"stop"}` |

> **Desempenho sem GPU.** Numa CPU de notebook (i7-1255U), o `qwen3:8b` processa o prompt a
> ~19 tokens/s e gera a ~4 tokens/s; só o system prompt do opencode tem ~7k tokens, então cada
> tarefa leva dezenas de minutos. Por isso `alien.agent.request-timeout` é 30 min. Com GPU, ou
> com um Ollama em outra máquina (`alien.agent.base-url`), o mesmo fluxo roda em segundos. Os
> testes não dependem de modelo: usam um LLM roteirizado (veja *Testes*).

A Toca também pode ser criada sozinha, sem missão (útil para depurar a semeadura):

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

| Rota (Tocas) | O que faz |
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

core/mission/                         a missão (M1)
├── domain/model/                     Mission, MissionId, MissionStatus, AgentModel, AlienEvent, NewEvent, EventType
├── port/agent/                       AgentSessionPort + AgentEvent (neutro: sem formato do opencode)
├── port/event/ · port/repository/    EventStorePort, MissionRepository
├── usecase/ (+ command/)             Start/Get/Stop/WatchMission, FailInterruptedMissions
└── application/                      MissionConductor (orquestrador), MissionEventHub, TaskTimeline
adapters/mission/
├── web/ (controller, request, response, mapper, handler)
├── websocket/ (handler, message, mapper)   MissionWebSocketHandler: replay + ao vivo
├── opencode/                         OpencodeSessionAdapter (HTTP + SSE), OpencodeEventTranslator
├── persistence/                      SqliteEventStore, SqliteMissionRepository
└── scheduling/ · config/             MissionRecovery, SQLite, executor de threads virtuais

alien-web/src/
├── domain/                           envelope AlienEvent e o reducer puro da timeline (eventos → passos)
├── api/                              REST (missionsApi) e MissionSocket (reconexão com lastSeq)
├── hooks/                            useMissionTimeline
└── components/                       App, NewMissionForm, MissionView, Timeline, TerminalPanel, DiffPanel
```

Cada fluxo (criar, semear, falhar, descartar, faxina, órfãos...) está desenhado classe a classe em
[`docs/sequencias.md`](docs/sequencias.md).

## Testes

```bash
cd alien-web
npm test     # reducer da timeline, reconexão do WebSocket com lastSeq, renderização

cd alien-server
mvn test     # unitários (domínio, casos de uso, adaptadores com git e HTTP reais)
mvn verify   # + integração: sobe Tocas reais no Docker (precisa da imagem e da rede alien-net)
```

O `MissionLifecycleIT` roda missões completas com o **opencode de verdade** numa Toca de verdade,
mas com um LLM roteirizado (`ScriptedLlmServer`, compatível com a API da OpenAI) no lugar do
Ollama: ele devolve tool calls fixas (ler `calc.py`, corrigir, rodar o teste). O teste confere a
timeline pelo WebSocket, a reconexão com `lastSeq` e o Parar, em segundos e sem GPU. Os fluxos SSE
reais usados nos testes do adaptador estão em `alien-server/src/test/resources/opencode/`.

## Especificação

A especificação completa, com diagramas, está em
[`docs/alien-code-especificacao.pdf`](docs/alien-code-especificacao.pdf).

[`docs/referencias/opencode-1.18.33-openapi.json`](docs/referencias/opencode-1.18.33-openapi.json)
é o contrato real da API do opencode fixado na imagem, extraído de `GET /doc`.

### Mapeamento real dos eventos (atualiza a seção 8.3)

O OpenAPI do opencode 1.18.33 declara duas gerações de eventos (`message.part.*` e
`session.next.*`), mas o servidor **emite só a primeira**. Conferido gravando o SSE de sessões reais:

| SSE do opencode 1.18.33 | `AgentEvent` (neutro) | Evento Alien |
|---|---|---|
| `message.part.delta` de parte `reasoning` | `ReasoningDelta` | `thinking.delta` |
| `message.part.delta` de parte `text` | `TextDelta` | `assistant.delta` |
| `message.part.updated` tool `running` | `ToolStarted` | `tool.started` |
| `message.part.updated` tool `completed` / `error` | `ToolFinished` | `tool.completed` (+ `terminal.output` se `bash`) |
| idem, com `metadata.filediff` (edit/write) | `FileChanged` | `file.changed` |
| `message.part.updated` parte `step-finish` (tokens) | `ModelCallFinished` | `budget.updated` (acumulado) |
| `session.error` `MessageAbortedError` | `SessionFailed(aborted)` | fim da tarefa por Parar |
| `session.error` (outros, ex.: `APIError`) | `SessionFailed` | `step.failed` + missão `FAILED` |
| `session.idle` | `SessionIdle` | fim da tarefa (`step.completed`) |

O `message.part.delta` traz só o id da parte; o tipo (texto ou raciocínio) vem do
`message.part.updated` anterior, por isso o tradutor guarda o tipo de cada parte.

## Diferenças conscientes em relação à especificação (M0 e M1)

- **Rede da Toca:** a rede `alien-net` ainda é uma bridge comum. O bloqueio de saída (só Ollama e
  proxy de pacotes) chega no M2, junto com o proxy — uma rede Docker `internal` não permite publicar
  a porta do opencode para o servidor no host.
- **Limite de processos:** 512 em vez de 256; opencode + Node + Maven passam de 256 com folga.
- **Persistência:** missões e eventos em SQLite (`~/.alien-code/alien.db`); Tocas continuam em
  memória, e missões ativas durante um reinício viram `FAILED` na subida seguinte.
- **Alterações não commitadas** do repositório original ainda não são levadas para a Toca.
- **Permissões do agente (M1):** sem cartões de aprovação na UI ainda, a Toca recebe `edit` e
  `bash` liberados (`webfetch` negado). Os pedidos de permissão viram `approval.requested` no M2.
- **Saídas grandes (M1):** `output` e `patch` são cortados em 16 mil caracteres no evento
  (`truncated: true`); o endpoint de blobs chega no M2.
- **Alien Web:** React 19 (a especificação cita 18, a versão estável quando foi escrita) e Vitest 4,
  compatível com o Node 20 da máquina.
- **Rede `alien-net`:** passa a ser criada pelo `compose.yaml`, junto com o Ollama (alias `ollama`).
- **Configuração do opencode:** entregue à Toca em `OPENCODE_CONFIG_CONTENT`, gerada a partir de
  `alien.agent.*` (provedor Ollama compatível com OpenAI, `timeout` do provedor ampliado).

## Referências

- [awslabs/aidlc-workflows](https://github.com/awslabs/aidlc-workflows): metodologia e motor de
  workflow (perfis, portões de aprovação, sensores, auditoria), com suporte nativo ao opencode.
- [Graphify-Labs/graphify](https://github.com/Graphify-Labs/graphify): grafo de dependências local
  (tree-sitter + MCP), usado para ordenar tarefas dependentes entre repositórios.
