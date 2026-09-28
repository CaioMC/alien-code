"""Diagramas vetoriais da especificação do Alien Code.

Cada função devolve um reportlab Drawing com a largura útil da página (W).
Tudo é desenhado em código para que o PDF possa ser regenerado sem
ferramentas externas (mermaid, graphviz...).
"""
from math import atan2, cos, sin

from reportlab.graphics.shapes import Drawing, Group, Line, Polygon, Rect, String, Circle
from reportlab.lib.colors import HexColor, white
from reportlab.pdfbase.pdfmetrics import stringWidth

W = 495  # largura útil (A4 com margens de 50pt)

FONT = "DejaVu"
FONT_B = "DejaVu-Bold"
MONO = "DejaVuMono"

INK = HexColor("#1F2937")
MUTED = HexColor("#6B7280")
LINE = HexColor("#9CA3AF")
PRIMARY = HexColor("#3F3D8F")
PRIMARY_50 = HexColor("#EEF0FB")
ACCENT = HexColor("#D9543F")
ACCENT_50 = HexColor("#FDEDEA")
GREEN = HexColor("#2F855A")
GREEN_50 = HexColor("#E8F5EE")
AMBER = HexColor("#B7791F")
AMBER_50 = HexColor("#FFF6E0")
BLUE = HexColor("#2B6CB0")
BLUE_50 = HexColor("#E6F0FA")
GRAY_50 = HexColor("#F5F6F8")
RED = HexColor("#C53030")

THEMES = {
    "primary": (PRIMARY_50, PRIMARY),
    "accent": (ACCENT_50, ACCENT),
    "green": (GREEN_50, GREEN),
    "amber": (AMBER_50, AMBER),
    "blue": (BLUE_50, BLUE),
    "gray": (GRAY_50, LINE),
    "white": (white, LINE),
}


# ---------------------------------------------------------------- primitivas

def wrap(text, font, size, width):
    lines = []
    for paragraph in text.split("\n"):
        words, cur = paragraph.split(" "), ""
        for w in words:
            cand = (cur + " " + w).strip()
            if stringWidth(cand, font, size) <= width or not cur:
                cur = cand
            else:
                lines.append(cur)
                cur = w
        lines.append(cur)
    return lines


def text(g, x, y, s, size=8, font=FONT, color=INK, anchor="start"):
    g.add(String(x, y, s, fontName=font, fontSize=size, fillColor=color, textAnchor=anchor))


def box(g, x, y, w, h, title=None, body=None, theme="primary", title_size=8.5,
        body_size=7, radius=5, dashed=False, align="middle", title_color=None):
    fill, stroke = THEMES[theme]
    r = Rect(x, y, w, h, rx=radius, ry=radius, fillColor=fill, strokeColor=stroke, strokeWidth=0.9)
    if dashed:
        r.strokeDashArray = [3, 2]
    g.add(r)
    lines = []
    if title:
        for ln in wrap(title, FONT_B, title_size, w - 10):
            lines.append((ln, FONT_B, title_size, title_color or (INK if theme in ("gray", "white") else stroke)))
    if body:
        for ln in wrap(body, FONT, body_size, w - 10):
            lines.append((ln, FONT, body_size, INK))
    total = sum(s + 2.2 for _, _, s, _ in lines)
    if align == "middle":
        cy = y + h / 2 + total / 2 - lines[0][2] + 1 if lines else 0
    else:  # top
        cy = y + h - 5 - (lines[0][2] if lines else 0)
    for ln, f, s, c in lines:
        text(g, x + w / 2, cy, ln, s, f, c, "middle")
        cy -= s + 2.2


def frame(g, x, y, w, h, label, theme="gray", dashed=True):
    fill, stroke = THEMES[theme]
    r = Rect(x, y, w, h, rx=7, ry=7, fillColor=fill, strokeColor=stroke, strokeWidth=0.9)
    if dashed:
        r.strokeDashArray = [4, 3]
    g.add(r)
    text(g, x + 8, y + h - 12, label, 7.5, FONT_B, stroke if theme != "gray" else MUTED)


