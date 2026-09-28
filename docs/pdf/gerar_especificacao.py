"""Gera docs/alien-code-especificacao.pdf.

Uso:  python gerar_especificacao.py   (requer reportlab)
"""
from datetime import date
from pathlib import Path

from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (BaseDocTemplate, CondPageBreak, Frame, KeepTogether, NextPageTemplate,
                                PageBreak, PageTemplate, Paragraph, Preformatted, Spacer, Table,
                                TableStyle)
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.lib.colors import white

FONTS = "/usr/share/fonts/truetype/dejavu/"
pdfmetrics.registerFont(TTFont("DejaVu", FONTS + "DejaVuSans.ttf"))
pdfmetrics.registerFont(TTFont("DejaVu-Bold", FONTS + "DejaVuSans-Bold.ttf"))
pdfmetrics.registerFont(TTFont("DejaVuMono", FONTS + "DejaVuSansMono.ttf"))
pdfmetrics.registerFontFamily("DejaVu", normal="DejaVu", bold="DejaVu-Bold", italic="DejaVu", boldItalic="DejaVu-Bold")

import diagramas as D  # noqa: E402  (precisa das fontes registradas)

OUT = Path(__file__).resolve().parent.parent / "alien-code-especificacao.pdf"
VERSION = "0.1 (rascunho para discussão)"
TODAY = date.today().strftime("%d/%m/%Y")

# ------------------------------------------------------------------ estilos
base = ParagraphStyle("base", fontName="DejaVu", fontSize=9.2, leading=13.4, textColor=D.INK, alignment=TA_LEFT)
S = {
    "body": base,
    "small": ParagraphStyle("small", parent=base, fontSize=7.8, leading=10.5, textColor=D.MUTED),
    "h1": ParagraphStyle("h1", parent=base, fontName="DejaVu-Bold", fontSize=17, leading=22, textColor=D.PRIMARY,
                         spaceBefore=4, spaceAfter=10),
    "h2": ParagraphStyle("h2", parent=base, fontName="DejaVu-Bold", fontSize=11.5, leading=15, textColor=D.INK,
                         spaceBefore=12, spaceAfter=5),
    "h3": ParagraphStyle("h3", parent=base, fontName="DejaVu-Bold", fontSize=9.6, leading=13, textColor=D.PRIMARY,
                         spaceBefore=8, spaceAfter=3),
    "bullet": ParagraphStyle("bullet", parent=base, leftIndent=12, bulletIndent=2, spaceAfter=2),
    "cell": ParagraphStyle("cell", parent=base, fontSize=7.8, leading=10.2),
    "cellb": ParagraphStyle("cellb", parent=base, fontSize=7.8, leading=10.2, fontName="DejaVu-Bold"),
    "cellh": ParagraphStyle("cellh", parent=base, fontSize=7.8, leading=10.2, fontName="DejaVu-Bold", textColor=white),
    "code": ParagraphStyle("code", fontName="DejaVuMono", fontSize=7.3, leading=9.6, textColor=D.INK),
    "caption": ParagraphStyle("caption", parent=base, fontSize=7.8, leading=10.5, textColor=D.MUTED, spaceBefore=4,
                              spaceAfter=10),
    "callout": ParagraphStyle("callout", parent=base, fontSize=8.8, leading=12.6),
    "toc1": ParagraphStyle("toc1", parent=base, fontName="DejaVu-Bold", fontSize=9.5, leading=15),
    "toc2": ParagraphStyle("toc2", parent=base, fontSize=8.6, leading=12.5, leftIndent=14, textColor=D.MUTED),
}

story = []
fig_n = [0]


def P(t, st="body"):
    story.append(Paragraph(t, S[st]))


def H1(t):
    story.append(PageBreak())
    story.append(Paragraph(t, S["h1"]))


def H2(t, need=45):
    story.append(CondPageBreak(need * mm))
    story.append(Paragraph(t, S["h2"]))


def H3(t):
    story.append(Paragraph(t, S["h3"]))


def UL(items):
    for it in items:
        story.append(Paragraph(it, S["bullet"], bulletText="•"))
    story.append(Spacer(1, 4))


def OL(items):
    for i, it in enumerate(items, 1):
        story.append(Paragraph(it, S["bullet"], bulletText=f"{i}."))
    story.append(Spacer(1, 4))


def FIG(drawing, caption):
    fig_n[0] += 1
    story.append(KeepTogether([Spacer(1, 4), drawing,
                               Paragraph(f"<b>Figura {fig_n[0]}.</b> {caption}", S["caption"])]))


def CODE(src, title=None):
    items = []
    if title:
        items.append(Paragraph(f"<b>{title}</b>", S["small"]))
        items.append(Spacer(1, 2))
    pre = Preformatted(src.strip("\n"), S["code"])
    t = Table([[pre]], colWidths=[D.W])
    t.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), D.GRAY_50),
                           ("BOX", (0, 0), (-1, -1), 0.5, D.LINE),
                           ("LEFTPADDING", (0, 0), (-1, -1), 8), ("RIGHTPADDING", (0, 0), (-1, -1), 8),
                           ("TOPPADDING", (0, 0), (-1, -1), 6), ("BOTTOMPADDING", (0, 0), (-1, -1), 6)]))
    items.append(t)
    items.append(Spacer(1, 8))
    story.append(KeepTogether(items))


def TABLE(header, rows, widths, first_bold=True):
    data = [[Paragraph(h, S["cellh"]) for h in header]]
    for r in rows:
        data.append([Paragraph(c, S["cellb"] if (i == 0 and first_bold) else S["cell"]) for i, c in enumerate(r)])
    total = sum(widths)
    t = Table(data, colWidths=[w * D.W / total for w in widths], repeatRows=1)
    style = [("BACKGROUND", (0, 0), (-1, 0), D.PRIMARY),
             ("VALIGN", (0, 0), (-1, -1), "TOP"),
             ("LINEBELOW", (0, 0), (-1, -1), 0.4, D.LINE),
             ("LEFTPADDING", (0, 0), (-1, -1), 5), ("RIGHTPADDING", (0, 0), (-1, -1), 5),
             ("TOPPADDING", (0, 0), (-1, -1), 4), ("BOTTOMPADDING", (0, 0), (-1, -1), 4)]
    for i in range(1, len(data)):
        if i % 2 == 0:
            style.append(("BACKGROUND", (0, i), (-1, i), D.GRAY_50))
    t.setStyle(TableStyle(style))
    story.append(t)
    story.append(Spacer(1, 8))


def CALLOUT(t, theme="primary"):
    fill, stroke = D.THEMES[theme]
    tb = Table([[Paragraph(t, S["callout"])]], colWidths=[D.W])
    tb.setStyle(TableStyle([("BACKGROUND", (0, 0), (-1, -1), fill),
                            ("LINEBEFORE", (0, 0), (0, -1), 3, stroke),
                            ("LEFTPADDING", (0, 0), (-1, -1), 10), ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                            ("TOPPADDING", (0, 0), (-1, -1), 7), ("BOTTOMPADDING", (0, 0), (-1, -1), 7)]))
    story.append(tb)
    story.append(Spacer(1, 8))


# ------------------------------------------------------------------ páginas
class Doc(BaseDocTemplate):
    def afterFlowable(self, f):
        if isinstance(f, Paragraph) and f.style.name in ("h1", "h2"):
            txt = f.getPlainText()
            level = 0 if f.style.name == "h1" else 1
            key = f"k{id(f)}"
            self.canv.bookmarkPage(key)
            self.canv.addOutlineEntry(txt, key, level=level, closed=level > 0)
            self.notify("TOCEntry", (level, txt, self.page, key))


