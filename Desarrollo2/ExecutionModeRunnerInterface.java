package SSII.IntegrityHIDS;

import java.io.IOException;

/**
 * <p>Especifica la mínima interfaz que debe cumplir un modo de ejecución del programa.</p>
 *
 * <p>El módulo soporta varios modos de ejecución que han sido implementados en clases
 * independientes, pero todos basados en esta simple interfaz. El método 'main'
 * del módulo instancia un modo de ejecución y después llama al método 'execute'.</p>
 */
public interface ExecutionModeRunnerInterface {
    /**
     * <p>Ejecuta la tarea implementada por el ExecutionModeRunnerInterface.</p>
     *
     * <p>
     *     Dependiendo del modo de ejecución que implementa la clase, la ejecución
     *     será una comprobación de integridad o alguna otra acción.
     * </p>
     *
     * @param options Opciones de ejecución.
     * @param configFile Objeto que representa al archivo de configuración
     * @return Código de retorno del modo de ejecución tras ejecutarse.
     * @throws IOException Un error inesperado de acceso a alguno de los archivos de <pre>options</pre>.
     */
    int execute(Options options, ConfigurationFile configFile) throws IOException;
}
