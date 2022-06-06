package pt.tecnico.rec;

import io.grpc.stub.StreamObserver;
import pt.tecnico.rec.grpc.Rec.*;
import pt.tecnico.rec.grpc.RecordServiceGrpc.RecordServiceImplBase;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedList;
import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.BoolValue;
import com.google.protobuf.InvalidProtocolBufferException;

import static io.grpc.Status.INVALID_ARGUMENT;

public class RecordServerImpl extends RecordServiceImplBase{

	private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
	/** Helper method to print debug messages. */
	private static void debug(String debugMessage) {
		if (DEBUG_FLAG)
			System.err.print(debugMessage);
	}
	private static final List<String> allRecNames = new LinkedList<>(List.of("station_nbikes_", "station_nrequests_", "station_nreturns_",
																			"user_nbicloins_", "user_hasbike_"));
	private static final List<String> allRecNamesInt = new LinkedList<>(List.of("station_nbikes_", "station_nrequests_", "station_nreturns_",
																			"user_nbicloins_"));
	private static final List<String> allRecNamesBool = new LinkedList<>(List.of("user_hasbike_"));

	Map<String, Any> records = new ConcurrentHashMap<String, Any>();
	Map<String, Integer> recordTags = new ConcurrentHashMap<String, Integer>();

	private synchronized final String printHashMap(){
		String d = "Printing HashMap:\n-RecordName-\t\t-RecordValue-\n";
		Any recordValue;
		for(Map.Entry<String,Any> entry : records.entrySet()){
			recordValue = entry.getValue();

			d += entry.getKey() + "\t";
			if(recordValue.is(Int32Value.class)){
				try{
					d += recordValue.unpack(Int32Value.class).getValue();
				} catch(InvalidProtocolBufferException ex) {
					System.out.println("Caught ProtocolBuffer exception with description: " +
							ex.getMessage());
				}
			}else if (recordValue.is(BoolValue.class)){
				try{
					d += recordValue.unpack(BoolValue.class).getValue();
				} catch(InvalidProtocolBufferException ex) {
					System.out.println("Caught ProtocolBuffer exception with description: " +
							ex.getMessage());
				}
			}
			d += "\n";
		}
		return d;
	}

