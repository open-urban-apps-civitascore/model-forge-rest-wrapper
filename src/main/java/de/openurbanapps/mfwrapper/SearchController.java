package de.openurbanapps.mfwrapper;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/**
 * Pre-pivot label search, e.g.
 * {@code GET /api/v1/artifacts/search?type=dataset&label=civitas:origin=marketplace}.
 * The embedded facade has no labels; the wrapper stores them inside the dataset
 * manifest and filters here. The marketplace only reads each hit's {@code id}.
 */
@RestController
public class SearchController {

    private final DataSetService dataSets;

    public SearchController(DataSetService dataSets) {
        this.dataSets = dataSets;
    }

    @GetMapping(value = "/api/v1/artifacts/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public String search(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam("label") String label) {
        if (type != null && !"dataset".equals(type.toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "label search is only supported for type=dataset");
        }

        int eq = label.indexOf('=');
        if (eq <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "label must be <key>=<value>");
        }
        String key = label.substring(0, eq);
        String value = label.substring(eq + 1);

        ArrayNode hits = dataSets.newArray();
        for (DataSetService.StoredDataSet stored : dataSets.listAll()) {
            JsonNode labels = stored.content().path("labels");
            if (value.equals(labels.path(key).asText(null))) {
                String id = stored.content().path("aliasId").asText(stored.registryId().value());
                hits.addObject().put("id", id);
            }
        }
        return hits.toString();
    }
}
