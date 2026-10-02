package dev.aliencode.core.toca.application;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Toca;
import dev.aliencode.core.toca.domain.model.TocaEndpoint;
import dev.aliencode.core.toca.domain.model.TocaId;
import dev.aliencode.core.toca.port.harness.AgentHarnessPort;
import dev.aliencode.core.toca.port.harness.HarnessNotReadyException;
import dev.aliencode.core.toca.port.repository.TocaRepository;
import dev.aliencode.core.toca.port.sandbox.ExecResult;
import dev.aliencode.core.toca.port.sandbox.ManagedSandbox;
import dev.aliencode.core.toca.port.sandbox.SandboxHandle;
import dev.aliencode.core.toca.port.sandbox.SandboxPort;
import dev.aliencode.core.toca.port.sandbox.SandboxRequest;
import dev.aliencode.core.toca.port.workspace.WorkspaceSnapshotPort;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/** Dublês simples das portas da Toca, com registro do que foi chamado. */
final class TocaTestDoubles {

    private TocaTestDoubles() {
    }

    static final class FakeSandbox implements SandboxPort {
        final List<SandboxRequest> created = new ArrayList<>();
        final List<String> copiedTo = new ArrayList<>();
        final List<List<String>> executed = new ArrayList<>();
        final List<String> removed = new ArrayList<>();
        final List<ManagedSandbox> managed = new ArrayList<>();
        ExecResult execResult = new ExecResult(0, "", "");
        /** Resposta por comando; quando nulo, todo comando devolve {@link #execResult}. */
        Function<List<String>, ExecResult> onExec;
        RuntimeException failOnCopy;

        @Override
        public SandboxHandle create(SandboxRequest request) {
            this.created.add(request);
            this.managed.add(new ManagedSandbox("c-" + request.tocaId().value(), request.tocaId().value()));
            return new SandboxHandle("c-" + request.tocaId().value(), "127.0.0.1", 40000 + this.created.size());
        }

        @Override
        public void copyDirectory(String containerId, Path source, String targetDir) {
            if (nonNull(this.failOnCopy)) {
                throw this.failOnCopy;
            }
            this.copiedTo.add(targetDir);
        }

        @Override
        public ExecResult exec(String containerId, List<String> command, Duration timeout) {
            this.executed.add(command);
            return isNull(this.onExec) ? this.execResult : this.onExec.apply(command);
        }

        @Override
        public void remove(String containerId) {
            this.removed.add(containerId);
            this.managed.removeIf(m -> m.containerId().equals(containerId));
        }

        @Override
        public List<ManagedSandbox> listManaged() {
            return List.copyOf(this.managed);
        }
    }

    static final class FakeSnapshots implements WorkspaceSnapshotPort {
        final List<String> snapshotted = new ArrayList<>();
        final List<Path> discarded = new ArrayList<>();

        @Override
        public Path snapshot(RepositorySeed seed) {
            this.snapshotted.add(seed.name());
            return Path.of("/tmp/snap").resolve(seed.name());
        }

        @Override
        public void discard(Path snapshot) {
            this.discarded.add(snapshot);
        }
    }

    static final class FakeHarness implements AgentHarnessPort {
        boolean ready = true;
        TocaEndpoint lastEndpoint;

        @Override
        public String awaitReady(TocaEndpoint endpoint, Duration timeout) {
            this.lastEndpoint = endpoint;
            if (!this.ready) {
                throw new HarnessNotReadyException("opencode não respondeu");
            }
            return "1.18.33";
        }
    }

    static final class InMemoryTocas implements TocaRepository {
        private final ConcurrentHashMap<TocaId, Toca> tocas = new ConcurrentHashMap<>();

        @Override
        public void save(Toca toca) {
            this.tocas.put(toca.id(), toca);
        }

        @Override
        public Optional<Toca> findById(TocaId id) {
            return Optional.ofNullable(this.tocas.get(id));
        }

        @Override
        public List<Toca> findAll() {
            return List.copyOf(this.tocas.values());
        }
    }
}
