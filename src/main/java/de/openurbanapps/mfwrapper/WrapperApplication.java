package de.openurbanapps.mfwrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Thin REST host around the embedded Model Forge facade.
 *
 * <p>Model Forge removed its standalone REST service (commit e12db23) in the pivot
 * to an embedded Java library; the planned host (portal-backend) has not adopted it
 * yet. This wrapper re-exposes exactly the small pre-pivot HTTP surface the
 * marketplace add-on prototype consumes, mapping it onto the embedded facade
 * ({@code de.civitascore.modelforge.facade.ModelForge}).
 *
 * <p>It doubles as an anti-corruption layer: the marketplace keeps speaking the
 * pre-ADR-14 vocabulary ({@code datastructure} URNs, deterministic dataset ids,
 * labels), and the wrapper translates to the current embedded model (element
 * URNs, minted ids, labels stored in the dataset manifest document).
 *
 * <p>Development aid only — not a deployment artifact.
 */
@SpringBootApplication
public class WrapperApplication {

    public static void main(String[] args) {
        SpringApplication.run(WrapperApplication.class, args);
    }
}
