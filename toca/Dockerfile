# Imagem da Toca: o ambiente efêmero onde o agente trabalha.
# Construída uma vez na máquina do dev e reaproveitada por todas as missões.
# Versões fixadas de propósito: atualizar é uma decisão explícita (RNF-07).
FROM eclipse-temurin:21-jdk-noble

ARG OPENCODE_VERSION=1.18.33

RUN apt-get update \
 && apt-get install -y --no-install-recommends git curl ca-certificates ripgrep maven nodejs npm python3 \
 && rm -rf /var/lib/apt/lists/*

RUN npm install -g "opencode-ai@${OPENCODE_VERSION}" && npm cache clean --force

# A imagem base já tem o usuário "ubuntu" com uid 1000; trocamos por "alien" com o mesmo uid,
# que é o dono dos arquivos semeados em /workspace.
RUN userdel -r ubuntu && useradd -m -u 1000 -s /bin/bash alien \
 && mkdir -p /workspace && chown alien:alien /workspace

COPY --chmod=755 toca-entrypoint.sh /usr/local/bin/toca-entrypoint.sh
# a pasta antes do COPY: o --chmod=644 também valeria para ela, e pasta sem "x" não se atravessa
RUN install -d -m 755 /etc/alien
COPY --chmod=644 toca-gitignore /etc/alien/gitignore

USER alien
WORKDIR /workspace
ENV OPENCODE_PORT=4096
EXPOSE 4096

ENTRYPOINT ["toca-entrypoint.sh"]
