package pt.tecnico.bicloin.hub;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.bicloin.hub.grpc.HubServiceGrpc;
import pt.tecnico.bicloin.hub.grpc.Hub.*;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;

public class HubFrontend {
    final ManagedChannel channel;
    HubServiceGrpc.HubServiceBlockingStub stub;
    ZKNaming zkNaming;

    public HubFrontend(String zooHost, String zooPort, String path) throws ZKNamingException {
        zkNaming = new ZKNaming(zooHost, zooPort);
        //lookup
        ZKRecord record = zkNaming.lookup(path);
        String target = record.getURI();

        channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        stub = HubServiceGrpc.newBlockingStub(channel);
    }

    public PingResponse ping(PingRequest request) {
        return stub.ping(request);
    }

    public SystemStatusResponse sysStatus(SystemStatusRequest request) {
        return stub.sysStatus(request);
    }

    public UserLoginResponse userLogin(UserLoginRequest request) {
        return stub.userLogin(request);
    }

    public StationInfoResponse infoStation(StationInfoRequest request) {
        return stub.infoStation(request);
    }

    public LocateStationResponse locateStation(LocateStationRequest request) {
        return stub.locateStation(request);
    }

    public BalanceResponse balance(BalanceRequest request) {
        return stub.balance(request);
    }

    public TopUpResponse topUp(TopUpRequest request) {
        return stub.topUp(request);
    }

    public BikeUpResponse bikeUp(BikeUpRequest request) {
        return stub.bikeUp(request);
    }

    public BikeDownResponse bikeDown(BikeDownRequest request) {
        return stub.bikeDown(request);
    }

    //Nota: Método de uso exclusivo em testes. Se não for realizado bikeDown antes, vai "eliminar" bicicletas do sistema.
    public ClearUserDataResponse clearUserData(ClearUserDataRequest request) {
        return stub.clearUserData(request);
    }

    public void shutdown() {
        channel.shutdownNow();
    }
}
