package com.example.fingerprint_api.service;

import com.digitalpersona.uareu.Engine;
import com.digitalpersona.uareu.Fmd;
import com.digitalpersona.uareu.UareUGlobal;
import com.digitalpersona.uareu.UareUException;
import com.example.fingerprint_api.model.User;
import com.example.fingerprint_api.repository.UserRepository;
import com.example.fingerprint_api.util.CryptoUtils;
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

    // Mantenemos en memoria las FMD y su lista de usuarios
    private List<Fmd> userFmds = new ArrayList<>();
    private List<User> users = new ArrayList<>();

    @PostConstruct
    public void init() {
        logger.info("Loading users and FMDs into memory...");
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (user.getFingerprintTemplate() != null) {
                try {
                    byte[] decryptedFmdBytes = CryptoUtils.decrypt(user.getFingerprintTemplate());
                    Fmd fmd = UareUGlobal.GetImporter().ImportFmd(
                            decryptedFmdBytes,
                            Fmd.Format.ANSI_378_2004,
                            Fmd.Format.ANSI_378_2004
                    );
                    userFmds.add(fmd);
                    users.add(user);
                } catch (Exception e) {
                    logger.error("Error loading FMD for user {}: {}", user.getId(), e.getMessage());
                }
            }
        }
        logger.info("Loaded {} FMDs into memory", userFmds.size());
    }

    // ========== Métodos CRUD básicos de Usuario ==========

    public User createUser(User user) {
        User savedUser = userRepository.save(user);
        if (user.getFingerprintTemplate() != null) {
            try {
                byte[] decrypted = CryptoUtils.decrypt(user.getFingerprintTemplate());
                Fmd fmd = UareUGlobal.GetImporter().ImportFmd(decrypted, Fmd.Format.ANSI_378_2004, Fmd.Format.ANSI_378_2004);
                synchronized (this) {
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
                byte[] decrypted = CryptoUtils.decrypt(userDetails.getFingerprintTemplate());
                Fmd fmd = UareUGlobal.GetImporter().ImportFmd(decrypted, Fmd.Format.ANSI_378_2004, Fmd.Format.ANSI_378_2004);
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
            int idx = users.indexOf(user);
            if (idx != -1) {
                userFmds.remove(idx);
                users.remove(idx);
            }
        }
        userRepository.deleteById(id);
    }

    /**
     * Método "clásico": captura en ese momento. (Lo usabas antes)
     * Se puede mantener si deseas, pero ahora en el "checador" lo hacemos en MultiReaderFingerprintService.
     */
    public Optional<User> identifyUserByCapture(String readerName) throws UareUException, InterruptedException, IOException {
        // Este quedaría obsoleto si prefieres la lógica en MultiReaderFingerprintService
        return Optional.empty();
    }

    /**
     * Identificación recibiendo un Fmd ya capturado (por ejemplo, desde el "checador").
     */
    public synchronized Optional<User> identifyUser(Fmd capturedFmd) throws UareUException {
        // Nota: Se sincroniza la lectura de userFmds / users
        if (userFmds.isEmpty()) {
            logger.warn("No hay FMDs en memoria");
            return Optional.empty();
        }
        Engine engine = UareUGlobal.GetEngine();
        int threshold = Engine.PROBABILITY_ONE / 100000; // 1 en 100,000

        Engine.Candidate[] candidates = engine.Identify(
                capturedFmd,
                0,
                userFmds.toArray(new Fmd[0]),
                threshold,
                1
        );
        if (candidates.length > 0) {
            int index = candidates[0].fmd_index;
            if (index >= 0 && index < users.size()) {
                User matched = users.get(index);
                logger.info("Usuario identificado: {} (index: {})", matched.getId(), index);
                return Optional.of(matched);
            }
        }
        return Optional.empty();
    }

    /**
     * Guarda la huella en la BD (encriptada) y la sube a userFmds en memoria.
     */
    public void saveFingerprintTemplate(Long userId, byte[] fmdBytes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
        try {
            byte[] encryptedFmd = CryptoUtils.encrypt(fmdBytes);
            user.setFingerprintTemplate(encryptedFmd);
            User saved = userRepository.save(user);

            Fmd fmd = UareUGlobal.GetImporter().ImportFmd(fmdBytes, Fmd.Format.ANSI_378_2004, Fmd.Format.ANSI_378_2004);
            synchronized (this) {
                int idx = users.indexOf(user);
                if (idx != -1) {
                    userFmds.set(idx, fmd);
                    users.set(idx, saved);
                } else {
                    userFmds.add(fmd);
                    users.add(saved);
                }
            }
        } catch (Exception e) {
            logger.error("Error saving fingerprint for user {}: {}", userId, e.getMessage());
        }
    }
}
