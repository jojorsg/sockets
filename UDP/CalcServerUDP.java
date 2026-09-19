package UDP;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalcServerUDP {
    //ALTERE AQUI O LOSS-RATE!
    private static final double LOSS_RATE = 0.3;

    private static final int PORT = 9876;    //Definida porta padrão de conexão
    private static final int BUFFER_SIZE = 1024;   //Tamanho do buffer
    private static final int THREAD_POOL_SIZE = 10;    //Threads concorrentes

    
    
    // Abre o DatagramSocket, aceita opcionalmente a porta via argumento e entra no loop de recepção.
    //cada pacote recebido é despachado para uma thread do pool. Aplica a perda simulada antes do despacho.
    public static void main(String[] args) {
        int port = PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida. Usando porta padrão: " + PORT);
            }
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Servidor UDP ouvindo na porta " + port + " (loss-rate=" + LOSS_RATE + ")");

            byte[] buffer = new byte[BUFFER_SIZE];

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(packet);
                } catch (IOException e) {
                    System.err.println("Erro ao receber datagrama: " + e.getMessage());
                    continue;
                }

                // Simulação de perda: recebe, mas não responde.
                if (Math.random() < LOSS_RATE) {
                    System.out.println("Pacote descartado (simulação de perda) de "
                            + packet.getAddress() + ":" + packet.getPort());
                    continue;
                }

                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                InetAddress clientAddr = packet.getAddress();
                int clientPort = packet.getPort();

                pool.execute(() -> processRequest(socket, data, clientAddr, clientPort));
            }
        } catch (SocketException e) {
            System.err.println("Erro ao abrir socket UDP: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }



    // Decodifica a requisição, chama handleRequest e envia a resposta de volta ao cliente. 
    // Sincroniza o `send` para evitar concorrência no socket.
    private static void processRequest(DatagramSocket socket, byte[] data, InetAddress addr, int port) {
        String request = new String(data, StandardCharsets.UTF_8).trim();
        String response = handleRequest(request);

        byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
        DatagramPacket respPacket = new DatagramPacket(respBytes, respBytes.length, addr, port);

        try {
            synchronized (socket) {
                socket.send(respPacket);
            }
            System.out.println("Respondido para " + addr + ":" + port + " -> " + response);
        } catch (IOException e) {
            System.err.println("Erro ao enviar resposta: " + e.getMessage());
        }
    }



    // Faz o parsing do comando, executa a operação e devolve a resposta no formato RESULT:<n>:<resultado> ou ERROR:<n>:<mensagem> caso ocorra algum erro. 
    // Trata divisão por zero, operação inválida, número inválido e formato inválido.
    private static String handleRequest(String request) {
        try {
            // Formato: CALC:<n>:<operando1>:<op>:<operando2>
            String[] parts = request.split(":", 5);

            if (parts.length != 5 || !parts[0].equalsIgnoreCase("CALC")) {
                return "ERROR:0:requisição inválida";
            }

            int seq = Integer.parseInt(parts[1]);
            double op1 = Double.parseDouble(parts[2]);
            String op = parts[3];
            double op2 = Double.parseDouble(parts[4]);

            double result;

            switch (op) {
                case "+":
                    result = op1 + op2;
                    break;
                case "-":
                    result = op1 - op2;
                    break;
                case "*":
                    result = op1 * op2;
                    break;
                case "/":
                    if (op2 == 0.0) {
                        return "ERROR:" + seq + ":divisão por zero";
                    }
                    result = op1 / op2;
                    break;
                default:
                    return "ERROR:" + seq + ":operação inválida";
            }

            return "RESULT:" + seq + ":" + result;

        } catch (NumberFormatException e) {
            return "ERROR:0:número inválido";
        } catch (Exception e) {
            return "ERROR:0:erro ao processar requisição";
        }
    }
}