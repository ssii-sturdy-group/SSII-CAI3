package SSII.IntegrityHIDS;

import SSII.IntegrityHIDS.Reporting.Incident;
import SSII.IntegrityHIDS.Reporting.IntegrityCheck;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Implementa los modos de ejecución 'integrity-check' y 'daemon'.</p>
 *
 * <p>El modo de ejecucion 'integrity-check' lee el archivo de configuración
 * y el archivo de hashes y comienza una comprobación de integridad de los
 * archivos indicados en el archivo de configuración.</p>
 *
 * <p>En concreto, para cada archivo contenido en el archivo de configuración,
 * hará lo siguiente:</p>
 *
 * <ul>
 *  <li>Comprobará que la ruta del archivo en la configuración es canónica.</li>
 *  <li>Comprobará que existe una entrada correspondiente en el archivo de hashes.</li>
 *  <li>Comprobará que el archivo existe en la ruta indicada.</li>
 *  <li>Comprobará que el hash del archivo coincide con el de la entrada en el archivo de hashes.</li>
 * </ul>
 *
 * <p>Cualquier fallo se traduce en una nueva entrada en el archivo de incidentes.
 * Si al final de la comprobación aún quedan entradas en el archivo de hashes que
 * no estaban en el archivo de configuración, se emitirán incidencias para esas
 * entradas también.</p>
 *
 * <p>El modo de ejecución 'daemon' realiza una comprobación de integridad con
 * pausas que duran el número de minutos indicado en la configuración
 * de la ejecuión.</p>
 */
public class DaemonExecutionMode implements ExecutionModeRunnerInterface {
    /**
     * Las incidencias llevan asociado un código de error que indica la naturaleza del incidente.
     */
    public enum IncidentCode {
        NO_ERROR,
        /** Un archivo listado en la configuración no posee una entrada en la lista de hashes. Se puede solucionar ejecutando el modo 'first-run'. */
        MISSING_FILEHASH,
        /** Un archivo listado en la configuración y con una entrada en la lista de hashes no existe en disco. */
        MISSING_FILE,
        /** Un archivo listado en la configuración no es igual que cuando se creó el archivo de hashes. */
        HASH_MISSMATCH,
        /** Un archivo listado en el archivo de hashes no esta en el archivo de configuración. */
        UNKNOWN_FILE
    }

