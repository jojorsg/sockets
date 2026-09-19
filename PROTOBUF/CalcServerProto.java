package PROTOBUF;

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CalcServerProto {
    private static final int PORT = 9878;

    // Abre o ServerSocket e despacha cada cliente para uma thread do pool.
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
            System.out.println("Servidor Protobuf ouvindo na porta " + port);

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


    //Loop que lê CalcMessage delimitados via BufferedInputStream, processa como requisição, e devolve CalcMessage de resposta via BufferedOutputStream. 
    //Isola erros por cliente.
    private static void handleClient(Socket client) {
        String clientInfo = client.getRemoteSocketAddress().toString();
        System.out.println("Cliente conectado: " + clientInfo);

            try (Socket s = client;
                InputStream in = new BufferedInputStream(s.getInputStream());
                OutputStream out = new BufferedOutputStream(s.getOutputStream())) {

            while (true) {
                CalcMessage msg;
                try {
                    msg = CalcMessage.parseDelimitedFrom(in);
                } catch (IOException e) {
                    System.err.println("Erro ao ler mensagem de " + clientInfo + ": " + e.getMessage());
                    break;
                }

                if (msg == null) {
                    // cliente fechou a conexão
                    break;
                }

                if (msg.getType() != MessageType.REQUEST || !msg.hasRequest()) {
                    System.err.println("Mensagem inesperada de " + clientInfo);
                    continue;
                }

                CalculationRequest req = msg.getRequest();
                CalculationResponse resp = processRequest(req);

                CalcMessage resposta = CalcMessage.newBuilder()
                        .setType(MessageType.RESPONSE)
                        .setResponse(resp)
                        .build();

                resposta.writeDelimitedTo(out);
                out.flush();

                System.out.println(clientInfo + " -> seq=" + req.getSequence()
                        + " | " + (resp.getSuccess() ? "RESULT:" + resp.getResult() : "ERROR:" + resp.getErrorMessage()));
            }

        } catch (IOException e) {
            System.err.println("Erro com cliente " + clientInfo + ": " + e.getMessage());
        } finally {
            System.out.println("Cliente desconectado: " + clientInfo);
        }
    }



    // Executa a operação e monta um CalculationResponse com success=true e result, ou success=false com error_message. 
    // Trata divisão por zero, operação inválida, e exceções genéricas.
    private static CalculationResponse processRequest(CalculationRequest req) {
        int seq = req.getSequence();
        double op1 = req.getOperand1();
        double op2 = req.getOperand2();
        String op = req.getOperation();

        CalculationResponse.Builder builder = CalculationResponse.newBuilder()
                .setSequence(seq);

        try {
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
                        return builder.setSuccess(false)
                                .setErrorMessage("divisão por zero")
                                .build();
                    }
                    result = op1 / op2;
                    break;
                default:
                    return builder.setSuccess(false)
                            .setErrorMessage("operação inválida")
                            .build();
            }

            return builder.setSuccess(true)
                    .setResult(result)
                    .build();

        } catch (Exception e) {
            return builder.setSuccess(false)
                    .setErrorMessage("erro ao processar requisição")
                    .build();
        }
    }
}