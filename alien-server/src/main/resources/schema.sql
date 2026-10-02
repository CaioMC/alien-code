-- Executado a cada subida (spring.sql.init.mode=always): só cria o que ainda não existe.

CREATE TABLE IF NOT EXISTS mission (
    id             TEXT PRIMARY KEY,
    title          TEXT NOT NULL,
    prompt         TEXT NOT NULL,
    seed           TEXT NOT NULL,
    model          TEXT NOT NULL,
    status         TEXT NOT NULL,
    toca_id        TEXT,
    session_id     TEXT,
    created_at     TEXT NOT NULL,
    finished_at    TEXT,
    failure_reason TEXT
);

-- Append-only. seq é monotônico por missão e é a base do replay (lastSeq).
CREATE TABLE IF NOT EXISTS mission_event (
    mission_id     TEXT    NOT NULL,
    seq            INTEGER NOT NULL,
    ts             TEXT    NOT NULL,
    type           TEXT    NOT NULL,
    step_id        TEXT,
    parent_step_id TEXT,
    source         TEXT    NOT NULL,
    payload        TEXT    NOT NULL,
    PRIMARY KEY (mission_id, seq)
);

-- Uma entrega por missão: o patch colhido da Toca, esperando o dev aplicar ou descartar.
CREATE TABLE IF NOT EXISTS delivery (
    mission_id      TEXT PRIMARY KEY,
    repository      TEXT NOT NULL,
    repository_path TEXT NOT NULL,
    base_commit     TEXT NOT NULL,
    patch           TEXT NOT NULL,
    files           TEXT NOT NULL,
    status          TEXT NOT NULL,
    branch          TEXT NOT NULL,
    head_commit     TEXT,
    created_at      TEXT NOT NULL,
    resolved_at     TEXT
);
