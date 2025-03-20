package com.example.fingerprint_api.util;

import io.github.cdimascio.dotenv.Dotenv;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

public class CryptoUtils {

    // Usamos AES/CBC/PKCS5Padding
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";

    // Cargar el archivo .env usando Dotenv
    private static final Dotenv dotenv = Dotenv.load();

    // Se carga la clave desde la variable de entorno "FINGERPRINT_ENCRYPTION_KEY".
    // La clave debe ser de 32 caracteres (256 bits) para AES-256.
    private static final byte[] KEY = loadKey();

    private static byte[] loadKey() {
        String keyStr = dotenv.get("FINGERPRINT_ENCRYPTION_KEY");
        if (keyStr == null || keyStr.isEmpty()) {
            throw new RuntimeException("La variable de entorno FINGERPRINT_ENCRYPTION_KEY no está configurada");
        }
        if (keyStr.length() != 32) {
            throw new RuntimeException("La clave debe tener 32 caracteres para AES-256.");
        }
        return keyStr.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Cifra los datos utilizando AES-256 en modo CBC.
     * Se genera un IV aleatorio que se antepone a los datos cifrados para su uso en el descifrado.
     */
    public static byte[] encrypt(byte[] plainData) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(KEY, KEY_ALGORITHM);
        // Generar IV aleatorio
        byte[] iv = new byte[cipher.getBlockSize()];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] encrypted = cipher.doFinal(plainData);

        // Combina IV + datos cifrados (se necesita el IV para descifrar)
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(iv);
        outputStream.write(encrypted);
        return outputStream.toByteArray();
    }

    /**
     * Descifra los datos previamente cifrados.
     * Se extrae el IV de los primeros bytes del arreglo recibido.
     */
    public static byte[] decrypt(byte[] encryptedData) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(KEY, KEY_ALGORITHM);
        int blockSize = cipher.getBlockSize();

        // Extraer el IV (primeros blockSize bytes)
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, blockSize);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        // El resto son los datos cifrados reales
        byte[] actualEncrypted = Arrays.copyOfRange(encryptedData, blockSize, encryptedData.length);

        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(actualEncrypted);
    }
}