    /**
     * Escribe un incidente.
     *
     * @param incidentFileWriter FileWriter en el que se escribirá la incidencia.
     * @param code Tipo de incidencia.
     * @param file Ruta del archivo que generó la incidencia.
     * @throws IOException Si no se pudo escribir la incidencia en disco.
     */
    private static void writeIncident(BufferedWriter incidentFileWriter, IncidentCode code, String file) throws IOException {
        incidentFileWriter.write(new Incident(code, file).toString());
        incidentFileWriter.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int execute(Options options, ConfigurationFile configFile) throws IOException {
        System.out.println("Configured algorithm: " + options.getAlgorithm().toString());
        int rval = 0;

        // Si el modo de ejecución es 'daemon'...
        if (options.getExecutionMode() == Options.ExecutionMode.DAEMON) {
            rval = executeDaemonMode(options, configFile);
        }
        // Si el modo de ejecución es 'integrity-check'
        else if (options.getExecutionMode() == Options.ExecutionMode.INTEGRITY_CHECK) {
            rval = executeSingleIntegrityCheckMode(options, configFile);
        }

        return rval;
    }

    /**
     * Ejecuta una única comprobación de integridad.
     *
     * @param options Opciones de ejecución
     * @param configFile Archivo de configuración
     * @return 0
     * @throws IOException Error al leer el archivo de hashes o escribir en el de incidentes.
     */
    public int executeSingleIntegrityCheckMode(Options options, ConfigurationFile configFile) throws IOException {
        // Hacer una única comprobación de integridad.
        System.out.println("-- Integrity check starting...");
        checkIntegrity(options, configFile);
        System.out.println("-- Integrity check done.");

        return 0;
    }

    /**
     * <p>Ejecuta comprobaciones de integridad periódicas.</p>
     *
     * <p>
     *     Lee el archivo de hashes indicado por las opciones de ejecución y hace una comprobación inicial.
     *     Tras ello, entrará en un bucle en el que esperará el número de minutos indicado en las opciones
     *     de ejecución y realizará una comprobación de integridad hasta que sea interrumpido.
     * </p>
     *
     * @param options Opciones de ejecución
     * @param configFile Archivo de configuración
     * @return 0
     * @throws IOException Error al escribir en el archivo de incidencias.
     */
    public int executeDaemonMode(Options options, ConfigurationFile configFile) throws IOException {
        // Leer una vez el archivo de hashes
        Map<String, String> fileHashMap = readHashesFile(options.getHashesFile());

        return executeDaemonMode(options, configFile, fileHashMap);
    }

    public int executeDaemonMode(Options options, ConfigurationFile configFile, Map<String, String> fileHashMap) throws IOException {
        // Hacer una comprobación de integridad inicial
        System.out.println("-- Integrity check starting...");
        checkIntegrity(options, configFile, fileHashMap);

        // Si no ha habido errores de ejecución (puede que haya habido incidencias)
        // entrar en bucle
        boolean interrupted = false;
        while (!interrupted) {
            try {
                System.out.println("-- Integrity check done. Sleeping for " + options.getMinutesBetweenIntegrityChecks() + " minutes.");
                // Esperar el periodo indicado entre comprobaciones de integridad.
                Thread.sleep(1000 * 60 * options.getMinutesBetweenIntegrityChecks());
                System.out.println("-- Integrity check starting...");
                // Hacer comprobación de integridad.
                checkIntegrity(options, configFile, fileHashMap);
            } catch (InterruptedException ie) {
                System.err.println("Interruption requested!");
                interrupted = true;
            }
        }

        return 0;
    }

    /**
     * <p>Realiza una comprobación de integridad.</p>
     *
     * <p>Obtiene los hashes del archivo de hashes indicado por el objeto options.</p>
     *
     * @param options Opciones de ejecución
     * @param configFile Objeto que representa al archivo de configuración con la lista de archivos.
     * @throws IOException Si se produce un error inesperado de acceso.
     */
    private void checkIntegrity(Options options, ConfigurationFile configFile) throws IOException {
        Map<String, String> fileHashMap = readHashesFile(options.getHashesFile());
        checkIntegrity(options, configFile, fileHashMap);
    }

    /**
     * Realiza una comprobación de integridad.
     *
     * @param options Opciones de ejecución
     * @param configFile Objeto que representa al archivo de configuración con la lista de archivos
     * @param inFileHashMap Mapa entre las rutas canónicas de los archivos y el hash seguro de los mismos
     * @throws IOException Error al escribir en el archivo de incidentes.
     */
    private void checkIntegrity(Options options, ConfigurationFile configFile, Map<String, String> inFileHashMap) throws IOException
    {
        // Obtener lista de archivos a comprobar de la configuración
        String[] fileList = configFile.getFileList();
        // Copiar el mapa de archivos-hashes para poder eliminar de nuestra copia los archivos ya comprobados
        Map<String, String> fileHashMap = new HashMap<>(inFileHashMap);
        BufferedWriter incidentsFileWriter = null;

        try {
            // Crear/abrir archivo de incidentes (modo 'append').
            incidentsFileWriter = new BufferedWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    new FileOutputStream(options.getIncidentsFile(), true), // Appending
                                    "UTF-8"
                            )
                    )
            );

            int knownFileIncidents = 0;