def arrow(g, pts, color=INK, label=None, label_pos=0.5, dashed=False, width=0.9,
          label_dx=0, label_dy=3, size=7, both=False, label_anchor="middle", label_color=None, label_bg=False):
    for (x1, y1), (x2, y2) in zip(pts, pts[1:]):
        ln = Line(x1, y1, x2, y2, strokeColor=color, strokeWidth=width)
        if dashed:
            ln.strokeDashArray = [3, 2]
        g.add(ln)
    _head(g, pts[-2], pts[-1], color)
    if both:
        _head(g, pts[1], pts[0], color)
    if label:
        # rótulo no segmento mais longo
        segs = list(zip(pts, pts[1:]))
        (x1, y1), (x2, y2) = max(segs, key=lambda s: abs(s[0][0] - s[1][0]) + abs(s[0][1] - s[1][1]))
        lx = x1 + (x2 - x1) * label_pos + label_dx
        ly = y1 + (y2 - y1) * label_pos + label_dy
        for i, ln in enumerate(label.split("\n")):
            if label_bg:
                tw = stringWidth(ln, FONT, size)
                bx = lx - tw / 2 if label_anchor == "middle" else lx if label_anchor == "start" else lx - tw
                g.add(Rect(bx - 2, ly - i * (size + 1.5) - 2, tw + 4, size + 3, fillColor=white, strokeColor=None))
            text(g, lx, ly - i * (size + 1.5), ln, size, FONT, label_color or MUTED, label_anchor)


def _head(g, p1, p2, color, L=5.5, Wd=2.8):
    (x1, y1), (x2, y2) = p1, p2
    a = atan2(y2 - y1, x2 - x1)
    bx, by = x2 - L * cos(a), y2 - L * sin(a)
    g.add(Polygon([x2, y2, bx + Wd * sin(a), by - Wd * cos(a), bx - Wd * sin(a), by + Wd * cos(a)],
                  fillColor=color, strokeColor=color, strokeWidth=0.5))


def pill(g, x, y, s, theme="accent", size=6.5, pad=10):
    fill, stroke = THEMES[theme]
    w = stringWidth(s, FONT_B, size) + pad
    g.add(Rect(x, y, w, size + 6, rx=(size + 6) / 2, ry=(size + 6) / 2, fillColor=fill,
               strokeColor=stroke, strokeWidth=0.7))
    text(g, x + w / 2, y + 3.2, s, size, FONT_B, stroke, "middle")
    return w


# ---------------------------------------------------------------- diagramas

def d_v1_v2():
    d = Drawing(W, 250)
    g = Group()
    # V1
    frame(g, 0, 0, 238, 250, "V1 — execução em pipeline (coding-agent)", "accent")
    box(g, 14, 196, 100, 30, "Chat", "poc-websocket-demo", "white")
    box(g, 124, 196, 100, 30, "Issue no GitHub", "a tarefa", "white")
    box(g, 14, 118, 210, 58, "Runner do GitHub Actions", None, "white", align="top")
    box(g, 24, 124, 90, 30, "container sem rede", "repo A", "gray", body_size=6.5, title_size=7)
    box(g, 124, 124, 90, 30, "outro runner", "repo B (não enxerga A)", "gray", body_size=6.5, title_size=7, dashed=True)
    box(g, 70, 64, 100, 30, "PR em rascunho", "a entrega", "white")
    arrow(g, [(114, 211), (124, 211)])
    arrow(g, [(174, 196), (174, 176)])
    arrow(g, [(119, 118), (119, 94)])
    notes = ["precisa de um pool de máquinas/runners",
             "tarefas dependentes rodam em pipelines",
             "separadas e não conhecem umas às outras",
             "ciclo de feedback lento (fila + build)"]
    for i, n in enumerate(notes):
        text(g, 14, 44 - i * 10, ("✕ " if i != 2 else "   ") + n, 6.8, FONT, RED)
    # V2
    frame(g, 256, 0, 239, 250, "V2 — Alien Code (local, efêmero)", "green")
    box(g, 270, 196, 100, 30, "Alien Web", "timeline passo a passo", "white")
    box(g, 380, 196, 102, 30, "Alien Server", "orquestrador", "white")
    box(g, 270, 100, 212, 78, "Toca — container efêmero", None, "white", align="top")
    box(g, 278, 136, 64, 24, "opencode", None, "primary", title_size=7)
    box(g, 347, 136, 64, 24, "AI-DLC", None, "primary", title_size=7)
    box(g, 416, 136, 60, 24, "graphify", None, "primary", title_size=7)
    box(g, 278, 106, 198, 24, None, "/workspace: repo A + repo B no mesmo grafo", "gray", body_size=6.5)
    box(g, 320, 60, 112, 28, "Repositório local", "branch alien/<missão>", "white")
    arrow(g, [(370, 211), (380, 211)], both=True)
    arrow(g, [(431, 196), (431, 178)])
    arrow(g, [(376, 100), (376, 88)], label="patch aprovado", label_dx=36, label_dy=-6)
    notes = ["roda na máquina do dev: sem pool",
             "tarefas dependentes na mesma Toca,",
             "ordenadas pelo grafo de dependências",
             "cada passo aparece ao vivo na UI"]
    for i, n in enumerate(notes):
        text(g, 270, 34 - i * 10 + 10, ("✓ " if i != 2 else "   ") + n, 6.8, FONT, GREEN)
    d.add(g)
    return d


