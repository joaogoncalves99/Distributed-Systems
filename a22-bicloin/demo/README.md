# Guião de Demonstração

## 1. Preparação do sistema

Para testar o sistema e todos os seus componentes, é necessário preparar um ambiente com dados para proceder à verificação dos testes.

### 1.1. Lançar o *registry*

Para lançar o *ZooKeeper*, ir à pasta `zookeeper/bin` e correr o comando  
`./zkServer.sh start` (Linux) ou `zkServer.cmd` (Windows).

É possível também lançar a consola de interação com o *ZooKeeper*, novamente na pasta `zookeeper/bin` e correr `./zkCli.sh` (Linux) ou `zkCli.cmd` (Windows).

### 1.2. Compilar o projeto

Primeiramente, é necessário compilar e instalar todos os módulos e suas dependências --  *rec*, *hub*, *app*, etc.
Para isso, basta ir à pasta *root* do projeto e correr o seguinte comando:

```sh
$ mvn clean install -DskipTests
```

### 1.3. Lançar e testar o *rec*

Para proceder aos testes, é preciso em primeiro lugar lançar o servidor *rec* .
Para isso basta ir à pasta *rec* e executar:

```sh
$ mvn compile exec:java
```

Este comando vai colocar o *rec* no endereço *localhost* e na porta *8091*.

Para confirmar o funcionamento do servidor com um *ping*, fazer:

```sh
$ cd rec-tester
$ mvn compile exec:java
```

Para executar toda a bateria de testes de integração, fazer:

```sh
$ mvn verify
```

Todos os testes devem ser executados sem erros.


### 1.4. Lançar e testar o *hub*

Semelhante a como se procedeu para o *rec*, o hub necessita primeiro de lançar tanto o servidor *rec* como o servidor *hub*.
Começar por ir à diretoria *rec* e executar:
```sh
$ mvn compile exec:java
```

De seguida lançar o servidor *hub* com os seguintes comandos:

```sh
$ cd hub
$ mvn compile exec:java
```

Este comando vai colocar o *hub* no endereço *localhost* e na porta *8081*.

Para confirmar o funcionamento do servidor, fazer:

```sh
$ cd hub-tester
$ mvn compile exec:java
```

Ou para correr o conjunto de testes de integração, executar:

```sh
$ mvn verify
```

Todos os testes devem ser executados sem erros.


### 1.5. *App*

Iniciar a aplicação com a utilizadora alice:

```sh
$ app localhost 2181 alice +35191102030 38.7380 -9.3000
```

**Nota:** Para poder correr o script *app* diretamente é necessário fazer `mvn install` e adicionar ao *PATH* ou utilizar diretamente os executáveis gerados na pasta `target/appassembler/bin/`.

Como alternativa, somente para uma instância, é possível correr o *app* usando o Maven, exatamente como realizado para os servidores.
Na diretoria da *app*, executar:

```sh
$ mvn compile exec:java
```

Isto irá iniciar aplicação com a utilizadora alice.

Abrir outra consola, e iniciar a aplicação com o utilizador bruno.

Depois de lançar todos os componentes, tal como descrito acima, já temos o que é necessário para usar o sistema através dos comandos.


## 2. Teste dos comandos

Nesta secção vamos correr os comandos necessários para testar todas as operações do sistema.
Cada subsecção é respetiva a cada operação presente no *hub*, e baseia-se nos valores de início da *app*, ou seja, cada exemplo é independente dos outros.

### 2.1. *balance*

Procedimento regular:

```sh
> balance
alice 0 BIC
```

Visto este comando não receber argumentos, não possui um caso de erro.

### 2.2 *top-up*

Procedimento regular:

```sh
> top-up 10
alice 100 BIC
```

Procedimento de caso de erro:

```sh
> top-up 50
ERRO INVALID_ARGUMENT: A quantia deve ser um número inteiro entre 1 e 20 EUR.
```
### 2.3 *info-station*

Procedimento regular:

```sh
> info istt
IST Taguspark, lat 38.7372, -9.3023 long, 20 docas, 4 BIC prémio, 12 bicicletas, 0 levantamentos, 0 devoluções, https://www.google.com/maps/place/38.7372,-9.3023
```

Procedimentos de caso de erro:

```sh
> info heyo
ERRO NOT_FOUND: A estação indicada não foi encontrada.
```

```sh
> info abcdef
ERRO INVALID_ARGUMENT: O ID é de 4 caracteres alfanuméricos.
```

### 2.4 *locate-station*

Procedimento regular:

```sh
> scan 3
istt, lat 38.7372, -9.3023 long, 20 docas, 4 BIC pŕemio, 12 bicicletas, a 0 metros
stao, lat 38.6867, -9.3124 long, 30 docas, 3 BIC pŕemio, 20 bicicletas, a 5 metros
jero, lat 38.6972, -9.2064 long, 30 docas, 3 BIC pŕemio, 20 bicicletas, a 9 metros
```

