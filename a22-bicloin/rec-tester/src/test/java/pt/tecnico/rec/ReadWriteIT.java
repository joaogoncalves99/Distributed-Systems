package pt.tecnico.rec;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

import static io.grpc.Status.INVALID_ARGUMENT;

public class ReadWriteIT extends BaseIT{

	static String username1 = "uN1";
	static String stationname1 = "sN1";
	static Integer nBicloins1 = 100;
	static Integer nBicloins2 = 1234;
	static boolean hasBike = true;
	static boolean hasntBike = false;
	static Integer nBikes1 = 100;
	static Integer nBikes2 = 1234;
	static Integer nReturns1 = 100;
	static Integer nReturns2 = 1234;
	static Integer nRequests1 = 100;
	static Integer nRequests2 = 1234;

	String varUser;
	String varStation;
	Integer varNBic;
	boolean varHasBike;
	Integer varNBikes;
	Integer varNReturns;
	Integer varNRequests;

	// initialization and clean-up for each test

	@BeforeEach
	public void setUp() {
		frontend.deleteRecordsUser(username1);
		frontend.deleteRecordsStation(stationname1);
		varUser = "err";
		varStation = "err";
		varNBic = -1;
		varHasBike = false;
		varNBikes = -1;
		varNReturns = -1;
		varNRequests = -1;
	}

	@AfterEach
	public void tearDown() {

	}

	// tests

	@Test
	public void write_and_read() {
		try {
			frontend.writeUserNBicloins(username1, nBicloins1);
			varNBic = frontend.readUserNBicloins(username1);
			assertEquals(nBicloins1, varNBic);
		} catch(InvalidProtocolBufferException ex) {
			System.out.println("Caught ProtocolBuffer exception with description: " +
					ex.getMessage());
		}

	}

	@Test
	public void write_and_read_update() {
			try {
				frontend.writeUserNBicloins(username1, nBicloins1);
				varNBic = frontend.readUserNBicloins(username1);

				frontend.writeUserHasBike(username1, hasBike);
				varHasBike = frontend.readUserHasBike(username1);

				frontend.writeStationNBikes(stationname1, nBikes1);
				varNBikes = frontend.readStationNBikes(stationname1);

				frontend.writeStationNRequests(stationname1, nRequests1);
				varNRequests = frontend.readStationNRequests(stationname1);

				frontend.writeStationNReturns(stationname1, nReturns1);
				varNReturns = frontend.readStationNReturns(stationname1);

				assertEquals(nBicloins1, varNBic);
				assertEquals(hasBike, varHasBike);
				assertEquals(nBikes1, varNBikes);
				assertEquals(nRequests1, varNRequests);
				assertEquals(nReturns1, varNReturns);

				frontend.writeUserNBicloins(username1, nBicloins2);
				varNBic = frontend.readUserNBicloins(username1);

				frontend.writeUserHasBike(username1, hasntBike);
				varHasBike = frontend.readUserHasBike(username1);

				frontend.writeStationNBikes(stationname1, nBikes2);
				varNBikes = frontend.readStationNBikes(stationname1);

				frontend.writeStationNRequests(stationname1, nRequests2);
				varNRequests = frontend.readStationNRequests(stationname1);

				frontend.writeStationNReturns(stationname1, nReturns2);
				varNReturns = frontend.readStationNReturns(stationname1);

				assertEquals(nBicloins2, varNBic);
				assertEquals(hasntBike, varHasBike);
				assertEquals(nBikes2, varNBikes);
				assertEquals(nRequests2, varNRequests);
				assertEquals(nReturns2, varNReturns);

			} catch(InvalidProtocolBufferException ex) {
				System.out.println("Caught ProtocolBuffer exception with description: " +
						ex.getMessage());
			}
	}

	@Test
	public void no_write_then_read(){
		try{
			varNBic = frontend.readUserNBicloins(username1);
			varHasBike = frontend.readUserHasBike(username1);
			varNBikes = frontend.readStationNBikes(stationname1);
			varNRequests = frontend.readStationNRequests(stationname1);
			varNReturns = frontend.readStationNReturns(stationname1);

			assertEquals(0, varNBic);
			assertEquals(false, varHasBike);
			assertEquals(0, varNBikes);
			assertEquals(0, varNRequests);
			assertEquals(0, varNReturns);
		} catch(InvalidProtocolBufferException ex) {
			System.out.println("Caught ProtocolBuffer exception with description: " +
					ex.getMessage());
		}
	}

	@Test
	public void wrong_recordName_format(){
		ReadRequest readrequest = ReadRequest.newBuilder().setRecordName("wrong_format_record_name").build();
		assertEquals(
				INVALID_ARGUMENT.getCode(),
				assertThrows(
						StatusRuntimeException.class, () -> frontend.read(readrequest))
						.getStatus()
						.getCode());
	}

	@Test
	public void wrong_RecordValue_type(){
		WriteRequest writerequest = WriteRequest.newBuilder().setRecordName("set_user_hasbike_failuser").setRecordValue(Any.pack(Int32Value.newBuilder().setValue(1).build())).build();
		assertEquals(
				INVALID_ARGUMENT.getCode(),
				assertThrows(
						StatusRuntimeException.class, () -> frontend.write(writerequest))
						.getStatus()
						.getCode());
	}

}
