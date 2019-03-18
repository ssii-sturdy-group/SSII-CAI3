package SSII.IntegrityHIDS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

/**
 * Contiene las opciones de ejecución.
 *
 * También incluye la lógica necesaria para parsear los argumentos de entrada.
 */
class Options {
    /**
     * Modos de ejecución disponibles.
     */
    enum ExecutionMode {
        /** Muestra ayuda */
        HELP,
        /** Hashea los archivos indicados en la configuración, guarda resultados en archivo de hashes */
        HASH,
        /** Hashea los archivos indicados en la configuración, compara con hashes guardados, genera incidentes */
        INTEGRITY_CHECK,
        /** Igual que INTEGRITY_CHECK, pero tras comprobación duerme unos minutos y vuelve a comprobar */
        DAEMON,
        /** Hashea los archivos indicados en la configuración y entra en modo DAEMON. */
        HASH_AND_DAEMON,
        /** Lee el archivo de incidentes y reconstruye los resultados de las pruebas de integridad */
        REPORT
    }

    /**
     * Algoritmos de hash seguro soportados por la aplicación.
     */
    enum HashAlgorithm {
        /** MD5, no recomendado */
        MD5,
        /** SHA1, seguridad mejorada, más lento que MD5, más seguro pero puede que no suficiente */
        SHA1,
        /** SHA256, seguridad muy mejorada, menor rendimiento, recomendado */
        SHA256
        // TODO Comprobar si plataforma soporta SHA384, SHA512, ...
    }

    /**
     * @return Devuelve un objeto Options con valores por defecto válidos
     */
    private static Options getDefaults()
    {
        String basePath = System.getProperty("user.dir") + File.separator;

        return new Options(
            ExecutionMode.HASH_AND_DAEMON,
            basePath + "main.conf",
            basePath + "hashes.lst",
            basePath + "incidents.txt",
            basePath + "indicators.txt",
            HashAlgorithm.SHA1
        );
    }

    private Options(
            ExecutionMode mode,
            String configFile,
            String hashesFile,
            String issuesFile,
            String indicatorsFile,
            HashAlgorithm algorithm)
    {
        this.executionMode = mode;
        this.configFile = configFile;
        this.hashesFile = hashesFile;
        this.incidentsFile = issuesFile;
        this.indicatorsFile = indicatorsFile;
        this.algorithm = algorithm;
        this.minutesBetweenIntegrityChecks = 60;
    }

    Options(Options other)
    {
        this.executionMode = other.getExecutionMode();
        this.configFile = other.getConfigFile();
        this.hashesFile = other.getHashesFile();
        this.incidentsFile = other.getIncidentsFile();
        this.indicatorsFile = other.getIndicatorsFile();
        this.algorithm = other.getAlgorithm();
        this.minutesBetweenIntegrityChecks = other.getMinutesBetweenIntegrityChecks();
    }

    /**
     * Parsea el array de argumentos de entrada.
     *
     * Parámetros soportados:
     *  "-config=_path_" : Path canónico al archivo de configuración
     *  "-hashes=_path_" : Path canónico al archivo de hashes
     *  "-incidents=_path_" : Path canónico al archivo de incidentes
     *  "-indicatos=_path_" : Path canónico al archivo de indicadores
     *
     * Además, se acepta otro argumento no precedido por '-' que debe indicar
     * el modo de ejecución (por defecto 'daemon'). Los modos disponibles
     * son 'help', 'first-run', 'integrity-check', 'daemon' y 'report'.
     *
     * @param args Argumentos de entrada del programa
     * @return Opciones indicadas en argumentos. Si no esta presente, se usa el valor por defecto.
     */
    static Options fromArguments(String[] args)
    {
        Options rval = Options.getDefaults();
        for (String arg : args) {
            if (arg.startsWith("-config=")) {
                rval.setConfigFile(arg.replaceAll("-config=", ""));
            }
            else if (arg.startsWith("-hashes=")) {
                rval.setHashesFile(arg.replaceAll("-hashes=", ""));
            }
            else if (arg.startsWith("-incidents=")) {
                rval.setIncidentsFile(arg.replaceAll("-issues=", ""));
            }
            else if (arg.startsWith("-indicators=")) {
                rval.setIndicatorsFile(arg.replaceAll("-indicators=", ""));
            }
            else if (arg.startsWith("-")) {
                throw new IllegalArgumentException("Unknown switch arg: " + arg);
            }
            else {
                // Should be execution mode
                switch (arg) {
                    case "help":
                        rval.setExecutionMode(ExecutionMode.HELP);
                        break;
                    case "hash":
                        rval.setExecutionMode(ExecutionMode.HASH);
                        break;
                    case "daemon":
                        rval.setExecutionMode(ExecutionMode.DAEMON);
                        break;
                    case "integrity-check":
                        rval.setExecutionMode(ExecutionMode.INTEGRITY_CHECK);
                        break;
                    case "hash-and-daemon":
                        rval.setExecutionMode(ExecutionMode.HASH_AND_DAEMON);
                        break;
                    case "report":
                        rval.setExecutionMode(ExecutionMode.REPORT);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown execution mode: " + arg);
                }
            }
        }

        return rval;
    }

    /**
     * Comprueba que el path del archivo de configruación es canónico, existe y es legible.
     * @throws IOException Archivo no encontrado, no leible o escribible.
     */
    void checkConfigFile() throws IOException {
        checkFilePath(this.configFile, false);
    }

