package com.caciopee.loganalyzer.controller;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Gère proprement les uploads dépassant la taille maximale autorisée.
 * Renvoie un 413 JSON explicite plutôt qu'une erreur 500 brute.
 * Déclaré avec une priorité haute pour être choisi avant le handler générique.
 */
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new UploadErrorResponse(
                        "File too large",
                        "La taille maximale d'envoi est dépassée. Compressez vos logs ou "
                        + "importez-les via le chemin de dossier local."));
    }

    public static class UploadErrorResponse {
        public String error;
        public String message;

        public UploadErrorResponse(String error, String message) {
            this.error = error;
            this.message = message;
        }

        public String getError() { return error; }
        public String getMessage() { return message; }
    }
}