def d_arquitetura():
    d = Drawing(W, 410)
    g = Group()
    frame(g, 0, 0, W, 410, "Máquina do desenvolvedor (tudo local)", "gray")
    # Web
    box(g, 14, 346, 180, 42, "Alien Web", "React + TypeScript · chat, timeline de execução, terminal, diff, grafo, preview", "accent")
    # Server
    frame(g, 14, 176, 300, 150, "Alien Server — Java 21 · Spring Boot · Spring AI", "primary", dashed=False)
    mods = [
        ("Conversa", "intenção, contexto, Spring AI + Ollama"),
        ("Orquestrador de Missões", "dono do objetivo, estados, halting"),
        ("Planejador (DAG)", "tarefas ordenadas pelo grafo"),
        ("Hub de Eventos", "normaliza, numera (seq), replay"),
        ("Gerente da Toca", "Docker API: cria, limita, destrói"),
        ("Entrega", "patch, verificação, git am, PR opcional"),
    ]
    for i, (t, b) in enumerate(mods):
        cx = 22 + (i % 2) * 146
        cy = 266 - (i // 2) * 42
        box(g, cx, cy, 138, 36, t, b, "white", title_size=7.5, body_size=6.3)
    # host side
    box(g, 334, 346, 146, 42, "Ollama (host)", "modelos abertos (qwen-coder, devstral…) · única saída de rede da Toca", "amber")
    box(g, 334, 276, 146, 50, "Repositórios locais", "entrada: git clone --local · saída: git am na branch; original nunca montado", "blue")
    box(g, 334, 206, 146, 50, "Docker Engine", "rede interna alien-net, volumes, limites de CPU/memória", "gray")
    box(g, 334, 176, 146, 22, None, "SQLite: missões, eventos, auditoria", "gray", body_size=6.5)
    # Toca
    frame(g, 14, 14, 466, 140, "Toca — container efêmero por missão (imagem alien/toca)", "green", dashed=False)
    box(g, 24, 84, 140, 48, "AI-DLC (.aidlc/)", "perfis, estágios, portões de aprovação, sensores, auditoria", "primary", body_size=6.3)
    box(g, 172, 84, 140, 48, "opencode serve :4096", "harness do agente; HTTP + SSE /event; plugin AI-DLC; MCP graphify", "primary", body_size=6.3)
    box(g, 320, 84, 150, 48, "graphify", "grafo AST local; servidor MCP; affected; global graph", "primary", body_size=6.3)
    box(g, 24, 24, 288, 50, "/workspace", "repo-a/  repo-b/  (git clone --local)  ·  graphify-out/  ·  aidlc-docs/", "white", body_size=6.5)
    box(g, 320, 24, 150, 50, "Toolchains + preview", "JDK, Maven, Node, Python; dev server exposto só em 127.0.0.1", "white", body_size=6.3)
    # setas
    arrow(g, [(104, 346), (104, 326)], label="WebSocket (eventos) + REST", label_dx=58, label_dy=-2, both=True)
    arrow(g, [(334, 360), (264, 360), (264, 326)], label="chat (Spring AI)", label_dx=0, label_dy=4, both=False)
    arrow(g, [(334, 230), (314, 230)], both=True)
    arrow(g, [(334, 300), (314, 300)], both=True)
    arrow(g, [(242, 176), (242, 132)], label="HTTP + SSE", label_dx=5, label_dy=10, both=True, label_anchor="start")
    arrow(g, [(164, 108), (172, 108)], both=True)
    arrow(g, [(312, 108), (320, 108)], both=True)
    arrow(g, [(470, 132), (488, 132), (488, 368), (480, 368)], color=AMBER)
    arrow(g, [(80, 176), (80, 154)], label="docker run/exec/rm", label_dx=5, label_dy=0, label_anchor="start")
    d.add(g)
    return d


def d_ciclo_toca():
    d = Drawing(W, 175)
    g = Group()
    steps = [
        ("1 Provisionar", "docker run da imagem alien/toca; rede alien-net; limites; TTL", "gray"),
        ("2 Semear", "projeto existente: git clone --local · novo: template ou vazio", "blue"),
        ("3 Preparar", "aidlc config --harness opencode; opencode.json; caches", "primary"),
        ("4 Indexar", "graphify update (AST, sem LLM); global add por repo", "primary"),
        ("5 Executar", "tarefas do DAG via opencode; eventos ao vivo", "accent"),
        ("6 Colher", "verificação final; git format-patch; graph diff; auditoria", "green"),
        ("7 Descartar", "docker rm -v; volumes apagados; só o patch sobra", "gray"),
    ]
    bw, gap = 64, 7.5
    for i, (t, b, th) in enumerate(steps):
        x = i * (bw + gap)
        box(g, x, 60, bw, 90, t, b, th, title_size=7.2, body_size=6.2, align="top")
        if i < len(steps) - 1:
            arrow(g, [(x + bw, 105), (x + bw + gap, 105)])
    # faixa inferior
    g.add(Rect(0, 8, W, 36, rx=5, ry=5, fillColor=AMBER_50, strokeColor=AMBER, strokeWidth=0.8))
    text(g, 10, 30, "Falha, cancelamento ou TTL expirado em qualquer passo  →  vai direto para 7 (Descartar).", 7.2, FONT_B, AMBER)
    text(g, 10, 17, "Modo 'manter Toca' (debug): pula o 7 e mantém o container parado por até 24h para inspeção.", 7, FONT, INK)
    d.add(g)
    return d


def d_sequencia():
    H = 470
    d = Drawing(W, H)
    g = Group()
    lanes = [("Dev (UI)", "accent"), ("Alien Server", "primary"), ("Docker/Toca", "gray"),
             ("opencode", "primary"), ("graphify", "primary"), ("Ollama", "amber")]
    xs = [38 + i * 84 for i in range(len(lanes))]
    top = H - 8
    for x, (name, th) in zip(xs, lanes):
        box(g, x - 38, top - 24, 76, 22, name, None, th, title_size=7.5)
        ln = Line(x, top - 24, x, 6, strokeColor=LINE, strokeWidth=0.6)
        ln.strokeDashArray = [2, 2]
        g.add(ln)
    msgs = [
        (0, 1, "\"Adicione desconto no checkout\" (repo-api, repo-web)", False),
        (1, 5, "clarificar intenção / montar missão", False),
        (1, 0, "plan.proposed: perguntas + perfil (feature)", True),
        (0, 1, "respostas + aprovar plano", False),
        (1, 2, "criar Toca, semear repos, preparar", False),
        (2, 4, "graphify update + global add", False),
        (1, 4, "affected(\"PricingClient\") → DAG", False),
        (1, 0, "plan.updated: T1 lib → T2 api → T3 web", True),
        (1, 3, "POST /session + prompt_async (T1)", False),
        (3, 5, "raciocínio + tool calls", False),
        (3, 4, "MCP query_graph / get_neighbors", False),
        (3, 1, "SSE: message.part.updated, file.edited", True),
        (1, 0, "step.*, tool.*, terminal.output, diff (ao vivo)", True),
        (3, 1, "SSE: permission / pergunta do portão", True),
        (1, 0, "approval.requested → Dev aprova", True),
        (1, 2, "verificação final (build + testes afetados)", False),
        (1, 0, "delivery.ready: patch + graph diff", True),
        (0, 1, "aprovar entrega", False),
        (1, 2, "format-patch → git am na branch local; docker rm", False),
    ]
    y = top - 42
    for a, b, label, ret in msgs:
        x1, x2 = xs[a], xs[b]
        off = 2 if x2 > x1 else -2
        arrow(g, [(x1 + off, y), (x2 - off, y)], color=MUTED if ret else INK, dashed=ret)
        tw = stringWidth(label, FONT, 6.4)
        lx = min(max((x1 + x2) / 2, tw / 2 + 2), W - tw / 2 - 2)
        g.add(Rect(lx - tw / 2 - 2, y + 1.5, tw + 4, 8.5, fillColor=white, strokeColor=None))
        text(g, lx, y + 3.5, label, 6.4, FONT, INK, "middle")
        y -= 22.5
    d.add(g)
    return d


def d_estados():
    d = Drawing(W, 290)
    g = Group()
    bw, bh = 90, 30
    X = [0, 135, 270, 405]
    pos = {
        "CRIADA": (X[0], 245), "CLARIFICANDO": (X[1], 245), "PLANO_PROPOSTO": (X[2], 245), "PROVISIONANDO": (X[3], 245),
        "INDEXANDO": (X[3], 170), "EXECUTANDO": (X[2], 170), "AGUARDANDO_HUMANO": (X[1], 170),
        "VERIFICANDO": (X[2], 95), "ENTREGA_PROPOSTA": (X[3], 95), "ENTREGUE": (X[3], 25),
        "ESCALADA": (X[1], 95), "FALHOU": (X[0], 25), "CANCELADA": (X[1], 25), "DESCARTADA": (X[2], 25),
    }
    themes = {"ENTREGUE": "green", "FALHOU": "accent", "CANCELADA": "gray", "ESCALADA": "amber",
              "AGUARDANDO_HUMANO": "amber", "DESCARTADA": "gray", "EXECUTANDO": "accent"}
    for k, (x, y) in pos.items():
        box(g, x, y, bw, bh, k.replace("_", " "), None, themes.get(k, "primary"), title_size=7)

    def c(k, side, off=0):
        x, y = pos[k]
        return {"r": (x + bw, y + bh / 2 + off), "l": (x, y + bh / 2 + off), "t": (x + bw / 2 + off, y + bh),
                "b": (x + bw / 2 + off, y)}[side]

    arrow(g, [c("CRIADA", "r"), c("CLARIFICANDO", "l")])
    arrow(g, [c("CLARIFICANDO", "r"), c("PLANO_PROPOSTO", "l")])
    arrow(g, [c("PLANO_PROPOSTO", "r"), c("PROVISIONANDO", "l")], label="aprovado", label_dy=4, size=6.5)
    arrow(g, [c("PROVISIONANDO", "b"), c("INDEXANDO", "t")])
    arrow(g, [c("INDEXANDO", "l"), c("EXECUTANDO", "r")])
    arrow(g, [c("EXECUTANDO", "l", 7), c("AGUARDANDO_HUMANO", "r", 7)], label="portão", label_dy=3, size=6.5)
    arrow(g, [c("AGUARDANDO_HUMANO", "r", -7), c("EXECUTANDO", "l", -7)], label="resposta", label_dy=-9, size=6.5)
    arrow(g, [c("EXECUTANDO", "b", 20), c("VERIFICANDO", "t", 20)], label="DAG concluído", label_dx=4, label_dy=0,
          label_anchor="start", size=6.5)
    arrow(g, [c("VERIFICANDO", "t", -20), c("EXECUTANDO", "b", -20)], color=MUTED, dashed=True)
    text(g, 290, 148, "falhou e há", 6.3, FONT, MUTED, "end")
    text(g, 290, 140, "orçamento", 6.3, FONT, MUTED, "end")
    arrow(g, [c("VERIFICANDO", "r"), c("ENTREGA_PROPOSTA", "l")], label="verde", label_dy=4, size=6.5)
    arrow(g, [c("ENTREGA_PROPOSTA", "b"), c("ENTREGUE", "t")], label="aprovada", label_dx=4, label_dy=0,
          label_anchor="start", size=6.5)
    arrow(g, [c("VERIFICANDO", "l"), c("ESCALADA", "r")], color=AMBER, label="halting", label_dy=4, size=6.5,
          label_color=AMBER)
    arrow(g, [c("ESCALADA", "t"), c("AGUARDANDO_HUMANO", "b")], color=AMBER, label="pede orientação", label_dx=-4,
          label_dy=0, label_anchor="end", size=6.5, label_color=AMBER)
    arrow(g, [c("ENTREGUE", "l"), c("DESCARTADA", "r")], color=MUTED)
    arrow(g, [c("CANCELADA", "r"), c("DESCARTADA", "l")], color=MUTED)
    arrow(g, [c("FALHOU", "b"), (45, 10), (315, 10), c("DESCARTADA", "b")], color=MUTED)
    text(g, 0, 86, "De qualquer estado ativo:", 6.6, FONT, MUTED)
    text(g, 0, 77, "→ CANCELADA (usuário)", 6.6, FONT, MUTED)
    text(g, 0, 68, "→ FALHOU (erro de infra)", 6.6, FONT, MUTED)
    d.add(g)
    return d


def d_grafo_dag():
    d = Drawing(W, 250)
    g = Group()
    frame(g, 0, 0, 262, 250, "Grafo global (graphify) — 3 repositórios", "blue", dashed=False)
    nodes = {
        "PricingClient": (62, 190, "repo-lib"), "DiscountRule": (200, 190, "repo-lib"),
        "OrderService": (62, 120, "repo-api"), "CheckoutController": (200, 120, "repo-api"),
        "checkoutApi.ts": (62, 50, "repo-web"), "CheckoutPage.tsx": (200, 50, "repo-web"),
    }
    theme_by = {"repo-lib": "primary", "repo-api": "green", "repo-web": "accent"}
    for n, (x, y, r) in nodes.items():
        box(g, x - 48, y - 13, 96, 28, n, r, theme_by[r], title_size=6.8, body_size=5.8)
    E = [("OrderService", "PricingClient", "calls"), ("DiscountRule", "PricingClient", "uses"),
         ("CheckoutController", "OrderService", "calls"), ("checkoutApi.ts", "CheckoutController", "HTTP (INFERRED)"),
         ("CheckoutPage.tsx", "checkoutApi.ts", "imports")]
    for a, b, rel in E:
        (x1, y1, _), (x2, y2, _) = nodes[a], nodes[b]
        if y1 == y2:
            arrow(g, [(x1 - 48 if x1 > x2 else x1 + 48, y1), (x2 + 48 if x1 > x2 else x2 - 48, y2)],
                  label=rel, label_dy=4, size=6)
        else:
            sx = x1 + (6 if x2 > x1 else -6 if x2 < x1 else 0)
            arrow(g, [(x1, y1 + 15), (x2, y2 - 13)], label=rel, label_dx=4 if x1 == x2 else -28,
                  label_anchor="start" if x1 == x2 else "middle", size=6, label_dy=0 if x1 == x2 else 10)
    text(g, 10, 12, "affected(\"PricingClient\") → OrderService, DiscountRule,", 6.4, MONO, INK)
    text(g, 10, 4 + 0, "CheckoutController, checkoutApi.ts, CheckoutPage.tsx", 6.4, MONO, INK)
    # DAG
    frame(g, 276, 0, 219, 250, "DAG de tarefas da missão", "accent", dashed=False)
    box(g, 290, 180, 190, 36, "T1 · repo-lib", "novo método discountFor() em PricingClient", "primary", body_size=6.3)
    box(g, 290, 120, 92, 40, "T2 · repo-api", "OrderService aplica desconto", "green", body_size=6.1, title_size=7)
    box(g, 388, 120, 92, 40, "T4 · repo-lib", "testes de DiscountRule", "primary", body_size=6.1, title_size=7)
    box(g, 290, 60, 190, 40, "T3 · repo-web", "CheckoutPage mostra desconto (contrato de T2)", "accent", body_size=6.3)
    arrow(g, [(336, 180), (336, 160)])
    arrow(g, [(434, 180), (434, 160)])
    arrow(g, [(336, 120), (336, 100)])
    text(g, 290, 40, "T2 e T4 podem rodar em paralelo (worktrees", 6.5, FONT, INK)
    text(g, 290, 31, "separadas); T3 só começa com o contrato de T2", 6.5, FONT, INK)
    text(g, 290, 22, "pronto. O grafo é atualizado depois de cada", 6.5, FONT, INK)
    text(g, 290, 13, "tarefa, então a próxima já enxerga a mudança.", 6.5, FONT, INK)
    d.add(g)
    return d


def d_loop():
    d = Drawing(W, 215)
    g = Group()
    box(g, 0, 150, 92, 44, "Clarificar", "ambiguidade? pergunta ao humano", "amber", body_size=6.3)
    box(g, 104, 150, 92, 44, "Planejar", "passos + pós-condições", "primary", body_size=6.3)
    box(g, 208, 150, 92, 44, "Gerar", "opencode edita e executa", "accent", body_size=6.3)
    box(g, 312, 150, 92, 44, "Verificar", "sensores + build + testes afetados", "primary", body_size=6.3)
    box(g, 416, 150, 79, 44, "Concluir", "tarefa verde", "green", body_size=6.3)
    for x in (92, 196, 300, 404):
        arrow(g, [(x, 172), (x + 12, 172)])
    arrow(g, [(358, 150), (358, 124), (254, 124), (254, 150)], color=ACCENT, label="falhou: devolve a saída do erro (iteração n+1)",
          label_dy=-10, size=6.5, label_color=ACCENT)
    box(g, 208, 40, 196, 50, "Condição de parada (halting)", "máx. iterações (8) · orçamento de tokens · tempo de parede (20 min) · sem progresso 3x",
        "amber", body_size=6.3)
    arrow(g, [(385, 150), (385, 90)], color=AMBER)
    box(g, 416, 40, 79, 50, "Escalar", "pede orientação ao humano na UI", "amber", body_size=6.3)
    arrow(g, [(404, 65), (416, 65)], color=AMBER)
    box(g, 0, 40, 196, 50, "Aprender (compartimento 3)", "correções humanas viram regras candidatas; só entram após aprovação",
        "gray", body_size=6.3)
    arrow(g, [(455, 40), (455, 20), (98, 20), (98, 40)], color=MUTED, dashed=True, label="orientação e correções", label_dy=4, size=6.5)
    d.add(g)
    return d


def d_eventos():
    d = Drawing(W, 175)
    g = Group()
    srcs = [("opencode SSE /event", "partes de mensagem, tools, arquivos, permissões"),
            ("Docker", "logs de provisionamento, exit codes, recursos"),
            ("graphify", "indexação, affected, graph diff"),
            ("AI-DLC", "estágio atual, portões, audit.md")]
    for i, (t, b) in enumerate(srcs):
        box(g, 0, 132 - i * 40, 128, 34, t, b, "gray", title_size=7.3, body_size=6)
        arrow(g, [(128, 149 - i * 40), (160, 149 - i * 40 if i in (1, 2) else 100)], color=MUTED)
    box(g, 160, 60, 96, 80, "Adaptadores + Normalizador", "traduz cada fonte para o envelope Alien; agrupa em passos (stepId)", "primary", body_size=6.2)
    box(g, 272, 60, 96, 80, "Event Store", "append-only (SQLite); seq monotônico por missão; replay na reconexão", "primary", body_size=6.2)
    box(g, 384, 60, 111, 80, "WebSocket /ws/missions", "Alien Web renderiza a timeline, terminal, diff e grafo", "accent", body_size=6.2)
    arrow(g, [(256, 100), (272, 100)])
    arrow(g, [(368, 100), (384, 100)])
    text(g, 272, 40, "Reconectar com lastSeq=N  →  servidor reenvia N+1…atual (sem polling).", 7, FONT, INK)
    text(g, 272, 28, "Mesma ideia de reconexão do poc-websocket-demo (V1).", 7, FONT, MUTED)
    d.add(g)
    return d


def d_wireframe():
    d = Drawing(W, 300)
    g = Group()
    g.add(Rect(0, 0, W, 300, rx=6, ry=6, fillColor=white, strokeColor=INK, strokeWidth=1))
    g.add(Rect(0, 280, W, 20, rx=6, ry=6, fillColor=PRIMARY, strokeColor=PRIMARY))
    g.add(Rect(0, 280, W, 8, fillColor=PRIMARY, strokeColor=PRIMARY))
    text(g, 10, 287, "Alien Code", 8, FONT_B, white)
    text(g, 120, 287, "missão #42 · Desconto no checkout · EXECUTANDO · 07:32 · 18.4k tokens", 7, FONT, white)
    # chat
    g.add(Rect(6, 6, 140, 268, rx=4, ry=4, fillColor=GRAY_50, strokeColor=LINE, strokeWidth=0.6))
    text(g, 12, 262, "Conversa", 7.5, FONT_B, INK)
    bubbles = [("Dev", "Adicione desconto no checkout…", 222, "white"),
               ("Alien", "Entendi. Perfil feature, 3 repos. Duas perguntas: …", 180, "primary"),
               ("Dev", "Percentual, máx. 30%.", 140, "white"),
               ("Alien", "Plano com 4 tarefas pronto. Aprovar?", 100, "primary")]
    for who, msg, y, th in bubbles:
        box(g, 12, y, 128, 34, who, msg, th, title_size=6.3, body_size=5.9)
    g.add(Rect(12, 14, 128, 20, rx=3, ry=3, fillColor=white, strokeColor=LINE, strokeWidth=0.6))
    text(g, 16, 21, "Mensagem… (Enter envia, Esc para)", 6, FONT, MUTED)
    # timeline
    g.add(Rect(152, 6, 170, 268, rx=4, ry=4, fillColor=white, strokeColor=LINE, strokeWidth=0.6))
    text(g, 158, 262, "Execução", 7.5, FONT_B, INK)
    steps = [("✓", "Entender o pedido", "4s", GREEN), ("✓", "Provisionar Toca", "11s", GREEN),
             ("✓", "Indexar grafo (3 repos)", "23s", GREEN), ("✓", "T1 · PricingClient.discountFor", "2m10", GREEN),
             ("●", "T2 · OrderService + desconto", "rodando", ACCENT), ("", "   ↳ read_file OrderService.java", "", MUTED),
             ("", "   ↳ edit OrderService.java (+14 −2)", "", MUTED), ("", "   ↳ bash mvn -q test -pl api", "", MUTED),
             ("●", "T4 · testes DiscountRule", "rodando", ACCENT), ("○", "T3 · CheckoutPage", "aguarda T2", MUTED),
             ("○", "Verificação final", "", MUTED), ("○", "Entrega (aprovação)", "", MUTED)]
    y = 244
    for ic, t, st, col in steps:
        text(g, 160, y, ic, 7, FONT_B, col)
        text(g, 172, y, t, 6.4, FONT if ic == "" else FONT_B, INK if ic else MUTED)
        text(g, 316, y, st, 6, FONT, col, "end")
        y -= 19
    # painel direito
    g.add(Rect(328, 6, 161, 268, rx=4, ry=4, fillColor=white, strokeColor=LINE, strokeWidth=0.6))
    tabs = ["Terminal", "Diff", "Grafo", "Preview", "Plano"]
    x = 332
    for i, t in enumerate(tabs):
        w = pill(g, x, 256, t, "primary" if i == 0 else "gray", 5.3, pad=6)
        x += w + 3
    g.add(Rect(332, 90, 153, 160, rx=3, ry=3, fillColor=HexColor("#111827"), strokeColor=INK))
    term = ["$ mvn -q test -pl api", "[INFO] Running OrderServiceTest", "Tests run: 12, Failures: 1",
            "  expected 90.00 but was 100.00", "… agente lê o erro e corrige", "$ mvn -q test -pl api",
            "Tests run: 12, Failures: 0 ✓"]
    for i, t in enumerate(term):
        text(g, 336, 238 - i * 11, t, 5.9, MONO, HexColor("#D1FAE5") if "✓" in t else HexColor("#E5E7EB"))
    box(g, 332, 14, 153, 68, "Aprovação pendente", "opencode pede para rodar: npm install date-fns (rede via proxy). [Permitir] [Negar]",
        "amber", title_size=7, body_size=6.1)
    d.add(g)
    return d


def d_roadmap():
    d = Drawing(W, 120)
    g = Group()
    ms = [("M0", "Esqueleto", "monorepo, imagem Toca, opencode no container"),
          ("M1", "Timeline ao vivo", "SSE → eventos → UI; 1 repo, 1 tarefa"),
          ("M2", "Entrega segura", "patch, verificação, git am, aprovação"),
          ("M3", "Grafo", "graphify, MCP, affected, testes afetados"),
          ("M4", "Multi-repo + DAG", "global graph, paralelismo, contratos"),
          ("M5", "AI-DLC completo", "perfis, portões, sensores, aprendizado")]
    g.add(Line(10, 80, W - 10, 80, strokeColor=PRIMARY, strokeWidth=2))
    bw = (W - 10) / len(ms)
    for i, (m, t, b) in enumerate(ms):
        x = 5 + i * bw
        g.add(Circle(x + bw / 2, 80, 7, fillColor=ACCENT if i == 0 else PRIMARY, strokeColor=white, strokeWidth=1.5))
        text(g, x + bw / 2, 77.5, m[1], 6.5, FONT_B, white, "middle")
        text(g, x + bw / 2, 96, m, 7.5, FONT_B, PRIMARY, "middle")
        box(g, x + 3, 4, bw - 6, 62, t, b, "white", title_size=7.2, body_size=6.2, align="top")
    d.add(g)
    return d


def d_camadas():
    d = Drawing(W, 150)
    g = Group()
    box(g, 0, 110, W, 34, "adapters (entrada)", "WebSocketMissionHandler · MissionRestController · ApprovalController", "accent", body_size=6.6)
    box(g, 0, 66, W, 34, "core — application + usecase", "StartMission · AnswerQuestion · ApproveGate · ApproveDelivery · CancelMission · MissionConductor (orquestrador)", "primary", body_size=6.6)
    box(g, 0, 22, W, 34, "core — domain + ports", "Mission · Task · TaskGraph · Step · AlienEvent · HaltingPolicy · ports: SandboxPort, AgentHarnessPort, CodeGraphPort, RepositoryPort, EventStorePort, ChatModelPort", "green", body_size=6.6)
    text(g, 0, 10, "adapters (saída): DockerSandboxAdapter · OpencodeHttpAdapter (HTTP+SSE) · GraphifyCliAdapter ·", 6.8, FONT, INK)
    text(g, 0, 1, "GitCliAdapter · SqliteEventStore · OllamaChatAdapter", 6.8, FONT, INK)
    d.add(g)
    return d
