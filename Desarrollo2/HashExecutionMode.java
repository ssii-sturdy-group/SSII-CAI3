package SSII.IntegrityHIDS;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Implementa el modo de ejecución <i>hash</i>.</p>
 *
 * <p>El modo de ejecución <i>first-run</i> tiene como objetivo obtener los hashes
 * de los archivos listados en el archivo de configuración para su control de integridad.</p>
 *
 * <p>Durante la ejecución de este modo se leerá la configuración y se creará un nuevo
 * archivo de hashes. Para cada archivo listado en el archivo de configuración para
 * comprobar su integridad:</p>
 *
 * <ul>
 *     <li>Se comprobará su path (debe ser canónico, existir y ser legible).</li>
 *     <li>Se computará el hash del archivo.</li>
 *     <li>Se guardará una entrada correspondiente al archivo en el archivo de hashes.</li>
 * </ul>
 *
 * <p>El objeto de opciones es el que dicta cuál es el archivo de hashes.</p>
 */
public class HashExecutionMode implements ExecutionModeRunnerInterface {
    private Map<String, String> fileHashMap;

    public HashExecutionMode()
    {
        fileHashMap = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int execute(Options options, ConfigurationFile configFile) throws IOException
    {
        String[] fileList = configFile.getFileList();
        fileHashMap.clear();
        BufferedWriter hashesFileWriter = null;
        try {
            // Crear un nuevo archivo de hashes
            hashesFileWriter = new BufferedWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(options.getHashesFile()),
                                    "UTF-8"
                            )
                    )
            );

            System.out.println("Selected algorithm: " + options.getAlgorithm().toString());
            System.out.println("Hashing files...");

            // Go through our file list
            for (String filePath : fileList) {
                String hashString;
                try {
                    FileHashingUtils.checkFilePath(filePath);

                    byte[] hash = FileHashingUtils.getHashOfFile(filePath, options.algorithmToPlatformName());
                    hashString = FileHashingUtils.byteArrayToHexString(hash);
                    fileHashMap.put(filePath, hashString);
                    System.out.println(hashString + " : " + filePath);
                } catch (FileNotFoundException fnfe) {
                    System.err.println("File not found: " + filePath);
                    continue;
                }

                hashesFileWriter.write(hashString + ":" + filePath + "\n");
            }
        } finally {
            // Pase lo que pase, cuidar el archivo de hashes.
            if (hashesFileWriter != null) {
                hashesFileWriter.flush();
                hashesFileWriter.close();
            }
        }

        return 0;
    }

    public Map<String, String> getFileHashMap() {
        return fileHashMap;
    }
}
