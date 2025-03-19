# OSAgent - Assistente de Terminal com ChatGPT

O OSAgent é uma ferramenta de linha de comando que utiliza o ChatGPT para auxiliar em tarefas do terminal. Ele pode tanto responder perguntas quanto executar tarefas, tudo através de linguagem natural.

## Funcionalidades

- **Modo Pergunta**: Responde perguntas sobre o sistema ou arquivos
  - "Qual o uso de memória atual?"
  - "Quais os 5 arquivos mais pesados neste diretório?"
  - "Quantos processos Java estão rodando?"

- **Modo Tarefa**: Executa sequências de comandos para realizar tarefas
  - "Criar um backup dos arquivos .txt"
  - "Limpar arquivos temporários"
  - "Instalar o Docker"

- **Interação Inteligente**:
  - Faz perguntas de esclarecimento quando necessário
  - Executa comandos de forma sequencial e segura
  - Analisa resultados para determinar próximos passos
  - Fornece feedback claro sobre o progresso

- **Instruções Personalizadas**:
  - Permite adicionar instruções específicas para o assistente
  - Suporta regras e comportamentos customizados
  - Ideal para adaptar o assistente a necessidades específicas

- **Histórico de Conversas**:
  - Salva automaticamente o histórico de cada conversa em formato JSON
  - Permite continuar conversas anteriores a partir de qualquer ponto
  - Organiza os históricos com nomes descritivos baseados no conteúdo

- **Comandos Interativos**:
  - Suporte a comandos que exigem interação do usuário durante a execução
  - Fornece aviso claro antes de executar comandos interativos
  - Permite interação direta com ferramentas como vim, ssh-keygen, gh auth, etc.

## Pré-requisitos

- Java 17 ou superior
- Maven
- Uma chave de API do OpenAI (GPT-3.5 Turbo)

## Instalação

### 1. Clone o repositório:
```bash
git clone https://github.com/seu-usuario/osagent.git
cd osagent
```

### 2. Execute o script de instalação:

#### No macOS/Linux:
```bash
chmod +x install.sh
./install.sh
```

#### No Windows:
```batch
install.bat
```

Os scripts de instalação irão:
- Verificar se os pré-requisitos estão instalados
- Compilar o projeto
- Configurar o ambiente
- Instalar o comando `osagent` globalmente

**Nota**: No Windows, você precisará reiniciar o prompt de comando após a instalação para usar o comando `osagent`.

### Instalação Manual (Alternativa)

Se preferir instalar manualmente, siga estes passos:

#### 2.1. Compile o projeto:
```bash
mvn clean package
```

#### 2.2. Configure a chave de API do OpenAI:
Adicione a seguinte linha ao seu arquivo `~/.bashrc`, `~/.zshrc` ou equivalente:
```bash
export OPENAI_API_KEY="sua-chave-api-aqui"
```

#### 2.3. Instale globalmente:

##### No macOS/Linux:
```bash
# Criar diretório para aplicativos locais (se não existir)
mkdir -p ~/Applications/osagent

# Copiar o JAR para o diretório de aplicativos
cp target/osagent-1.0-SNAPSHOT-jar-with-dependencies.jar ~/Applications/osagent/osagent.jar

# Criar script de execução
echo '#!/bin/bash
java -jar ~/Applications/osagent/osagent.jar "$@"' > ~/Applications/osagent/osagent

# Tornar o script executável
chmod +x ~/Applications/osagent/osagent

# Criar link simbólico no diretório bin
sudo ln -s ~/Applications/osagent/osagent /usr/local/bin/osagent
```

##### No Windows:
```batch
:: Criar diretório para aplicativos locais (se não existir)
mkdir "%USERPROFILE%\Applications\osagent"

:: Copiar o JAR para o diretório de aplicativos
copy target\osagent-1.0-SNAPSHOT-jar-with-dependencies.jar "%USERPROFILE%\Applications\osagent\osagent.jar"

:: Criar script batch
echo @echo off > "%USERPROFILE%\Applications\osagent\osagent.bat"
echo java -jar "%USERPROFILE%\Applications\osagent\osagent.jar" %%* >> "%USERPROFILE%\Applications\osagent\osagent.bat"

:: Adicionar ao PATH do usuário
setx PATH "%PATH%;%USERPROFILE%\Applications\osagent"
```