def cover(c, doc):
    w, h = A4
    c.saveState()
    c.setFillColor(D.PRIMARY)
    c.rect(0, h * 0.42, w, h * 0.58, fill=1, stroke=0)
    c.setFillColor(D.ACCENT)
    c.rect(0, h * 0.42 - 6, w, 6, fill=1, stroke=0)
    # alienígena geométrico
    cx, cy = w - 120, h - 130
    c.setStrokeColor(white)
    c.setLineWidth(3)
    c.line(cx - 14, cy + 40, cx - 30, cy + 78)
    c.line(cx + 14, cy + 40, cx + 30, cy + 78)
    c.setFillColor(D.ACCENT)
    c.circle(cx - 30, cy + 80, 6, fill=1, stroke=0)
    c.circle(cx + 30, cy + 80, 6, fill=1, stroke=0)
    c.setFillColor(white)
    c.ellipse(cx - 40, cy - 50, cx + 40, cy + 48, fill=1, stroke=0)
    c.setFillColor(D.PRIMARY)
    for side in (-1, 1):
        c.saveState()
        c.translate(cx + side * 17, cy - 2)
        c.rotate(-side * 30)
        c.ellipse(-9, -15, 9, 15, fill=1, stroke=0)
        c.restoreState()
    c.setStrokeColor(D.PRIMARY)
    c.setLineWidth(1.5)
    c.line(cx - 7, cy - 32, cx + 7, cy - 32)
    c.setFillColor(white)
    c.setFont("DejaVu", 10)
    c.drawString(50, h - 90, "ESPECIFICAÇÃO TÉCNICA · PROJETO DE ESTUDO · V2")
    c.setFont("DejaVu-Bold", 40)
    c.drawString(50, h - 190, "Alien Code")
    c.setFont("DejaVu", 14)
    c.drawString(50, h - 220, "Assistente de codificação local com harness opencode,")
    c.drawString(50, h - 240, "ambiente efêmero e execução visível passo a passo")
    c.setFont("DejaVu", 9.5)
    c.drawString(50, h * 0.42 + 40, "Sucessor de poc-websocket-demo (assistente) + coding-agent (agente codificador)")
    c.setFillColor(D.INK)
    c.setFont("DejaVu-Bold", 10)
    y = h * 0.42 - 60
    for k, v in [("Versão", VERSION), ("Data", TODAY), 
                 ("Referências", "awslabs/aidlc-workflows (MIT-0) · Graphify-Labs/graphify (Apache-2.0)"),
                 ("Stack", "Java 21 · Spring Boot · Spring AI · React + TypeScript · Docker · opencode · Ollama")]:
        c.setFont("DejaVu-Bold", 9)
        c.drawString(50, y, k)
        c.setFont("DejaVu", 9)
        c.drawString(140, y, v)
        y -= 18
    c.setFillColor(D.MUTED)
    c.setFont("DejaVu", 8)
    c.drawString(50, 50, "Documento gerado por docs/pdf/gerar_especificacao.py — edite o script, não o PDF.")
    c.restoreState()


def page(c, doc):
    w, h = A4
    c.saveState()
    c.setStrokeColor(D.LINE)
    c.setLineWidth(0.5)
    c.line(50, h - 40, w - 50, h - 40)
    c.setFont("DejaVu", 7.5)
    c.setFillColor(D.MUTED)
    c.drawString(50, h - 34, "Alien Code — Especificação técnica")
    c.drawRightString(w - 50, h - 34, f"v{VERSION.split(' ')[0]} · {TODAY}")
    c.line(50, 40, w - 50, 40)
    c.drawRightString(w - 50, 28, str(doc.page))
    c.setFillColor(D.ACCENT)
    c.rect(50, 28, 14, 3, fill=1, stroke=0)
    c.restoreState()


doc = Doc(str(OUT), pagesize=A4, leftMargin=50, rightMargin=50, topMargin=55, bottomMargin=55,
          title="Alien Code — Especificação técnica",
          subject="Assistente de codificação local com harness opencode")
fr = Frame(50, 55, A4[0] - 100, A4[1] - 110, id="f", leftPadding=0, rightPadding=0, topPadding=0, bottomPadding=0)
doc.addPageTemplates([PageTemplate("cover", [fr], onPage=cover), PageTemplate("body", [fr], onPage=page)])

story.append(NextPageTemplate("body"))
story.append(PageBreak())
story.append(Paragraph("Sumário", S["h1"]))
toc = TableOfContents()
toc.levelStyles = [S["toc1"], S["toc2"]]
toc.dotsMinLevel = 0
story.append(toc)

# ================================================================== 1
H1("1. Resumo executivo")
P("O <b>Alien Code</b> é a segunda versão do estudo de agentes de codificação. Ele junta, num único produto "
  "que roda na máquina do desenvolvedor, os dois papéis que na V1 estavam separados: o <b>assistente</b> "
  "(poc-websocket-demo, chat com streaming) e o <b>agente codificador</b> (coding-agent, executado dentro de "
  "uma pipeline do GitHub Actions).")
P("Em vez de disparar uma pipeline por tarefa, o Alien Code sobe um <b>ambiente efêmero local</b> — um "
  "container Docker que chamamos de <b>Toca</b> — com os repositórios envolvidos, e dentro dele executa o "
  "<b>opencode</b> como harness do agente, usando modelos abertos servidos pelo <b>Ollama</b>. Tudo o que o "
  "agente faz (planejar, ler arquivos, editar, rodar testes, pedir aprovação) vira um evento que aparece "
  "<b>ao vivo, passo a passo</b>, numa timeline de execução no estilo do Manus e do Claude Code Web.")
P("Dois projetos de referência apresentados no TDC dão a espinha dorsal:")
UL(["<b>AI-DLC Workflows</b> (awslabs) — metodologia e motor de workflow neutro de harness, com suporte "
    "nativo ao opencode: perfis de trabalho, estágios, portões de aprovação humana, sensores de verificação, "
    "trilha de auditoria e condições de parada. O Alien Code usa o AI-DLC <i>dentro</i> da Toca e adota seus "
    "princípios no orquestrador.",
    "<b>graphify</b> (Graphify-Labs) — transforma o código em um grafo de conhecimento local (tree-sitter, sem "
    "LLM). O Alien Code usa o grafo para <b>descobrir e ordenar dependências entre tarefas</b>, inclusive entre "
    "repositórios diferentes, e o expõe ao agente via MCP para que ele consulte o grafo em vez de varrer "
    "arquivos."])
CALLOUT("<b>A ideia em uma frase:</b> o pedido vira uma <b>missão</b>; o grafo de dependências vira um "
        "<b>DAG de tarefas</b>; a Toca é a <b>mesa de trabalho descartável</b>; o opencode é quem <b>codifica</b>; "
        "a timeline é a <b>janela</b> por onde o desenvolvedor acompanha tudo; e o patch aprovado é a "
        "<b>entrega</b> na branch local.")

H2("1.1 O que muda em relação à V1")
TABLE(["Aspecto", "V1 (poc-websocket-demo + coding-agent)", "V2 (Alien Code)"], [
    ["Onde roda o agente", "Runner do GitHub Actions, um por issue", "Container efêmero local (Toca), um por missão"],
    ["Harness", "Loop próprio em Java (agent-runner)", "opencode (servidor HTTP + SSE) + AI-DLC"],
    ["Dependências entre tarefas", "Pipelines independentes, sem visão umas das outras",
     "Grafo global (graphify) + DAG de tarefas na mesma Toca"],
    ["Visibilidade", "Comentário na issue + polling do status", "Timeline ao vivo: passos, tools, terminal, diff, grafo"],
    ["Aprovação humana", "Revisão do PR no final", "Portões durante a missão (plano, permissões, entrega)"],
    ["Entrega", "PR em rascunho no GitHub", "Patch aplicado numa branch local; PR opcional"],
    ["Infra necessária", "Pool de runners, tokens, filas", "Docker + Ollama na própria máquina"],
], [1.1, 2, 2])

