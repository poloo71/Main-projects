package tcppruebas.uni.TCP_v5;

/**
 * Enumeración que define los posibles estados de un TSocket
 * durante el ciclo de vida de una conexión TCP.
 */
public enum State {
    /** Estado inicial. El socket no existe o está cerrado. */
    CLOSED,
    
    /** (Servidor) Esperando una petición de conexión entrante (Passive Open). */
    LISTEN,
    
    /** (Cliente) Se ha enviado un SYN y se espera respuesta (Active Open). */
    SYN_SENT,
    
    /** La conexión está abierta y se pueden transmitir datos bidireccionalmente. */
    ESTABLISHED,
    
    /** Se ha solicitado el cierre (local) y se espera confirmación del remoto. */
    FIN_WAIT,
    
    /** El remoto ha solicitado cerrar. Esperamos a que la aplicación local termine. */
    CLOSE_WAIT
}