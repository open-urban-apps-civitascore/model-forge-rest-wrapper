package de.openurbanapps.mfwrapper;

import de.civitascore.modelforge.contract.ArtifactKind;
import de.civitascore.modelforge.contract.CreateArtifactCommand;
import de.civitascore.modelforge.contract.SaveArtifactCommand;
import de.civitascore.modelforge.contract.VersionBump;
import de.civitascore.modelforge.facade.ModelForge;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Pre-pivot dataset surface: POST (shell from a title), PUT/GET/DELETE by
 * {@code ?id=<urn>}. Bodies are handled as strings and parsed with the same
 * Jackson 3 the facade uses, keeping Spring MVC's Jackson 2 out of the picture.
 */
@RestController
@RequestMapping(value = "/api/v1/datasets", produces = MediaType.APPLICATION_JSON_VALUE)
public class DataSetController {

    private static final List<String> REF_FIELDS = List.of(
            "dataStructureRefs", "dataSourceRefs", "dataSinkRefs", "mappingRefs", "pipelineRefs");

    private final ModelForge modelForge;
    private final DataSetService dataSets;
    private final Vocabulary vocabulary;

    public DataSetController(ModelForge modelForge, DataSetService dataSets, Vocabulary vocabulary) {
        this.modelForge = modelForge;
        this.dataSets = dataSets;
        this.vocabulary = vocabulary;
    }

    @GetMapping
    public ResponseEntity<String> get(@RequestParam("id") String id) {
        DataSetService.StoredDataSet stored = dataSets.resolve(id)
                .orElseThrow(() -> notFound(id));
        return ResponseEntity.ok(toMarketplaceShape(stored.content()));
    }

    /** Creates the shell dataset from a title; the marketplace fills it via PUT. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestBody String body) {
        JsonNode request = dataSets.parse(body);
        String title = request.path("title").asText(null);
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }

        String alias = vocabulary.datasetAlias(title);
        if (dataSets.resolve(alias).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "dataset already exists: " + alias);
        }

        ObjectNode content = dataSets.newObject();
        content.put("aliasId", alias);
        content.put("title", title);
        content.put("version", "1.0");
        REF_FIELDS.forEach(field -> content.putArray(field));
        modelForge.createArtifact(new CreateArtifactCommand(ArtifactKind.DATA_SET, title, content));

        // Respond in the old shape: the deterministic alias is the dataset's id.
        ObjectNode shell = content.deepCopy();
        shell.remove("aliasId");
        shell.put("id", alias);
        return ResponseEntity.status(HttpStatus.CREATED).body(shell.toString());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> update(@RequestParam("id") String id, @RequestBody String body) {
        DataSetService.StoredDataSet stored = dataSets.resolve(id)
                .orElseThrow(() -> notFound(id));

        // Store the marketplace's document translated to the embedded vocabulary,
        // keeping the old identity in aliasId (Model Forge re-stamps `id` itself).
        ObjectNode incoming = (ObjectNode) dataSets.parse(vocabulary.toEmbedded(body));
        String alias = stored.content().path("aliasId").asText(id);
        incoming.put("aliasId", alias);
        incoming.remove("id");

        modelForge.saveArtifact(new SaveArtifactCommand(
                stored.registryId(), ArtifactKind.DATA_SET, incoming, VersionBump.PATCH));
        return ResponseEntity.ok(toMarketplaceShape(incoming));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@RequestParam("id") String id) {
        DataSetService.StoredDataSet stored = dataSets.resolve(id)
                .orElseThrow(() -> notFound(id));
        modelForge.deleteArtifact(stored.registryId());
        return ResponseEntity.noContent().build();
    }

    /** Stored manifest → old REST shape: alias as id, vocabulary translated back. */
    private String toMarketplaceShape(ObjectNode content) {
        ObjectNode out = (ObjectNode) dataSets.parse(vocabulary.toMarketplace(content.toString()));
        String alias = out.path("aliasId").asText(null);
        if (alias != null) {
            out.put("id", alias);
        }
        out.remove("aliasId");
        return out.toString();
    }

    private ResponseStatusException notFound(String id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found: " + id);
    }
}