Procedimento de caso de erro:

```sh
> scan 0
ERRO INVALID_ARGUMENT: Valor inválido para a quantia de estações a se representar.
```

(Edge-case da situação onde o utilizador tem coordenadas fora das possíveis)
```sh
> scan 2
ERRO INVALID_ARGUMENT: Valores inválidos para as coordenadas.
```

### 2.5 *bike-up*

Procedimento regular:

```sh
> move 38.737 -9.302
alice em https://www.google.com/maps/place/38.737,-9.302
> bike-up istt
OK
```

Procedimento de caso de erro:

```sh
> bike-up ista
ERRO OUT_OF_RANGE: Fora do alcançe desta estação.
```

### 2.6 *bike-down*

Procedimento regular:

```sh
> move 38.737 -9.302
alice em https://www.google.com/maps/place/38.737,-9.302
> bike-up istt
OK
> bike-down istt
OK
```

Procedimento de caso de erro:

```sh
> bike-down ista
ERRO OUT_OF_RANGE: Fora do alcançe desta estação.
```

```sh
> bike-down istt
ERRO INVALID_ARGUMENT: O utilizador não está a utilizar uma bicicleta.
```

### 2.7 *ping*

Procedimento regular:

```sh
> ping Hello
Hello Hello!
```

Procedimento de caso de erro:

```sh
> ping
ERRO Argumentos inválidos
```

### 2.8 *sys-status*

Procedimento regular:

(Depende da quantidade de instâncias de servidores já foram abertas previamente)
```sh
> sys-status
Server: /grpc/bicloin/hub/1     Status: Up
Server: /grpc/bicloin/hub/2     Status: Down
Server: /grpc/bicloin/rec/1     Status: Up
```

Visto este comando não receber argumentos, não possui um caso de erro.

## 3. Replicação e Tolerância a faltas


Primeiramente, é necessário confirmar que tanto o ZooKeeper está operacional, bem como todos os módulos e suas dependências estão instaladas/compiladas --  *rec*, *hub* e *app*.
Caso não tenha realizado os passos anteriores, o ZooKeeper pode ser inicializado se seguir a secção `1.1`, e o projeto pode ser compilado na totalidade seguindo o passo `1.2`

De seguida, vamos iniciar os servidores *rec*. Começando, no terminal, da diretoria `A22-Bicloin`, realize os seguintes comandos:
```sh
$ cd rec
$ mvn compile exec:java -Ddebug
```
Este comando inicia a instância 1 de *rec*, em modo debug.

Em novos terminais, aceder em cada à pasta *rec/target/appassembler/bin*
```shell
$ cd target/appassembler/bin
```
E executar nas janelas diferentes:
```sh
$ ./rec localhost 2181 localhost 809{i} {i}   //Onde i equivale à instância do servidor. Vamos considerar neste demo instâncias 2 a 5
```

De seguida, a partir da dirétoria root, ir à pasta *hub* para lançar o servidor *hub* em outra janela:
```sh
$ cd hub
$ mvn compile exec:java -Ddebug
```

E agora, também a partir da dirétoria root, à pasta *app* e verificar a execução normal do programa:
```sh
$ cd app
$ mvn compile exec:java < ../demo/commands.txt
```
Depois de executado, nesta mesma pasta, iniciar a aplicação de forma normal:
```sh
$ mvn compile exec:java
```
É possível executar agora comandos na app, para cada read e write utilizados para estes comandos o hub vai contactar todos os recs
como por exemplo:
```sh
> balance
> top-up 10
> info istt
```
Sendo o esperado o hub enviar 5 reads no balance, 5 reads seguidos de 5 writes no top-up e 15 reads para o info, sendo 5 para o numero de bicicletas, 5 para o numero de request e os outros 5 para o numero de returns.
O hub não precisa de esperar de todas as respostas às suas requests para continuar a execução, sendo apenas necessário o peso das réplicas que respondam ser maior ao threshold necessário para essa operação.
Isso pode ser averiguado se, ao correr os comandos anteriores, antes de correr o top-up um se utilizar o comando *CTRL+Z* em uma réplica com peso 0.5 (possível saber qual ao se ver no hub a linha "Received read from replica X with weight Y" no comando balance).
Depois de correr o comando top-up é possível ver no hub que não recebeu a resposta da réplica em falta, no entanto continuou com a execução. Também depois deste comando se for utilizado o comando *fg* no terminal da réplica que for terminada esta volta a entar em execução.
É depois possivel utilizar o comando info que voltará a utilizar a réplica que tinha falhado.

----

## 4. Considerações Finais

Estes testes não cobrem tudo, pelo que deve ter sempre em conta os testes de integração e o código.
