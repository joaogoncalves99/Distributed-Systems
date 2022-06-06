package pt.tecnico.rec;

import com.google.protobuf.Any;
import com.google.protobuf.BoolValue;
import com.google.protobuf.Int32Value;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.tecnico.rec.grpc.RecordServiceGrpc;
import pt.tecnico.rec.grpc.Rec.*;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;

import java.util.*;
import java.util.stream.Collectors;

public class QuorumFrontend {

    private static final boolean TIME_FLAG = (System.getProperty("time") != null);
    private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
    /** Helper method to print debug messages. */
    private static void debug(String debugMessage) {
        if (DEBUG_FLAG)
            System.err.print(debugMessage);
    }

    static int macroReadCount = 0;
    static int readCount = 0;
    static int macroWriteCount = 0;
    static int writeCount = 0;

    static long readTime = 0;
    static long writeTime = 0;

    private static void time() {
        if(TIME_FLAG)
            System.err.format("Amount of reads: %d (%d)\tTotal time elapsed: %d\n" +
                            "Amount of writes: %d (%d)\tTotal time elapsed: %d\n",
                    macroReadCount, readCount, readTime,
                    macroWriteCount, writeCount, writeTime);
    }

    int totalWeight;

    ZKNaming zkNaming;

    String zkRecServerPaths = "/grpc/bicloin/rec";

    List<ManagedChannel> channels = new ArrayList<>();
    List<RecordServiceGrpc.RecordServiceStub> recordStubList = new ArrayList<>();
    Map<RecordServiceGrpc.RecordServiceStub, Float> stubWeightMap = new HashMap<>();

    ResponseCollector collector = new ResponseCollector();

   public QuorumFrontend(String zooHost, String zooPort) {
        zkNaming = new ZKNaming(zooHost, zooPort);
        Map<RecordServiceGrpc.RecordServiceStub, Long> map = new HashMap<>();

        Collection<ZKRecord> zkRecords = GetListResults(zkNaming, zkRecServerPaths);
        for (ZKRecord record : zkRecords) {
            ZKRecord onlineRec;
            try{
                onlineRec = zkNaming.lookup(record.getPath());
            }
            catch (ZKNamingException zkne) {
                debug("Rec " + record.getPath() + "does not exist.");
                continue;
            }
            String target = onlineRec.getURI();
            ManagedChannel channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
            RecordServiceGrpc.RecordServiceStub stub = RecordServiceGrpc.newStub(channel);

            long time = TimeMeasuredPing(zooHost, zooPort, onlineRec.getPath());
            if(time != -1){
                channels.add(channel);
                recordStubList.add(stub);
                map.put(stub, time);
            }

        }
        SetRecordWeights(map, recordStubList);
        time();
        totalWeight = recordStubList.size();
    }

    private long TimeMeasuredPing(String zooHost, String zooPort, String serverPath) {
        try{
            RecordFrontend recFrontend = new RecordFrontend(zooHost, zooPort, serverPath);

            long startTime = System.currentTimeMillis();
            recFrontend.ping(PingRequest.newBuilder().setInput("Timer").build());
            long endTime = System.currentTimeMillis();
            recFrontend.shutdown();
            return endTime - startTime;
        }
        catch (ZKNamingException | StatusRuntimeException e) {
            return -1;
        }
    }

