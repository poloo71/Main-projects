package tcppruebas.uni.TCP_v5;

import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import tcppruebas.uni.util.CircularQueue;

/**
 * Implementación de un Socket TCP simplificado (v5).
 * Características:
 * - Orientado a conexión (Handshake 3-vías).
 * - Fiabilidad mediante ARQ (Retransmisión por Timeout).
 * - Control de Flujo (Ventana Deslizante).
 * - Ordenación de paquetes (Buffer de desordenados).
 * - Thread-safe mediante Monitores (Locks y Conditions).
 */
public class TSocket {

    private final SimNet network;
    private final int localPort;
    private int remotePort;

    // --- Monitores para sincronización ---
    protected final ReentrantLock lock = new ReentrantLock();
    protected final Condition esperoAck = lock.newCondition();      // Bloqueo del emisor si la ventana está llena
    protected final Condition dataDisponible = lock.newCondition(); // Bloqueo del receptor si no hay datos
    protected final Condition esperoConexion = lock.newCondition(); // Bloqueo durante el handshake

    // --- Variables de Estado del Protocolo (Sliding Window) ---
    private int snd_next;   // Siguiente número de secuencia a enviar
    private int snd_unack;  // Número de secuencia del paquete más antiguo sin confirmar
    private int rcv_next;   // Siguiente número de secuencia que se espera recibir
    private int snd_wnd;    // Ventana anunciada por el receptor remoto

    // --- Estructuras de Memoria ---
    // Almacena copias de los segmentos enviados esperando ACK (para retransmitir)
    private ConcurrentHashMap<Integer, TCPSegment_v5> unackedSegments = new ConcurrentHashMap<>();
    // Almacena segmentos recibidos fuera de orden (buffer de reordenación)
    private TreeMap<Integer, TCPSegment_v5> out_of_order_segs = new TreeMap<>();

    // --- Gestión de Temporizadores (ARQ) ---
    private Timer timerService = new Timer(true);
    private TimerTask sndRtTimer = null;
    private final long RTO = 500; // Retransmission TimeOut (ms)

    private int retransmissionCount = 0;
    private final int MAX_RETRIES = 5; // Límite de intentos antes de abortar

    // --- Configuración ---
    private final CircularQueue<TCPSegment_v5> rcvQueue; // Buffer de recepción para la aplicación
    private final int capacidadColaRecepcion = 50;
    private final int MSS = 30; // Maximum Segment Size

    private State state; // Estado actual de la máquina de estados

    public TSocket(SimNet network, int localPort) {
        this.network = network;
        this.localPort = localPort;
        this.rcvQueue = new CircularQueue<>(capacidadColaRecepcion);
        this.state = State.CLOSED;
        this.snd_next = 0;
        this.snd_unack = 0;
        this.rcv_next = 0;
        this.snd_wnd = 1;
    }

    // =========================================================
    //                 GESTIÓN DE TIMEOUTS (ARQ)
    // =========================================================

    /** Inicia o reinicia el temporizador de retransmisión. */
    private void startRTO() {
        if (state == State.CLOSED) return;
        if (sndRtTimer != null) sndRtTimer.cancel();

        sndRtTimer = new TimerTask() {
            @Override
            public void run() {
                timeout();
            }
        };
        try {
            timerService.schedule(sndRtTimer, RTO);
        } catch (IllegalStateException e) { }
    }

    /** Detiene el temporizador. */
    private void stopRTO() {
        if (sndRtTimer != null) {
            sndRtTimer.cancel();
            sndRtTimer = null;
        }
    }

