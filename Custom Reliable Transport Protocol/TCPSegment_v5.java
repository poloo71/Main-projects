package tcppruebas.uni.TCP_v5;

/**
 * Representa un segmento TCP (Unidad de datos del protocolo).
 * Contiene las cabeceras de control y el payload de datos.
 */
public class TCPSegment_v5 {
    
    // --- Flags de Control (Cabecera) ---
    public boolean syn; // Synchronize: Solicitud de inicio de conexión
    public boolean fin; // Finish: Solicitud de fin de conexión
    public boolean psh; // Push: Indica que el segmento contiene datos de aplicación
    public boolean ack; // Acknowledgment: Indica que el campo ackNum es válido

    // --- Control de Flujo y Fiabilidad ---
    public int seqNum;  // Número de secuencia del primer byte de datos de este segmento
    public int ackNum;  // Número de secuencia del siguiente byte que se espera recibir
    public int wnd;     // Ventana de recepción: espacio libre actual en el buffer del receptor
    
    // --- Carga útil ---
    public byte[] data; // Datos de la aplicación (Payload)

    // --- Direccionamiento ---
    public int sourcePort; // Puerto del emisor
    public int destPort;   // Puerto del receptor

    /** Constructor por defecto: Inicializa un segmento vacío sin datos. */
    public TCPSegment_v5() {
        this.data = new byte[0];
    }
    
    /** Devuelve una representación en texto del segmento para facilitar la depuración. */
    @Override
    public String toString() {
        String flags = "";
        if (syn) flags += "SYN ";
        if (fin) flags += "FIN ";
        if (psh) flags += "PSH ";
        if (ack) flags += "ACK ";
        return String.format("[%s src=%d dst=%d seq=%d ack=%d wnd=%d len=%d]", 
                flags.trim(), sourcePort, destPort, seqNum, ackNum, wnd, data.length);
    }
}