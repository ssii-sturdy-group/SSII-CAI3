package SSII.IntegrityHIDS.Reporting;

import SSII.IntegrityHIDS.DaemonExecutionMode;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>Encapsula los resultados de una comprobación de integridad.</p>
 *
 * <p>Esta clase es usada por <code>DaemonExecutionMode</code> y <code>ReportExecutionMode</code>
 * para escribir y leer archivos de incidencias.</p>
 */
public class IntegrityCheck {
    /**
     * Devuelve una cadena que indica un inicio de comprobación de integridad en el formato de los archivos de incidentes.
     *
     * @param checkStart Momento en el que comienza la comprobación de integridad.
     * @param fileCount Número de archivos que se van a comprobar.
     * @return Cadena indicando el inicio de comprobación.
     */
    public static String getFormattedStartHeader(LocalDateTime checkStart, int fileCount)
    {
        return "-- " + checkStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " -- Known files: " + fileCount + "\n";
    }

    /**
     * Lee un archivo de incidentes y devuelve un array con las sesiones de comprobación.
     *
     * @param filePath Ruta al archivo de incidentes.
     * @return Array de sesiones de comprobación.
     * @throws IOException El archivo de incidentes no existe o no se puede leer.
     */
    public static IntegrityCheck[] parseIncidentsFile(String filePath) throws IOException {
        ArrayList<IntegrityCheck> sessions = new ArrayList<>();
        BufferedReader fileReader = null;
        try {
            InputStream fis = new FileInputStream(filePath);
            InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
            fileReader = new BufferedReader(isr);

            String line;
            while ((line = fileReader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                else if (line.startsWith("-- ")) {
                    sessions.add(parseIntegrityCheck(fileReader, line));
                }
                else {
                    throw new IOException("The following line is invalid, Integrity check session start expected:\n" + line);
                }
            }
        } finally {
            if (fileReader != null)
                fileReader.close();
        }

        IntegrityCheck[] rval = new IntegrityCheck[sessions.size()];
        rval = sessions.toArray(rval);
        return rval;
    }

    /**
     * Parsea una sesión de comprobación de integridad.
     *
     * @param fileReader Objeto para leer del archivo de incidentes.
     * @param sessionStartLine Primera línea de la sesión.
     * @return Un objeto IntegrityCheck con los resultados de la sesión.
     * @throws IOException Error al leer del archivo o al parsearlo.
     */
    private static IntegrityCheck parseIntegrityCheck(BufferedReader fileReader, String sessionStartLine) throws IOException {
        // Parse first line
        LocalDateTime checkStartTime = parseTimestampFromFirstLine(sessionStartLine);
        int fileCount = parseFileCountFromFirstLine(sessionStartLine);
        ArrayList<Incident> incidents = new ArrayList<>();

        // Parse incidents until empty line
        String line;
        while ((line = fileReader.readLine()) != null && !line.isEmpty()) {
            Incident incident = parseIncidentFromFile(fileReader, line);
            incidents.add(incident);
        }

        return new IntegrityCheck(checkStartTime, fileCount, incidents);
    }
    private static LocalDateTime parseTimestampFromFirstLine(String sessionStartLine) throws IOException {
        int i = sessionStartLine.indexOf(" -- Known files: ");
        if (i == -1) {
            throw new IOException("Invalid integrity check start line: " + sessionStartLine);
        }
        String tmp = sessionStartLine.substring(0, i);
        tmp = tmp.replace("-- ", "").trim();

        try {
            return LocalDateTime.parse(tmp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException dtpe) {
            throw new IOException("Invalid date format in integrity check start line: " + sessionStartLine);
        }
    }
    private static int parseFileCountFromFirstLine(String sessionStartLine) throws IOException {
        int i = sessionStartLine.indexOf(" -- Known files: ");
        if (i == -1) {
            throw new IOException("Invalid integrity check start line: " + sessionStartLine);
        }
        String tmp = sessionStartLine.substring(i).replace(" -- Known files: ", "").trim();
        try {
            int rval = Integer.parseInt(tmp);
            if (rval < 0) {
                throw new IOException("Invalid file count in integrity check start line (negative): " + sessionStartLine);
            }
            return rval;
        } catch (NumberFormatException nfe) {
            throw new IOException("Invalid file count in integrity check start line (not a number): " + sessionStartLine);
        }
    }
    private static Incident parseIncidentFromFile(BufferedReader fileReader, String firstLine) throws IOException {
        int i = firstLine.lastIndexOf(']');
        if (i < 0) {
            throw new IOException("Invalid incident line: " + firstLine);
        }
        String tmp = firstLine.substring(0, i + 1).trim();
        String[] parts = tmp.split("\\Q][\\E");
        if  (parts.length != 2)
            throw new IOException("Invalid incident line: " + firstLine);

        LocalDateTime timestamp = LocalDateTime.parse(parts[0].substring(1), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String errorString = parts[1].substring(0, parts[1].length()-1);
        String fileName =  fileReader.readLine();

        DaemonExecutionMode.IncidentCode errorCode;
        if (errorString.equals(DaemonExecutionMode.IncidentCode.MISSING_FILEHASH.toString()))
            errorCode = DaemonExecutionMode.IncidentCode.MISSING_FILEHASH;
        else if (errorString.equals(DaemonExecutionMode.IncidentCode.MISSING_FILE.toString()))
            errorCode = DaemonExecutionMode.IncidentCode.MISSING_FILE;
        else if (errorString.equals(DaemonExecutionMode.IncidentCode.HASH_MISSMATCH.toString()))
            errorCode = DaemonExecutionMode.IncidentCode.HASH_MISSMATCH;
        else if (errorString.equals(DaemonExecutionMode.IncidentCode.UNKNOWN_FILE.toString()))
            errorCode = DaemonExecutionMode.IncidentCode.UNKNOWN_FILE;
        else {
            throw new IOException("Unknown error code in incident line: " + firstLine);
        }

        return new Incident(timestamp, errorCode, fileName);
    }

    private List<Incident> incidentList;
    private LocalDateTime startTimestamp;
    private int fileCount;

    /**
     * Construye un objeto IntegrityCheck.
     *
     * @param startTime Momento en que comenzó la comprobación de integridad.
     * @param fileCount Número de archivos que se pretendían comprobar.
     * @param incidents Incidentes ocurridos.
     */
    public IntegrityCheck(LocalDateTime startTime, int fileCount, Iterable<Incident> incidents)
    {
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount can not be less than 0.");
        }

        this.startTimestamp = startTime;
        this.fileCount = fileCount;
        incidentList = new ArrayList<Incident>((Collection<? extends Incident>) incidents);
        sortIncidentList(incidentList);
    }

    private void sortIncidentList(List<Incident> list)
    {
        list.sort((incident, t1) -> incident.getTimestamp().compareTo(t1.getTimestamp()));
    }

    public List<Incident> getIncidentList()
    {
        return this.incidentList;
    }

    public int getFileCount()
    {
        return fileCount;
    }

    public void setFileCount(int fileCount)
    {
        this.fileCount = fileCount;
    }

    public LocalDateTime getStartTime()
    {
        return this.startTimestamp;
    }

    public void setStartTime(LocalDateTime startTime)
    {
        this.startTimestamp = startTime;
    }

    /**
     * Devuelve una lista de incidentes de un tipo determinado.
     *
     * @param code Tipo de incidente que deben tener los incidentes devueltos.
     * @return Lista de incidentes del tipo indicado en esta comprobación de integridad.
     */
    public List<Incident> getIncidents(DaemonExecutionMode.IncidentCode code)
    {
        ArrayList<Incident> rval = new ArrayList<>();
        for (Incident incident : incidentList) {
            if (incident.getIncidentType() == code)
                rval.add(incident);
        }

        sortIncidentList(rval);
        return rval;
    }

    /**
     * Devuelve una cadena con el formato del archivo de incidentes representando los resultados de la comprobación.
     *
     * @return Texto de la sesión de comprobación.
     */
    public String toString()
    {
        String rval = "";

        rval += getFormattedStartHeader(this.getStartTime(), this.getFileCount());
        for (Incident incident : this.getIncidentList()) {
            rval += incident.toString();
        }

        return rval;
    }
}
