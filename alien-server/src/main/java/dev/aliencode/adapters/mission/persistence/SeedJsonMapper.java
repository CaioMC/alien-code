package dev.aliencode.adapters.mission.persistence;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.aliencode.core.toca.domain.model.RepositorySeed;
import dev.aliencode.core.toca.domain.model.Seed;

/** A semeadura da missão como JSON na coluna {@code mission.seed}. */
@Component
class SeedJsonMapper {

    private static final String EXISTING = "existing";
    private static final String NEW = "new";

    private final ObjectMapper json;

    SeedJsonMapper(ObjectMapper json) {
        this.json = json;
    }

    String toJson(Seed seed) {
        ObjectNode node = this.json.createObjectNode();

        switch (seed) {
            case Seed.ExistingRepositories(List<RepositorySeed> repositories) -> {
                node.put("type", EXISTING);

                ArrayNode array = node.putArray("repositories");

                for (RepositorySeed repository : repositories) {
                    array.addObject()
                            .put("name", repository.name())
                            .put("path", repository.path().toString())
                            .put("ref", repository.ref());
                }
            }
            case Seed.NewProject(String name) -> node.put("type", NEW).put("name", name);
        }

        return node.toString();
    }

    Seed fromJson(String text) {
        try {
            JsonNode node = this.json.readTree(text);

            if (NEW.equals(node.path("type").asText())) {
                return new Seed.NewProject(node.path("name").asText());
            }

            List<RepositorySeed> repositories = new ArrayList<>();

            for (JsonNode repository : node.path("repositories")) {
                repositories.add(new RepositorySeed(
                        repository.path("name").asText(),
                        Path.of(repository.path("path").asText()),
                        repository.path("ref").asText(null)
                ));
            }

            return new Seed.ExistingRepositories(repositories);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Semeadura gravada inválida: " + text, e);
        }
    }
}