    private void SetRecordWeights(Map<RecordServiceGrpc.RecordServiceStub, Long> recordPingTimeMap, List<RecordServiceGrpc.RecordServiceStub> stubList) {
        List<Map.Entry<RecordServiceGrpc.RecordServiceStub, Long>> recordPingTimes = new ArrayList<>(recordPingTimeMap.entrySet());
        recordPingTimes.sort(Map.Entry.comparingByValue());

        List<Float> weightList = new ArrayList<>();

        /* Exemplos de casos
             * Para 1: 1
             * ...
             * Para 4: 1 | 1 | 1 | 1
             * Para 5: 2 | 1 | 1 | 0.5 | 0.5
             * Para 6: 2 | 1 | 1 | 1 | 0.5 | 0.5
             * Para 7: 3 | 1 | 1 | 1 | 0.33 | 0.33 | 0.33
             * Para 8: 3 | 1 | 1 | 1 | 1 | 0.33 | 0.33 | 0.33
             * Para 9: 4 | 1 | 1 | 1 | 1 | 0.25 | 0.25 | 0.25 | 0.25
             * Para 11:5 | 1 | 1 | 1 | 1 | 1 | 0.2 | 0.2 | 0.2 | 0.2 | 0.2
             * etc
         */
        int mainDivisionSize = recordPingTimeMap.size() / 2;
        if(recordPingTimes.size() == 1) mainDivisionSize = 1;

        if(recordPingTimes.size() != 2 && recordPingTimes.size() % 2 == 0) {
            mainDivisionSize = mainDivisionSize - 1;
        }

        weightList.add((float) mainDivisionSize);

        for (int i = 0; i < mainDivisionSize; i++) {
            weightList.add(1f);
        }
        if(recordPingTimes.size() != 2 && recordPingTimes.size() % 2 == 0) weightList.add(1f);

        for (int i = 0; i < mainDivisionSize; i++) {
                weightList.add(1f / mainDivisionSize);
        }

        for (int i = 0; i < recordPingTimes.size(); i++) {
            stubWeightMap.put(recordPingTimes.get(i).getKey(), weightList.get(i));
        }
    }

    public void RecalculateTotalWeight(float weightLoss) {
        totalWeight = recordStubList.size();

        if(weightLoss > totalWeight / 2f){
            this.shutdown();
            return;
        }
        totalWeight -= weightLoss;
    }

    private Collection<ZKRecord> GetListResults(ZKNaming zkNameServer, String serverPath) {
        try {
            return zkNameServer.listRecords(serverPath);
        }
        catch (ZKNamingException e) {
            return new ArrayList<>();
        }
    }

    public List<PingResponse> ping(PingRequest request) {
        try {
            collector = new ResponseCollector();
            synchronized (collector) {
                collector.setExpectedResponseAmount(totalWeight / 2f);
                for (RecordServiceGrpc.RecordServiceStub stub : recordStubList) {
                    stub.ping(request, new ResponseObserver<>(collector, stubWeightMap.get(stub), recordStubList.indexOf(stub) + 1));
                }
                List<PingResponse> pingResponses = GetQuorumResponses().stream()
                        .filter(obj -> obj instanceof PingResponse)
                        .map(obj -> (PingResponse) obj).collect(Collectors.toList());

                return pingResponses;
            }
        }
        catch (ZKNamingException zkne) {
            debug("Error in ping. " + zkne.getMessage());
            return null;
        }
    }

    public ReadResponse read(ReadRequest request) {
        int tag = 0;
        int maxIdx = 0;
        long startTime = 0;
        long endTime;

        Context ctx = Context.current().fork();
        Context originalCtx = ctx.attach();

        try{
            collector = new ResponseCollector();
            synchronized (collector) {
                if(TIME_FLAG) startTime = System.currentTimeMillis();

                collector.setExpectedResponseAmount(totalWeight * 0.2f);
                for (RecordServiceGrpc.RecordServiceStub stub : recordStubList) {
                    debug("Contacting replica " + (recordStubList.indexOf(stub) + 1) + " sending read: " + request.getRecordName() + "\n");
                    stub.read(request, new ResponseObserver<>(collector, stubWeightMap.get(stub), recordStubList.indexOf(stub) + 1));
                }

                List<ReadResponse> readResponses = GetQuorumResponses().stream()
                        .filter(obj -> obj instanceof ReadResponse)
                        .map(obj -> (ReadResponse) obj).collect(Collectors.toList());

                int i = 0;
                for (ReadResponse response : readResponses) {
                    if(response.getTag() > tag) {
                        tag = response.getTag();
                        maxIdx = i;
                    }
                    i++;
                }

                if(TIME_FLAG){
                    endTime = System.currentTimeMillis();
                    readTime += (endTime - startTime);
                }
                macroReadCount++;
                return readResponses.get(maxIdx);
            }
        }
        catch (ZKNamingException zkne) {
            debug("Error in read. " + zkne.getMessage());
            return null;
        }
        finally {
            ctx.detach(originalCtx);
        }
    }

