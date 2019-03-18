package SSII.IntegrityHIDS;

import SSII.IntegrityHIDS.Reporting.IntegrityCheck;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ReportExecutionMode implements ExecutionModeRunnerInterface {

    @Override
    public int execute(Options options, ConfigurationFile configFile) throws IOException {
        IntegrityCheck[] sessions = IntegrityCheck.parseIncidentsFile(options.getIncidentsFile());

        System.out.println("Readed file " + options.getIncidentsFile());
        System.out.println("The file contains " + sessions.length + " integrity check logs.");

        BufferedWriter outputWriter = null;
        try {
            outputWriter = new BufferedWriter(new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(options.getIndicatorsFile()), "UTF-8")
            ));
            outputWriter.write("Report started at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            float previousRatio = -1;
            for (IntegrityCheck session : sessions) {
                float currentRatio = ((float) session.getIncidentList().size()) / (float) session.getFileCount();

                String report = getReport(session, previousRatio, currentRatio);

                System.out.println(report);
                outputWriter.write(report);

                previousRatio = currentRatio;
            }
        } finally {
            if (outputWriter != null) {
                outputWriter.flush();
                outputWriter.close();
            }
        }

        return 0;
    }

    private String getReport(IntegrityCheck session, float previousRatio, float currentRatio) {
        String report = "\n";
        report += "Check date: " + session.getStartTime() + "\n";
        report += "  Configured     : " + session.getFileCount() + "\n";
        report += "  Hash not found : " + session.getIncidents(DaemonExecutionMode.IncidentCode.MISSING_FILEHASH).size() + "\n";
        report += "  File not found : " + session.getIncidents(DaemonExecutionMode.IncidentCode.MISSING_FILE).size() + "\n";
        report += "  Integrity fail : " + session.getIncidents(DaemonExecutionMode.IncidentCode.HASH_MISSMATCH).size() + "\n";
        report += "  Unknown file   : " + session.getIncidents(DaemonExecutionMode.IncidentCode.UNKNOWN_FILE).size() + "\n";
        report += "  Failure ratio  : " + (currentRatio*100) + "%\n";
        if (previousRatio != -1) {
            String tendency;
            if (currentRatio < previousRatio) {
                tendency = "DOWN"; // Mejoramos, menos fallos
            }
            else if (currentRatio == previousRatio) {
                tendency = "EQUAL";
            }
            else {
                tendency = "UP";   // Empeoramos, más fallos
            }
            report += "  Tendency       : " + tendency + "\n";
        }
        return report;
    }
}
