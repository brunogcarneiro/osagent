package com.osagent;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

    @Option(names = {"-c", "--continue"}, description = "Continuar uma conversa a partir de um arquivo de histórico")
    private File historyContinueFile;

    private final OpenAiService service;
    private final List<ChatMessage> conversationHistory;
    private final Gson gson;
    private final String historyDir = "./.osagent/history";
    private String currentHistoryFile; // Nome do arquivo de histórico atual

    public App() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("OPENAI_API_KEY não está definida no ambiente");
        }
        this.service = new OpenAiService(apiKey, Duration.ofSeconds(60));
        this.conversationHistory = new ArrayList<>();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        
        // Garantir que o diretório de histórico exista
        File directory = new File(historyDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    @Override
    public Integer call() {
        try {
            if (historyContinueFile != null) {
                // Verificar se o diretório de histórico existe
                File historyDirectory = new File(historyDir);
                if (!historyDirectory.exists()) {
                    // Criar o diretório de histórico se não existir
                    boolean dirCriado = historyDirectory.mkdirs();
                    if (dirCriado) {
                        System.out.println("Diretório de histórico criado: " + historyDir);
                    } else {
                        System.err.println("Não foi possível criar o diretório de histórico: " + historyDir);
                        return 1;
                    }
                }
                
                if (!historyDirectory.isDirectory()) {
                    System.err.println("O caminho especificado não é um diretório: " + historyDir);
                    return 1;
                }

                File[] historyFiles = historyDirectory.listFiles((dir, name) -> name.endsWith(".json"));
                if (historyFiles == null || historyFiles.length == 0) {
                    System.err.println("Nenhum histórico de conversa encontrado.");
                    return 1;
                }

                System.out.println("\nHistóricos de conversa disponíveis:");
                for (int i = 0; i < historyFiles.length; i++) {
                    System.out.printf("%d. %s%n", i + 1, historyFiles[i].getName());
                }

                // Solicitar escolha do usuário
                System.out.print("\nDigite o número do histórico que deseja carregar: ");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                String input = reader.readLine();
                
                try {
                    int escolha = Integer.parseInt(input);
                    if (escolha < 1 || escolha > historyFiles.length) {
                        System.err.println("Número inválido.");
                        return 1;
                    }
                    
                    // Carregar o histórico escolhido
                    File historicoEscolhido = historyFiles[escolha - 1];
                    carregarHistoricoConversa(historicoEscolhido);
                    currentHistoryFile = historicoEscolhido.getName();
                    System.out.println("\nHistórico de conversa carregado. Continuando conversa...\n");
                    
                    // Adicionar mensagem indicando continuação
                    conversationHistory.add(new ChatMessage("user", "Estou retomando essa conversa. Minha próxima pergunta/instrução é: " + instrucao));
                    
                    // Iniciar o loop de processamento da conversa
                    processarConversa();
                } catch (NumberFormatException e) {
                    System.err.println("Por favor, digite um número válido.");
                    return 1;
                }
            } else {
                iniciarConversa();
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Erro: " + e.getMessage());
            return 1;
        }
    }

    private void carregarHistoricoConversa(File historico) throws IOException {
        String conteudo = new String(Files.readAllBytes(historico.toPath()));
        ChatMessage[] mensagens = gson.fromJson(conteudo, ChatMessage[].class);
        
        // Adicionar mensagens ao histórico atual
        for (ChatMessage mensagem : mensagens) {
            conversationHistory.add(mensagem);
        }
    }

    private void salvarHistoricoConversa() {
        try {
            // Se já existe um arquivo de histórico, usar o mesmo nome
            if (currentHistoryFile != null) {
                String caminhoCompleto = historyDir + "/" + currentHistoryFile;
                String historicoJson = gson.toJson(conversationHistory);
                Files.write(Paths.get(caminhoCompleto), historicoJson.getBytes());
                System.out.println("\nHistórico da conversa atualizado em: " + caminhoCompleto);
                return;
            }
            
            // Se não existe arquivo, criar um novo
            String descricao = obterDescricaoHistorico();
            
            // Formatar nome do arquivo: YYYYMMDDHHMMSS-[DESCRICAO]
            LocalDateTime agora = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            String timestamp = agora.format(formatter);
            
            // Limitar descrição a 50 caracteres
            if (descricao.length() > 50) {
                descricao = descricao.substring(0, 50);
            }
            // Substituir caracteres inválidos para nome de arquivo
            descricao = descricao.replaceAll("[^a-zA-Z0-9\\-_]", "_");
            
            currentHistoryFile = timestamp + "-" + descricao + ".json";
            String caminhoCompleto = historyDir + "/" + currentHistoryFile;
            
            // Salvar conversa em formato JSON
            String historicoJson = gson.toJson(conversationHistory);
            Files.write(Paths.get(caminhoCompleto), historicoJson.getBytes());
            
            System.out.println("\nHistórico da conversa salvo em: " + caminhoCompleto);
        } catch (Exception e) {
            System.err.println("Erro ao salvar histórico: " + e.getMessage());
        }
    }
    
    private String obterDescricaoHistorico() {
        // Extrair a instrução inicial para sugerir uma descrição
        try {
            String instrucaoInicial = "";
            for (ChatMessage msg : conversationHistory) {
                if (msg.getRole().equals("user")) {
                    instrucaoInicial = msg.getContent();
                    break;
                }
            }
            
            // Pedir ao modelo para gerar uma descrição
            List<ChatMessage> mensagensTempDesc = new ArrayList<>();
            mensagensTempDesc.add(new ChatMessage("system", 
                "Crie uma descrição curta (até 50 caracteres) para um arquivo de histórico de conversa " +
                "baseado na instrução inicial do usuário. Use apenas letras, números, hífens e underscores. " +
                "Seja conciso e direto."));
            mensagensTempDesc.add(new ChatMessage("user", "Instrução inicial: \"" + instrucaoInicial + "\""));
            
            ChatCompletionRequest requestDesc = ChatCompletionRequest.builder()
                    .model("o1-2024-12-17")
                    .messages(mensagensTempDesc)
                    .build();
            
            ChatMessage respostaDesc = service.createChatCompletion(requestDesc).getChoices().get(0).getMessage();
            return respostaDesc.getContent().trim();
        } catch (Exception e) {
            // Em caso de erro, usar um nome genérico
            return "conversa_osagent";
        }
    }

    private void processarConversa() {
        while (true) {
            String resposta = obterProximoComando();
            
            if (resposta.startsWith("PERGUNTA: ")) {
                System.out.println("\n" + resposta);
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    String respostaUsuario = reader.readLine();
                    conversationHistory.add(new ChatMessage("user", respostaUsuario));
                    salvarHistoricoConversa();
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
                        salvarHistoricoConversa();
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao ler resposta do usuário: " + e.getMessage());
                }
                // Salvar histórico antes de encerrar
                salvarHistoricoConversa();
                break;
            }

            if (resposta.startsWith("RESPOSTA: ")) {
                System.out.println("\n" + resposta.substring(9));
                
                // Perguntar se o usuário deseja encerrar ou continuar
                System.out.println("\nDeseja encerrar a conversa? (Digite 's' para sair ou qualquer outra coisa para continuar)");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    String entradaUsuario = reader.readLine();
                    if (entradaUsuario != null && entradaUsuario.trim().toLowerCase().equals("s")) {
                        // Salvar histórico antes de encerrar
                        salvarHistoricoConversa();
                        break; // Encerrar se a resposta for 's'
                    } else {
                        // Repassar a entrada do usuário diretamente para a IA
                        conversationHistory.add(new ChatMessage("user", entradaUsuario != null ? entradaUsuario : ""));
                        salvarHistoricoConversa();
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao ler entrada do usuário: " + e.getMessage());
                    // Salvar histórico antes de encerrar
                    salvarHistoricoConversa();
                    break;
                }
            }

            if (resposta.equalsIgnoreCase("TAREFA_CONCLUIDA")) {
                System.out.println("\nTarefa concluída com sucesso!");
                
                // Perguntar se o usuário deseja encerrar ou continuar
                System.out.println("\nDeseja encerrar a conversa? (Digite 's' para sair ou qualquer outra coisa para continuar)");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    String entradaUsuario = reader.readLine();
                    if (entradaUsuario != null && entradaUsuario.trim().toLowerCase().equals("s")) {
                        // Salvar histórico antes de encerrar
                        salvarHistoricoConversa();
                        break; // Encerrar se a resposta for 's'
                    } else {
                        // Repassar a entrada do usuário diretamente para a IA
                        conversationHistory.add(new ChatMessage("user", entradaUsuario != null ? entradaUsuario : ""));
                        salvarHistoricoConversa();
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao ler entrada do usuário: " + e.getMessage());
                    // Salvar histórico antes de encerrar
                    salvarHistoricoConversa();
                    break;
                }
            }
            
            // Verificar se é um comando interativo
            if (resposta.startsWith("INTERATIVO: ")) {
                String comando = resposta.substring(12).trim();
                System.out.println("\nATENÇÃO: O comando '" + comando + "' é interativo e requer sua entrada durante a execução.");
                System.out.println("Deseja executar este comando interativamente? (s/n)");
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                try {
                    String confirmacao = reader.readLine();
                    if (confirmacao == null || !confirmacao.trim().toLowerCase().startsWith("s")) {
                        conversationHistory.add(new ChatMessage("user", 
                            "O usuário decidiu não executar o comando interativo. " + 
                            "Por favor, sugira uma alternativa ou pergunte ao usuário como deseja prosseguir."));
                        salvarHistoricoConversa();
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao ler confirmação do usuário: " + e.getMessage());
                    salvarHistoricoConversa();
                    continue;
                }
                
                // Executar o comando interativamente
                System.out.println("\nExecutando comando interativo: " + comando);
                String resultado = executarComandoInterativo(comando);
                System.out.println("Resultado:\n" + resultado);
                
                conversationHistory.add(new ChatMessage("user", "Comando interativo executado. " + 
                    "O usuário interagiu diretamente com o programa. Resultado/saída final:\n" + resultado));
                salvarHistoricoConversa();
                continue;
            }

            // Se não for nenhum dos casos especiais acima, é um comando de terminal normal
            if (!resposta.trim().isEmpty()) {
                System.out.println("\nExecutando comando: " + resposta);
                String resultado = executarComando(resposta);
                System.out.println("Resultado:\n" + resultado);
                
                conversationHistory.add(new ChatMessage("user", "Resultado do comando: " + resultado));
                salvarHistoricoConversa();
                continue;
            }
        }
    }

    private void iniciarConversa() {
        // Mensagem inicial para o ChatGPT
        String systemPrompt = "Você é um assistente especializado em comandos de terminal. " +
                "Você recebe um objetivo e deve identificar uma sequência de comandos para atingir o objetivo. " +
                "Você deve enviar apenas um comando por vez e aguardar o resultado antes de enviar o próximo. " +
                "Existem três tipos de resposta que você pode enviar: " +
                "1. Comandos regulares: Para comandos que não exigem interação durante a execução, envie apenas o comando, sem nenhum texto adicional. " +
                "2. Comandos interativos: Para comandos que exigem entrada do usuário durante a execução (como gh auth login, ssh-keygen, vim, etc.), " +
                "   use o prefixo 'INTERATIVO: ' seguido do comando. Exemplo: 'INTERATIVO: gh auth login'. " +
                "3. Perguntas: Para solicitar informações do usuário, use o prefixo 'PERGUNTA: ' seguido da sua pergunta. " +
                "NUNCA envie comandos com parâmetros falsos ou placeholders como {seu_usuario}, {nome}, etc. " +
                "Se você não souber o valor de um parâmetro, use 'PERGUNTA: ' para obter essa informação antes de enviar o comando. " +
                "Para perguntas, você pode solicitar dois tipos de informações: " +
                "- Informações sobre qual caminho seguir para atingir o objetivo, caso identifique mais de um caminho. " +
                "- Informações necessárias para parâmetros de um comando, como nome de usuário ou token. " +
                "Existem dois tipos de objetivos: " +
                "1. Pergunta: O usuário quer obter informações sobre o sistema. " +
                "2. Ação: O usuário quer que algo seja feito no sistema. " +
                "Para finalizar a conversa: " +
                "- Se o objetivo for uma pergunta, envie 'RESPOSTA: ' seguido da resposta completa. " +
                "- Se o objetivo for uma ação, envie 'TAREFA_CONCLUIDA' após o último comando. " +
                "- Em caso de erro impeditivo, envie 'ERRO: ' seguido da explicação do erro.";

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

        // Iniciar o loop de processamento da conversa
        processarConversa();
    }
    
    private String executarComandoInterativo(String comando) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", comando);
            pb.redirectErrorStream(true); // Combinar stderr com stdout
            
            // Iniciar o processo
            Process process = pb.start();
            
            // Redirecionar a entrada/saída do processo para o terminal
            new Thread(() -> {
                try {
                    // Transferir stdout do processo para o console
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            
            // Transferir stdin do console para o processo
            BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
            OutputStream processStdin = process.getOutputStream();
            
            System.out.println("Digite suas entradas para o comando interativo. Pressione Ctrl+D para terminar.");
            String line;
            while ((line = consoleReader.readLine()) != null) {
                processStdin.write((line + "\n").getBytes());
                processStdin.flush();
            }
            
            try {
                processStdin.close();
            } catch (IOException e) {
                // Ignorar erro de fechamento
            }
            
            boolean terminou = process.waitFor(30, TimeUnit.SECONDS);
            if (!terminou) {
                process.destroyForcibly();
                return "Comando interativo executado mas excedeu o tempo limite de 30 segundos.";
            }
            
            return "Comando interativo executado com sucesso.";
        } catch (IOException | InterruptedException e) {
            return "Erro ao executar o comando interativo: " + e.getMessage();
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
