package pt.tecnico.bicloin.app;

import com.google.type.Money;
import io.grpc.StatusRuntimeException;
import pt.tecnico.bicloin.hub.HubFrontend;
import pt.tecnico.bicloin.hub.grpc.Hub;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;

import java.util.Collection;
import java.util.HashMap;

public class App {
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
    private static int debugCommandLine = 0;
    /** Helper method to print debug messages. */
    public static void debug(String debugMessage) {
        if (DEBUG_FLAG){
            System.err.print(debugCommandLine + ": " + debugMessage + "\n");
            debugCommandLine++;
        }
    }

    private String zooKeeperHost;
    private String zooKeeperPort;
    private String serverPath;
    private HubFrontend hub = null;

    private String userID;
    private String userName;
    private String userPhone;
    private double userLatitude;
    private double userLongitude;

    static class LocationTag {
        public double tagLatitude;
        public double tagLongitude;

        public LocationTag(double latitude, double longitude) {
            tagLatitude = latitude;
            tagLongitude = longitude;
        }
    }

    HashMap<String, LocationTag> tagMap = new HashMap<>();

    public boolean AttemptServerConnection(String zooHost, String zooPort, String path) {
        zooKeeperHost = zooHost;
        zooKeeperPort = zooPort;

        try {
            Shutdown();     //Shutdown previous hub, if it existed
            hub = new HubFrontend(zooHost, zooPort, path);

            Hub.PingRequest request = Hub.PingRequest.newBuilder().setInput("ping").build();
            Hub.PingResponse response = hub.ping(request);
            return !response.getOutput().isEmpty();
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean ConnectToServer(String host, String port, String path) throws ZKNamingException{
        serverPath = path;

        ZKNaming zkNaming = new ZKNaming(host, port);
        Collection<ZKRecord> hubServerRecords = zkNaming.listRecords(path);
        for (ZKRecord record : hubServerRecords) {
            if(AttemptServerConnection(host, port, record.getPath())){
                return true;
            }
        }
        return false;
    }

    public void LoginToServer(String id, String phoneNumber, double latitude, double longitude) throws IllegalArgumentException{
        Hub.UserLoginRequest request = Hub.UserLoginRequest.newBuilder().setUserID(id).build();
        try{
            Hub.UserLoginResponse response = hub.userLogin(request);

            if(response.getUserPhoneNumber().equals(phoneNumber)){
                this.userID = id;
                this.userName = response.getUserName();
                this.userPhone = phoneNumber;
                this.userLatitude = latitude;
                this.userLongitude = longitude;
            }
            else
                throw new IllegalArgumentException();
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }

    public boolean ExecuteCommand(String input) throws ZKNamingException{
        String[] inputArgs = input.split("[ ]+");
        debug(input);
        if(inputArgs.length == 0)
            return true;

        try{
            switch (inputArgs[0]){
                case "balance":
                    CheckBalance(inputArgs);
                    break;
                case "top-up":
                    TopUp(inputArgs);
                    break;
                case "bike-up":
                    BikeUp(inputArgs);
                    break;
                case "bike-down":
                    BikeDown(inputArgs);
                    break;
                case "tag":
                    SetTag(inputArgs);
                    break;
                case "move":
                    MoveTo(inputArgs);
                    break;
                case "at":
                    CurrentLocation(inputArgs);
                    break;
                case "info":
                    StationInfo(inputArgs);
                    break;
                case "scan":
                    LocateClosestStations(inputArgs);
                    break;
                case "ping":
                    PingServer(inputArgs);
                    break;
                case "sys-status":
                    SystemStatus(inputArgs);
                    break;
                case "help":
                    DisplayAvailableCommands(inputArgs);
                    break;
                case "zzz":
                    AppSleep(inputArgs);
                    break;
                case "exit":
                    return false;
                case "#":
                case "\n":
                case "":
                case " ":
                    break;
                default:
                    System.out.println("ERRO Comando inválido");
                    break;
            }
        }
        catch (StatusRuntimeException sre) {
            if(!ConnectToServer(zooKeeperHost, zooKeeperPort, serverPath))
                throw new ZKNamingException();
            else
                ExecuteCommand(input);      //Repeat the parsing if it managed to reconnect
        }
        return true;
    }

    public void CheckBalance(String[] inputArgs) {
        if(inputArgs.length != 1) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.BalanceRequest request = Hub.BalanceRequest.newBuilder().setUserID(userID).build();
        try{
            Hub.BalanceResponse response = hub.balance(request);
            System.out.format("%s %d BIC%n", userID, response.getUserBicloinBalance());
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }

    public void TopUp(String[] inputArgs) {
        long value;
        try {
            value = Long.parseLong(inputArgs[1]);
            if(inputArgs.length != 2) {
                System.out.println("ERRO Argumentos inválidos");
                return;
            }
        }
        catch (NumberFormatException nfe) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.TopUpRequest request = Hub.TopUpRequest.newBuilder()
                .setUserID(userID)
                .setUserChargeAmount(Money.newBuilder()
                        .setCurrencyCode("EUR")
                        .setUnits(value))
                .setUserPhoneNumber(userPhone).build();

        try{
            Hub.TopUpResponse response = hub.topUp(request);
            System.out.format("%s %d BIC%n", userID, response.getUserBicloinBalance());
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }

    public void SetTag(String[] inputArgs) {
        if(inputArgs.length != 4) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        try {
            tagMap.put(inputArgs[3],
                    new LocationTag(Double.parseDouble(inputArgs[1]), Double.parseDouble(inputArgs[2])));
            System.out.println("OK");
        } catch (NumberFormatException nfe) {
            System.out.println("ERRO Argumentos inválidos");
        }
    }

    public void MoveTo(String[] inputArgs) {
        if(inputArgs.length == 2) {
            LocationTag tag = tagMap.get(inputArgs[1]);
            userLatitude = tag.tagLatitude;
            userLongitude = tag.tagLongitude;
            System.out.format("%s em https://www.google.com/maps/place/%s,%s%n",
                    userID,
                    Double.toString(userLatitude).replace(",", "."),
                    Double.toString(userLongitude).replace(",", "."));
        }
        else if (inputArgs.length == 3) {
            try{
                userLatitude = Double.parseDouble(inputArgs[1]);
                userLongitude = Double.parseDouble(inputArgs[2]);
                System.out.format("%s em https://www.google.com/maps/place/%s,%s%n",
                        userID,
                        inputArgs[1],
                        inputArgs[2]);
            }
            catch (NumberFormatException nfe) {
                System.out.println("ERRO Argumentos inválidos");
            }
        }
        else {
            System.out.println("ERRO Argumentos inválidos");
        }
    }

    public void CurrentLocation(String[] inputArgs) {
        if(inputArgs.length != 1) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        System.out.format("%s em https://www.google.com/maps/place/%s,%s%n",
                userID,
                Double.toString(userLatitude).replace(",", "."),
                Double.toString(userLongitude).replace(",", "."));
    }

    public void LocateClosestStations(String[] inputArgs) {
        int value;
        try{
            if(inputArgs.length != 2) {
                System.out.println("ERRO Argumentos inválidos");
                return;
            }
            value = Integer.parseInt(inputArgs[1]);
        }
        catch (NumberFormatException nfe) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.LocateStationRequest request = Hub.LocateStationRequest.newBuilder()
                .setUserLatitude(userLatitude)
                .setUserLongitude(userLongitude)
                .setAmountToDisplay(value).build();

        Hub.LocateStationResponse response;
        try{
            response = hub.locateStation(request);
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
            return;
        }

        for(int i = 0; i < response.getStationIDCount(); i++) {
            Hub.StationInfoRequest infoRequest = Hub.StationInfoRequest.newBuilder()
                    .setStationID(response.getStationID(i)).build();
            Hub.StationInfoResponse infoResponse = hub.infoStation(infoRequest);

            String latitude = Double.toString(infoResponse.getStationLatitude()).replace(",", ".");
            String longitude =  Double.toString(infoResponse.getStationLongitude()).replace(",", ".");
            System.out.format("%s, lat %s, %s long, %d docas, %d BIC pŕemio, %d bicicletas, a %d metros%n",
                    response.getStationID(i),
                    latitude,
                    longitude,
                    infoResponse.getStationDockCapacity(),
                    infoResponse.getStationPrize(),
                    infoResponse.getStationAvailableBikes(),
                    (int) HaversineDistance(userLatitude, userLongitude,
                            infoResponse.getStationLatitude(), infoResponse.getStationLongitude()));
        }
    }

    public void StationInfo(String[] inputArgs){
        if(inputArgs.length != 2) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.StationInfoRequest request = Hub.StationInfoRequest.newBuilder().setStationID(inputArgs[1]).build();
        Hub.StationInfoResponse response;
        try{
            response = hub.infoStation(request);
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
            return;
        }

        String latitude = Double.toString(response.getStationLatitude()).replace(",", ".");
        String longitude =  Double.toString(response.getStationLongitude()).replace(",", ".");
        System.out.format("%s, lat %s, %s long, %d docas, %d BIC pŕemio, %d bicicletas, %d levantamentos, %d devoluções, " +
                        "https://www.google.com/maps/place/%s,%s%n",
                response.getStationName(),
                latitude,
                longitude,
                response.getStationDockCapacity(),
                response.getStationPrize(),
                response.getStationAvailableBikes(),
                response.getRequestedStatistic(),
                response.getReturnedStatistic(),
                latitude,
                longitude);
    }

    public void BikeUp(String[] inputArgs) {
        if(inputArgs.length != 2) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.BikeUpRequest request = Hub.BikeUpRequest.newBuilder()
                .setUserID(userID)
                .setUserLatitude(userLatitude)
                .setUserLongitude(userLongitude)
                .setStationID(inputArgs[1]).build();

        try{
            Hub.BikeUpResponse response = hub.bikeUp(request);
            if(response.getRequestAccepted())
                System.out.println("OK");
            else
                System.out.println("ERRO fora de alcance");
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }

    public void BikeDown(String[] inputArgs) {
        if(inputArgs.length != 2) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.BikeDownRequest request = Hub.BikeDownRequest.newBuilder()
                .setUserID(userID)
                .setUserLatitude(userLatitude)
                .setUserLongitude(userLongitude)
                .setStationID(inputArgs[1]).build();

        try{
            Hub.BikeDownResponse response = hub.bikeDown(request);
            if(response.getRequestAccepted())
                System.out.println("OK");
            else
                System.out.println("ERRO fora de alcance");
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }

    public void PingServer(String[] inputArgs) {
        if (inputArgs.length != 2) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        Hub.PingRequest request = Hub.PingRequest.newBuilder().setInput(inputArgs[1]).build();
        try {
            Hub.PingResponse response = hub.ping(request);
            System.out.println(response.getOutput());
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }
    public void SystemStatus(String[] inputArgs) {
        if(inputArgs.length > 2) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }

        //Irrelevant if any other input is used
        Hub.SystemStatusRequest request = Hub.SystemStatusRequest.newBuilder().setInput("ping").build();
        try {
            Hub.SystemStatusResponse response = hub.sysStatus(request);

            for(int i = 0; i < response.getServerPathList().size(); i++) {
                System.out.println("Server: " + response.getServerPath(i) + "\tStatus: " + response.getServerStatus(i));
            }
        }
        catch (StatusRuntimeException sre) {
            System.out.println("ERRO " + sre.getMessage());
        }
    }

    public void Shutdown() {
        if(hub != null) {
            hub.shutdown();
        }
    }

    private void AppSleep(String[] inputArgs) {
        if(inputArgs.length != 2) {
            System.out.println("ERRO Argumentos inválidos");
            return;
        }
        try {
            int milliseconds = Integer.parseInt(inputArgs[1]);
            Thread.sleep(milliseconds);
        }
        catch (NumberFormatException nfe) {
            System.out.println("ERRO Argumentos inválidos");
        }
        catch (InterruptedException ie) {
            System.out.println("ERRO Ocorreu um problema inesperado");
        }

    }

    private void DisplayAvailableCommands(String[] inputArgs) {
        System.out.println("Lista de commandos disponíveis:\n" +
                "balance\t\t\t\t\t\tApresenta a quantia atual de Bicloins do utilizador\n" +
                "top-up <valor>\t\t\t\t\tCompra de novas Bicloins (Valor entre 1 e 20 EUR)\n" +
                "info <ID da estação>\t\t\t\tApresenta a informação detalhada da estação indicada\n" +
                "scan <quantidade a apresentar>\t\t\tApresenta as estações mais próximas do utilizador\n" +
                "bike-up <ID da estação>\t\t\t\tRequesita uma bicicleta da estação indicada\n" +
                "bike-down <ID da estação>\t\t\tRetorna uma bicicleta à estação indicada\n" +
                "ping <mensagem>\t\t\t\t\tTeste de conectividade ao servidor\n" +
                "sys-status\t\t\t\t\tApresenta todos os servidors na rede, bem como os seus estados de operacionalidade\n" +
                "tag <latitude> <longitude> <nome da nova tag>\tCria uma nova tag que marca o ponto indicado\n" +
                "move <nome da tag>\t\t\t\tMove a posição do utilizador para a posição indicada pela tag\n" +
                "move <latitude> <longitude>\t\t\tMove a posição do utilizador para a posição indicada pelas coordenadas\n" +
                "at\t\t\t\t\t\tIndica a posição atual do utilizador\n" +
                "zzz <milissegundos>\t\t\t\tA aplicação fica inativa durante a duração indicada" +
                "exit\t\t\t\t\t\tSai da aplicação.");
    }

    /* Source for this code: https://github.com/jasonwinn/haversine/blob/master/Haversine.java */
    private static double HaversineDistance(double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
        debug("User at " + startLatitude + ", " + startLongitude + "\tStation at " + endLatitude + ", " + endLongitude);
        double earthRadius = 6371e3;    //metres

        double dLat  = Math.toRadians((endLatitude - startLatitude));
        double dLong = Math.toRadians((endLongitude - startLongitude));

        startLatitude = Math.toRadians(startLatitude);
        endLatitude   = Math.toRadians(endLatitude);

        double a = Haversine(dLat) + Math.cos(startLatitude) * Math.cos(endLatitude) * Haversine(dLong);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }

    private static double Haversine(double val) {
        return Math.pow(Math.sin(val / 2), 2);
    }
}
