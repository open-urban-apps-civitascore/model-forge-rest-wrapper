package de.openurbanapps.mfwrapper;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ArtifactSearchQuery;
import de.civitascore.modelforge.contract.ArtifactSummary;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Dataset lookup on top of the facade. The marketplace addresses datasets by the
 * old deterministic URN; the embedded registry knows them by its minted URN.
 * The stored manifest carries the old URN as {@code aliasId}, and this service
 * resolves either identity by scanning the (small) dataset population — a
 * prototype-scale trade-off, documented in the README.
 */
@Service
public class DataSetService {

    /** A stored dataset: the registry's identity plus the manifest content. */
    public record StoredDataSet(ArtifactId registryId, ObjectNode content) {}

    private final ModelForge modelForge;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataSetService(ModelForge modelForge) {
        this.modelForge = modelForge;
    }

    public List<StoredDataSet> listAll() {
        List<ArtifactSummary> candidates = modelForge.search(
                new ArtifactSearchQuery(null, "dataset", null, 1000, 0));
        if (candidates.isEmpty()) {
            // Type-token mismatch fallback: scan everything and filter by URN.
            candidates = modelForge.search(new ArtifactSearchQuery(null, null, null, 1000, 0)).stream()
                    .filter(summary -> summary.artifactId().value().contains(":dataset:"))
                    .toList();
        }

        List<StoredDataSet> result = new ArrayList<>();
        for (ArtifactSummary summary : candidates) {
            String logical = summary.logicalId() != null ? summary.logicalId() : summary.artifactId().value();
            modelForge.getArtifact(new ArtifactId(logical)).ifPresent(view -> {
                if (view.content() instanceof ObjectNode content) {
                    result.add(new StoredDataSet(new ArtifactId(logical), content));
                }
            });
        }
        return result;
    }

    /** Resolve by alias (old deterministic URN) or by the registry's own URN. */
    public Optional<StoredDataSet> resolve(String id) {
        String wanted = stripVersion(id);
        return listAll().stream()
                .filter(ds -> matches(ds, id, wanted))
                .findFirst();
    }

    private boolean matches(StoredDataSet ds, String exact, String logicalWanted) {
        String alias = ds.content().path("aliasId").asText(null);
        String contentId = ds.content().path("id").asText(null);
        String registry = ds.registryId().value();
        return exact.equals(alias) || exact.equals(contentId) || exact.equals(registry)
                || logicalWanted.equals(stripVersion(alias))
                || logicalWanted.equals(stripVersion(registry));
    }

    /** `urn:…:Name:1.0.0` → `urn:…:Name`; leaves version-free URNs untouched. */
    static String stripVersion(String urn) {
        if (urn == null) {
            return "";
        }
        return urn.replaceAll(":(\\d+\\.\\d+(\\.\\d+)?|latest)$", "");
    }

    public ObjectNode newObject() {
        return mapper.createObjectNode();
    }

    public tools.jackson.databind.node.ArrayNode newArray() {
        return mapper.createArrayNode();
    }

    public JsonNode parse(String json) {
        return mapper.readTree(json);
    }
}
