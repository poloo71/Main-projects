package tcppruebas.uni.TCP_v5;

import java.util.ArrayList;

/**
 * Representa la capa de Protocolo (Nivel de Transporte).
 * Su función principal es el multiplexado/demultiplexado: recibir paquetes de la red
 * y entregarlos al TSocket correspondiente basándose en los puertos.
 */
public class Protocol {

    private final SimNet network;
    // Lista protegida de todos los sockets registrados en el sistema
    private final ArrayList<TSocket> activeSockets;

    public Protocol(SimNet network) {
        this.network = network;
        this.activeSockets = new ArrayList<>();

        // Inicia el hilo en segundo plano que procesa los paquetes entrantes
        new Thread(new ReceiverTask()).start();
    }

    /** Registra un socket para que pueda recibir paquetes. */
    public void addActiveTSocket(TSocket socket) {
        synchronized (activeSockets) {
            activeSockets.add(socket);
        }
    }

    /** Elimina un socket del registro. */
    public void removeActiveTSocket(TSocket socket) {
        synchronized (activeSockets) {
            activeSockets.remove(socket);
        }
    }

    /**
     * Busca el socket destinatario para un paquete recibido.
     * Prioriza sockets conectados (puerto local y remoto coinciden) 
     * sobre sockets en escucha (solo puerto local coincide).
     */
    protected TSocket getMatchingTSocket(int localPort, int remotePort) {
        synchronized (activeSockets) {
            // 1. Buscar coincidencia exacta (Conexión establecida)
            for (TSocket socket : activeSockets) {
                if (socket.getLocalPort() == localPort && socket.getRemotePort() == remotePort) {
                    return socket;
                }
            }
            
            // 2. Buscar servidor en escucha (Listen)
            for (TSocket socket : activeSockets) {
                if (socket.getLocalPort() == localPort && socket.getRemotePort() == 0) {
                    return socket;
                }
            }
        }
        return null;
    }

    /**
     * Tarea en segundo plano ("El Cartero").
     * Lee continuamente de la red y reparte los paquetes a los sockets.
     */
    private class ReceiverTask implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // Lectura bloqueante de la red
                    TCPSegment_v5 seg = network.receive();

                    if (seg != null) {
                        // Identificación del destinatario
                        TSocket socket = getMatchingTSocket(seg.destPort, seg.sourcePort);

                        if (socket != null) {
                            // Entrega del paquete al socket para su procesamiento
                            socket.processReceivedSegment(seg);
                        } else {
                            // Paquete descartado: puerto cerrado o destino desconocido
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[PROTO] Error crítico en ReceiverTask: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}