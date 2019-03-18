package SSII.IntegrityHIDS.Reporting;

import SSII.IntegrityHIDS.DaemonExecutionMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Encapsula un incidente.</p>
 *
 * <p>El método <code>toString()</code> produce una cadena en el formato usado por el archivo
 * de incidentes para un incidente.</p>
 */
public class Incident {
    /** Descripción corta de los incidentes para incluir en el archivo de incidentes. */
    private static final Map<DaemonExecutionMode.IncidentCode, String> incidentCodeDescriptions;
    static
    {
        incidentCodeDescriptions = new HashMap<>();
        incidentCodeDescriptions.put(DaemonExecutionMode.IncidentCode.NO_ERROR, "No incident");
        incidentCodeDescriptions.put(DaemonExecutionMode.IncidentCode.MISSING_FILEHASH, "The following file is listed in the configuration file but not in the hashes file.");
        incidentCodeDescriptions.put(DaemonExecutionMode.IncidentCode.MISSING_FILE, "The following file is listed in the configuration file but is not found in the system.");
        incidentCodeDescriptions.put(DaemonExecutionMode.IncidentCode.HASH_MISSMATCH, "The following file's actual hash does not match the stored hash.");
        incidentCodeDescriptions.put(DaemonExecutionMode.IncidentCode.UNKNOWN_FILE, "The following file appears in the hash list but is not listed in the configuration file.");
    }

    private LocalDateTime timestamp;
    private DaemonExecutionMode.IncidentCode incidentType;
    private String filePath;

    /**
     * <p>Crea un nuevo objeto incidente.</p>
     *
     * <p>El <i>timestamp</i> será el momento actual.</p>
     *
     * @param incidentType Código de incidente.
     * @param filePath Ruta al archivo relativo al incidente.
     */
    public Incident(DaemonExecutionMode.IncidentCode incidentType, String filePath)
    {
        this.timestamp = LocalDateTime.now();
        this.incidentType = incidentType;
        this.filePath = filePath;
    }

    /**
     * <p>Crea un nuevo objeto incidente.</p>
     *
     * @param timestamp <p>Momento del incidente.</p>
     * @param incidentCode <p>Código del incidente.</p>
     * @param filePath <p>Ruta al archivo relativo al incidente.</p>
     */
    public Incident(LocalDateTime timestamp, DaemonExecutionMode.IncidentCode incidentCode, String filePath)
    {
        this.timestamp = timestamp;
        this.incidentType = incidentCode;
        this.filePath = filePath;
    }

    public LocalDateTime getTimestamp()
    {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp)
    {
        this.timestamp = timestamp;
    }

    public DaemonExecutionMode.IncidentCode getIncidentType()
    {
        return this.incidentType;
    }

    public void setIncidentType(DaemonExecutionMode.IncidentCode incidentCode)
    {
        this.incidentType = incidentCode;
    }

    public String getFilePath()
    {
        return this.filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String toString()
    {
        String rval = "";

        rval += "[" + timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "]";
        rval += "[" + incidentType.toString() + "]";
        rval += incidentCodeDescriptions.get(incidentType) + "\n";
        rval += filePath + "\n";

        return rval;
    }
}