# ================================================================== 2
H1("2. Contexto e problema")
P("A V1 provou o ciclo <i>pensa → age → observa</i> dentro de um container sem rede e a entrega por PR "
  "em rascunho. Ao evoluir, apareceram limitações estruturais de executar o agente <b>em pipeline</b>:")
OL(["<b>Pool de máquinas.</b> Cada tarefa ocupa um runner. Para paralelizar é preciso um pool (self-hosted "
    "ou pago), com modelo, caches e imagens aquecidos em cada máquina. Runners padrão são lentos para "
    "inferência local.",
    "<b>Dependências cruzadas.</b> Uma mudança real costuma tocar mais de um repositório (biblioteca → API "
    "→ front). Em pipelines separadas, a tarefa do front não enxerga a mudança da API que ainda não foi "
    "mergeada: cada execução parte de um <i>main</i> diferente e não sabe de quem depende.",
    "<b>Feedback lento e opaco.</b> O desenvolvedor só vê o resultado no fim (ou um log bruto). Não há "
    "como intervir no meio, responder uma dúvida ou negar um comando.",
    "<b>Credenciais e superfície.</b> Tokens de GitHub, permissões de workflow e regras anti-loop do "
    "GITHUB_TOKEN complicam o fluxo."])
FIG(D.d_v1_v2(), "Da execução em pipeline (V1) para a execução local e efêmera (V2).")
P("A resposta da V2 é trazer a execução para perto do desenvolvedor, sem abrir mão do isolamento: a "
  "máquina local é o \"pool\", o container efêmero é o isolamento, e o grafo de dependências é o que "
  "permite que tarefas relacionadas sejam executadas <b>juntas e na ordem certa</b>.")

H2("2.1 Objetivos")
UL(["Executar um agente de codificação com modelos abertos (Ollama) <b>100% local</b>, sem pipeline.",
    "Isolar cada missão num <b>ambiente efêmero</b> criado a partir de um projeto existente ou de um projeto novo.",
    "Mostrar <b>cada passo</b> da execução em tempo real, com possibilidade de intervir (parar, responder, aprovar, negar).",
    "Tratar <b>dependências entre mudanças</b> (inclusive entre repositórios) de forma explícita, a partir de um grafo.",
    "Reaproveitar o que o mercado já resolveu: <b>opencode</b> como harness, <b>AI-DLC</b> como metodologia/"
    "motor de workflow e <b>graphify</b> como mapa de dependências.",
    "Continuar sendo material de estudo: arquitetura explícita (Clean Architecture), decisões documentadas, testes."])
H2("2.2 Fora de escopo (nesta versão)")
UL(["Execução remota ou em nuvem, multiusuário e autenticação corporativa.",
    "Deploy em produção a partir da missão (a entrega termina na branch local ou num PR).",
    "Treinar ou ajustar modelos. Usamos modelos abertos como estão.",
    "Substituir a revisão humana: o Alien Code reduz o esforço de revisão, não o elimina."])

# ================================================================== 3
H1("3. Projetos de referência e como usamos")
H2("3.1 AI-DLC Workflows (awslabs/aidlc-workflows)")
P("O AI-DLC (AI-Driven Development Life Cycle) transforma assistentes de código em workflows de entrega "
  "estruturados e verificáveis. Um <b>núcleo neutro</b> (core/) é empacotado para vários harnesses — Claude "
  "Code, Kiro, Codex, Cursor, Copilot e <b>opencode</b> (harness/opencode). Pontos relevantes que "
  "encontramos no repositório e na especificação AI-DLC Workflows 2.0:")
UL(["<b>5 fases, 33 estágios, 14 agentes-persona</b> (arquiteto, desenvolvedor, qualidade, devsecops…) e "
    "<b>11 perfis</b> de workflow (feature, bugfix, refactor, poc, mvp, security-patch, express…).",
    "<b>Modelo de três compartimentos</b> por estágio: <i>o quê</i> (entradas/saídas), <i>como sabemos que "
    "está certo</i> (pós-condições) e <i>o que aprendemos</i> (regras candidatas).",
    "<b>Loop autocorretivo com condição de parada</b>: itera contra pós-condições até convergir ou atingir "
    "limite de iterações/tokens; então <b>escala para o humano</b>.",
    "<b>Portões de aprovação humana</b>, estado e auditoria controlados por ferramenta (não pelo modelo), "
    "e <b>sensores</b> (linter, type-check, rastreabilidade, seções obrigatórias).",
    "No opencode, o AI-DLC instala: <font face='DejaVuMono'>opencode.json</font> (skills, instruções e "
    "permissões), o comando <font face='DejaVuMono'>/aidlc</font>, 14 subagentes em "
    "<font face='DejaVuMono'>.opencode/agents/</font> e um <b>plugin adaptador</b> que mapeia eventos do "
    "opencode (chat.message, tool.execute.before/after, session.idle) para os hooks do núcleo."])
H3("Como o Alien Code usa o AI-DLC")
TABLE(["O que adotamos", "Como", "Onde"], [
    ["Motor AI-DLC dentro da Toca", "A imagem da Toca traz o binário aidlc; na preparação rodamos "
     "<font face='DejaVuMono'>aidlc config --harness opencode</font> no workspace da missão.", "Toca / passo 3"],
    ["Perfis como tipo de missão", "O orquestrador classifica o pedido (feature, bugfix, refactor, poc, novo "
     "projeto → mvp/poc) e escolhe o perfil; tarefas pequenas usam <b>express</b>.", "Orquestrador"],
    ["Portões de aprovação", "Perguntas e aprovações que o AI-DLC faz ao humano no opencode são capturadas "
     "pelo SSE e viram <b>approval.requested</b> na UI; a resposta volta pela API do opencode.", "Hub de eventos / UI"],
    ["Três compartimentos", "Cada tarefa do DAG é descrita como: objetivo e artefatos (o quê), comandos de "
     "verificação executáveis (pós-condições) e notas de aprendizado.", "Planejador"],
    ["Halting + escalonamento", "Limites de iteração, tokens, tempo e \"sem progresso\" são aplicados pelo "
     "orquestrador, fora do modelo (tool-owned state).", "Orquestrador"],
    ["Trilha de auditoria", "aidlc-docs/ (estado, audit) é colhido junto com o patch e anexado à missão.", "Entrega"],
    ["Sem delegação oculta", "Só o orquestrador cria sessões/subtarefas; o agente não cria outros agentes "
     "livremente.", "Princípio de design"],
], [1.3, 3, 1])
CALLOUT("<b>Atenção — modelos locais:</b> o AI-DLC recomenda modelos de raciocínio fortes. Com modelos "
        "abertos menores, o workflow completo (33 estágios) tende a ser lento e frágil. Por isso o Alien Code "
        "tem dois modos: <b>AI-DLC completo</b> (perfis maiores, quando o modelo aguenta) e <b>AI-DLC lite</b> "
        "(perfil express/bugfix ou só os princípios aplicados pelo orquestrador). O modo é configurável por "
        "missão.", "amber")

