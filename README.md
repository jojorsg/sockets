# Calculadora Remota com Sockets — UDP, TCP e Protocol Buffers

Implementação de um serviço cliente-servidor de calculadora remota em três variantes:

- **UDP** — protocolo textual sobre `DatagramSocket`, com perda simulada no servidor e retransmissão no cliente.
- **TCP** — protocolo textual sobre `Socket`/`ServerSocket`, entrega confiável e ordenada.
- **Protobuf** — mesmo comportamento do TCP, mas com as mensagens serializadas em binário via Protocol Buffers.

O objetivo é comparar empiricamente o comportamento dos protocolos diante de perda de mensagens, medindo RTT, retransmissões e perdas definitivas. Foi utilizado java openjdk 21.0.12.

## Estrutura de diretórios

sockets/  
├── UDP/  
&nbsp;  ├── CalcServerUDP.java  
&nbsp;  └── CalcClientUDP.java  
├── TCP/  
&nbsp;  ├── CalcServerTCP.java  
&nbsp;  └── CalcClientTCP.java  
└── PROTOBUF/  
&nbsp;  ├── calc.proto  
&nbsp;  ├── protobuf-java-4.36.2.jar  
&nbsp;  ├── CalcServerProto.java  
&nbsp;  ├── CalcClientProto.java  
&nbsp;  └── (classes geradas pelo protoc)  

## Compilação e execução

 - **Obs:** é importante que esses comandos sejam rodados dentro da pasta sockets/

**UDP**  
cd ~/sockets  
javac UDP/*.java **(compilação)**  

Em terminais separados, rode:  
java UDP.CalcServerUDP **(servidor)**  
java UDP.CalcClientUDP **(cliente)**  

**TCP**  
cd ~/sockets  
javac TCP/*.java **(compilação)**  

Em terminais separados, rode:  
java TCP.CalcServerTCP **(servidor)**  
java TCP.CalcClientTCP **(cliente)**  

**Protobuf (necessário ter o compilador protoc)**   
cd \~/sockets  
protoc --java_out=. PROTOBUF/calc.proto  
export PROTOBUF_JAR=\~/sockets/PROTOBUF/protobuf-java-4.36.2.jar  
javac -cp "$PROTOBUF_JAR:." PROTOBUF/*.java **(compilação)**  

Em terminais separados, rode:  
java -cp "$PROTOBUF_JAR:." PROTOBUF.CalcServerProto **(servidor)**  
java -cp "PROTOBUF/protobuf-java-4.36.2.jar:." PROTOBUF.CalcClientProto **(cliente)**  

## 1. UDP

### `UDP/CalcServerUDP.java`

**Constantes configuráveis**

| Constante | Descrição |
|---|---|
| `LOSS_RATE` | Fração de mensagens descartadas (ex.: `0.1` = 10%; `0.3` = 30%; `0.5` = 50%; etc). **Altere na linha 12.** |
| `PORT` | Porta padrão do servidor (9876). |
| `BUFFER_SIZE` | Tamanho do buffer de recepção (1024 bytes). |
| `THREAD_POOL_SIZE` | Número de threads concorrentes (10). |


### `UDP/CalcClientUDP.java`

Cliente UDP que envia N=20 requisições, mede o RTT de cada uma, aplica timeout + retransmissão, e gera um relatório `.txt` ao final.

**Constantes configuráveis**

| Constante | Descrição |
|---|---|
| `N` | Número de requisições (20). |
| `TIMEOUT_MS` | Timeout por tentativa (500 ms). |
| `MAX_TENTATIVAS` | Máximo de tentativas por requisição (5). |
| `HOST` / `PORT` | Endereço e porta do servidor (localhost:9876). |
| `MODO_AUTOMATICO` | `true` = gera requisições aleatórias; `false` = solicita comandos no terminal. |

---

## 2. TCP

### `TCP/CalcServerTCP.java`

**Constantes configuráveis**

| Constante | Descrição |
|---|---|
| `PORT` | Porta padrão do servidor (9877). |

---

### `TCP/CalcClientTCP.java`

Cliente TCP com o mesmo comportamento do cliente UDP (N=20, mede RTT, gera relatório), mas **sem retransmissão** — o TCP já garante entrega.

**Constantes configuráveis**

| Constante | Descrição |
|---|---|
| `N` | Número de requisições (20). |
| `TIMEOUT_MS` | Timeout de conexão e de leitura (5000 ms). |
| `HOST` / `PORT` | Endereço e porta do servidor (localhost:9877). |
| `MODO_AUTOMATICO` | `true` = gera requisições aleatórias; `false` = solicita comandos no terminal. |

---

## 3. Protobuf

### `PROTOBUF/calc.proto`

Definição do protocolo em Protocol Buffers. Escolhi um formato com `oneof` para encapsular requisição ou resposta em uma única mensagem.

**Mensagens**

| Mensagem | Campos |
|---|---|
| `CalculationRequest` | `sequence` (int32), `operand1` (double), `operand2` (double), `operation` (string) |
| `CalculationResponse` | `sequence` (int32), `success` (bool), `result` (double), `error_message` (string) |
| `CalcMessage` | `type` (enum `MessageType`), `oneof payload { request, response }` |

**Enum**

| Enum | Valores |
|---|---|
| `MessageType` | `UNKNOWN=0`, `REQUEST=1`, `RESPONSE=2` |

---

### `PROTOBUF/CalcServerProto.java`

**Constantes configuráveis**

| Constante | Descrição |
|---|---|
| `PORT` | Porta padrão do servidor (9878). |

---

### `PROTOBUF/CalcClientProto.java`

**Constantes configuráveis**

| Constante | Descrição |
|---|---|
| `N` | Número de requisições (20). |
| `TIMEOUT_MS` | Timeout de conexão e de leitura (5000 ms). |
| `HOST` / `PORT` | Endereço e porta do servidor (localhost:9878). |
| `MODO_AUTOMATICO` | `true` = gera requisições aleatórias; `false` = solicita comandos no terminal. |

---

## Formato do protocolo de aplicação

Independentemente da variante (UDP, TCP textual ou Protobuf), a semântica é a mesma:  
Requisição: CALC:\<n>:\<operando1>:\<op>:\<operando2>  
Resposta OK: RESULT:\<n>:\<resultado>  
Resposta erro: ERROR:\<n>:\<mensagem>  

Onde:

- `<n>` — número de sequência (0, 1, 2, ...)  
- `<operando1>`, `<operando2>` — inteiros ou decimais  
- `<op>` — `+`, `-`, `*` ou `/`  

---

## Formato de relatório gerado após execuções no lado do cliente

=== Relatório <protocolo> ===  
Requisições: \<N>  
Tempo total (ms): \<ms>   
RTT médio (ms): \<ms>  
RTT máximo (ms): \<ms>  
Retransmissões: \<n>              (apenas UDP)  
Perdidas definitivamente: \<n>  
Sequências perdidas: [ ... ]  
