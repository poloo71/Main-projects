# Implementación de Protocolo de Transporte Fiable (TCP Custom)

## 📖 Descripción

Este proyecto es una implementación personalizada de un **Protocolo de Capa de Transporte** (similar a TCP) construido desde cero en Java. 

El objetivo principal fue diseñar un sistema de comunicación robusto capaz de garantizar la **entrega fiable y ordenada de datos** sobre un medio físico simulado (`SimNet`) que introduce errores y pérdida de paquetes de forma aleatoria (hasta un 20% de pérdida).

El sistema utiliza programación concurrente avanzada para gestionar múltiples conexiones y estados de forma eficiente.

## 🚀 Características Principales

* **Orientado a Conexión:** Implementación completa de Máquina de Estados Finitos (FSM) incluyendo *3-Way Handshake* y cierre de conexión de 4 vías.
* **Fiabilidad (ARQ):** Sistema de retransmisión automática basado en Timeouts para recuperar paquetes perdidos.
* **Control de Flujo:** Mecanismo de **Ventana Deslizante** (Sliding Window) para optimizar el rendimiento sin saturar al receptor.
* **Concurrencia Robusta:** Arquitectura *Thread-Safe* utilizando monitores (`ReentrantLock`, `Condition`) para evitar condiciones de carrera y *deadlocks*.
* **Gestión de Desorden:** Buffer de reordenación capaz de procesar paquetes que llegan fuera de secuencia y rellenar "huecos" (gaps).
* **Recuperación de Errores:** Manejo de casos extremos como "Zombie Sockets", pérdida de ACKs finales y retransmisiones en estados de cierre (`FIN_WAIT`).

## 🛠️ Arquitectura

El proyecto se divide en capas para desacoplar responsabilidades:

1.  **Capa de Aplicación (`Testv5`):** Genera datos y valida la integridad del mensaje recibido.
2.  **Capa de Transporte (`TSocket` & `Protocol`):** Gestiona la lógica de estados, timers, ACKs y multiplexación por puertos.
3.  **Unidad de Datos (`TCPSegment`):** Encapsula los flags (SYN, FIN, ACK, PSH), números de secuencia y payload.
4.  **Capa Física Simulada (`SimNet`):** Introduce latencia y pérdida de paquetes probabilística.

## 💻 Instalación y Ejecución

### Requisitos
* Java Development Kit (JDK) 8 o superior.

### Configuración de la Simulación
Puedes ajustar la tasa de pérdida de paquetes (variable lossRate) en el archivo `Testv5.java`.

