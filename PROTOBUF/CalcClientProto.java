package PROTOBUF;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class CalcClientProto {
    private static final int N = 20;
    private static final int TIMEOUT_MS = 5000;

    private static final String HOST = "localhost";
    private static final int PORT = 9878;

    // true = gera N requisições aleatórias automaticamente.
    // false = solicita comandos digitados no terminal.
    private static final boolean MODO_AUTOMATICO = true;

    
    // Ponto de entrada: decide entre modo automático e manual.
    public static void main(String[] args) {
        try {
            String host = HOST;
            int port = PORT;

            if (args.length > 0) host = args[0];
            if (args.length > 1) port = Integer.parseInt(args[1]);

            if (!MODO_AUTOMATICO) {
                executarModoManual(host, port);
                return;
            }

            executarAutomatico(host, port);

        } catch (Exception e) {
            System.err.println("Erro no cliente Protobuf: " + e.getMessage());
        }
    }


    // Gera N requisições, serializa cada uma como CalcMessage, envia via writeDelimitedTo, 
    // lê a resposta via parseDelimitedFrom, mede RTT, e gera o relatório final
    private static void executarAutomatico(String host, int port) {
        List<Requisicao> requisicoes = gerarRequisicoes(N);

        List<Long> rtts = new ArrayList<>();
        int perdidas = 0;
        List<Integer> sequenciasPerdidas = new ArrayList<>();

        long inicioTotal = System.nanoTime();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            for (Requisicao req : requisicoes) {
                long inicioReq = System.nanoTime();

                CalcMessage mensagem = CalcMessage.newBuilder()
                        .setType(MessageType.REQUEST)
                        .setRequest(CalculationRequest.newBuilder()
                                .setSequence(req.seq)
                                .setOperand1(req.op1)
                                .setOperand2(req.op2)
                                .setOperation(req.op)
                                .build())
                        .build();

                mensagem.writeDelimitedTo(out);
                out.flush();

                CalcMessage resposta;
                try {
                    resposta = CalcMessage.parseDelimitedFrom(in);
                } catch (SocketTimeoutException e) {
                    resposta = null;
                }

                if (resposta == null) {
                    perdidas++;
                    sequenciasPerdidas.add(req.seq);
                    System.out.println("Requisição seq=" + req.seq + " sem resposta (timeout).");
                    continue;
                }

                if (resposta.getType() != MessageType.RESPONSE || !resposta.hasResponse()) {
                    System.err.println("Resposta inesperada para seq=" + req.seq);
                    continue;
                }

                CalculationResponse resp = resposta.getResponse();
                long fimReq = System.nanoTime();
                long rttMs = (fimReq - inicioReq) / 1_000_000;

                rtts.add(rttMs);

                System.out.println("Enviado: CALC:" + req.seq + ":" + req.op1 + ":"
                        + req.op + ":" + req.op2);
            }

        } catch (ConnectException e) {
            System.err.println("Conexão recusada: " + e.getMessage());
            return;
        } catch (SocketTimeoutException e) {
            System.err.println("Timeout de conexão: " + e.getMessage());
            return;
        } catch (IOException e) {
            System.err.println("Erro de E/S: " + e.getMessage());
            return;
        }

        long fimTotal = System.nanoTime();
        long tempoTotalMs = (fimTotal - inicioTotal) / 1_000_000;

        double rttMedio = rtts.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long rttMax = rtts.stream().mapToLong(Long::longValue).max().orElse(0L);

        String relatorio = String.format(
                "=== Relatório Protobuf ===%n" +
                "Requisições: %d%n" +
                "Tempo total (ms): %d%n" +
                "RTT médio (ms): %.2f%n" +
                "RTT máximo (ms): %d%n" +
                "Perdidas definitivamente: %d%n" +
                "Sequências perdidas: %s%n",
                N,
                tempoTotalMs,
                rttMedio,
                rttMax,
                perdidas,
                sequenciasPerdidas
        );

        System.out.println(relatorio);
        salvarRelatorio("protobuf", relatorio);
    }


    // Lê comandos do teclado, converte para CalcMessage, envia e exibe a resposta.
    private static void executarModoManual(String host, int port) throws IOException {
        try (Socket socket = new Socket(host, port);
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());


            System.out.println("Digite comandos no formato CALC:<n>:<operando1>:<op>:<operando2> (ou 'sair'):");

            String linha;
            while ((linha = teclado.readLine()) != null) {
                if (linha.equalsIgnoreCase("sair")) break;

                Requisicao req = parsearComando(linha);
                if (req == null) {
                    System.out.println("Formato inválido. Use CALC:<n>:<op1>:<op>:<op2>");
                    continue;
                }

                CalcMessage mensagem = CalcMessage.newBuilder()
                        .setType(MessageType.REQUEST)
                        .setRequest(CalculationRequest.newBuilder()
                                .setSequence(req.seq)
                                .setOperand1(req.op1)
                                .setOperand2(req.op2)
                                .setOperation(req.op)
                                .build())
                        .build();

                mensagem.writeDelimitedTo(out);
                out.flush();

                CalcMessage resposta = CalcMessage.parseDelimitedFrom(in);

                if (resposta == null || !resposta.hasResponse()) {
                    System.out.println("Resposta inválida do servidor.");
                }
            }
        }
    }


    // Converte CALC:<n>:<op1>:<op>:<op2> em um objeto Requisicao. Retorna null se o formato for inválido.
    private static Requisicao parsearComando(String linha) {
        try {
            String[] parts = linha.trim().split(":", 5);
            if (parts.length != 5 || !parts[0].equalsIgnoreCase("CALC")) return null;

            int seq = Integer.parseInt(parts[1]);
            double op1 = Double.parseDouble(parts[2]);
            String op = parts[3];
            double op2 = Double.parseDouble(parts[4]);

            return new Requisicao(seq, op1, op, op2);
        } catch (Exception e) {
            return null;
        }
    }


    // Gera N Requisicao aleatórias
    private static List<Requisicao> gerarRequisicoes(int n) {
        Random random = new Random();
        String[] ops = {"+", "-", "*", "/"};
        List<Requisicao> lista = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            String op = ops[random.nextInt(ops.length)];

            double op1 = Math.round((random.nextDouble() * 200 - 100) * 100.0) / 100.0;
            double op2 = Math.round((random.nextDouble() * 200 - 100) * 100.0) / 100.0;

            lista.add(new Requisicao(i, op1, op, op2));
        }

        return lista;
    }

    //Grava o relatório em protobuf_report_<timestamp>.txt.
    private static void salvarRelatorio(String prefixo, String conteudo) {
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));

        String nome = prefixo + "_report_" + timestamp + ".txt";

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(nome), StandardCharsets.UTF_8))) {
            pw.print(conteudo);
            System.out.println("Relatório salvo em: " + new File(nome).getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro ao salvar relatório: " + e.getMessage());
        }
    }


    // Classe interna com os campos seq, op1, op, op2.
    private static class Requisicao {
        final int seq;
        final double op1;
        final String op;
        final double op2;

        Requisicao(int seq, double op1, String op, double op2) {
            this.seq = seq;
            this.op1 = op1;
            this.op = op;
            this.op2 = op2;
        }
    }
}