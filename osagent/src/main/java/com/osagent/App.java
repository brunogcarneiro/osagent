package com.osagent;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Hello world!
 *
 */
@Command(name = "osagent", mixinStandardHelpOptions = true, version = "1.0",
        description = "Executa comandos no terminal com ajuda do ChatGPT")
public class App implements Callable<Integer> {

    @Parameters(index = "0", description = "A instrução a ser executada")
    private String instrucao;

    @Option(names = {"-i", "--instructionsFile"}, description = "Arquivo com instruções personalizadas")
    private File instructionsFile;

    private final OpenAiService service;
    private final List<ChatMessage> conversationHistory;

    public App() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY não está definida no ambiente");
        }
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.conversationHistory = new ArrayList<>();
    }

    @Override
    public Integer call() {
        try {
            iniciarConversa();
            return 0;
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            return 1;
        }
    }

    private void iniciarConversa() {
        // Mensagem inicial para o ChatGPT
        String systemPrompt = "Você é um assistente especializado em comandos de terminal. " +
                "Você recebe um objetivo e deve identificar uma sequência de comandos de terminal para atingir o objetivo. " +
                "Você deve enviar os comandos de terminal completos, apenas um por vez, com todos os parâmetros necessários. " +
                "Ao enviar um comando, envie apenas o comando, sem nenhum outro texto. Ele deve ser copiado e colado no terminal. " +
                "Eu irei executar os comandos e vou te retornar o resultado. " +
                "Você deve analisar o resultado e enviar o próximo comando ou concluir o processo se o objetivo foi atingido. " +
                "NUNCA envie comandos com parâmetros falsos ou placeholders como {seu_usuario}, {nome}, etc. " +
                "Se você não souber o valor de um parâmetro, pergunte ao usuário. " +
                "Se você precisar de mais informações, use 'PERGUNTA: ' para perguntar ao usuário. " +
                "Você pode solicitar dois tipos de informações: " +
                "1. Informações sobre qual caminho seguir para atingir o objetivo, caso você identifique que há mais de um caminho. " +
                "2. Informações necessárias para determinação de parâmetros de um comando, como por exemplo, um nome de usuário ou um token. " +
                "Existem dois tipos de objetivos: " +
                "1. Pergunta: o usuário quer saber algo, como por exemplo, alguma informação sobre um sistema rodando no máquina. " +
                "2. Ação: o usuário quer que algo seja feito, como por exemplo, criar um novo arquivo ou uma nova pasta." +
                "Se o objetivo for uma pergunta, encerre a conversa enviando 'RESPOSTA:' seguido da resposta completa, objetiva e não-ambígua. " +
                "Se o objetivo for uma ação, encerre a conversa enviando 'TAREFA_CONCLUIDA' após o último comando de terminal. " +
                "Em caso de erro, encerre a conversa enviando 'ERRO: ' seguido da mensagem de erro. E aguarde uma resposta do usuário para tentar novamente.";

        // Adicionar instruções personalizadas se fornecidas
        if (instructionsFile != null && instructionsFile.exists()) {
            try {
                String customInstructions = new String(Files.readAllBytes(instructionsFile.toPath()));
                systemPrompt += "\n\n" + customInstructions;
            } catch (IOException e) {
                System.err.println("Erro ao ler arquivo de instruções: " + e.getMessage());
            }
        }

        conversationHistory.add(new ChatMessage("system", systemPrompt));
        conversationHistory.add(new ChatMessage("user", instrucao));

        while (true) {
            String resposta = obterProximoComando();
            
            if (resposta.startsWith("PERGUNTA: ")) {
                System.out.println("\n" + resposta);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    String respostaUsuario = reader.readLine();
                    conversationHistory.add(new ChatMessage("user", respostaUsuario));
                    continue;
                } catch (IOException e) {
                    System.err.println("Erro ao ler resposta do usuário: " + e.getMessage());
                    return;
                }
            }
            
            if (resposta.startsWith("ERRO: ")) {
                System.err.println("\n" + resposta);
                // Aguardar resposta do usuário após um erro para possível esclarecimento
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    System.out.println("\nPressione ENTER para encerrar ou forneça mais informações para tentar novamente:");
                    String respostaUsuario = reader.readLine();
                    if (respostaUsuario != null && !respostaUsuario.trim().isEmpty()) {
                        conversationHistory.add(new ChatMessage("user", respostaUsuario));
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao ler resposta do usuário: " + e.getMessage());
                }
                break;
            }

            if (resposta.startsWith("RESPOSTA: ")) {
                System.out.println("\n" + resposta.substring(9));
                break;
            }

            if (resposta.equalsIgnoreCase("TAREFA_CONCLUIDA")) {
                System.out.println("\nTarefa concluída com sucesso!");
                break;
            }

            // Se não for nenhum dos casos especiais acima, é um comando de terminal
            if (!resposta.trim().isEmpty()) {
                System.out.println("\nExecutando comando: " + resposta);
                String resultado = executarComando(resposta);
                System.out.println("Resultado:\n" + resultado);

                conversationHistory.add(new ChatMessage("user", "Resultado do comando: " + resultado));
            }
        }
    }

    private String obterProximoComando() {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("o1-2024-12-17")
                .messages(conversationHistory)
                .build();

        ChatMessage resposta = service.createChatCompletion(request).getChoices().get(0).getMessage();
        conversationHistory.add(resposta);
        return resposta.getContent().trim();
    }

    private String executarComando(String comando) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"bash", "-c", comando});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            while ((line = errorReader.readLine()) != null) {
                output.append("ERRO: ").append(line).append("\n");
            }

            process.waitFor();
            return output.toString();
        } catch (IOException | InterruptedException e) {
            return "Erro ao executar o comando: " + e.getMessage();
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }
}
