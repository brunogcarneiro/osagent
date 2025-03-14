@echo off

REM Verificar se o Java está instalado
java -version >nul 2>&1
if errorlevel 1 (
    echo Erro: Java nao esta instalado. Por favor, instale o Java 17 ou superior.
    exit /b 1
)

REM Verificar se o Maven está instalado
mvn -version >nul 2>&1
if errorlevel 1 (
    echo Erro: Maven nao esta instalado. Por favor, instale o Maven.
    exit /b 1
)

REM Verificar se OPENAI_API_KEY está configurada
if "%OPENAI_API_KEY%"=="" (
    echo Erro: OPENAI_API_KEY nao esta configurada.
    echo Por favor, configure a variavel de ambiente OPENAI_API_KEY com sua chave API.
    echo Voce pode fazer isso através do Painel de Controle ^> Sistema ^> Variaveis de Ambiente
    exit /b 1
)

REM Compilar o projeto
echo Compilando o projeto...
call mvn clean package
if errorlevel 1 (
    echo Erro ao compilar o projeto.
    exit /b 1
)

REM Criar diretório de instalação
echo Criando diretorio de instalacao...
if not exist "%USERPROFILE%\Applications\osagent" mkdir "%USERPROFILE%\Applications\osagent"

REM Copiar o JAR
echo Copiando arquivos...
copy target\osagent-1.0-SNAPSHOT-jar-with-dependencies.jar "%USERPROFILE%\Applications\osagent\osagent.jar"

REM Criar script batch
echo Criando script de execucao...
echo @echo off > "%USERPROFILE%\Applications\osagent\osagent.bat"
echo java -jar "%USERPROFILE%\Applications\osagent\osagent.jar" %%* >> "%USERPROFILE%\Applications\osagent\osagent.bat"

REM Adicionar ao PATH do usuário
echo Adicionando ao PATH...
setx PATH "%PATH%;%USERPROFILE%\Applications\osagent"

echo.
echo Instalacao concluida!
echo Voce precisa reiniciar o prompt de comando para usar o comando 'osagent'.
echo Exemplo: osagent "qual o uso de memoria atual?"
echo. 