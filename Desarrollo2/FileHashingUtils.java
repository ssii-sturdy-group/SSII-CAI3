package SSII.IntegrityHIDS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Encapsula métodos y código para calcular el hash o comprobar el hash de archivos.
 */
public class FileHashingUtils {
    private static final String[] hexDigits ={"0", "1", "2", "3","4", "5", "6", "7","8", "9", "a", "b","c", "d", "e", "f"};

    /**
     * Calcula el hash de un archivo dado el path y un algoritmo.
     *
     * @param path Path, en forma canónica.
     * @param algorithm Algoritmo que se debe usar al calcular el hash.
     * @return Bytes conteniendo el hash del archivo.
     * @throws IOException El archivo no existe o no se puede leer.
     * @throws IllegalArgumentException El path no esta en forma canónica.
     * @throws RuntimeException El algoritmo no esta soportado por la plataforma.
     */
    public static byte[] getHashOfFile(String path, String algorithm)
            throws IOException
    {
        File file = new File(path);
        if (!Objects.equals(file.getCanonicalPath(), path)) {
            throw new IllegalArgumentException("Argument 'path' is not the canonical path: " + path);
        }
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + path);
        }
        if (!file.canRead()) {
            throw new RuntimeException("Can not read file: " + path);
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance(algorithm);
            md.update(Files.readAllBytes(Paths.get(path)));
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            // La documentación de Java7 dice que al menos MD5, SHA1 y SHA256 deben estar soportados.
            throw new RuntimeException("Algorithm '" + algorithm + "' is not supported by the platform.");
        }
    }

    /**
     * Comprueba la integridad de un archivo, dada la ruta y el hash que debe tener.
     *
     * @param path Path al archivo en forma canónica.
     * @param hashHexString Cadena hexadecimal que representa el hash del archivo.
     * @param algorithm Algoritmo que se usó al calcular el hash original.
     * @return True si el hash actual coincide con el que se pasó como argumento.
     * @throws IOException Archivo no encontrado o no se puede leer.
     * @throws IllegalArgumentException Path no esta en forma canónica.
     * @throws RuntimeException Algoritmo no soportado por la plataforma.
     */
    public static boolean compareHashOfFile(String path, String hashHexString, String algorithm)
            throws IOException
    {
        byte[] hashOfFile = getHashOfFile(path, algorithm);
        String hexStringOfFile = byteArrayToHexString(hashOfFile);

        return hexStringOfFile.equals(hashHexString);
    }

    /**
     * Convierte un array de bytes en una cadena hexadecimal.
     *
     * @param hash Bytes.
     * @return Cadena hexadecimal que representa al array de bytes.
     */
    public static String byteArrayToHexString(byte[] hash)
    {
        String rval = "";
        for (byte aHash : hash) {
            rval += byteToHexString(aHash);
        }

        return rval;
    }

    /**
     * Convierte un byte a cadena hexadecimal.
     *
     * @param hash Byte
     * @return Cadena hexadecimal que representa el byte.
     */
    private static String byteToHexString(byte hash)
    {
        int n = hash;
        if (n < 0) {
            n=256 +n;
        }
        int d1 = n / 16;
        int d2 = n % 16;
        return hexDigits[d1] + hexDigits[d2];
    }

    /**
     * Comprueba que el argumento es un path canónico a un archivo existente y legible.
     *
     * @param filePath Path del archivo
     * @throws IOException Archivo no encontrado o no legible.
     */
    public static void checkFilePath(String filePath) throws IOException {
        File file = new File(filePath);
        if (!filePath.equals(file.getCanonicalPath())) {
            throw new IllegalArgumentException("File path '" + filePath + "' is not canonical (" + file.getCanonicalPath() + ").");
        }
        if (!file.exists()) {
            throw new FileNotFoundException("File not found: " + filePath);
        }
        if (!file.canRead()) {
            throw new IOException("Can not read: " + filePath);
        }
    }
}
