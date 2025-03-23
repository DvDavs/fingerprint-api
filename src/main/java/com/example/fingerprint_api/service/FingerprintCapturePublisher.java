// NUEVO ARCHIVO: com.example.fingerprint_api.service.FingerprintCapturePublisher.java
package com.example.fingerprint_api.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class FingerprintCapturePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public FingerprintCapturePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Llamado cada vez que se obtiene una nueva huella en Base64.
     * 'readerName' = nombre del lector, 'base64Fingerprint' = imagen en base64
     */
    public void publishNewCapture(String readerName, String base64Fingerprint) {
        // Envía a todos los clientes suscritos a /topic/fingerprints
        // un objeto JSON con la información:
        var payload = new FingerprintEvent(readerName, base64Fingerprint);
        messagingTemplate.convertAndSend("/topic/fingerprints", payload);
    }

    // Clase simple para encapsular datos a enviar:
    public static class FingerprintEvent {
        public String readerName;
        public String base64;
        public FingerprintEvent(String readerName, String base64) {
            this.readerName = readerName;
            this.base64 = base64;
        }
    }
}
