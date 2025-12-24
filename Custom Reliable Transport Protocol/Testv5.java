package tcppruebas.uni.TCP_v5;

public class Testv5 {

    public static void main(String[] args) throws InterruptedException {

        SimNet network = new SimNet(0.2); // Tu loss rate
        Protocol protocol = new Protocol(network);

        TSocket client = new TSocket(network, 10);
        TSocket server = new TSocket(network, 20);

        protocol.addActiveTSocket(client);
        protocol.addActiveTSocket(server);

        String mensajeTexto = "Hello world! Hello world! Hello world! Hello world!";
        byte[] datosAEnviar = mensajeTexto.getBytes();

        System.out.println("=== INICIO DEL TEST COMPLETO ===");

        // --- HILO SERVIDOR ---
        Thread serverThread = new Thread(() -> {
            server.listen();
            
            byte[] bufferRecep = new byte[datosAEnviar.length];
            int leidos = server.receiveData(bufferRecep); 

            String msj = new String(bufferRecep, 0, leidos);
            System.out.println("\n[TEST SERVER]: ¡DATOS RECIBIDOS!: " + msj);
            
            // TRUCO: Esperamos un tiempo generoso para dar margen a las retransmisiones del cliente
            // Si el cliente pierde ACKs, necesita tiempo para reenviar antes de que cerremos.
            try { Thread.sleep(4000); } catch (Exception e) {} 

            System.out.println("[TEST SERVER]: Cerrando servidor...");
            server.close();
        });
        serverThread.start();

        Thread.sleep(200);

        // --- HILO CLIENTE ---
        Thread clientThread = new Thread(() -> {
            client.connect(20); 
            
            System.out.println("[TEST CLIENT]: Enviando datos...");
            client.sendData(datosAEnviar);
            
            // --- CAMBIO CLAVE: ESPERAR CONFIRMACIÓN ---
            // No cerramos hasta que el servidor nos haya confirmado todo.
            // Esto asegura que los TIMEOUTs ocurran AHORA, no al final.
            System.out.println("[TEST CLIENT]: Esperando ACKs...");
            while (!client.areAllSegmentsConfirmed()) {
                try { Thread.sleep(100); } catch (Exception e) {}
            }
            System.out.println(" ¡Todo confirmado!");

            System.out.println("[TEST CLIENT]: Terminado. Cerrando cliente...");
            client.close(); 
        });
        clientThread.start();
        
        // Esperamos a que ambos terminen
        serverThread.join();
        clientThread.join();

        System.out.println("=== FIN DE LA EJECUCIÓN ===");
        System.exit(0); // <--- Esto mata todo, incluidos los hilos atascados
    }
}