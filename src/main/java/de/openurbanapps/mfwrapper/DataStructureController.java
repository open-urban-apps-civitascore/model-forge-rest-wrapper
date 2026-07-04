package de.openurbanapps.mfwrapper;

import de.civitascore.modelforge.contract.ArtifactId;
import de.civitascore.modelforge.contract.ImportResult;
import de.civitascore.modelforge.contract.ImportSchemaCommand;
import de.civitascore.modelforge.facade.ModelForge;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/**
 * Pre-pivot datastructure surface. A marketplace "datastructure" (one JSON
 * Schema) is an ADR-14 "element" in the embedded model; the vocabulary is
 * translated on whole documents so {@code $id}/{@code $ref} stay consistent.
 */
@RestController
@RequestMapping(value = "/api/v1/datastructures", produces = MediaType.APPLICATION_JSON_VALUE)
public class DataStructureController {

    private final ModelForge modelForge;
    private final DataSetService json;
    private final Vocabulary vocabulary;

    public DataStructureController(ModelForge modelForge, DataSetService json, Vocabulary vocabulary) {
        this.modelForge = modelForge;
        this.json = json;
        this.vocabulary = vocabulary;
    }

    /** Existence probe — the marketplace only inspects the status code. */
    @GetMapping
    public ResponseEntity<String> get(@RequestParam("id") String id) {
        return modelForge.getArtifact(new ArtifactId(vocabulary.toEmbedded(id)))
                .map(view -> ResponseEntity.ok(vocabulary.toMarketplace(view.content().toString())))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "datastructure not found: " + id));
    }

    /** Import one JSON Schema; responds with the versioned pin as {@code resourceId}. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(@RequestBody String body) {
        JsonNode request = json.parse(vocabulary.toEmbedded(body));
        JsonNode schema = request.path("schema");
        if (schema.isMissingNode() || !schema.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body must carry a 'schema' object");
        }

        ImportResult result = modelForge.importSchema(new ImportSchemaCommand(schema));
        String resourceId = vocabulary.toMarketplace(result.rootArtifactId().value());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("{\"resourceId\":\"" + resourceId + "\"}");
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(
            @RequestParam("id") String id,
            @RequestParam(value = "force", required = false) String force) {
        ArtifactId artifactId = new ArtifactId(vocabulary.toEmbedded(id));
        if (modelForge.getArtifact(artifactId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "datastructure not found: " + id);
        }
        modelForge.deleteArtifact(artifactId);
        return ResponseEntity.noContent().build();
    }
}
