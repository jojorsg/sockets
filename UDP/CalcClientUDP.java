package UDP;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class CalcClientUDP {
    private static final int N = 20;
    private static final int TIMEOUT_MS = 500;
    private static final int MAX_TENTATIVAS = 5;

    private static final String HOST = "localhost";
    private static final int PORT = 9876;

    // true = gera N requisições aleatoriamente de forma automática.
    // false = solicita comandos digitados no terminal.
    private static final boolean MODO_AUTOMATICO = true;

    
    // Ponto de entrada. Lê host/porta dos argumentos (opcionais) e decide entre modo automático e manual.
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
            System.err.println("Erro no cliente UDP: " + e.getMessage());
        }
    }



    //Gera N requisições aleatórias, envia cada uma com retransmissão em caso de timeout, mede RTT, contabiliza retransmissões e perdas definitivas, e gera o relatório final
    private static void executarAutomatico(String host, int port) throws IOException {
        List<Requisicao> requisicoes = gerarRequisicoes(N);

        List<Long> rtts = new ArrayList<>();
        int retransmissoes = 0;
        int perdidasDefinitivas = 0;
        List<Integer> sequenciasPerdidas = new ArrayList<>();

        long inicioTotal = System.nanoTime();

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress serverAddr = InetAddress.getByName(host);

            for (Requisicao req : requisicoes) {
                String mensagem = req.paraComando();
                boolean respondida = false;
                long inicioReq = System.nanoTime();

                for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
                    if (tentativa > 1) {
                        retransmissoes++;
                        System.out.println("Retransmissão " + (tentativa - 1) + " para seq=" + req.seq);
                    }

                    byte[] dados = mensagem.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket pacote = new DatagramPacket(dados, dados.length, serverAddr, port);
                    socket.send(pacote);

                    String resposta = receberResposta(socket, req.seq, TIMEOUT_MS);

                    if (resposta != null) {
                        long fimReq = System.nanoTime();
                        long rttMs = (fimReq - inicioReq) / 1_000_000;

                        rtts.add(rttMs);
                        System.out.println("Enviado: " + mensagem);

                        respondida = true;
                        break;
                    }
                }

                if (!respondida) {
                    perdidasDefinitivas++;
                    sequenciasPerdidas.add(req.seq);
                    System.out.println("Requisição seq=" + req.seq
                            + " PERDIDA DEFINITIVAMENTE após " + MAX_TENTATIVAS + " tentativas.");
                }
            }
        }

        long fimTotal = System.nanoTime();
        long tempoTotalMs = (fimTotal - inicioTotal) / 1_000_000;

        double rttMedio = rtts.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long rttMax = rtts.stream().mapToLong(Long::longValue).max().orElse(0L);

        String relatorio = String.format(
                "=== Relatório UDP ===%n" +
                "Requisições: %d%n" +
                "Tempo total (ms): %d%n" +
                "RTT médio (ms): %.2f%n" +
                "RTT máximo (ms): %d%n" +
                "Retransmissões: %d%n" +
                "Perdidas definitivamente: %d%n" +
                "Sequências perdidas: %s%n",
                N,
                tempoTotalMs,
                rttMedio,
                rttMax,
                retransmissoes,
                perdidasDefinitivas,
                sequenciasPerdidas
        );

        System.out.println(relatorio);
        salvarRelatorio("udp", relatorio);
    }


    // Aguarda uma resposta cujo número de sequência bata com o esperado. 
    // Ignora respostas antigas/inesperadas dentro da janela de timeout. 
    //Retorna `null` em caso de timeout.
    private static String receberResposta(DatagramSocket socket, int seqEsperada, int timeoutMs) {
        long fim = System.currentTimeMillis() + timeoutMs;
        byte[] buffer = new byte[1024];

        while (true) {
            long restante = fim - System.currentTimeMillis();
            if (restante <= 0) return null;

            try {
                socket.setSoTimeout((int) restante);

                DatagramPacket respostaPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(respostaPacket);

                String resposta = new String(
                        respostaPacket.getData(),
                        0,
                        respostaPacket.getLength(),
                        StandardCharsets.UTF_8
                ).trim();

                if (resposta.startsWith("RESULT:" + seqEsperada + ":")
                        || resposta.startsWith("ERROR:" + seqEsperada + ":")) {
                    return resposta;
                }

                // Resposta antiga/inesperada: ignora e continua aguardando.
            } catch (SocketTimeoutException e) {
                return null;
            } catch (IOException e) {
                System.err.println("Erro ao receber resposta: " + e.getMessage());
                return null;
            }
        }
    }

    //Gera uma lista de N `Requisicao` aleatórias (operandos e operação).
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


    // Lê comandos digitados pelo usuário no formato CALC:... e envia um a um. Se digitar "sair", encerra o programa.
    private static void executarModoManual(String host, int port) throws IOException {
        try (DatagramSocket socket = new DatagramSocket();
             BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in))) {

            socket.setSoTimeout(TIMEOUT_MS);
            InetAddress serverAddr = InetAddress.getByName(host);

            System.out.println("Digite comandos no formato CALC:<n>:<operando1>:<op>:<operando2> (ou 'sair'):");

            String linha;
            while ((linha = teclado.readLine()) != null) {
                if (linha.equalsIgnoreCase("sair")) break;

                byte[] dados = linha.getBytes(StandardCharsets.UTF_8);
                DatagramPacket pacote = new DatagramPacket(dados, dados.length, serverAddr, port);
                socket.send(pacote);

                String resposta = receberRespostaQualquer(socket, TIMEOUT_MS);

                if (resposta == null) {
                    System.out.println("Timeout: sem resposta do servidor.");
                } 
            }
        }
    }
    
    //Versão simplificada do método receberResposta, que é usado no modo manual (retorna a primeira resposta que chegar).
    private static String receberRespostaQualquer(DatagramSocket socket, int timeoutMs) {
        try {
            socket.setSoTimeout(timeoutMs);

            byte[] buffer = new byte[1024];
            DatagramPacket respostaPacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(respostaPacket);

            return new String(
                    respostaPacket.getData(),
                    0,
                    respostaPacket.getLength(),
                    StandardCharsets.UTF_8
            ).trim();

        } catch (SocketTimeoutException e) {
            return null;
        } catch (IOException e) {
            System.err.println("Erro ao receber resposta: " + e.getMessage());
            return null;
        }
    }


    // Grava o relatório em udp_report_<timestamp>.txt no diretório de execução
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



    //Serializa a requisição no formato CALC:<n>:<op1>:<op>:<op2>.
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

        String paraComando() {
            return "CALC:" + seq + ":" + op1 + ":" + op + ":" + op2;
        }
    }
}