package SSII.IntegrityHIDS;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * <p>Clase que encapsula el código necesario para ejecutar la utilidad desde línea de comandos.</p>
 *
 * <p>El programa soporta varios modos de ejecución. El trabajo de esta clase es:</p>
 * <ul>
 *  <li>Determinar modo de ejecución a partir de los argumentos.</li>
 *  <li>Construir un objeto Options a partir de los argumentos y el archivo de configuración.</li>
 *  <li>Instanciar un ExecutionModeRunnerInterface según el modo de ejecución determinado antes.</li>
 *  <li>Ejecutarlo.</li>
 * </ul>
 *
 * <p>Los modos de ejecución disponibles son:</p>
 * <ul>
 *     <li>help: Muestra ayuda en consola</li>
 *     <li>first-run: Hashea los archivos indicados en el archivo de configuración y lo guarda en
 *         el archivo de hashes.</li>
 *     <li>integrity-check : Comprueba que los archivos listados en la configuración tienen el mismo hash
 *         ahora que cuando se ejecutó el 'first-run'. Guarda incidencias en el archivo configruado para ello.</li>
 *     <li>daemon: Hace un 'integrity-check' cada x minutos (indicado en configuración, por defecto 60).</li>
 *     <li>report: Lee el archivo de incidencias y reconstruye los resultados de los integrity-check.</li>
 * </ul>
 */
public class Main {
    /**
     * <p>Punto de entrada para el módulo en modo línea de órdenes.</p>
     *
     * @see Options Argumentos de entrada
     * @param args Argumentos de entrada.
     */
    public static void main(String[] args) {
        // Parsear argumentos de entrada
        Options options;
        try {
            options = Options.fromArguments(args);
        } catch (IllegalArgumentException iae) {
            System.err.println("Error while parsing input arguments.");
            System.err.println(iae.getMessage());
            System.out.println("Use 'help' as the sole argument to learn about the input accepted by this program.");
            System.exit(1);
            return;
        }

        // Si los argumentos nos dicen que el usuario quiere ayuda, mostrarla y salir.
        if (options.getExecutionMode() == Options.ExecutionMode.HELP) {
            showHelp();
            return;
        }
        System.out.println("Execution mode: " + options.getExecutionMode());

        // En cualquier otro caso, tenemos que leer el archivo de configuración
        ConfigurationFile configFile;
        try {
            // Comprueba que el archivo existe, el path es canónico, legible, ...
            options.checkConfigFile();
            // Leemos la configuración
            System.out.println("Reading configuration file: " + options.getConfigFile());
            configFile = new ConfigurationFile(options.getConfigFile());
            // Aplicamos las opciones al objeto de opciones
            options = configFile.applyOptionsTo(options);
        }
        catch (IllegalArgumentException iae) {
            System.err.println("Error parsing configuration file " + options.getConfigFile());
            System.err.println(iae.getMessage());
            System.exit(1);
            return;
        }
        catch (FileNotFoundException fnfe) {
            System.err.println("Error: the configuration file doesn't exists.");
            System.err.println(fnfe.getMessage());
            System.exit(1);
            return;
        }
        catch (IOException ioe) {
            System.err.println("Input/Output error while reading configuration file " + options.getConfigFile());
            System.err.println(ioe.getMessage());
            System.exit(1);
            return;
        }
        catch (Exception e) {
            System.err.println("Unexpected error while parsing configuration file " + options.getConfigFile());
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        try {
            // Comprobamos que los archivos de hashes, incidentes e indicaciones existen, son paths canónicos
            // y son legibles o escribibles, dependiendo del modo de ejecución.
            options.checkFiles();
        } catch (Exception e) {
            System.err.println("Error while checking configured input and output files.");
            System.err.println("Check the following files exists and are readable or writable, depending on the execution mode.");
            System.err.println("Hashes file    : " + options.getHashesFile());
            System.err.println("Incidents file : " + options.getIncidentsFile());
            System.err.println("Indicators file: " + options.getIndicatorsFile());
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        try {
            // Instanciamos el ExecutionModeRunnerInterface apropiado según las opciones
            ExecutionModeRunnerInterface runner = getRunner(options);
            // Ejecutamos el ExecutionModeRunnerInterface y usamos su código de retorno como
            // código de retorno del programa.
            System.exit(runner.execute(options, configFile));
        }
        catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            for (StackTraceElement elem : e.getStackTrace())
                System.err.println(elem.toString());
            System.exit(1);
        }
    }

    /**
     * <p>Devuelve una instancia de un objeto derivado de ExecutionModeRunnerInterface basándose en las opciones.</p>
     *
     * <p>Según el valor de Options.getExecutionMode(), instanciará una de las clases que implementan la
     * interfaz comentada.</p>
     *
     * @param options Opciones de esta ejecución.
     * @return Instancia de un ExecutionModeRunnerInterface que implementa el modo de ejecución indicado en options.
     */
    private static ExecutionModeRunnerInterface getRunner(Options options) {
        switch (options.getExecutionMode()) {
            case HASH:
                return new HashExecutionMode();
            case DAEMON:
            case INTEGRITY_CHECK:
                // Ambos estan implementados por DaemonExecutionMode
                return new DaemonExecutionMode();
            case HASH_AND_DAEMON:
                return new HashAndDaemonExecutionMode();
            case REPORT:
                return new ReportExecutionMode();
            default:
                throw new RuntimeException("Unknown execution mode: " + options.getExecutionMode().toString());
        }
    }

    private static void showHelp()
    {
        System.out.println("ssii-integrityhids - A simple HIDS based on integrity checks");
        System.out.println(" by Rafael Gálvez-Cañero for the SSII course of Computer Grade in University of Seville (16/17)");
        System.out.println();
        System.out.println("Usage: ssii-integrityhids [execution mode] [options]");
        System.out.println();
        System.out.println("Available execution modes:");
        System.out.println(" help             Shows this information.");
        System.out.println(" hash             Computes the hashes of the files listed in the configuration file ");
        System.out.println("                  and saves them in the hashes file.");
        System.out.println(" integrity-check  Reads the hashes file and checks the files in the configuration file ");
        System.out.println("                  have the same hashes. Any incident is logged into the incidents file.");
        System.out.println(" daemon           Enters in a loop where it does an integrity check and then waits a ");
        System.out.println("                  number of limits indicated by the configuration file before doing another ");
        System.out.println("                  integrity check. This loop goes on until interrupted by the user.");
        System.out.println(" hash-and-check   This is pretty much the same as executing 'hash' and immediately after ");
        System.out.println("                  'daemon'. It calculates the hashes of the files indicated by the ");
        System.out.println("                  configuration file and then enters in daemon mode.");
        System.out.println(" report           Reads the incidents file and rebuilds the history of integrity checks, ");
        System.out.println("                  writing into the indicators file the fail ratio and the tendency.");
        System.out.println();
    }
}
