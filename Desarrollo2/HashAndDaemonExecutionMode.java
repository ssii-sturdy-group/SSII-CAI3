package SSII.IntegrityHIDS;

import java.io.IOException;
import java.util.HashMap;

/**
 * <p>Implementa el modo de ejecución <i>hash-and-check</i>.</p>
 *
 * <p>Este modo de ejecución es el equivalente a ejecutar el modo 'hash'
 * seguido inmediatamente por el modo 'daemon'.</p>
 *
 * @see HashExecutionMode
 * @see DaemonExecutionMode
 */
public class HashAndDaemonExecutionMode implements ExecutionModeRunnerInterface {
    /**
     * {@inheritDoc}
     */
    @Override
    public int execute(Options options, ConfigurationFile configFile) throws IOException {
        HashMap<String, String> fileHashMap;
        {
            HashExecutionMode hashMode = new HashExecutionMode();
            int rval = hashMode.execute(options, configFile);
            if (rval != 0)
                return rval;
            else
                fileHashMap = new HashMap<>(hashMode.getFileHashMap());
        }

        DaemonExecutionMode daemonMode = new DaemonExecutionMode();
        return daemonMode.executeDaemonMode(options, configFile, fileHashMap);
    }
}