    public WriteResponse write(WriteRequest request) {
        long startTime = 0;
        long endTime;

        Context ctx = Context.current().fork();
        Context originalCtx = ctx.attach();

        try{
            collector = new ResponseCollector();
            synchronized (collector) {
                if(TIME_FLAG) startTime = System.currentTimeMillis();

                collector.setExpectedResponseAmount(totalWeight * 0.9f);
                for (RecordServiceGrpc.RecordServiceStub stub : recordStubList) {
                    debug("Contacting replica " + (recordStubList.indexOf(stub) + 1) + " sending write: " + request.getRecordName() + "\n");
                    stub.write(request, new ResponseObserver<>(collector, stubWeightMap.get(stub), recordStubList.indexOf(stub) + 1));
                    writeCount++;
                }
                List<WriteResponse> writeResponses = GetQuorumResponses().stream()
                        .filter(obj -> obj instanceof WriteResponse)
                        .map(obj -> (WriteResponse) obj).collect(Collectors.toList());

                if(TIME_FLAG){
                    endTime = System.currentTimeMillis();
                    writeTime += (endTime - startTime);
                }
                macroWriteCount++;

                return writeResponses.stream().findFirst().orElse(null);
            }
        }
        catch (ZKNamingException zkne) {
            debug("Error in write. " + zkne.getMessage());
            return null;
        }
        finally {
            ctx.detach(originalCtx);
        }
    }

    public void deleteAllRecords() {
        try {
            synchronized (collector) {
                collector.setExpectedResponseAmount(totalWeight / 2f);
                for (RecordServiceGrpc.RecordServiceStub stub : recordStubList) {
                    stub.deleteAllRecords(DeleteAllRecordsRequest.newBuilder().build(), new ResponseObserver<>(collector, stubWeightMap.get(stub), recordStubList.indexOf(stub) + 1));
                }
                GetQuorumResponses();
            }
        }
        catch (ZKNamingException zkne){
            debug("Error in deleteAllRecords. " + zkne.getMessage());
        }
    }

    public void deleteRecord(DeleteRecordRequest request) {
        try {
            synchronized (collector) {
                collector.setExpectedResponseAmount(totalWeight / 2f);
                for (RecordServiceGrpc.RecordServiceStub stub : recordStubList) {
                    stub.deleteRecord(request, new ResponseObserver<>(collector, stubWeightMap.get(stub), recordStubList.indexOf(stub) + 1));
                }
                GetQuorumResponses();
            }
        }
        catch (ZKNamingException zkne){
            debug("Error in deleteRecord. " + zkne.getMessage());
        }
    }

    private List<Object> GetQuorumResponses() throws ZKNamingException{
        List<Object> responses;
        try{
            collector.wait(5*1000);
            if(!collector.getNotificationStatus()) RecalculateTotalWeight(collector.getLostWeight());
            responses = new ArrayList<>(collector.getResponseList());
            collector.clearResponseList();
            return responses;
        }
        catch (InterruptedException ie) {
            System.out.println(ie.getMessage());
            return new ArrayList<>();
        }
    }

    public int readGetTag(String recordName) {
        ReadRequest req = ReadRequest.newBuilder().setRecordName(recordName).build();
        ReadResponse res = this.read(req);
        Integer tag = res.getTag();
        debug("completed read\tGetTag\t->\t" + recordName + "\t" + tag + "\n");
        return tag;
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
        debug("completed read\tUserNBicloins\t->\t" + user + "\t" + nb + "\t" + res.getTag() + "\n");
        time();

        return nb;
    }

    public boolean readUserHasBike(String user) throws InvalidProtocolBufferException{
        ReadRequest req = ReadRequest.newBuilder().setRecordName("get_user_hasbike_" + user).build();
        ReadResponse res = this.read(req);
        boolean nb = res.getRecordValue().unpack(BoolValue.class).getValue();
        debug("completed read\tUserHasBike\t->\t" + user + "\t" + nb + "\t" + res.getTag() + "\n");
        time();

        return nb;
    }