H2("3.2 graphify (Graphify-Labs/graphify)")
P("O graphify mapeia um projeto (código, docs, PDFs, imagens) em um <b>grafo de conhecimento</b> que se "
  "consulta em vez de fazer grep. O código é analisado com <b>tree-sitter</b> de forma determinística, "
  "<b>sem LLM e sem sair da máquina</b>. Cada aresta tem um rótulo de confiança "
  "(<font face='DejaVuMono'>EXTRACTED</font>, <font face='DejaVuMono'>INFERRED</font>, "
  "<font face='DejaVuMono'>AMBIGUOUS</font>). Recursos que usamos diretamente:")
UL(["<font face='DejaVuMono'>graphify update &lt;dir&gt;</font> — (re)constrói o grafo só por AST; incremental.",
    "<font face='DejaVuMono'>graphify affected \"&lt;nó&gt;\" --depth N</font> — <b>raio de impacto</b>: quem "
    "chama, importa, herda ou usa um símbolo (calls, imports, inherits, implements, uses…).",
    "<font face='DejaVuMono'>graphify global add graph.json --as repo</font> e "
    "<font face='DejaVuMono'>merge-graphs</font> — <b>grafo entre repositórios</b>, com passagem que resolve "
    "chamadas que cruzam a fronteira de repo (cross_repo_calls).",
    "<font face='DejaVuMono'>graphify --mcp</font> — servidor MCP com ferramentas query_graph, get_node, "
    "get_neighbors, shortest_path, god_nodes, graph_stats.",
    "<font face='DejaVuMono'>graphify opencode install</font> — instala AGENTS.md e um plugin "
    "<i>tool.execute.before</i> que incentiva o agente a consultar o grafo antes de ler arquivos.",
    "graph.html, GRAPH_REPORT.md e <font face='DejaVuMono'>graph_diff(G_old, G_new)</font> — visualização e "
    "comparação antes/depois."])
H3("Como o Alien Code usa o graphify")
TABLE(["Uso", "Detalhe"], [
    ["Descobrir dependências", "Ao montar a missão, o planejador pergunta ao grafo quais nós são afetados pelos "
     "símbolos citados no pedido e em que repositórios estão."],
    ["Ordenar tarefas (DAG)", "Arestas <i>calls/imports/uses</i> entre os nós afetados definem a ordem: quem é "
     "dependido é feito primeiro; tarefas sem aresta entre si podem rodar em paralelo."],
    ["Economizar contexto", "Via MCP, o agente pede vizinhos e caminhos em vez de abrir dezenas de arquivos — "
     "essencial para modelos locais com janela de 32k tokens."],
    ["Testes afetados", "Na verificação, <i>affected</i> sobre os arquivos alterados seleciona as classes de teste "
     "a rodar primeiro (rápido), antes da suíte completa."],
    ["Revisão da entrega", "O graph diff (antes × depois) mostra novas dependências, ciclos de import e nós "
     "centrais tocados — um resumo estrutural para o revisor."],
    ["Visualização", "O graph.html da Toca é servido na aba <b>Grafo</b> da UI, com os nós tocados destacados."],
], [1.2, 4])
P("<b>Licenças:</b> AI-DLC é MIT-0 e graphify é Apache-2.0; ambos permitem uso e empacotamento na imagem da "
  "Toca, mantendo os avisos de licença (NOTICE do graphify).", "small")

H2("3.3 opencode como harness")
P("O opencode é o agente que efetivamente codifica. Escolhemos o opencode porque ele (1) funciona com "
  "provedores abertos, inclusive Ollama; (2) tem um <b>modo servidor</b> "
  "(<font face='DejaVuMono'>opencode serve</font>, porta padrão 4096) com API HTTP e um fluxo de "
  "<b>Server-Sent Events</b> em <font face='DejaVuMono'>GET /event</font> — exatamente o que precisamos para "
  "a timeline; (3) é suportado nativamente pelo AI-DLC e pelo graphify.")
TABLE(["Endpoint do opencode", "Uso no Alien Code"], [
    ["POST /session", "Uma sessão por tarefa do DAG (sessões paralelas para tarefas independentes)."],
    ["POST /session/:id/prompt_async", "Envia a instrução da tarefa sem bloquear; o progresso chega pelo SSE."],
    ["GET /event (SSE)", "Fonte principal da timeline: partes de mensagem, tool calls, arquivos, permissões, idle."],
    ["POST /session/:id/permissions/:pid", "Responde aprovações (ex.: comando fora da lista permitida)."],
    ["POST /session/:id/abort", "Botão <b>Parar</b> da UI e condição de parada do orquestrador."],
    ["GET /session/:id/diff", "Diff da sessão para a aba Diff e para conferência na colheita."],
], [1.6, 3.4])
P("A API é protegida por <font face='DejaVuMono'>OPENCODE_SERVER_PASSWORD</font> (basic auth) e só é exposta "
  "na rede interna da Toca. A especificação OpenAPI exata deve ser conferida na versão fixada da imagem "
  "(endpoint <font face='DejaVuMono'>/doc</font>).", "small")

# ================================================================== 4
H1("4. Arquitetura")
P("O sistema tem três blocos: o <b>Alien Web</b> (interface), o <b>Alien Server</b> (orquestrador, na "
  "máquina do dev) e a <b>Toca</b> (container efêmero onde o agente trabalha). O Ollama roda no host e é a "
  "única saída de rede permitida à Toca.")
FIG(D.d_arquitetura(), "Visão de componentes. Setas duplas indicam comunicação nos dois sentidos.")
H2("4.1 Componentes")
TABLE(["Componente", "Responsabilidade", "Tecnologia"], [
    ["Alien Web", "Chat, timeline de execução, terminal ao vivo, diff, grafo, preview, painel de aprovações.",
     "React 18 + TypeScript + Vite"],
    ["Conversa", "Entende a intenção, faz perguntas de clarificação, decide quando abrir uma missão.",
     "Spring AI + Ollama (streaming, como na V1)"],
    ["Orquestrador de Missões", "Dono do objetivo; máquina de estados; aplica halting; conduz o DAG; "
     "registra auditoria.", "Java 21, Spring Boot"],
    ["Planejador (DAG)", "Transforma pedido + grafo em tarefas com dependências, contratos e pós-condições.",
     "Java + graphify (CLI)"],
    ["Hub de Eventos", "Recebe eventos de todas as fontes, normaliza, numera e distribui por WebSocket.",
     "Spring WebSocket, SQLite"],
    ["Gerente da Toca", "Cria, limita, observa e destrói containers; volumes; rede interna.",
     "docker-java (Docker Engine API)"],
    ["Entrega", "Colhe patch, roda verificação final, aplica na branch local após aprovação; PR opcional.",
     "git CLI, gh CLI (opcional)"],
    ["Toca", "Ambiente efêmero com opencode serve, AI-DLC, graphify, toolchains e o workspace.",
     "Imagem Docker alien/toca"],
], [1.2, 3, 1.4])
H2("4.2 Organização do Alien Server (Clean Architecture)")
P("Mantemos o estilo da V1: portas e adaptadores, um caso de uso por classe, domínio sem dependência de "
  "framework. Trocar o opencode por outro harness (ou o Docker por Podman) é trocar um adaptador.")
FIG(D.d_camadas(), "Camadas do Alien Server e principais portas.")

# ================================================================== 5
H1("5. Ambiente efêmero — a Toca")
P("A Toca é um container criado <b>por missão</b> e destruído no fim. Ela resolve o isolamento que na V1 "
  "vinha do runner do GitHub, mas localmente e em segundos. O repositório original do desenvolvedor "
  "<b>nunca é montado</b> na Toca: o agente trabalha numa cópia, e só um patch aprovado volta.")
