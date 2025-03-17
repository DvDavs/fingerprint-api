package com.example.fingerprint_api.controller;

import com.digitalpersona.uareu.UareUException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.logging.Logger;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

    /**
     * Maneja las excepciones de tipo UareUException y devuelve una respuesta
     * HTTP con el código de error y mensaje descriptivo.
     */
    @ExceptionHandler(UareUException.class)
    public ResponseEntity<String> handleUareUException(UareUException ex) {
        logger.severe("UareUException ocurrida: Código " + ex.getCode() + " - " + ex.toString());
        String errorMessage = "UareUException ocurrida: Código " + ex.getCode() + " - " + ex.toString();
        return new ResponseEntity<>(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Maneja cualquier otra excepción no capturada específicamente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        logger.severe("Excepción inesperada: " + ex.getMessage());
        String errorMessage = "Ocurrió un error inesperado: " + ex.getMessage();
        return new ResponseEntity<>(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
