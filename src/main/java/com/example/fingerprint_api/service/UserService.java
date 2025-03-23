package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.UareUGlobal;
import com.example.fingerprint_api.model.User;
import com.example.fingerprint_api.repository.UserRepository;
import com.example.fingerprint_api.util.CryptoUtils;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FingerprintService fingerprintService;

    // Listas para mantener FMDs y usuarios en memoria
    private List<Fmd> userFmds = new ArrayList<>();
    private List<User> users = new ArrayList<>();

    // Cargar FMDs al iniciar la aplicación
    @PostConstruct
    public void init() {
        logger.info("Loading users and FMDs into memory...");
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (user.getFingerprintTemplate() != null) {
                try {
                    byte[] decryptedFmdBytes = CryptoUtils.decrypt(user.getFingerprintTemplate());
                    Fmd fmd = fingerprintService.importFmdFromByteArray(decryptedFmdBytes);
                    userFmds.add(fmd);
                    users.add(user);
                } catch (Exception e) {
                    logger.error("Error loading FMD for user {}: {}", user.getId(), e.getMessage());
                }
            }
        }
        logger.info("Loaded {} FMDs into memory", userFmds.size());
    }

    public User createUser(User user) {
        User savedUser = userRepository.save(user);
        if (user.getFingerprintTemplate() != null) {
            try {
                byte[] decryptedFmdBytes = CryptoUtils.decrypt(user.getFingerprintTemplate());
                Fmd fmd = fingerprintService.importFmdFromByteArray(decryptedFmdBytes);
                synchronized (this) { // Sincronizar para evitar problemas en entornos multi-hilo
                    userFmds.add(fmd);
                    users.add(savedUser);
                }
            } catch (Exception e) {
                logger.error("Error adding FMD for new user {}: {}", savedUser.getId(), e.getMessage());
            }
        }
        return savedUser;
    }

    public Optional<User> getUser(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        user.setName(userDetails.getName());
        user.setEmail(userDetails.getEmail());
        if (userDetails.getFingerprintTemplate() != null) {
            user.setFingerprintTemplate(userDetails.getFingerprintTemplate());
            try {
                byte[] decryptedFmdBytes = CryptoUtils.decrypt(userDetails.getFingerprintTemplate());
                Fmd fmd = fingerprintService.importFmdFromByteArray(decryptedFmdBytes);
                synchronized (this) {
                    int index = users.indexOf(user);
                    if (index != -1) {
                        userFmds.set(index, fmd);
                        users.set(index, user);
                    }
                }
            } catch (Exception e) {
                logger.error("Error updating FMD for user {}: {}", id, e.getMessage());
            }
        }
        return userRepository.save(user);
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        synchronized (this) {
            int index = users.indexOf(user);
            if (index != -1) {
                userFmds.remove(index);
                users.remove(index);
            }
        }
        userRepository.deleteById(id);
    }

    public void saveFingerprintTemplate(Long userId, byte[] fmdBytes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
       // saveFingerprintTemplate method snippet (after)
        byte[] encryptedFmd;
        try {
            encryptedFmd = CryptoUtils.encrypt(fmdBytes);
        } catch (Exception e) {
            logger.error("Error encrypting fingerprint template for user {}: {}", userId, e.getMessage());
            throw new RuntimeException(e);
        }
        user.setFingerprintTemplate(encryptedFmd);
        User savedUser = userRepository.save(user);
        try {
            Fmd fmd = fingerprintService.importFmdFromByteArray(fmdBytes);
            synchronized (this) {
                int index = users.indexOf(user);
                if (index != -1) {
                    userFmds.set(index, fmd);
                    users.set(index, savedUser);
                } else {
                    userFmds.add(fmd);
                    users.add(savedUser);
                }
            }
        } catch (Exception e) {
            logger.error("Error adding/updating FMD for user {}: {}", userId, e.getMessage());
        }
    }

    public Optional<User> identifyUser() throws UareUException, InterruptedException, IOException {
        logger.info("Capturing fingerprint for identification");
        Fmd capturedFmd = fingerprintService.captureFingerprintFmd();
        logger.info("Fingerprint captured successfully");

        Engine engine = UareUGlobal.GetEngine();
        int threshold = Engine.PROBABILITY_ONE / 100000; // Umbral de probabilidad (ajustable)
        synchronized (this) { // Sincronizar acceso a las listas
            if (userFmds.isEmpty()) {
                logger.warn("No fingerprints loaded in memory");
                return Optional.empty();
            }
            Engine.Candidate[] candidates = engine.Identify(
                    capturedFmd,
                    0,
                    userFmds.toArray(new Fmd[0]),
                    threshold,
                    1 // Número máximo de coincidencias a devolver
            );
            if (candidates.length > 0) {
                int index = candidates[0].fmd_index;
                if (index >= 0 && index < users.size()) {
                    User matchedUser = users.get(index);
                    logger.info("User identified: {} (index: {})", matchedUser.getId(), index);
                    return Optional.of(matchedUser);
                }
            }
        }
        logger.warn("No matching user found");
        return Optional.empty();
    }
}