    public Integer readStationNBikes(String station) throws InvalidProtocolBufferException{
        ReadRequest req = ReadRequest.newBuilder().setRecordName("get_station_nbikes_" + station).build();
        ReadResponse res = this.read(req);
        Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
        debug("completed read\tStationNBikes\t->\t" + station + "\t" + nb + "\t" + res.getTag() + "\n");
        time();

        return nb;
    }

    public Integer readStationNRequests(String station) throws InvalidProtocolBufferException{
        ReadRequest req = ReadRequest.newBuilder().setRecordName("get_station_nrequests_" + station).build();
        ReadResponse res = this.read(req);
        Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
        debug("completed read\tStationNRequests\t->\t" + station + "\t" + nb + "\t" + res.getTag() + "\n");
        time();

        return nb;
    }

    public Integer readStationNReturns(String station) throws InvalidProtocolBufferException{
        ReadRequest req = ReadRequest.newBuilder().setRecordName("get_station_nreturns_" + station).build();
        ReadResponse res = this.read(req);
        Integer nb = res.getRecordValue().unpack(Int32Value.class).getValue();
        debug("completed read\tStationNReturns\t->\t" + station + "\t" + nb + "\t" + res.getTag() + "\n");
        time();

        return nb;
    }
    public void writeUserNBicloins(String user, Integer nb) throws InvalidProtocolBufferException{
        String recordName = "_user_nbicloins_" + user;

        int tag = readGetTag("get" + recordName);
        WriteRequest req = WriteRequest.newBuilder()
                .setRecordName("set" + recordName)
                .setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build()))
                .setTag(tag + 1).build();
        time();

        this.write(req);
        debug("completed write\tUserNBicloins\t->\t" + user + "\t" + nb + "\t" + tag + "\n");
    }

    public void writeUserHasBike(String user, boolean nb) throws InvalidProtocolBufferException{
        String recordName = "_user_hasbike_" + user;

        int tag = readGetTag("get" + recordName);
        WriteRequest req = WriteRequest.newBuilder()
                .setRecordName("set" + recordName)
                .setRecordValue(Any.pack(BoolValue.newBuilder().setValue(nb).build()))
                .setTag(tag + 1).build();
        time();

        this.write(req);
        debug("completed write\tUserHasBike\t->\t" + user + "\t" + nb + "\t" + tag + "\n");
    }

    public void writeStationNBikes(String station, Integer nb) throws InvalidProtocolBufferException{
        String recordName = "_station_nbikes_" + station;

        int tag = readGetTag("get" + recordName);
        WriteRequest req = WriteRequest.newBuilder()
                .setRecordName("set" + recordName)
                .setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build()))
                .setTag(tag + 1).build();
        time();

        this.write(req);
        debug("completed write\tStationNBikes\t->\t" + station + "\t" + nb + "\t" + tag + "\n");
    }

    public void writeStationNRequests(String station, Integer nb) throws InvalidProtocolBufferException{
        String recordName = "_station_nrequests_" + station;

        int tag = readGetTag("get" + recordName);
        WriteRequest req = WriteRequest.newBuilder()
                .setRecordName("set" + recordName)
                .setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build()))
                .setTag(tag + 1).build();
        time();

        this.write(req);
        debug("completed write\tStationNRequests\t->\t" + station + "\t" + nb + "\t" + tag + "\n");
    }

    public void writeStationNReturns(String station, Integer nb) throws InvalidProtocolBufferException{
        String recordName = "_station_nreturns_" + station;

        int tag = readGetTag("get" + recordName);
        WriteRequest req = WriteRequest.newBuilder()
                .setRecordName("set" + recordName)
                .setRecordValue(Any.pack(Int32Value.newBuilder().setValue(nb).build()))
                .setTag(tag + 1).build();
        time();

        this.write(req);
        debug("completed write\tStationNReturns\t->\t" + station + "\t" + nb + "\t" + tag + "\n");
    }

    public void shutdown() {
        for (ManagedChannel channel : channels) {
            channel.shutdownNow();
        }
    }
}
