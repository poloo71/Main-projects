package tcppruebas.uni.TCP_v5;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import tcppruebas.uni.util.CircularQueue;

/**
 * Simula una red de transmisión de paquetes.
 * Introduce pérdidas de paquetes de forma aleatoria para probar la robustez del protocolo.
 * Utiliza una cola circular protegida por monitores para simular el medio físico.
 */
public class SimNet {

    private final CircularQueue<TCPSegment_v5> queue; 
    private final ReentrantLock lock;
    private final Condition vcVacio; // Esperar si la red está vacía (Receive)
    private final Condition vcLleno; // Esperar si la red está saturada (Send)
    private final double lossRate;   // Probabilidad de pérdida (0.0 a 1.0)
    
    private final int NET_CAPACITY = 10; // Capacidad máxima de paquetes en tránsito

    public SimNet(double lossRate) {
        this.queue = new CircularQueue<>(NET_CAPACITY);
        this.lock = new ReentrantLock();
        this.vcVacio = lock.newCondition();
        this.vcLleno = lock.newCondition(); 
        this.lossRate = lossRate;
    }

    /**
     * Envía un segmento a la red.
     * Puede bloquearse si la red está saturada.
     * Puede descartar el paquete silenciosamente según el lossRate.
     */
    public void send(TCPSegment_v5 seg) {
        lock.lock();
        try {
            // Simulación de pérdida de paquetes (capa física no fiable)
            if (Math.random() <= lossRate) {
                return; // El paquete se "pierde" y no llega a la cola
            }

            // Control de congestión de la red física
            while (queue.full()) {
                vcLleno.awaitUninterruptibly();
            }

            queue.put(seg);
            vcVacio.signalAll(); // Avisar al receptor de que hay datos

        } finally {
            lock.unlock();
        }
    }

    /**
     * Extrae un segmento de la red.
     * Se bloquea si no hay paquetes viajando por la red.
     */
    public TCPSegment_v5 receive() {
        lock.lock();
        try {
            while (queue.empty()) {
                vcVacio.awaitUninterruptibly();
            }
            
            TCPSegment_v5 seg = queue.get();
            vcLleno.signalAll(); // Avisar al emisor de que hay espacio
            return seg;
        } finally {
            lock.unlock();
        }
    }
}