            incidentsFileWriter.write("\n");
            // Imprimir en archivo de incidentes una cabecera para indicar el comienzo de una comprobación de integridad.
            incidentsFileWriter.write(IntegrityCheck.getFormattedStartHeader(LocalDateTime.now(), fileHashMap.size()));
            // Para cada archivo de la lista del archivo de configuración...
            for (String fileName : fileList) {
                // Comprobar que el archivo esta presente en el archivo de hashes
                if (!fileHashMap.containsKey(fileName)) {
                    System.err.println("ERROR: File listed in configuration '" + fileName + "' is not present in hashes file '" + options.getHashesFile() + "'.");
                    writeIncident(incidentsFileWriter, IncidentCode.MISSING_FILEHASH, fileName);
                    knownFileIncidents++;
                    continue;
                }

                // Comprobar que el path del archivo es canónico y demás
                try {
                    FileHashingUtils.checkFilePath(fileName);
                } catch (FileNotFoundException fne) {
                    System.err.println("ERROR: File listed in configuration file '" + fileName + "' is not found.");
                    writeIncident(incidentsFileWriter, IncidentCode.MISSING_FILE, fileName);
                    knownFileIncidents++;
                    fileHashMap.remove(fileName);
                    continue;
                }

                // Comparar hash de archivo con hash guardado en archivo de hashes
                boolean isValid = FileHashingUtils.compareHashOfFile(
                        fileName, fileHashMap.get(fileName), options.algorithmToPlatformName()
                );

                // Si no coincide, guardar incidencia
                if (!isValid) {
                    System.err.println("ERROR: Integrity check failure in file '" + fileName + "'.");
                    writeIncident(incidentsFileWriter, IncidentCode.HASH_MISSMATCH, fileName);
                    knownFileIncidents++;
                }

                // Eliminar del mapa de hashes el archivo comprobado.
                // Al terminar el bucle el mapa contendrá solo los archivos que no estén listados
                // en la configuración.
                fileHashMap.remove(fileName);
            }

            // fileHashMap contiene ahora sólo los archivos que no estan listados en la configuración
            if (!fileHashMap.isEmpty()) {
                System.err.println("ERROR: The hashes file '" + options.getHashesFile() + "' contains hashes for files not listed in the configuration file '" + options.getConfigFile() + "'.");
                // Escribir una incidencia por cada entrada del archivo de hashes
                for (Map.Entry<String, String> entry : fileHashMap.entrySet()) {
                    System.err.println("Unknown file: " + entry.getKey());
                    writeIncident(incidentsFileWriter, IncidentCode.UNKNOWN_FILE, entry.getKey());
                }
            }

            // Imprimir en pantalla un resumen del resultado de la comprobación
            System.out.println("Integrity check summary:");
            System.out.println(" " + fileList.length + " TOTAL");
            System.out.println(" " + (fileList.length - knownFileIncidents) + " OK");
            System.out.println(" " + knownFileIncidents + " ERROR");
            System.out.println(" " + fileHashMap.size() + " UNKNOWN");
        } finally {
            if (incidentsFileWriter != null) {
                incidentsFileWriter.flush();
                incidentsFileWriter.close();
            }
        }
    }

    /**
     * <p>Lee el archivo de hashes.</p>
     *
     * <p>El archivo de hashes es un archivo de texto plano en el que cada línea
     * representa un hash y la ruta del archivo a que corresponde, separados
     * por ':'.</p>
     *
     * @param path Ruta al archivo de hashes
     * @return Mapa con las rutas de los archivos como clave y el hash como valor.
     * @throws IOException Si se produce un error al leer el archivo.
     */
    private Map<String, String> readHashesFile(String path) throws IOException {
        Map<String, String> rval = new HashMap<>();
        BufferedReader br = null;
        try {
            InputStream fis = new FileInputStream(path);
            InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
            br = new BufferedReader(isr);

            String line;
            while ((line = br.readLine()) != null) {
                // Cada línea es un hash seguido de ':' seguido de la ruta del archivo.
                int indexOfColons = line.indexOf(':');
                if (indexOfColons < 0) {
                    throw new IOException("Line '" + "' is invalid.");
                }

                String hashString, fileName;
                hashString = line.substring(0, indexOfColons);
                fileName = line.substring(indexOfColons + 1);

                rval.put(fileName, hashString);
            }
            System.out.println("Parsed hash list file with " + rval.size() + " entries.");
        } finally {
            if (br != null) {
                br.close();
            }
        }

        return rval;
    }
}