## Uso

Após a instalação, você pode usar o comando `osagent` de qualquer diretório:

```bash
# Para fazer uma pergunta
osagent "qual o uso de CPU atual?"

# Para executar uma tarefa
osagent "criar um backup compactado dos arquivos modificados hoje"

# Para obter ajuda
osagent --help

# Para usar instruções personalizadas
osagent -i /caminho/para/instrucoes.txt "sua pergunta ou tarefa aqui"

# Para continuar uma conversa anterior
osagent -c

# O programa irá mostrar uma lista numerada dos históricos disponíveis:
# Históricos de conversa disponíveis:
# 1. 20240315123045-gerenciamento_de_pacotes.json
# 2. 20240315123512-backup_de_arquivos.json
# 3. 20240315124030-limpeza_de_cache.json
#
# Digite o número do histórico que deseja carregar:
```

### Exemplos de Uso

1. **Perguntas sobre o sistema**:
```bash
osagent "quanto espaço livre tem no disco?"
osagent "quais processos estão consumindo mais memória?"
```

2. **Gerenciamento de arquivos**:
```bash
osagent "encontrar arquivos duplicados neste diretório"
osagent "organizar fotos por data"
```

3. **Tarefas de manutenção**:
```bash
osagent "limpar arquivos de cache"
osagent "remover arquivos temporários mais antigos que 7 dias"
```

4. **Usando instruções personalizadas**:
```bash
# Criar arquivo de instruções
echo "Sempre responda em português do Brasil" > instrucoes.txt

# Usar o osagent com as instruções
osagent -i instrucoes.txt "what is the current memory usage?"
```

5. **Continuando conversas anteriores**:
```bash
# Iniciar uma conversa
osagent "como funciona o gerenciamento de pacotes neste sistema?"

# Continuar a mesma conversa mais tarde
osagent -c ./.osagent/history/20240315123045-gerenciamento_de_pacotes.json "como atualizar todos os pacotes?"
```

6. **Comandos interativos**:
```bash
# Configurar autenticação GitHub
osagent "configurar acesso ao GitHub CLI"

# Quando o assistente precisar usar um comando interativo, ele mostrará:
# ATENÇÃO: O comando 'gh auth login' é interativo e requer sua entrada durante a execução.
# Deseja executar este comando interativamente? (s/n)
```

### Formato das Instruções Personalizadas

O arquivo de instruções pode conter qualquer texto que você queira adicionar ao prompt do sistema. Por exemplo:

```text
Sempre responda em português do Brasil
Use termos técnicos simplificados
Forneça exemplos práticos quando possível
```

### Gerenciamento de Histórico

O OSAgent armazena automaticamente o histórico de todas as conversas em arquivos JSON organizados na pasta `.osagent/history/` dentro do diretório atual. Se esta pasta não existir, ela será criada automaticamente na primeira execução. Os arquivos de histórico são nomeados seguindo o padrão:

```
[TIMESTAMP]-[DESCRIÇÃO].json
```

Onde:
- `TIMESTAMP` é uma marca de data/hora no formato yyyyMMddHHmmss
- `DESCRIÇÃO` é uma breve descrição da conversa gerada automaticamente

Para continuar uma conversa, use a opção `-c` sem especificar um arquivo. O programa irá mostrar uma lista numerada dos históricos disponíveis e pedir para você escolher qual deseja carregar:

```bash
osagent -c

# O programa irá mostrar uma lista numerada dos históricos disponíveis:
# Históricos de conversa disponíveis:
# 1. 20240315123045-gerenciamento_de_pacotes.json
# 2. 20240315123512-backup_de_arquivos.json
# 3. 20240315124030-limpeza_de_cache.json
#
# Digite o número do histórico que deseja carregar:
```

Após escolher o histórico, você poderá continuar a conversa normalmente, e todas as novas mensagens serão salvas no mesmo arquivo de histórico.

## Limitações

- Requer uma chave de API válida do OpenAI
- Os comandos são executados no shell padrão do sistema
- Alguns comandos podem requerer permissões específicas
- O uso da API do OpenAI pode gerar custos
- Comandos interativos têm um tempo limite de 30 segundos

## Contribuindo

Sinta-se à vontade para abrir issues ou enviar pull requests com melhorias. 