    /**
     * Comprueba los archivos necesarios para el modo de ejecución actual.
     *
     * Cada archivo puede ser comprobado como archivo de entrada o de salida. Si se comprueba como
     * archivo de entrada, entonces su path debe ser canónico, el archivo debe existir y ser legible.
     * Cuando el archivo es de salida, se comprueba que el path es canónico y escribible, y si el
     * archivo no existe se crea vacío.
     *
     * Cada modo de ejecución considera a los diferentes archivos de la siguiente forma:
     * - help: No se comprueban archivos.
     * - first-run: archivo de configuración (lectura), archivo de hashes (escritura).
     * - integrity-check y dameon: archivo de configuración (lectura), archivo de hashes (lectura),
     *      archivo de incidentes (escritura).
     * - report: archivo de configuración (lectura), archivo de hashes (lectura), archivo de
     *      incidentes (lectura), archivo de indicadores (escritura).
     * @throws IOException Uno de los archivos no ha sido encontrado, no es legible o escribible.
     */
    void checkFiles() throws IOException {
        switch (executionMode) {
            case HELP:
                // Noop
                break;
            case HASH:
                // Requiere que el archivo de configuración exista y sea legible
                checkFilePath(this.configFile, false);
                checkFilePath(this.hashesFile, true);
                break;
            case DAEMON:
            case INTEGRITY_CHECK:
                // Requiere que el archivo de configuración y de hashes existan y sean legibles
                checkFilePath(this.configFile, false);
                checkFilePath(this.hashesFile, false);
                // Requiere que el archivo de incidentes se pueda escribir
                checkFilePath(this.incidentsFile, true);
                break;
            case HASH_AND_DAEMON:
                // Requiere que el archivo de configuración exista y sea legible
                checkFilePath(this.configFile, false);
                // Require escribir en hashes e incidentes
                checkFilePath(this.hashesFile, true);
                checkFilePath(this.incidentsFile, true);
                break;
            case REPORT:
                checkFilePath(this.configFile, false);
                checkFilePath(this.incidentsFile, false);
                checkFilePath(this.indicatorsFile, true);
                break;
        }
    }

    /**
     * Comprueba el path de un archivo.
     *
     * La ruta del archivo debe ser canónica. Si el archivo es de entrada, entonces
     * debe existir y ser legible. Si es de salida y existe, debe ser escribible. Si
     * no existe se intentará crear vacío.
     *
     * @param path Ruta al archivo.
     * @param isOutput Si es true se considerará al archivo 'de salida', 'de entrada' en otro caso.
     * @throws IOException Si se produce algún error en las comprobaciones.
     */
    private void checkFilePath(String path, boolean isOutput) throws IOException {
        File file = new File(path);

        if (!Objects.equals(file.getCanonicalPath(), path)) {
            throw new IllegalArgumentException("path '" + path + "' is not canonical: " + file.getCanonicalPath());
        }
        if (!isOutput) {
            if (!file.exists()) {
                throw new FileNotFoundException("File not found: " + path);
            }
            if (!file.canRead()) {
                throw new IOException("Can not read file: " + path);
            }
        }
        else {
            if (file.exists()) {
                if (!file.canWrite()) {
                    throw new IOException("Can not write file: " + path);
                }
            }
            else {
                if (!file.createNewFile()) {
                    throw new IOException("Can not create file: " + path);
                }
            }
        }
    }

    ExecutionMode getExecutionMode() {
        return executionMode;
    }

    void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode;
    }

    String getConfigFile() {
        return configFile;
    }

    void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    String getHashesFile() {
        return hashesFile;
    }

    void setHashesFile(String hashesFile) {
        this.hashesFile = hashesFile;
    }

    String getIncidentsFile() {
        return incidentsFile;
    }

    void setIncidentsFile(String incidentsFile) {
        this.incidentsFile = incidentsFile;
    }

    String getIndicatorsFile() {
        return indicatorsFile;
    }

    void setIndicatorsFile(String indicatorsFile) {
        this.indicatorsFile = indicatorsFile;
    }

    public HashAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(HashAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public int getMinutesBetweenIntegrityChecks() {
        return minutesBetweenIntegrityChecks;
    }

    public void setMinutesBetweenIntegrityChecks(int minutesBetweenIntegrityChecks) {
        if (minutesBetweenIntegrityChecks < 1) {
            throw new IllegalArgumentException("hoursBetweenIntegrifyChecks is less than 1");
        }
        this.minutesBetweenIntegrityChecks = minutesBetweenIntegrityChecks;
    }

    /**
     * Devuelve el nombre del algoritmo de hash seguro en un formato comprensible por la plataforma.
     * @return Nombre del algoritmo de cara a la plataforma.
     */
    public String algorithmToPlatformName() {
        switch (getAlgorithm()) {
            case MD5:
                return "MD5";
            case SHA1:
                return "SHA-1";
            case SHA256:
                return "SHA-256";
        }

        throw new RuntimeException("Internal error: unknown algorithm for Options.algorithmToPlatformName: " + getAlgorithm().toString());
    }

    public String toString()
    {
        return "Options {\n"
                + " Execution mode: " + executionMode.toString() + "\n"
                + " Hash algorithm: " + algorithm + "\n"
                + " Configuration file: " + configFile + "\n"
                + " Hashes file: " + hashesFile + "\n"
                + " Issues file: " + incidentsFile + "\n"
                + " Indicators file: " + indicatorsFile + "\n"
                + " Minutes between integrity checks: " + minutesBetweenIntegrityChecks + "\n}";
    }

    private ExecutionMode executionMode;
    private HashAlgorithm algorithm;
    private String configFile;
    private String hashesFile;
    private String incidentsFile;
    private String indicatorsFile;
    private int minutesBetweenIntegrityChecks;
}
