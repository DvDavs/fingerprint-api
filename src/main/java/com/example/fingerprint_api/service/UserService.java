// Java
package com.example.fingerprint_api.service;

import com.example.fingerprint_api.model.User;
import com.example.fingerprint_api.repository.UserRepository;
import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FingerprintService fingerprintService;

    public User createUser(User user) {
        logger.info("Saving new user: {}", user);
        User savedUser = userRepository.save(user);
        logger.info("User saved with id: {}", savedUser.getId());
        return savedUser;
    }

    public Optional<User> getUser(Long id) {
        logger.info("Retrieving user with id: {}", id);
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        logger.info("Retrieving all users");
        List<User> users = userRepository.findAll();
        logger.info("Total users found: {}", users.size());
        return users;
    }

    public User updateUser(Long id, User userDetails) {
        logger.info("Updating user with id: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    logger.error("User not found with id: {}", id);
                    return new RuntimeException("Usuario no encontrado");
                });
        user.setName(userDetails.getName());
        user.setEmail(userDetails.getEmail());
        if (userDetails.getFingerprintTemplate() != null) {
            user.setFingerprintTemplate(userDetails.getFingerprintTemplate());
        }
        User updatedUser = userRepository.save(user);
        logger.info("User updated successfully: {}", updatedUser);
        return updatedUser;
    }

    public void deleteUser(Long id) {
        logger.info("Deleting user with id: {}", id);
        userRepository.deleteById(id);
        logger.info("User deleted with id: {}", id);
    }

    public void saveFingerprintTemplate(Long userId, byte[] fmdBytes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        user.setFingerprintTemplate(fmdBytes);
        userRepository.save(user);
    }

    public Optional<User> identifyUser() throws UareUException, InterruptedException, IOException {
        logger.info("Capturing fingerprint for identification");
        Fmd capturedFmd = fingerprintService.captureFingerprintFmd();
        logger.info("Fingerprint captured successfully");

        int threshold = 2147;
        logger.info("Using fingerprint matching threshold: {}", threshold);

        List<User> users = userRepository.findAll();
        logger.info("Comparing against {} stored fingerprints", users.size());
        // Java
        for (User user : users) {
            if (user.getFingerprintTemplate() != null) {
                logger.info("Processing user id: {} with fingerprint template size: {}", user.getId(), user.getFingerprintTemplate().length);
                Fmd storedFmd = fingerprintService.importFmdFromByteArray(user.getFingerprintTemplate());
                logger.info("Imported fingerprint for user id: {}", user.getId());

                int score = fingerprintService.compareFingerprints(storedFmd, capturedFmd);
                logger.info("Comparison score for user id {}: {}", user.getId(), score);

                if (score < threshold) {
                    logger.info("Fingerprint match found for user id: {} with score: {}", user.getId(), score);
                    return Optional.of(user);
                } else {
                    logger.info("Fingerprint did not match for user id: {}. Score {} is not below threshold {}", user.getId(), score, threshold);
                }
            } else {
                logger.warn("User id: {} does not have a fingerprint template", user.getId());
            }
        }
        logger.warn("No fingerprint match found");
        return Optional.empty();
    }
}