FIG(D.d_ciclo_toca(), "Ciclo de vida da Toca.")
H2("5.1 Modos de semeadura")
TABLE(["Modo", "Como a Toca é semeada", "Observações"], [
    ["Projeto existente", "<font face='DejaVuMono'>git clone --local</font> de cada repo selecionado para "
     "/workspace/&lt;repo&gt;, na branch/commit escolhidos; alterações não commitadas podem ser incluídas "
     "como patch inicial (opcional).", "Um ou mais repositórios na mesma Toca (multi-repo)."],
    ["Projeto novo", "Template do catálogo (spring-boot-api, react-vite, python-cli…) ou diretório vazio; "
     "<font face='DejaVuMono'>git init</font> dentro da Toca.", "Perfil AI-DLC mvp/poc; a entrega cria um "
     "novo diretório local em vez de um patch."],
    ["Continuar missão", "Reaplica o patch da missão anterior sobre o mesmo commit base.",
     "Ajustes após revisão (equivalente ao \"ajustes pelo PR\" da V1)."],
], [1, 2.8, 1.8])
H2("5.2 Imagem alien/toca")
CODE("""
FROM eclipse-temurin:21-jdk            # base com JDK; Node e Python adicionados abaixo
RUN apt-get install -y git curl ripgrep python3 python3-venv nodejs npm maven
RUN npm i -g opencode-ai@<versão-fixada>                    # harness
RUN curl -fsSL .../aidlc-workflows/releases/<versão>/install.sh | sh   # AI-DLC
RUN pipx install graphifyy==<versão-fixada>                 # graphify (pacote com dois y)
RUN useradd -m -u 1000 alien
USER alien
WORKDIR /workspace
COPY toca-entrypoint.sh /usr/local/bin/
ENTRYPOINT ["toca-entrypoint.sh"]   # sobe: opencode serve --hostname 0.0.0.0 --port 4096
""", "Dockerfile (esboço) — versões sempre fixadas, imagem construída uma vez e reutilizada")
H2("5.3 Isolamento e limites")
TABLE(["Controle", "Valor padrão", "Motivo"], [
    ["Rede", "rede Docker <i>internal</i> alien-net; saída só para o proxy do Ollama e, se habilitado, "
     "para um proxy de pacotes com lista de domínios (Maven Central, npm)", "Mesmo princípio \"sem rede\" da V1, "
     "com exceção controlada"],
    ["Usuário", "não-root (uid 1000), <font face='DejaVuMono'>--cap-drop ALL</font>, "
     "<font face='DejaVuMono'>no-new-privileges</font>", "Reduz impacto de comando malicioso"],
    ["Recursos", "4 CPUs, 6 GB RAM, 256 PIDs, disco do volume 10 GB", "Não travar a máquina do dev"],
    ["Tempo de vida", "TTL de 60 min por missão (renovável pela UI)", "Evitar containers esquecidos"],
    ["Credenciais", "nenhuma: sem SSH, sem token Git, sem ~/.m2/settings com senha", "A entrega é feita "
     "pelo host, não pela Toca"],
    ["Portas", "4096 (opencode) e porta de preview publicadas só em 127.0.0.1", "Nada exposto na rede local"],
    ["Caches", "volumes somente leitura de ~/.m2 e cache npm do host (opcional)", "Build rápido sem rede"],
], [1, 2.6, 1.6])
CODE("""
docker run -d --name toca-m42 --network alien-net \\
  --cpus 4 --memory 6g --pids-limit 256 --cap-drop ALL \\
  --security-opt no-new-privileges --user 1000:1000 \\
  -v toca-m42-ws:/workspace -v ~/.m2/repository:/home/alien/.m2/repository:ro \\
  -p 127.0.0.1::4096 -e OPENCODE_SERVER_PASSWORD=<aleatória> \\
  --label alien.mission=42 --label alien.ttl=3600 alien/toca:0.1
""", "Equivalente em linha de comando do que o Gerente da Toca faz via Docker API")

# ================================================================== 6
H1("6. Harness de execução")
P("O Alien Code não reimplementa o loop do agente (como fazia o agent-runner da V1): ele <b>conduz</b> o "
  "opencode. O orquestrador decide <i>o quê</i> e <i>em que ordem</i>; o opencode decide <i>como</i> "
  "codificar cada tarefa; o AI-DLC dá a estrutura de estágios, portões e verificação.")
H2("6.1 Loop gerar → verificar → aprender")
FIG(D.d_loop(), "Loop autocorretivo por tarefa, com condição de parada e escalonamento (inspirado no AI-DLC 2.0).")
P("A verificação de cada tarefa é <b>executável e fora do alcance do modelo</b> (princípio 3 do AI-DLC): "
  "os comandos de pós-condição são definidos no plano e rodados pelo orquestrador via "
  "<font face='DejaVuMono'>docker exec</font>, não pelo agente. O agente pode rodar testes para se orientar, "
  "mas o veredito vem do orquestrador — como na V1, em que a pipeline verificava por conta própria.")
H2("6.2 Condições de parada (halting)")
TABLE(["Condição", "Padrão", "Ao atingir"], [
    ["Iterações de verificação por tarefa", "8", "Escala: mostra último erro e pede orientação"],
    ["Orçamento de tokens por missão", "400k (somado do opencode)", "Pausa a missão; pede aprovação para continuar"],
    ["Tempo de parede por tarefa", "20 min", "Aborta a sessão (/abort) e escala"],
    ["Sem progresso", "3 iterações com o mesmo erro e diff igual", "Escala imediatamente"],
    ["TTL da Toca", "60 min", "Aviso aos 50 min; descarte ao expirar"],
], [2, 1.6, 2.4])
H2("6.3 Configuração do opencode na Toca")
CODE("""
{
  "$schema": "https://opencode.ai/config.json",
  "provider": {
    "ollama": {
      "npm": "@ai-sdk/openai-compatible",
      "options": { "baseURL": "http://ollama-proxy:11434/v1" },
      "models": { "qwen3-coder:30b": {}, "devstral:24b": {} }
    }
  },
  "model": "ollama/qwen3-coder:30b",
  "mcp": {
    "graphify": { "type": "local", "command": ["graphify", "--mcp", "/workspace"] }
  },
  "permission": {
    "edit": "allow",
    "bash": { "*": "ask", "mvn *": "allow", "npm test*": "allow", "git status": "allow",
              "git diff*": "allow", "graphify *": "allow" }
  }
}
""", "opencode.json gerado na preparação (mesclado com o que o aidlc config instala)")
P("Comandos marcados como <i>ask</i> viram pedidos de aprovação na UI. Modelos citados são exemplos; o "
  "modelo é escolhido por missão entre os disponíveis no Ollama.", "small")

# ================================================================== 7
H1("7. Mapa de dependências e DAG de tarefas")
P("Este é o ponto que a V1 não resolvia: <b>tarefas que dependem umas das outras</b>. No Alien Code, todas "
  "as tarefas de uma missão rodam <b>na mesma Toca</b>, sobre o mesmo workspace, numa ordem derivada do grafo.")
