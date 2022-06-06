package pt.tecnico.rec;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.tecnico.rec.grpc.RecordServiceGrpc;
import pt.tecnico.rec.grpc.Rec.*;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.BoolValue;

public class RecordFrontend {

    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
  	/** Helper method to print debug messages. */
  	private static void debug(String debugMessage) {
  		if (DEBUG_FLAG)
  			System.err.print(debugMessage);
  	}

    final ManagedChannel channel;
    RecordServiceGrpc.RecordServiceBlockingStub stub;
    ZKNaming zkNaming;

    public RecordFrontend(String zooHost, String zooPort, String serverPath) throws ZKNamingException {
        zkNaming = new ZKNaming(zooHost, zooPort);
        // lookup
        ZKRecord record = zkNaming.lookup(serverPath);
        String target = record.getURI();

        channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        stub = RecordServiceGrpc.newBlockingStub(channel);
    }

    public void printHashMap(){
      PrintHashMapRequest request = PrintHashMapRequest.newBuilder().build();
      PrintHashMapResponse response = stub.printHashMap(request);
      debug(response.getOutput());
    }

    public PingResponse ping(PingRequest request) {
        return stub.ping(request);
    }

    public ReadResponse read(ReadRequest request) {
      return stub.read(request);
    }

    public WriteResponse write(WriteRequest request) {
      return stub.write(request);
    }

    public void deleteAllRecords() {
      stub.deleteAllRecords(DeleteAllRecordsRequest.newBuilder().build());
    }

    public void deleteRecord(DeleteRecordRequest request) {
      stub.deleteRecord(request);
    }

    public void deleteRecordsUser(String user){
      this.deleteRecord(DeleteRecordRequest.newBuilder().setRecordName("user_nbicloins_" + user).build());
      this.deleteRecord(DeleteRecordRequest.newBuilder().setRecordName("user_hasbike_" + user).build());
      debug("Deleted Records for user " + user + "\n");
    }

    public void deleteRecordsStation(String station){
      this.deleteRecord(DeleteRecordRequest.newBuilder().setRecordName("station_nbikes_" + station).build());
      this.deleteRecord(DeleteRecordRequest.newBuilder().setRecordName("station_nrequests_" + station).build());
      this.deleteRecord(DeleteRecordRequest.newBuilder().setRecordName("station_nreturns_" + station).build());
      debug("Deleted Records for station " + station + "\n");
    }

    public Integer readUserNBicloins(String user) throws InvalidProtocolBufferException{
      ReadRequest req = ReadRequest.newBuilder().setRecordName("get_user_nbicloins_" + user).build();
      ReadResponse res = this.read(req);
      Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
      debug("read\tUserNBicloins\t->\t" + user + "\t" + nb + "\n");
      return nb;
    }

    public boolean readUserHasBike(String user) throws InvalidProtocolBufferException{
      ReadRequest req = ReadRequest.newBuilder().setRecordName("get_user_hasbike_" + user).build();
      ReadResponse res = this.read(req);
      boolean nb = res.getRecordValue().unpack(BoolValue.class).getValue();
      debug("read\tUserHasBike\t->\t" + user + "\t" + nb + "\n");
      return nb;
    }

    public Integer readStationNBikes(String station) throws InvalidProtocolBufferException{
      ReadRequest req = ReadRequest.newBuilder().setRecordName("get_station_nbikes_" + station).build();
      ReadResponse res = this.read(req);
      Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
      debug("read\tStationNBikes\t->\t" + station + "\t" + nb + "\n");
      return nb;
    }

    public Integer readStationNRequests(String station) throws InvalidProtocolBufferException{
        ReadRequest req = ReadRequest.newBuilder().setRecordName("get_station_nrequests_" + station).build();
        ReadResponse res = this.read(req);
        Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
        debug("read\tStationNRequests\t->\t" + station + "\t" + nb + "\n");
        return nb;
    }

    public Integer readStationNReturns(String station) throws InvalidProtocolBufferException{
        ReadRequest req = ReadRequest.newBuilder().setRecordName("get_station_nreturns_" + station).build();
        ReadResponse res = this.read(req);
        Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
        debug("read\tStationNReturns\t->\t" + station + "\t" + nb + "\n");
        return nb;
    }
    public void writeUserNBicloins(String user, Integer nb) throws InvalidProtocolBufferException{
      debug("write\tUserNBicloins\t->\t" + user + "\t" + nb + "\n");
      WriteRequest req = WriteRequest.newBuilder().setRecordName("set_user_nbicloins_" + user).setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build())).build();
      this.write(req);
    }

    public void writeUserHasBike(String user, boolean nb) throws InvalidProtocolBufferException{
      debug("write\tUserHasBike\t->\t" + user + "\t" + nb + "\n");
      WriteRequest req = WriteRequest.newBuilder().setRecordName("set_user_hasbike_" + user).setRecordValue(Any.pack(BoolValue.newBuilder().setValue(nb).build())).build();
      this.write(req);
    }

    public void writeStationNBikes(String station, Integer nb) throws InvalidProtocolBufferException{
      debug("write\tStationNBikes\t->\t" + station + "\t" + nb + "\n");
      WriteRequest req = WriteRequest.newBuilder().setRecordName("set_station_nbikes_" + station).setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build())).build();
      this.write(req);
    }

    public void writeStationNRequests(String station, Integer nb) throws InvalidProtocolBufferException{
        debug("write\tStationNRequests\t->\t" + station + "\t" + nb + "\n");
        WriteRequest req = WriteRequest.newBuilder().setRecordName("set_station_nrequests_" + station).setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build())).build();
        this.write(req);
    }

    public void writeStationNReturns(String station, Integer nb) throws InvalidProtocolBufferException{
        debug("write\tStationNReturns\t->\t" + station + "\t" + nb + "\n");
        WriteRequest req = WriteRequest.newBuilder().setRecordName("set_station_nreturns_" + station).setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build())).build();
        this.write(req);
    }

    public void shutdown() {
        channel.shutdownNow();
    }
}
