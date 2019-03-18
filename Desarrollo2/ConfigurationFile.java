package SSII.IntegrityHIDS;

import org.ini4j.Config;
import org.ini4j.Wini;
import java.io.File;
import java.io.IOException;

/**
 * <p>Encapsula la l&oacute;gica de lectura de archivo configuraci&oacute;n.</p>
 *
 * <p>El archivo de configuraci&oacute;n permite especificar los otros archivos involucrados en la
 * operaci&oacute;n del m&oacute;dulo (hashes, incidentes, indicadores), el algoritmo de
 * hash seguro y los archivos que deben comprobarse o ser hasheados dependiendo del
 * modo de ejecuci&oacute;n.</p>
 *
 * <p>La sint&aacute;xis del archivo es la de los archivos .ini, a trav&eacute;s de la
 * biblioteca <code>ini4j</code>.</p>
 *
 * <p>La configuraci&oacute;n se divide en 2 secciones:</p>
 * <ul>
 *  <li>Secci&oacute;n "daemon": obligatoria; debe contener al menos una entrada "file".
 *   <ul>
 *       <li>"hash_algorithm": permite establecer el algoritmo de cifrado entre los disponibles:
 *           "MD5", "SHA1" y "SHA256".</li>
 *       <li>"check_period_minutes": indica el n&uacute;mero de minutos que se espera entre comprobaciones de integridad
 *           cuando el modo de ejecuci&oacute;n es "daemon".</li>
 *       <li>"file": Se permite m&aacute;s de una entrada. Cada entrada indica un archivo cuya integridad se quiere
 *           controlar.</li>
 *   </ul>
 *  </li>
 *
 *  <li>Secci&oacute;n "files": opcional; todas sus entradas son tambi&eacute;n opcionales.
 *   <ul>
 *       <li>"hashes_file": Ruta can&oacute;nica al archivo donde se guardar&aacute;n los hashes de los archivos cuya integridad
 *                               queremos controlar.</li>
 *       <li>"incidents_file": Ruta can&oacute;nica al archivo donde se guardar&aacute; un registro de las incidencias ocurridas durante
 *                               chequeos de integridad.</li>
 *       <li>"indicators_file": Ruta can&oacute;nica al archivo donde se guardar&aacute; informaci&oacute;n de trayectoria de los chequeos.</li>
 *   </ul>
 *  </li>
 * </ul>
 */
public class ConfigurationFile {
    // Cadenas que buscaremos en el archivo de configuraci&oacute;n.
    private final String DAEMON_SECTION = "daemon";
    private final String ALGORITHM_KEY = "hash_algorithm";
    private final String PERIOD_KEY = "check_period_minutes";
    private final String FILE_KEY = "file";

    private final String FILES_SECTION = "files";
    private final String HASHFILE_KEY = "hashes_file";
    private final String INCIDENTSFILE_KEY = "incidents_file";
    private final String INDICATORSFILE_KEY = "indicators_file";

    private String filePath;
    private Wini iniFileReader;

    /**
     * Crea un objeto ConfigurationFile a partir del archivo cuya ruta recibe como argumento.
     *
     * @param filePath Ruta al archivo de configuraci&oacute;n.
     * @throws IOException Archivo no existe, no es legible.
     */
    public ConfigurationFile(String filePath) throws IOException
    {
        this.filePath = filePath;
        this.iniFileReader = new Wini();
        Config conf = new Config();
        conf.setMultiOption(true);
        this.iniFileReader.setConfig(conf);
        this.iniFileReader.load(new File(filePath));

        checkContentKeys();
    }

    /**
     * <p>Comprueba que no hay entradas desconocidas en el archivo de configuraci&oacute;n.</p>
     *
     * <p>Lanza una IllegalArgumentException si alguna de las entradas del archivo de configuraci&oacute;n
     * tiene un nombre desconocido.</p>
     */
    private void checkContentKeys()
    {
        // S&oacute;lo se permite una secci&oacute;n 'daemon'
        if (!this.iniFileReader.containsKey(DAEMON_SECTION)) {
            throw new IllegalArgumentException("Configuration file doesn't have a " + DAEMON_SECTION + " section.");
        }
        // La secci&oacute;n 'daemon' debe tener al menos una entrada 'file'.
        Wini.Section daemonSection = iniFileReader.get(DAEMON_SECTION);
        if (daemonSection.getAll("file", String[].class).length == 0) {
            throw new IllegalArgumentException("Configuration file should have at least one file to check in the " + DAEMON_SECTION + " section.");
        }

        // Comprobar todos los nombres de secci&oacute;n
        for (String sectionName : iniFileReader.keySet()) {
            switch (sectionName) {
                case DAEMON_SECTION:
                case FILES_SECTION:
                    continue;

                default:
                    throw new IllegalArgumentException("Unknown section: " + sectionName);
            }
        }

        // Para la secci&oacute;n 'daemon', comprobar los nombres de sus entradas.
        for (String keyName : daemonSection.keySet()) {
            switch (keyName) {
                case FILE_KEY:
                case PERIOD_KEY:
                case ALGORITHM_KEY:
                    continue;

                default:
                    throw new IllegalArgumentException("Unknown key in " + DAEMON_SECTION + ": " + keyName);
            }
        }

        // Para la secci&oacute;n 'files', si existe, comprobar todos los nombres de sus entradas.
        if (iniFileReader.containsKey(FILES_SECTION)) {
            Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
            for (String keyName : filesSection.keySet()) {
                switch (keyName) {
                    case HASHFILE_KEY:
                    case INCIDENTSFILE_KEY:
                    case INDICATORSFILE_KEY:
                        continue;

                    default:
                        throw new IllegalArgumentException("Unknown key in " + FILES_SECTION + ": " + keyName);
                }
            }
        }
    }