FIG(D.d_grafo_dag(), "Do grafo global de três repositórios ao DAG de tarefas da missão.")
H2("7.1 Algoritmo do planejador")
OL(["<b>Indexar:</b> <font face='DejaVuMono'>graphify update</font> em cada repo e "
    "<font face='DejaVuMono'>graphify global add</font> para compor o grafo global da missão.",
    "<b>Ancorar:</b> o modelo de conversa extrai do pedido os conceitos citados; o planejador os resolve para "
    "nós do grafo (<font face='DejaVuMono'>query_graph</font>). Âncoras ambíguas viram pergunta ao usuário.",
    "<b>Expandir:</b> <font face='DejaVuMono'>affected</font> (profundidade 2, configurável) a partir das "
    "âncoras gera o conjunto de nós impactados, agrupados por repositório e módulo.",
    "<b>Propor tarefas:</b> o modelo agrupa nós impactados em tarefas coesas (normalmente uma por módulo), cada "
    "uma com objetivo, arquivos prováveis, <b>contrato</b> que produz/consome e comandos de verificação.",
    "<b>Ordenar:</b> se algum nó da tarefa B depende (calls/imports/uses) de um nó da tarefa A, cria-se a aresta "
    "A → B. Ciclos são quebrados juntando as tarefas. O resultado é um DAG com ordenação topológica.",
    "<b>Aprovar:</b> o DAG é mostrado ao usuário (aba Plano) antes de qualquer execução."])
H2("7.2 Execução do DAG")
UL(["Tarefas cujo predecessor terminou verde ficam <b>prontas</b>. Até N (padrão 2) rodam em paralelo, cada "
    "uma em uma sessão opencode e numa <b>git worktree</b> própria dentro da Toca.",
    "Ao terminar uma tarefa, sua worktree é integrada ao workspace principal (merge rápido; conflito → nova "
    "tarefa de integração ou escalonamento), e o grafo é <b>atualizado incrementalmente</b>. Assim, a tarefa "
    "seguinte já enxerga a mudança real da anterior — o que resolve o problema das pipelines separadas.",
    "O <b>contrato</b> de uma tarefa (assinatura pública, endpoint, DTO) é passado no prompt das dependentes, "
    "junto com o trecho do grafo relevante.",
    "Uma tarefa que escala ao humano bloqueia apenas seus descendentes; ramos independentes continuam."])

# ================================================================== 8
H1("8. Execução visível passo a passo")
P("Requisito central: o desenvolvedor acompanha <b>tudo</b> enquanto acontece, como no Manus ou no Claude "
  "Code Web — e pode interferir. Para isso, toda fonte de informação vira um evento com o mesmo envelope, "
  "armazenado em ordem e enviado por WebSocket.")
FIG(D.d_eventos(), "Caminho dos eventos até a interface.")
H2("8.1 Envelope do evento")
CODE("""
{
  "v": 1,
  "missionId": "m-42",
  "seq": 318,                          // monotônico por missão; base do replay
  "ts": "2026-09-28T14:03:11.482Z",
  "type": "tool.completed",
  "stepId": "t2.tool.17",              // passo desta linha na timeline
  "parentStepId": "t2",                // agrupa sob a tarefa T2
  "source": "opencode",                // opencode | docker | graphify | aidlc | alien
  "payload": {
    "tool": "bash", "title": "mvn -q test -pl api",
    "status": "failed", "exitCode": 1, "durationMs": 14210,
    "outputRef": "blob:m-42/318"       // saída grande fica fora do evento
  }
}
""", "Exemplo: um comando de teste que falhou dentro da tarefa T2")
H2("8.2 Catálogo de eventos")
TABLE(["Tipo", "Quando", "Na UI"], [
    ["mission.created / mission.state", "Missão aberta; mudança de estado", "Cabeçalho, badge de estado"],
    ["plan.proposed / plan.updated", "DAG proposto ou alterado", "Aba Plano; passos pendentes na timeline"],
    ["step.started / step.completed / step.failed", "Início e fim de qualquer passo", "Linha da timeline com ícone e duração"],
    ["assistant.delta / thinking.delta", "Texto e raciocínio do agente em streaming", "Texto sob o passo (raciocínio recolhível)"],
    ["tool.started / tool.completed", "Tool call do opencode (read, edit, bash, MCP…)", "Sub-linha ↳ com argumentos e resultado"],
    ["terminal.output", "stdout/stderr de comandos", "Aba Terminal ao vivo"],
    ["file.changed / diff.updated", "Arquivo editado; diff consolidado", "Aba Diff com +/− por arquivo"],
    ["graph.updated", "Grafo reindexado; nós tocados", "Aba Grafo com destaque"],
    ["approval.requested / approval.resolved", "Portão AI-DLC, permissão do opencode, plano, entrega", "Cartão de aprovação"],
    ["question.asked / question.answered", "Agente precisa de informação", "Pergunta no chat"],
    ["verification.result", "Pós-condições avaliadas", "Selo verde/vermelho na tarefa"],
    ["budget.updated", "Tokens, tempo, iterações", "Contadores no cabeçalho"],
    ["preview.ready", "Dev server da Toca respondeu", "Aba Preview (iframe)"],
    ["delivery.ready / delivery.applied", "Patch pronto; aplicado na branch", "Resumo final e link da branch/PR"],
], [1.9, 2, 1.9])
H2("8.3 Mapeamento a partir do opencode")
TABLE(["Evento SSE do opencode", "Evento Alien"], [
    ["message.part.updated (parte text)", "assistant.delta"],
    ["message.part.updated (parte reasoning)", "thinking.delta"],
    ["message.part.updated (parte tool: pending/running)", "tool.started"],
    ["message.part.updated (parte tool: completed/error)", "tool.completed (+ terminal.output para bash)"],
    ["file.edited / session.diff", "file.changed / diff.updated"],
    ["permission.* (pedido de permissão)", "approval.requested"],
    ["todo.updated", "plan.updated (sub-passos da tarefa)"],
    ["session.idle", "gatilho da verificação da tarefa"],
    ["session.error", "step.failed"],
], [3, 2.6])
P("Nomes de eventos devem ser validados contra a versão fixada do opencode; o adaptador isola essas "
  "diferenças do restante do sistema (mesma estratégia do plugin adaptador do AI-DLC).", "small")
H2("8.4 Interface", need=125)
FIG(D.d_wireframe(), "Wireframe da tela da missão: conversa, timeline de execução e painel com abas.")
UL(["<b>Timeline:</b> uma linha por passo (✓ concluído, ● rodando, ○ pendente, ✕ falhou), com duração; "
    "tool calls aparecem como sub-linhas recolhíveis. Clicar num passo foca o terminal/diff daquele passo.",
    "<b>Intervenções:</b> Parar (Esc) aborta a sessão atual; mandar mensagem durante a execução adiciona "
    "orientação à tarefa corrente (herdado da V1: interromper e substituir a rodada); aprovar/negar cartões.",
    "<b>Reconexão:</b> ao reabrir a aba, o cliente envia lastSeq e recebe o que perdeu — a missão continua "
    "rodando no servidor independente do navegador.",
    "<b>Replay:</b> missões terminadas podem ser reproduzidas a partir do Event Store (útil para estudo e demo)."])

# ================================================================== 9
H1("9. Fluxo principal e estados")
H2("9.1 Diagrama de sequência", need=180)
FIG(D.d_sequencia(), "Missão multi-repo do pedido à entrega. Setas tracejadas são eventos/respostas.")
H2("9.2 Máquina de estados da missão", need=120)
FIG(D.d_estados(), "Estados da missão. Transições são decididas pelo orquestrador, nunca pelo modelo. FALHOU, CANCELADA e ENTREGUE terminam em DESCARTADA (a Toca é removida).")
H2("9.3 Estados da tarefa")
P("Cada tarefa do DAG tem seu próprio ciclo: <b>PENDENTE → PRONTA → EXECUTANDO ⇄ VERIFICANDO → VERDE</b>, "
  "com saídas <b>AGUARDANDO_HUMANO</b> (portão, permissão ou pergunta), <b>ESCALADA</b> (halting), "
  "<b>BLOQUEADA</b> (predecessor escalado/falhou) e <b>CANCELADA</b>. A missão só vai para VERIFICANDO quando "
  "todas as tarefas estão VERDE.")

