#!/bin/bash

# Verificar se o Java está instalado
if ! command -v java &> /dev/null; then
    echo "Erro: Java não está instalado. Por favor, instale o Java 17 ou superior."
    exit 1
fi

# Verificar se o Maven está instalado
if ! command -v mvn &> /dev/null; then
    echo "Erro: Maven não está instalado. Por favor, instale o Maven."
    exit 1
fi

# Verificar se OPENAI_API_KEY está configurada
if [ -z "$OPENAI_API_KEY" ]; then
    echo "Erro: OPENAI_API_KEY não está configurada."
    echo "Por favor, adicione a seguinte linha ao seu ~/.bashrc ou ~/.zshrc:"
    echo "export OPENAI_API_KEY=\"sua-chave-api-aqui\""
    exit 1
fi

# Compilar o projeto
echo "Compilando o projeto..."
mvn clean package

# Criar diretório de instalação
echo "Criando diretório de instalação..."
mkdir -p ~/Applications/osagent

# Copiar o JAR
echo "Copiando arquivos..."
cp target/osagent-1.0-SNAPSHOT-jar-with-dependencies.jar ~/Applications/osagent/osagent.jar

# Criar script de execução
echo "Criando script de execução..."
cat > ~/Applications/osagent/osagent << 'EOF'
#!/bin/bash
java -jar ~/Applications/osagent/osagent.jar "$@"
EOF

# Tornar o script executável
chmod +x ~/Applications/osagent/osagent

# Criar link simbólico
echo "Criando link simbólico..."
if [ -f /usr/local/bin/osagent ]; then
    sudo rm /usr/local/bin/osagent
fi
sudo ln -s ~/Applications/osagent/osagent /usr/local/bin/osagent

echo "Instalação concluída!"
echo "Você pode agora usar o comando 'osagent' de qualquer diretório."
echo "Exemplo: osagent \"qual o uso de memória atual?\"" 