	@Override
    public synchronized void printHashMap(PrintHashMapRequest request, StreamObserver<PrintHashMapResponse> responseObserver) {
		PrintHashMapResponse response = PrintHashMapResponse.newBuilder().setOutput(this.printHashMap()).build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
    public synchronized void deleteAllRecords(DeleteAllRecordsRequest request, StreamObserver<DeleteAllRecordsResponse> responseObserver) {
		records.clear();
		DeleteAllRecordsResponse response = DeleteAllRecordsResponse.newBuilder().build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
		public synchronized void deleteRecord(DeleteRecordRequest request, StreamObserver<DeleteRecordResponse> responseObserver) {
		records.remove(request.getRecordName());
		DeleteRecordResponse response = DeleteRecordResponse.newBuilder().build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
		String input = request.getInput();

		if (input == null || input.isBlank()) {
			//responseObserver.onNext(PingResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("Input cannot be empty!").asRuntimeException());
			return ;
		}

		String output = "Hello " + input + "!";
		PingResponse response = PingResponse.newBuilder().
		setOutput(output).build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

		private static void readFailed(String rec, String add) { System.out.println("!Read Failed with request: " + rec + ", type: " + add + "!"); }

	@Override
    public synchronized void read(ReadRequest request, StreamObserver<ReadResponse> responseObserver) {
		String recordName = request.getRecordName();

		debug("Read \t->\t");

		if (recordName == null || recordName.isBlank()) {
			//responseObserver.onNext(ReadResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("RecordName cannot be empty!").asRuntimeException());
			readFailed(recordName, "RecordName empty");
			return;
		}

		boolean isRecord = false;
		for(String s : allRecNames){
			if(recordName.startsWith("get_" + s)){
				isRecord = true;
			}
		}
		if(!isRecord) {
			//responseObserver.onNext(ReadResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("RecordName format is not a record type!").asRuntimeException());
			readFailed(recordName, "RecordName wrong format");
			return;
		}

		String record = recordName.substring(recordName.indexOf("_")+1);
		debug(record + "\t");

		String recordType = record.substring(0,record.indexOf("_",record.indexOf("_")+1)+1);

		Any value = Any.pack(Int32Value.newBuilder().setValue(-1).build());

		Integer tag = recordTags.get(record);
		if(tag == null) {
			tag = 0;
		}
		debug(tag + "\t");

		if (allRecNamesBool.contains(recordType)){
			value = records.get(record);
			if(value==null) value = Any.pack(BoolValue.newBuilder().setValue(false).build());
			try{
				debug(value.unpack(BoolValue.class).getValue() + "\n");
			}catch(InvalidProtocolBufferException e){
				System.out.println(e.getMessage());
			}
		}else if (allRecNamesInt.contains(recordType)){
			value = records.get(record);
			if(value==null) value = Any.pack(Int32Value.newBuilder().setValue(0).build());
			try{
				debug(value.unpack(Int32Value.class).getValue() + "\n");
			}catch(InvalidProtocolBufferException e){
				System.out.println(e.getMessage());
			}
		}else{
			//responseObserver.onNext(ReadResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("RecordName format is not a record type!").asRuntimeException());
			readFailed(recordName, "RecordName wrong format");
			return;
		}

		ReadResponse response = ReadResponse.newBuilder().setRecordValue(value).setTag(tag).build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	private static void writeFailed(String rec, String add) { System.out.println("!Write Failed on record: " + rec + ", type: " + add + "!"); }

	@Override
    public synchronized void write(WriteRequest request, StreamObserver<WriteResponse> responseObserver) {
		String recordName = request.getRecordName();
		Any value = request.getRecordValue();
		Integer reqtag = request.getTag();

		debug("Write \t->\t");

		if (recordName == null || recordName.isBlank()) {
			//responseObserver.onNext(WriteResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("RecordName cannot be empty!").asRuntimeException());
			writeFailed(recordName, "RecordName empty");
			return ;
		}

		boolean isRecord = false;
		for(String s : allRecNames){
			if(recordName.startsWith("set_" + s)){
				isRecord = true;
			}
		}
		if(!isRecord) {
			//responseObserver.onNext(WriteResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("RecordName format is not a record type!").asRuntimeException());
			writeFailed(recordName, "RecordName format");
			return ;
		}

		String record = recordName.substring(recordName.indexOf("_")+1);
		debug(record + "\t");

		Integer currtag = recordTags.get(record);
		if(currtag != null && currtag > reqtag){
			//responseObserver.onNext(WriteResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("Tag invalid").asRuntimeException());
			writeFailed(record, "Tag Invalid");
			return ;
		}
		debug(reqtag + "\t");

		if (value == null) {
			//responseObserver.onNext(WriteResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("Value cannot be empty!").asRuntimeException());
			writeFailed(record, "Value empty");
			return ;
		}

		if(value.is(Int32Value.class)){
			boolean isType = false;
			for(String s : allRecNamesInt){
				if(record.startsWith(s)){
					isType = true;
					break;
			}
		}
		if(!isType){
			//responseObserver.onNext(WriteResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("Value is of wrong type!").asRuntimeException());
			writeFailed(record, "Value wrong type");
			return ;
		}
		try{
			debug(value.unpack(Int32Value.class).getValue() + "\n");
		}catch(InvalidProtocolBufferException e){
			debug(e.getMessage());
		}
		}else if (value.is(BoolValue.class)){
			boolean isType = false;
			for(String s : allRecNamesBool){
				if(record.startsWith(s)){
					isType = true;
					break;
				}
			}
			if(!isType){
				//responseObserver.onNext(WriteResponse.getDefaultInstance());
				responseObserver.onError(INVALID_ARGUMENT
				.withDescription("Value is of wrong type!").asRuntimeException());
				writeFailed(record, "Value wrong type");
				return ;
			}
			try{
				debug(value.unpack(BoolValue.class).getValue() + "\n");
			}catch(InvalidProtocolBufferException e){
				System.out.println(e.getMessage());
			}
		}else{
			//responseObserver.onNext(WriteResponse.getDefaultInstance());
			responseObserver.onError(INVALID_ARGUMENT
			.withDescription("Value is of wrong type!").asRuntimeException());
			writeFailed(record, "Value wrong type");
			return ;
		}

		records.put(record, value);
		if(reqtag != 0) recordTags.put(record, reqtag);

		WriteResponse	response = WriteResponse.newBuilder().build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}
}
