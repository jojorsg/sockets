package TCP;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CalcServerTCP {
    private static final int PORT = 9877;

    
    // Abre o ServerSocket, aceita opcionalmente a porta via argumento e entra no loop de accept. 
    // Cada cliente aceito é despachado para uma thread do pool (`newCachedThreadPool`)
    public static void main(String[] args) {
        int port = PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida. Usando porta padrão: " + PORT);
            }
        }

        ExecutorService pool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Servidor TCP ouvindo na porta " + port);

            while (true) {
                try {
                    Socket client = serverSocket.accept();
                    pool.execute(() -> handleClient(client));
                } catch (IOException e) {
                    System.err.println("Erro ao aceitar conexão: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao abrir ServerSocket: " + e.getMessage());
        } finally {
            pool.shutdown();
        }
    }



    // Loop de leitura linha-a-linha do cliente: para cada linha, chama handleRequest e devolve a resposta. 
    // Encerra quando o cliente fecha a conexão. Isola erros por cliente.
    private static void handleClient(Socket client) {
        String clientInfo = client.getRemoteSocketAddress().toString();
        System.out.println("Cliente conectado: " + clientInfo);

        try (Socket s = client;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String linha;
            while ((linha = in.readLine()) != null) {
                String resposta = handleRequest(linha.trim());
                out.println(resposta);
                System.out.println("Respondido para " + clientInfo + " -> " + linha + " | " + resposta);
            }

        } catch (IOException e) {
            System.err.println("Erro com cliente " + clientInfo + ": " + e.getMessage());
        } finally {
            System.out.println("Cliente desconectado: " + clientInfo);
        }
    }


    //Faz o parsing do comando, executa a operação e devolve a resposta no formato RESULT:<n>:<resultado> ou ERROR:<n>:<mensagem> caso ocorra algum erro. 
    //Trata divisão por zero, operação inválida, número inválido e formato inválido.
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