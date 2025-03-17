// Java
package com.example.fingerprint_api.controller;

import com.example.fingerprint_api.model.User;
import com.example.fingerprint_api.service.UserService;
import com.digitalpersona.uareu.UareUException;
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
        logger.info("Creating user: {}", user);
        User createdUser = userService.createUser(user);
        logger.info("User created with id: {}", createdUser.getId());
        return ResponseEntity.ok(createdUser);
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        logger.info("Fetching all users");
        List<User> users = userService.getAllUsers();
        logger.info("Found {} users", users.size());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<User> getUser(@PathVariable Long id) {
        logger.info("Fetching user with id: {}", id);
        User user = userService.getUser(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        logger.info("User fetched: {}", user);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        logger.info("Updating user with id: {}", id);
        User updatedUser = userService.updateUser(id, user);
        logger.info("User updated: {}", updatedUser);
        return ResponseEntity.ok(updatedUser);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        logger.info("Deleting user with id: {}", id);
        userService.deleteUser(id);
        logger.info("User deleted with id: {}", id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/identify")
    public ResponseEntity<?> identifyUser() {
        logger.info("Identifying user by fingerprint");
        try {
            Optional<User> user = userService.identifyUser();
            if (user.isPresent()) {
                logger.info("User identified: {}", user.get());
                return ResponseEntity.ok(user.get());
            } else {
                logger.warn("No matching user found for the fingerprint");
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("No se encontró un usuario que coincida con la huella");
            }
        } catch (UareUException | InterruptedException | IOException ex) {
            logger.error("Error identifying fingerprint: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al identificar la huella: " + ex.getMessage());
        }
    }
}