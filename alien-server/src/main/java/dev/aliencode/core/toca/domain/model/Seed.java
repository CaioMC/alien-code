package dev.aliencode.core.toca.domain.model;

import java.util.HashSet;
import java.util.List;

/** O que a Toca recebe em /workspace ao ser criada. */
public sealed interface Seed {

    /** Um ou mais repositórios locais já existentes (missão multi-repo). */
    record ExistingRepositories(List<RepositorySeed> repositories) implements Seed {

        public ExistingRepositories {
            if (repositories == null || repositories.isEmpty()) {
                throw new IllegalArgumentException("Informe ao menos um repositório para semear a Toca");
            }
            repositories = List.copyOf(repositories);
            var names = new HashSet<String>();
            for (RepositorySeed repository : repositories) {
                if (!names.add(repository.name())) {
                    throw new IllegalArgumentException("Nome de repositório repetido: " + repository.name());
                }
            }
        }
    }

    /** Projeto novo: uma pasta vazia com git init. */
    record NewProject(String name) implements Seed {

        public NewProject {
            if (name == null || !RepositorySeed.NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Nome de projeto inválido: '" + name + "'");
            }
        }
    }
}