# ================================================================== 10
H1("10. Modelo de domínio e API")
H2("10.1 Entidades")
TABLE(["Entidade", "Campos principais"], [
    ["Mission", "id, título, pedido original, perfil (feature/bugfix/…), modo AI-DLC (full/lite), estado, "
     "repos[], modelo, orçamento, criadaEm, tocaId"],
    ["RepoRef", "caminho local, commit base, branch de entrega, incluir não-commitados?"],
    ["TaskGraph", "tarefas[], arestas[] (de → para, motivo: nós e relação do grafo)"],
    ["Task", "id, repo, objetivo, contrato (produz/consome), arquivos prováveis, pós-condições[], estado, "
     "sessionId opencode, iterações"],
    ["PostCondition", "comando, diretório, timeout, obrigatória?"],
    ["Step", "id, parentId, tipo, título, estado, início/fim"],
    ["AlienEvent", "missionId, seq, ts, type, stepId, parentStepId, source, payload"],
    ["Approval", "id, tipo (plano, permissão, portão, entrega), pergunta, opções, resposta, quem, quando"],
    ["Delivery", "patches por repo, resumo, graph diff, auditoria AI-DLC, verificação final, branch/PR"],
], [1.2, 4.2])
H2("10.2 API REST")
TABLE(["Método e rota", "Descrição"], [
    ["POST /api/missions", "Abre missão a partir do chat (pedido, repos ou template, modelo, modo)"],
    ["GET /api/missions/{id}", "Snapshot: estado, plano, orçamento, última seq"],
    ["POST /api/missions/{id}/plan/approve", "Aprova (ou edita e aprova) o DAG"],
    ["POST /api/missions/{id}/approvals/{aid}", "Responde aprovação: allow, deny, allow-always, texto"],
    ["POST /api/missions/{id}/messages", "Orientação do usuário para a tarefa corrente"],
    ["POST /api/missions/{id}/stop", "Para a tarefa corrente (abort) ou a missão inteira"],
    ["POST /api/missions/{id}/delivery/approve", "Aplica patches nas branches locais; opcional: abrir PR"],
    ["GET /api/missions/{id}/blobs/{ref}", "Saídas grandes (logs, diffs) referenciadas por eventos"],
    ["GET /api/repos · GET /api/templates · GET /api/models", "Catálogos para abrir missão"],
], [2.2, 3.4])
H2("10.3 WebSocket")
CODE("""
WS /ws/missions/{id}?lastSeq=317        // servidor → cliente: envelopes AlienEvent (seq > 317)
cliente → servidor:
  {"type":"user_message", "content":"use BigDecimal, não double"}
  {"type":"stop"}
  {"type":"approval", "approvalId":"a-9", "decision":"allow"}
""", "Canal de eventos e comandos da missão (compatível com o estilo da V1)")

# ================================================================== 11
H1("11. Requisitos")
H2("11.1 Funcionais")
TABLE(["ID", "Requisito", "Prior."], [
    ["RF-01", "Conversar com o Alien em streaming e abrir uma missão a partir da conversa.", "Alta"],
    ["RF-02", "Abrir missão sobre um ou mais repositórios locais existentes, escolhendo branch/commit base.", "Alta"],
    ["RF-03", "Abrir missão de projeto novo a partir de template ou vazio.", "Alta"],
    ["RF-04", "Criar uma Toca isolada por missão e destruí-la ao final, falha, cancelamento ou TTL.", "Alta"],
    ["RF-05", "Executar tarefas com opencode + modelo do Ollama dentro da Toca.", "Alta"],
    ["RF-06", "Mostrar cada passo ao vivo: estado, raciocínio, tool calls, terminal, diffs.", "Alta"],
    ["RF-07", "Permitir parar, orientar, responder perguntas e aprovar/negar permissões durante a execução.", "Alta"],
    ["RF-08", "Verificar cada tarefa com pós-condições executáveis fora do controle do modelo.", "Alta"],
    ["RF-09", "Aplicar halting (iterações, tokens, tempo, sem progresso) e escalar ao humano.", "Alta"],
    ["RF-10", "Entregar patch aprovado em branch local alien/&lt;missão&gt;; PR opcional pelo host.", "Alta"],
    ["RF-11", "Construir o grafo (graphify) e expô-lo ao agente via MCP.", "Média"],
    ["RF-12", "Gerar DAG de tarefas a partir do grafo, incluindo dependências entre repositórios.", "Média"],
    ["RF-13", "Executar tarefas independentes em paralelo (worktrees) e integrá-las.", "Média"],
    ["RF-14", "Usar perfis, portões e sensores do AI-DLC (modo full) ou princípios (modo lite).", "Média"],
    ["RF-15", "Reconectar sem perder eventos e reproduzir missões antigas (replay).", "Média"],
    ["RF-16", "Preview do app rodando na Toca.", "Baixa"],
    ["RF-17", "Registrar correções humanas como regras candidatas (aprendizado) com aprovação.", "Baixa"],
], [0.6, 4.4, 0.6])
H2("11.2 Não funcionais")
TABLE(["ID", "Requisito"], [
    ["RNF-01", "Toca pronta (provisionar + semear + preparar) em até 15 s com imagem já baixada, sem contar a indexação."],
    ["RNF-02", "Latência evento → tela abaixo de 300 ms no mesmo host."],
    ["RNF-03", "Nenhuma credencial do desenvolvedor dentro da Toca; repo original nunca montado."],
    ["RNF-04", "Nenhum container órfão: reconciliador na inicialização remove Tocas sem missão ativa (por label)."],
    ["RNF-05", "Event Store durável: reiniciar o servidor não perde a história da missão."],
    ["RNF-06", "Tudo funciona offline, exceto download opcional de dependências via proxy com allowlist."],
    ["RNF-07", "Versões fixadas de opencode, AI-DLC e graphify na imagem; atualização é decisão explícita."],
    ["RNF-08", "Cobertura de testes do domínio e casos de uso ≥ 80%; testes de integração com Testcontainers."],
], [0.7, 5])

# ================================================================== 12
H1("12. Segurança")
P("O agente executa comandos gerados por um modelo. Tratamos tudo o que sai do modelo como <b>não "
  "confiável</b>, como na V1 (\"o revisor não depende do que o modelo afirmou\").")
TABLE(["Ameaça", "Mitigação"], [
    ["Comando destrutivo ou exfiltração", "Toca sem rede (exceto Ollama/proxy com allowlist), não-root, sem "
     "capabilities, sem credenciais; bash em modo <i>ask</i> exceto lista permitida."],
    ["Prompt injection vindo do código/docs do repo", "Permissões do opencode + aprovação humana para comandos "
     "fora da lista; conteúdo do repo nunca vira instrução do orquestrador."],
    ["Patch malicioso ou com segredos", "Verificação final pelo orquestrador; varredura de segredos (gitleaks) "
     "no patch; revisão humana obrigatória antes do git am."],
    ["Alteração no repo original", "Repo original nunca montado; entrega só via git am em branch nova, nunca na "
     "branch atual; working tree sujo bloqueia a aplicação."],
    ["Exposição da API do opencode", "Senha aleatória por Toca; portas publicadas apenas em 127.0.0.1."],
    ["Esgotar recursos do host", "Limites de CPU/memória/PIDs/disco; TTL; reconciliador de órfãos."],
    ["Cadeia de suprimentos da imagem", "Versões fixadas, checksums no Dockerfile, imagem construída localmente."],
], [1.6, 4])