    /**
     * <p>Aplica la configuraci&oacute;n especificada en el archivo al objeto Options.</p>
     *
     * <p>El objeto Options devuelto es una copia del original pero con la configuraci&oacute;n especificada
     * en el archivo aplicada. Si el archivo de configuraci&oacute;n no incluye una opci&oacute;n determinada
     * (por ejemplo, algoritmo de hash seguro) entonces el objeto devuelto tendr&aacute; como valor de esa
     * opci&oacute;n el que tenga el par&aacute;metro de la funci&oacute;n.</p>
     *
     * @param options Objeto options con los valores de opciones "por omisi&oacute;n".
     * @return Copia del objeto de entrada con las opciones del archivo de configuraci&oacute;n.
     */
    public Options applyOptionsTo(Options options) {
        // Copiamos el objeto de entrada
        Options rval = new Options(options);
        // Y aplicamos las opciones.
        if (hasHoursBetweenIntegrityCheck()) {
            rval.setMinutesBetweenIntegrityChecks(getHoursBetweenIntegrityCheck());
        }
        if (hasAlgorithm()) {
            rval.setAlgorithm(getAlgorithm());
        }
        if (hasHashesFile()) {
            rval.setHashesFile(getHashesFile());
        }
        if (hasIssuesFile()) {
            rval.setIncidentsFile(getIssuesFile());
        }
        if (hasIndicatorsFile()) {
            rval.setIndicatorsFile(getIndicatorsFile());
        }

        return rval;
    }

    public String getFilePath()
    {
        return this.filePath;
    }

    public String[] getFileList()
    {
        Wini.Section daemonSection = this.iniFileReader.get(DAEMON_SECTION);
        return daemonSection.getAll(FILE_KEY, String[].class);
    }

    public boolean hasHoursBetweenIntegrityCheck()
    {
        Wini.Section daemonSection = this.iniFileReader.get(DAEMON_SECTION);
        return daemonSection.containsKey(PERIOD_KEY);
    }

    public int getHoursBetweenIntegrityCheck()
    {
        Wini.Section daemonSection = this.iniFileReader.get(DAEMON_SECTION);
        int hours = Integer.parseInt(daemonSection.get(PERIOD_KEY));
        if (hours < 1) {
            throw new IllegalArgumentException(PERIOD_KEY + " is 0 or less, should be at least 1.");
        }

        return hours;
    }

    public boolean hasAlgorithm()
    {
        Wini.Section daemonSection = this.iniFileReader.get(DAEMON_SECTION);
        return daemonSection.containsKey(ALGORITHM_KEY);
    }

    public Options.HashAlgorithm getAlgorithm()
    {
        Wini.Section daemonSection = this.iniFileReader.get(DAEMON_SECTION);
        String algString = daemonSection.get(ALGORITHM_KEY);
        switch (algString) {
            case "MD5":
                return Options.HashAlgorithm.MD5;
            case "SHA1":
                return Options.HashAlgorithm.SHA1;
            case "SHA256":
                return Options.HashAlgorithm.SHA256;
            default:
                throw new IllegalArgumentException("Unknown '"+ ALGORITHM_KEY +"': " + algString);
        }
    }

    public boolean hasHashesFile()
    {
        Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
        return filesSection != null && filesSection.containsKey(HASHFILE_KEY);
    }

    public String getHashesFile()
    {
        Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
        if (!hasHashesFile()) {
            throw new RuntimeException("ConfigurationFile.getHashesFile called but configuration file does not have a '" + FILES_SECTION + "' or a '" + HASHFILE_KEY + "' value in it.");
        }
        return filesSection.get(HASHFILE_KEY);
    }

    public boolean hasIssuesFile()
    {
        Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
        return filesSection != null && filesSection.containsKey(INCIDENTSFILE_KEY);
    }

    public String getIssuesFile()
    {
        Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
        if (!hasIssuesFile()) {
            throw new RuntimeException("ConfigurationFile.getIncidentsFile called but configuration file does not have a '" + FILES_SECTION + "' or a '" + INCIDENTSFILE_KEY + "' value in it.");
        }
        return filesSection.get(INCIDENTSFILE_KEY);
    }

    public boolean hasIndicatorsFile()
    {
        Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
        return filesSection != null && filesSection.containsKey(INDICATORSFILE_KEY);
    }

    public String getIndicatorsFile()
    {
        Wini.Section filesSection = iniFileReader.get(FILES_SECTION);
        if (!hasIssuesFile()) {
            throw new RuntimeException("ConfigurationFile.getIndicatorsFile called but configuration file does not have a '" + FILES_SECTION + "' or a '"+ INDICATORSFILE_KEY +"' value in it.");
        }
        return filesSection.get(INDICATORSFILE_KEY);
    }
}
