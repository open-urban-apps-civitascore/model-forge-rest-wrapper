package de.openurbanapps.mfwrapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The anti-corruption core: translates between the marketplace's pre-ADR-14
 * vocabulary and the embedded Model Forge's current one.
 *
 * <ul>
 *   <li><b>URN type token:</b> the marketplace says {@code :datastructure:} for a
 *       single JSON-Schema artifact; the embedded model calls that an
 *       {@code :element:} (ADR-14 renamed the old DataStructure to Element). The
 *       translation is applied to whole JSON documents at the boundary, so
 *       {@code $id} and {@code $ref} stay consistent with each other.</li>
 *   <li><b>Dataset identity:</b> the pre-pivot service minted a deterministic URN
 *       from the dataset title, which the catalog's {@code modelForge.datasetId}
 *       relies on (idempotent installs). The embedded facade mints
 *       {@code sanitize(name)-<uuid>} instead. The wrapper therefore mints the
 *       old-style URN itself and keeps it as an {@code aliasId} inside the stored
 *       manifest, exposing it as the dataset's {@code id} to the marketplace.</li>
 * </ul>
 */
@Component
public class Vocabulary {

    private final String scope;
    private final String owner;
    private final String domain;
    private final String version;

    public Vocabulary(
            @Value("${wrapper.urn.scope}") String scope,
            @Value("${wrapper.urn.owner}") String owner,
            @Value("${wrapper.urn.domain}") String domain,
            @Value("${wrapper.urn.version}") String version) {
        this.scope = scope;
        this.owner = owner;
        this.domain = domain;
        this.version = version;
    }

    /** marketplace → embedded: {@code :datastructure:} becomes {@code :element:}. */
    public String toEmbedded(String text) {
        return text == null ? null : text.replace(":datastructure:", ":element:");
    }

    /** embedded → marketplace: {@code :element:} becomes {@code :datastructure:}. */
    public String toMarketplace(String text) {
        return text == null ? null : text.replace(":element:", ":datastructure:");
    }

    /**
     * Deterministic old-style dataset URN from the display title — replicates the
     * pre-pivot minting so catalog {@code datasetId}s keep matching: any run of
     * non-alphanumeric characters becomes a single hyphen.
     * "TrafficCounter Mittelerde" → {@code urn:core:platform:civitas:dataset:common:TrafficCounter-Mittelerde:1.0.0}
     */
    public String datasetAlias(String title) {
        String name = title.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "urn:core:%s:%s:dataset:%s:%s:%s".formatted(scope, owner, domain, name, version);
    }
}