# ================================================================== 13
H1("13. Estrutura do projeto e stack")
CODE("""
alien-code/
├── alien-server/                  Java 21 · Spring Boot 3 · Spring AI (Clean Architecture)
│   └── src/main/java/dev/aliencode/
│       ├── core/mission/{domain,usecase,application}
│       ├── core/planning/          TaskGraph, planejador, contratos
│       ├── core/events/            AlienEvent, EventStorePort
│       └── adapters/
│           ├── web/                REST + WebSocket
│           ├── sandbox/docker/     DockerSandboxAdapter (docker-java)
│           ├── harness/opencode/   OpencodeHttpAdapter + SSE → AlienEvent
│           ├── graph/graphify/     GraphifyCliAdapter (docker exec)
│           ├── git/                GitCliAdapter (entrega no host)
│           ├── persistence/sqlite/ Event Store, missões
│           └── ai/ollama/          conversa e planejamento
├── alien-web/                     React 18 · TypeScript · Vite
│   └── src/{chat,timeline,panels/{terminal,diff,graph,preview,plan},approvals}
├── toca/                          imagem do ambiente efêmero
│   ├── Dockerfile
│   ├── toca-entrypoint.sh
│   ├── opencode.base.json
│   └── templates/                 spring-boot-api, react-vite, python-cli
├── infra/
│   └── docker-compose.yml         ollama-proxy, rede alien-net, pkg-proxy opcional
└── docs/
    ├── alien-code-especificacao.pdf
    └── pdf/                        gerador deste documento
""", "Monorepo proposto")
TABLE(["Camada", "Escolha", "Por quê"], [
    ["Backend", "Java 21, Spring Boot 3, Spring AI, Spring WebSocket", "Continuidade com a V1; virtual threads para SSE"],
    ["Frontend", "React 18, TypeScript, Vite", "Mesma base da V1"],
    ["Persistência", "SQLite (Event Store + missões)", "Local, sem servidor, durável"],
    ["Containers", "Docker Engine API via docker-java", "Controle fino de limites e labels"],
    ["Harness", "opencode (modo servidor)", "HTTP + SSE, Ollama, suporte AI-DLC e graphify"],
    ["Metodologia", "AI-DLC 2.x (harness opencode)", "Perfis, portões, sensores, auditoria"],
    ["Grafo", "graphify (tree-sitter, MCP)", "Local, determinístico, multi-repo"],
    ["Modelos", "Ollama no host (ex.: qwen3-coder, devstral, gpt-oss)", "Abertos e locais"],
    ["Testes", "JUnit 5, Testcontainers, Vitest, Playwright", "Unidade, integração com Toca real, e2e da timeline"],
], [1, 2.4, 2.2])
CODE("""
alien:
  model:
    chat: ollama/qwen3:14b
    coder: ollama/qwen3-coder:30b
  toca:
    image: alien/toca:0.1
    cpus: 4
    memory: 6g
    ttl: 60m
    network: allowlist            # none | allowlist
    parallel-tasks: 2
  halting:
    max-iterations: 8
    max-tokens: 400000
    task-timeout: 20m
    no-progress-limit: 3
  aidlc:
    mode: lite                    # full | lite
  graph:
    affected-depth: 2
""", "application.yml (valores iniciais)")

# ================================================================== 14
H1("14. Plano de entrega")
FIG(D.d_roadmap(), "Marcos de entrega. Cada marco termina com algo demonstrável.")
TABLE(["Marco", "Critério de pronto"], [
    ["M0 · Esqueleto", "Monorepo criado; imagem alien/toca sobe opencode serve; Alien Server cria e destrói a "
     "Toca; teste de integração com Testcontainers."],
    ["M1 · Timeline ao vivo", "Missão com 1 repo e 1 tarefa; SSE do opencode vira eventos; UI mostra passos, tools "
     "e terminal; Parar funciona; reconexão com lastSeq."],
    ["M2 · Entrega segura", "Pós-condições executadas pelo orquestrador; halting; aprovação de permissões; patch "
     "aplicado em branch local; gitleaks."],
    ["M3 · Grafo", "graphify na Toca; MCP no opencode; testes afetados; aba Grafo; graph diff na entrega."],
    ["M4 · Multi-repo + DAG", "Grafo global; planejador gera DAG; worktrees paralelas; contratos entre tarefas."],
    ["M5 · AI-DLC completo", "Modo full com perfis e portões na UI; auditoria anexada; aprendizado com aprovação."],
], [1.3, 4.3])

# ================================================================== 15
H1("15. Riscos, decisões e glossário")
H2("15.1 Riscos")
TABLE(["Risco", "Impacto", "Resposta"], [
    ["Modelos locais pequenos não sustentam tarefas longas ou o AI-DLC completo", "Alto",
     "Modo lite; tarefas pequenas no DAG; grafo via MCP para poupar contexto; modelo por missão."],
    ["Hardware do dev insuficiente (GPU/RAM) para modelo + builds", "Alto",
     "Paralelismo 1 por padrão; limites da Toca; Ollama pode apontar para outra máquina da rede."],
    ["Mudanças na API/eventos do opencode entre versões", "Médio",
     "Versão fixada; adaptador isolado; testes de contrato contra /doc."],
    ["Inferência do graphify errada (aresta INFERRED/AMBIGUOUS)", "Médio",
     "DAG exige aprovação humana; arestas AMBIGUOUS destacadas no plano."],
    ["Conflitos ao integrar worktrees paralelas", "Médio", "Paralelismo só entre tarefas sem aresta; conflito vira tarefa de integração."],
    ["Build sem rede falha por dependência nova", "Médio", "Proxy de pacotes com allowlist e aprovação."],
], [2.3, 0.6, 2.7])
H2("15.2 Decisões em aberto")
UL(["Usar o SDK JS do opencode num sidecar Node ou consumir HTTP/SSE direto do Java (proposta: Java direto).",
    "Uma Toca por missão (proposta) ou Toca reutilizável por repositório com snapshot.",
    "Guardar o grafo global da missão na Toca (proposta) ou manter um global persistente no host entre missões.",
    "Abrir PR pelo host automaticamente ou só oferecer o comando (proposta: só oferecer)."])
H2("15.3 Glossário")
TABLE(["Termo", "Significado"], [
    ["Missão", "Unidade de trabalho pedida pelo usuário; tem plano, Toca, eventos e entrega."],
    ["Toca", "Container efêmero de uma missão, onde o agente trabalha."],
    ["Tarefa", "Nó do DAG; executada por uma sessão do opencode e verificada por pós-condições."],
    ["DAG", "Grafo acíclico dirigido que define a ordem e o paralelismo das tarefas."],
    ["Harness", "Programa que executa o loop do agente (aqui, o opencode)."],
    ["Pós-condição", "Verificação executável que decide se uma tarefa está correta."],
    ["Halting", "Condição de parada do loop autocorretivo; ao atingir, escala ao humano."],
    ["Portão", "Ponto em que o fluxo espera aprovação humana."],
    ["affected", "Consulta do graphify que devolve o raio de impacto de um símbolo."],
    ["MCP", "Model Context Protocol; forma padrão de expor ferramentas (como o grafo) ao agente."],
], [1, 4.4])

doc.multiBuild(story)
print(f"PDF gerado: {OUT}")
