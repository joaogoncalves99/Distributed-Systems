package pt.tecnico.rec;

import io.grpc.StatusRuntimeException;
import pt.tecnico.rec.grpc.Rec.PingRequest;
import pt.tecnico.rec.grpc.Rec.PingResponse;
import pt.tecnico.rec.grpc.Rec.WriteRequest;
import pt.tecnico.rec.grpc.Rec.WriteResponse;
import pt.tecnico.rec.grpc.Rec.ReadRequest;
import pt.tecnico.rec.grpc.Rec.ReadResponse;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Any;
import com.google.protobuf.Int32Value;
import com.google.protobuf.BoolValue;

public class RecordTester {

	public static void main(String[] args) {
		System.out.println(RecordTester.class.getSimpleName());

		// receive and print arguments
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// check arguments
		if (args.length < 2) {
			System.err.println("Argument(s) missing!");
			System.err.printf("Usage: java %s host port%n", RecordTester.class.getName());
			return;
		}

		final String host = args[0];
		final String port = args[1];
		final String path = args[4];
		RecordFrontend frontend = null;
		try {
			frontend = new RecordFrontend(host, port, path);
			PingRequest request = PingRequest.newBuilder().setInput("friend").build();
			PingResponse response = frontend.ping(request);
			System.out.println(response);
		} catch (StatusRuntimeException e) {
			System.out.println("Caught exception with description: " +
					e.getStatus().getDescription());
		} catch (ZKNamingException zke){
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}
		if(frontend != null)
			frontend.shutdown();

		try {
			frontend = new RecordFrontend(host, port, path);
			String username = "uName";
			String stationname = "sName";
			Integer nBicloins = 100;
			boolean hasBike = true;
			Integer nBikes = 100;

			frontend.writeUserNBicloins(username, nBicloins);
			frontend.readUserNBicloins(username);

			frontend.writeUserNBicloins(username, nBicloins+23);
			frontend.readUserNBicloins(username);

			frontend.writeUserHasBike(username, hasBike);
			frontend.readUserHasBike(username);

			frontend.writeUserHasBike(username, !hasBike);
			frontend.readUserHasBike(username);

			frontend.writeStationNBikes(stationname, nBikes);
			frontend.readStationNBikes(stationname);

			//suposed to fail
			ReadRequest readrequestwf = ReadRequest.newBuilder().setRecordName("wrong_format_record_name").build();
			ReadResponse readresponsewf = frontend.read(readrequestwf);
			System.out.println("wrong_format_record_name " + readresponsewf.getRecordValue().unpack(Int32Value.class).getValue());

			//suposed to fail
			WriteRequest writerequest = WriteRequest.newBuilder().setRecordName("set_user_hasbike_failuser").setRecordValue(Any.pack(Int32Value.newBuilder().setValue(1).build())).build();
			frontend.write(writerequest);

			ReadRequest readrequest = ReadRequest.newBuilder().setRecordName("get_user_hasbike_failuser").build();
			ReadResponse readresponse = frontend.read(readrequest);
			System.out.println("failuser " + readresponse.getRecordValue().unpack(BoolValue.class).getValue());

		} catch (StatusRuntimeException e){
			System.out.println("Caught exception with description: " +
					e.getStatus().getDescription());
		} catch (ZKNamingException zke){
			System.out.println("Caught ZooKeeper exception with description: " +
					zke.getMessage());
		}catch(InvalidProtocolBufferException ex) {
			System.out.println("Caught ProtocolBuffer exception with description: " +
					ex.getMessage());
		}

		frontend.printHashMap();

		if(frontend != null)
			frontend.shutdown();

	}

}
