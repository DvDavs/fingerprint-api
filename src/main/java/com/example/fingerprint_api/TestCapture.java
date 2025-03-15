package com.example.fingerprint_api;

import com.digitalpersona.uareu.*;

import java.lang.reflect.Field;
import java.util.Scanner;

public class TestCapture {
    public static void main(String[] args) {
        try {
            // Obtener la colección de lectores y refrescar la lista
            ReaderCollection readers = UareUGlobal.GetReaderCollection();
            readers.GetReaders();

            if (readers.isEmpty()) {
                System.out.println("❌ No se encontraron lectores de huella.");
                return;
            }

            // Mostrar los lectores disponibles
            System.out.println("\n📌 Lectores disponibles:");
            for (int i = 0; i < readers.size(); i++) {
                System.out.println((i + 1) + ". " + readers.get(i).GetDescription().name);
            }

            // Permitir al usuario seleccionar el lector deseado
            Scanner scanner = new Scanner(System.in);
            System.out.print("\n🔍 Selecciona el número del lector correcto: ");
            int selection = scanner.nextInt() - 1;
            scanner.close();

            if (selection < 0 || selection >= readers.size()) {
                System.out.println("❌ Selección inválida. Saliendo...");
                return;
            }

            Reader selectedReader = readers.get(selection);
            System.out.println("\n✅ Lector seleccionado: " + selectedReader.GetDescription().name);

            // Abrir el lector en modo EXCLUSIVE para evitar conflictos
            selectedReader.Open(Reader.Priority.EXCLUSIVE);

            // Verificar capacidades y obtener una resolución válida
            Reader.Capabilities caps = selectedReader.GetCapabilities();
            if (!caps.can_capture) {
                System.out.println("❌ ERROR: Este lector no soporta captura.");
                return;
            }
            int resolution = (caps.resolutions != null && caps.resolutions.length > 0) ? caps.resolutions[0] : 500;
            System.out.println("📌 Resolución seleccionada: " + resolution + " DPI");

            // Intentar calibrar el lector (si aplica)
            try {
                System.out.println("\n🔧 Intentando calibrar el lector...");
                selectedReader.Calibrate();
                System.out.println("✅ Calibración exitosa.");
            } catch (UareUException e) {
                System.out.println("⚠️ Advertencia: No se pudo calibrar el lector. Mensaje: " + e.getMessage());
            }

            // Establecer el tamaño esperado de imagen a 140000 bytes usando reflection
            try {
                Field field = selectedReader.getClass().getDeclaredField("m_nImageSize");
                field.setAccessible(true);
                field.setInt(selectedReader, 140000);
                System.out.println("📌 Se estableció el tamaño esperado de imagen a 140000 bytes.");
            } catch (Exception e) {
                System.out.println("⚠️ No se pudo establecer m_nImageSize: " + e.getMessage());
            }

            // Probar captura usando los formatos soportados (ANSI e ISO)
            Fid.Format[] formatos = { Fid.Format.ANSI_381_2004, Fid.Format.ISO_19794_4_2005 };
            boolean capturaExitosa = false;
            for (Fid.Format formato : formatos) {
                System.out.println("\n📸 Intentando captura con formato: " + formato);
                try {
                    Reader.CaptureResult cr = selectedReader.Capture(formato, Reader.ImageProcessing.IMG_PROC_DEFAULT, resolution, 5000);

                    if (cr != null) {
                        System.out.println("✅ CaptureResult recibido.");
                        System.out.println("➡️ Calidad de la captura: " + cr.quality);
                        if (cr.quality == Reader.CaptureQuality.GOOD) {
                            System.out.println("📸 La calidad de la imagen es BUENA.");
                            capturaExitosa = true;
                        } else {
                            System.out.println("⚠️ La calidad de la imagen NO es buena: " + cr.quality);
                        }
                        if (cr.image != null) {
                            System.out.println("📸 Imagen capturada correctamente. Tamaño: " + cr.image.getData().length + " bytes.");
                        } else {
                            System.out.println("⚠️ ERROR: No se recibió imagen de la huella.");
                        }
                    } else {
                        System.out.println("⚠️ ERROR: CaptureResult es null.");
                    }
                } catch (UareUException e) {
                    System.out.println("❌ Error en la captura con formato " + formato + ": " + e.getMessage());
                }
            }

            if (!capturaExitosa) {
                System.out.println("❌ ERROR: No se pudo capturar una huella válida.");
            }

            // Cerrar el lector
            selectedReader.Close();

        } catch (UareUException e) {
            System.out.println("❌ UareUException: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("❌ Excepción inesperada: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
