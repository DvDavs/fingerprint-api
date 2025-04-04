package com.example.fingerprint_api.controller;

import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.exception.ResourceNotFoundException; // Importar nuestra excepción
import org.slf4j.Logger; // *** CAMBIO: Importar SLF4J Logger ***
import org.slf4j.LoggerFactory; // *** CAMBIO: Importar SLF4J LoggerFactory ***
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
// import java.util.logging.Level; // ELIMINAR
// import java.util.logging.Logger; // ELIMINAR

@ControllerAdvice
public class GlobalExceptionHandler {

    // *** CAMBIO: Usar SLF4J Logger ***
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Maneja las excepciones de tipo UareUException.
     */
    @ExceptionHandler(UareUException.class)
    public ResponseEntity<String> handleUareUException(UareUException ex) {
        // *** CAMBIO: Usar logger.error y formato SLF4J ***
        // Incluir el código de error es útil para depurar UareU
        String errorMessage = String.format("Error UareU SDK: Código %d - %s", ex.getCode(), ex.toString());
        logger.error(errorMessage, ex); // Loguea el mensaje y la traza de la excepción
        // Devolver un mensaje genérico o específico al cliente según necesidad
        String clientMessage = "Ocurrió un error con el lector de huellas. Código: " + ex.getCode();
        return new ResponseEntity<>(clientMessage, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Maneja nuestra excepción personalizada ResourceNotFoundException (404).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<String> handleResourceNotFoundException(ResourceNotFoundException ex) {
        // Logueamos como warning porque es un error esperado (cliente pide algo que no existe)
        logger.warn("Recurso no encontrado: {}", ex.getMessage());
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND); // Devuelve 404
    }


    /**
     * Maneja cualquier otra excepción no capturada específicamente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGenericException(Exception ex) {
        // *** CAMBIO: Usar logger.error ***
        logger.error("Excepción inesperada capturada", ex); // Loguea mensaje y traza
        String errorMessage = "Ocurrió un error inesperado en el servidor."; // Mensaje genérico al cliente
        return new ResponseEntity<>(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}