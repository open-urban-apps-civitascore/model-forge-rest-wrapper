package de.openurbanapps.mfwrapper;

import de.civitascore.modelforge.contract.ModelForgeException;
import de.civitascore.modelforge.contract.RegistryUnavailableException;
import de.civitascore.modelforge.contract.ValidationFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps facade exceptions onto the old REST's status-code vocabulary the
 * marketplace error handling expects (4xx with a JSON error body).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<String> validation(ValidationFailedException e) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(RegistryUnavailableException.class)
    public ResponseEntity<String> registry(RegistryUnavailableException e) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(ModelForgeException.class)
    public ResponseEntity<String> modelForge(ModelForgeException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<String> error(HttpStatus status, String message) {
        String safe = message == null ? "" : message.replace("\"", "'").replace("\n", " ");
        return ResponseEntity.status(status)
                .header("Content-Type", "application/json")
                .body("{\"error\":\"" + safe + "\"}");
    }
}
