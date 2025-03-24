package com.example.fingerprint_api.controller;

import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.model.User;
import com.example.fingerprint_api.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User created = userService.createUser(user);
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAll() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getOne(@PathVariable Long id) {
        User user = userService.getUser(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable Long id, @RequestBody User u) {
        User updated = userService.updateUser(id, u);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Ejemplo: si quisieras exponer un endpoint que identifique
     * al usuario usando una captura puntual en el momento:
     */
    @PostMapping("/identifyByReader/{readerName}")
    public ResponseEntity<?> identifyByReader(@PathVariable String readerName) {
        // Si prefieres hacerlo directamente en "MultiReaderFingerprintService",
        // este endpoint sería opcional.
        try {
            // Podrías delegar a multiService.captureSingleFingerprintFmd(readerName)
            // y luego userService.identifyUser(fmd).
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body("No implementado aquí");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