    /**
     * Lógica ejecutada cuando salta el temporizador.
     * Retransmite el segmento más antiguo no confirmado.
     */
    private void timeout() {
        lock.lock();
        try {
            if (state == State.CLOSED) {
                stopRTO();
                return;
            }

            // Si hay datos pendientes de ACK
            if (snd_unack < snd_next && !unackedSegments.isEmpty()) {
                // Verificar límite de reintentos para evitar bucles infinitos
                if (retransmissionCount >= MAX_RETRIES) {
                    System.err.println("   [!!!] TIMEOUT: Demasiados intentos. Cerrando conexión forzosamente.");
                    state = State.CLOSED;
                    stopRTO();
                    esperoAck.signalAll(); // Liberar hilos bloqueados
                    return;
                }

                // Retransmitir
                TCPSegment_v5 seg = unackedSegments.get(snd_unack);
                if (seg != null) {
                    System.out.println("   [!!!] TIMEOUT: Retransmitiendo seq=" + seg.seqNum + " (Intento " + (retransmissionCount + 1) + ")");
                    network.send(seg);
                    retransmissionCount++;
                    startRTO(); // Reiniciar cuenta atrás
                }
            }
        } catch (Exception e) {
            System.err.println("[TIMER] Error: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    // =========================================================
    //               API PÚBLICA (CLIENTE / SERVIDOR)
    // =========================================================

    /** (Cliente) Inicia la conexión con el servidor (3-Way Handshake). */
    public void connect(int remotePort) {
        try {
            lock.lock();
            this.remotePort = remotePort;
            
            // 1. Enviar SYN
            TCPSegment_v5 seg = new TCPSegment_v5();
            seg.syn = true;
            seg.sourcePort = localPort;
            seg.destPort = remotePort;
            seg.seqNum = snd_next;

            unackedSegments.put(snd_next, seg);
            retransmissionCount = 0;

            state = State.SYN_SENT; // Cambio de estado antes de enviar
            System.out.println("[CLIENT]: Iniciando conexión (SYN)...");

            network.send(seg);
            startRTO();

            snd_next++;

            // 2. Esperar a que la conexión se establezca (SYN+ACK recibido)
            while (state != State.ESTABLISHED) {
                esperoConexion.awaitUninterruptibly();
            }
            stopRTO();
        } finally {
            lock.unlock();
        }
    }

    /** (Servidor) Espera pasivamente a recibir una conexión. */
    public void listen() {
        try {
            lock.lock();
            state = State.LISTEN;
            System.out.println("[SERVER]: Escuchando en puerto " + localPort + "...");
            
            // Bloqueo hasta completar el handshake
            while (state != State.ESTABLISHED) {
                esperoConexion.awaitUninterruptibly();
            }
            System.out.println("[SERVER]: ¡Conexión ESTABLECIDA!");
        } finally {
            lock.unlock();
        }
    }

    /**
     * Envía datos a través de la conexión.
     * Utiliza Ventana Deslizante y bloquea si no hay espacio en la ventana.
     */
    public void sendData(byte[] datosAtransmitir) {
        int offset = 0;
        lock.lock();
        try {
            while (offset < datosAtransmitir.length) {
                // Cálculo de la ventana efectiva
                int bytesEnVuelo = snd_next - snd_unack;
                int ventanaEfectiva = snd_wnd - bytesEnVuelo;

                // Bloqueo si la ventana está llena (Control de Flujo)
                while (ventanaEfectiva <= 0) {
                    if (snd_wnd == 0) {
                        ventanaEfectiva = 1; // Modo sonda (Probe) para desbloquear ventana 0
                        break;
                    }
                    try {
                        esperoAck.await(); // Esperar ACK que libere ventana
                        if (state == State.CLOSED) return;
                        
                        bytesEnVuelo = snd_next - snd_unack;
                        ventanaEfectiva = snd_wnd - bytesEnVuelo;
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                // Segmentación (MSS)
                int aEnviar = Math.min(datosAtransmitir.length - offset, Math.min(MSS, ventanaEfectiva));
                byte[] fragmento = new byte[aEnviar];
                System.arraycopy(datosAtransmitir, offset, fragmento, 0, aEnviar);

                // Creación del segmento
                TCPSegment_v5 segmento = new TCPSegment_v5();
                segmento.sourcePort = localPort;
                segmento.destPort = remotePort;
                segmento.seqNum = snd_next;
                segmento.data = fragmento;
                segmento.psh = true; // Push flag para datos
                segmento.ack = true; // Piggybacking del ACK
                segmento.ackNum = rcv_next;
                segmento.wnd = getRcvWindow();

                // Almacenar para posibles retransmisiones
                unackedSegments.put(snd_next, segmento);
                retransmissionCount = 0;

                if (snd_next == snd_unack) startRTO(); // Iniciar timer si es el primer paquete en vuelo

                System.out.println("[SND]: Enviando seq=" + snd_next + " (" + aEnviar + " bytes).");
                network.send(segmento);

                snd_next += aEnviar;
                offset += aEnviar;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Lee datos recibidos.
     * Bloquea si no hay datos disponibles en el buffer.
     */
    public int receiveData(byte[] datosAguardar) {
        int bytesLeidos = 0;
        lock.lock();
        try {
            while (bytesLeidos < datosAguardar.length) {
                // Si la cola está vacía, esperar
                if (rcvQueue.empty()) {
                    if (state == State.CLOSE_WAIT) break; // Si el otro lado cerró, terminamos
                    dataDisponible.awaitUninterruptibly();
                }
                if (rcvQueue.empty()) continue;

                // Consumir datos de la cola circular
                TCPSegment_v5 seg = rcvQueue.get();
                int aCopiar = Math.min(datosAguardar.length - bytesLeidos, seg.data.length);
                System.arraycopy(seg.data, 0, datosAguardar, bytesLeidos, aCopiar);
                bytesLeidos += aCopiar;

                // Enviar ACK de actualización de ventana
                if (rcvQueue.empty()) sendAck();
            }
        } finally {
            lock.unlock();
        }
        return bytesLeidos;
    }

    /** Cierra la conexión enviando un paquete FIN. */
    public void close() {
        lock.lock();
        try {
            if (state == State.CLOSED) return;

            TCPSegment_v5 fin = new TCPSegment_v5();
            fin.fin = true;
            fin.ack = true;
            fin.ackNum = rcv_next;
            fin.seqNum = snd_next;
            fin.sourcePort = localPort;
            fin.destPort = remotePort;

            unackedSegments.put(snd_next, fin);
            retransmissionCount = 0;

            state = State.FIN_WAIT; // Cambio de estado a espera de fin

            network.send(fin);
            startRTO();

            snd_next++;
        } finally {
            lock.unlock();
        }
    }

    // =========================================================
    //                 MÉTODOS AUXILIARES
    // =========================================================

    private void sendAck() {
        TCPSegment_v5 ack = new TCPSegment_v5();
        ack.ack = true;
        ack.ackNum = rcv_next;
        ack.wnd = getRcvWindow(); // Anunciar espacio libre
        ack.sourcePort = localPort;
        ack.destPort = remotePort;
        network.send(ack);
    }

    private int getRcvWindow() {
        return Math.max(0, (capacidadColaRecepcion - rcvQueue.size()) * MSS);
    }

    public boolean areAllSegmentsConfirmed() {
        lock.lock();
        try {
            return snd_unack == snd_next;
        } finally {
            lock.unlock();
        }
    }

    public int getLocalPort() { return localPort; }
    public int getRemotePort() { return remotePort; }

    // =========================================================
    //          MÁQUINA DE ESTADOS (Procesamiento de Paquetes)
    // =========================================================

    /**
     * Método principal que procesa cada paquete recibido según el estado actual.
     * Implementa la lógica de la máquina de estados TCP.
     */
    public void processReceivedSegment(TCPSegment_v5 s) {
        lock.lock();
        try {
            // Caso Especial: Si el socket está cerrado pero recibe un FIN retransmitido,
            // responde con ACK para permitir que el otro extremo cierre limpiamente.
            if (state == State.CLOSED) {
                if (s.fin) {
                    TCPSegment_v5 ack = new TCPSegment_v5();
                    ack.ack = true;
                    ack.ackNum = s.seqNum + 1;
                    ack.seqNum = snd_next;
                    ack.sourcePort = localPort;
                    ack.destPort = remotePort;
                    network.send(ack);
                }
                return;
            }

            switch (state) {
                // --- FASE DE CONEXIÓN ---
                case LISTEN:
                    if (s.syn) {
                        remotePort = s.sourcePort;
                        // Responder con SYN+ACK
                        TCPSegment_v5 resp = new TCPSegment_v5();
                        resp.syn = true;
                        resp.ack = true;
                        resp.sourcePort = localPort;
                        resp.destPort = remotePort;
                        resp.seqNum = snd_next;
                        resp.ackNum = s.seqNum + 1;
                        resp.wnd = getRcvWindow();
                        
                        unackedSegments.put(snd_next, resp);
                        rcv_next = resp.ackNum;
                        snd_next++;
                        
                        network.send(resp);
                        startRTO();
                        state = State.ESTABLISHED;
                        esperoConexion.signalAll();
                    }
                    break;

                case SYN_SENT:
                    if (s.syn && s.ack) {
                        // Handshake completado: Enviar ACK final
                        rcv_next = s.seqNum + 1;
                        snd_unack = s.ackNum;
                        snd_wnd = s.wnd;
                        stopRTO();
                        unackedSegments.clear();
                        
                        TCPSegment_v5 ack = new TCPSegment_v5();
                        ack.ack = true;
                        ack.seqNum = snd_next;
                        ack.ackNum = rcv_next;
                        ack.sourcePort = localPort;
                        ack.destPort = remotePort;
                        network.send(ack);
                        
                        state = State.ESTABLISHED;
                        esperoConexion.signalAll();
                    }
                    break;

                // --- FASE DE TRANSFERENCIA DE DATOS ---
                case ESTABLISHED:
                    // 1. Procesar ACKs (Liberar ventana y buffer de retransmisión)
                    if (s.ack) {
                        snd_wnd = s.wnd;
                        if (s.ackNum > snd_unack) {
                            for (int i = snd_unack; i < s.ackNum; i++) unackedSegments.remove(i);
                            snd_unack = s.ackNum;
                            retransmissionCount = 0;
                            stopRTO();
                            if (snd_unack < snd_next) startRTO(); // Reiniciar si quedan datos
                            esperoAck.signalAll(); // Desbloquear emisor
                        }
                    }

                    // 2. Procesar Datos entrantes
                    boolean processed = false;
                    if (s.seqNum == rcv_next) {
                        // A. Paquete en orden
                        if (!rcvQueue.full()) {
                            if (s.psh || s.data.length > 0) {
                                rcvQueue.put(s);
                                rcv_next += s.data.length;
                                dataDisponible.signal();
                            }
                            if (s.fin) {
                                rcv_next++;
                                state = State.CLOSE_WAIT; // Inicio de cierre pasivo
                                dataDisponible.signalAll();
                            }
                            processed = true;
                        }
                    } else if (s.seqNum > rcv_next) {
                        // B. Paquete fuera de orden (Hueco detectado) -> Guardar en buffer
                        System.out.println("[RCV]: Desorden (Llegó " + s.seqNum + ", esperaba " + rcv_next + "). Guardando...");
                        out_of_order_segs.put(s.seqNum, s);
                        sendAck(); // ACK duplicado para solicitar retransmisión rápida
                        return;
                    }

                    // 3. Revisar buffer de desordenados (Rellenar huecos)
                    if (processed) {
                        while (out_of_order_segs.containsKey(rcv_next)) {
                            TCPSegment_v5 sig = out_of_order_segs.get(rcv_next);
                            if (!rcvQueue.full()) {
                                out_of_order_segs.remove(rcv_next);
                                System.out.println("[RCV]: Recuperando seq=" + sig.seqNum + " del buffer");
                                if (sig.psh || sig.data.length > 0) {
                                    rcvQueue.put(sig);
                                    rcv_next += sig.data.length;
                                    dataDisponible.signal();
                                }
                                if (sig.fin) {
                                    rcv_next++;
                                    state = State.CLOSE_WAIT;
                                    dataDisponible.signalAll();
                                }
                            } else break;
                        }
                        sendAck(); // Confirmar todo lo procesado
                    } else {
                        sendAck(); // Confirmar recepción (incluso si es duplicado)
                    }
                    break;

                // --- FASE DE CIERRE ---
                case FIN_WAIT:
                    // Procesar ACKs pendientes para asegurar entrega fiable antes de cerrar
                    if (s.ack) {
                        if (s.ackNum > snd_unack) {
                            for (int i = snd_unack; i < s.ackNum; i++) unackedSegments.remove(i);
                            snd_unack = s.ackNum;
                            retransmissionCount = 0;
                            stopRTO();
                            if (snd_unack < snd_next) startRTO();
                        }
                    }

                    // Si recibimos el FIN del otro lado, cerramos completamente
                    if (s.fin) {
                        rcv_next++;
                        state = State.CLOSED;
                        stopRTO();
                    }
                    sendAck(); // Siempre responder en estados de cierre
                    break;

                case CLOSE_WAIT:
                    sendAck();
                    break;

                case CLOSED:
                    break;
            }
        } finally {
            lock.unlock();
        }
    }
}