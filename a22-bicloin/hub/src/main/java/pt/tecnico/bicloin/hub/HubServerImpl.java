package pt.tecnico.bicloin.hub;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import pt.tecnico.bicloin.hub.grpc.Hub.PingRequest;
import pt.tecnico.bicloin.hub.grpc.Hub.PingResponse;
import pt.tecnico.bicloin.hub.grpc.Hub.SystemStatusRequest;
import pt.tecnico.bicloin.hub.grpc.Hub.SystemStatusResponse;
import pt.tecnico.bicloin.hub.grpc.Hub.*;
import pt.tecnico.bicloin.hub.grpc.Hub.LocateStationResponse.Builder;
import pt.tecnico.bicloin.hub.grpc.HubServiceGrpc.HubServiceImplBase;
import pt.tecnico.bicloin.hub.hubDataTypes.User;
import pt.tecnico.rec.*;
import pt.tecnico.rec.grpc.Rec;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;
import pt.tecnico.bicloin.hub.hubDataTypes.Station;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.util.Collection;

import static io.grpc.Status.*;

public class HubServerImpl extends HubServiceImplBase{
	private static final boolean DEBUG_FLAG = (System.getProperty("debug") != null);
	private static int debugCommandLine = 0;
	/** Helper method to print debug messages. */
	public void debug(String debugMessage) {
		if (DEBUG_FLAG){
			System.err.print(debugCommandLine + ": " + debugMessage + "\n");
			debugCommandLine++;
		}
	}

	private String zkHost;
	private String zkPort;
	private String zkHubServerPaths = "/grpc/bicloin/hub";
	private String zkRecServerPaths = "/grpc/bicloin/rec";

	static public List<Station> stationList;
	static public List<User> userList;

	static private RecordFrontend rec;
	static private QuorumFrontend quorum;

	public HubServerImpl(String host, String port, String filePath1, String filePath2, boolean initRec) throws IllegalArgumentException, ZKNamingException{
		setZKHostPort(host, port);
		try {
			rec = ConnectToRec();
			quorum = ConnectToQuorums();
			debug(rec.ping(Rec.PingRequest.newBuilder().setInput("Test").build()).getOutput());

			quorum.ping(Rec.PingRequest.newBuilder().setInput("Test").build());

			if(!InitializeData(Arrays.asList(filePath1, filePath2), initRec)){
				ShutdownRecConnection();
				ShutdownQuorumConnections();
				throw new IllegalArgumentException();
			}
		}
		catch (ZKNamingException zke) {
			ShutdownRecConnection();
			ShutdownQuorumConnections();
			System.out.println("ERRO Hub não conseguiu aceder o servidor Record");
			throw zke;
		}
	}

	@Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
		String input = request.getInput();

		if (input == null || input.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
				.withDescription("O Input não pode ser vazio.").asRuntimeException());
			return;
		}

		String output = "Hello " + input + "!";
		PingResponse response = PingResponse.newBuilder().
				setOutput(output).build();
		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
	public void sysStatus(SystemStatusRequest request, StreamObserver<SystemStatusResponse> responseObserver) {
		String input = request.getInput();

		if (input == null || input.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O Input não pode ser vazio.").asRuntimeException());
			return;
		}

		ZKNaming zkNaming = new ZKNaming(zkHost, zkPort);

		SystemStatusResponse.Builder responseBuilder = SystemStatusResponse.newBuilder();
		try {
			Collection<ZKRecord> hubServers = GetListResults(zkNaming, zkHubServerPaths);
			List<ZKRecord> recServers = new ArrayList<>(GetListResults(zkNaming, zkRecServerPaths));

			for (ZKRecord zkRecord : hubServers) {
				String path = zkRecord.getPath();
				boolean isOnline = false;

				HubFrontend hubFrontend = new HubFrontend(zkHost, zkPort, path);
				PingRequest pingRequest = PingRequest.newBuilder().setInput(input).build();
				try {
					PingResponse pingResponse = hubFrontend.ping(pingRequest);
					if(pingResponse.getOutput() != null && !pingResponse.getOutput().isEmpty())
						isOnline = true;
				}catch (StatusRuntimeException sre){
					//Nothing needs to execute
				}

				responseBuilder.addServerPath(path);
				String status = isOnline ? "Up" : "Down";
				responseBuilder.addServerStatus(status);
			}

			for (ZKRecord zkRecord : recServers) {
				String path = zkRecord.getPath();
				boolean isOnline = false;

				RecordFrontend recFrontend = new RecordFrontend(zkHost, zkPort, path);
				Rec.PingRequest pingRequest = Rec.PingRequest.newBuilder().setInput("friend").build();

				try {
					Rec.PingResponse pingResponse = recFrontend.ping(pingRequest);
					if (pingResponse.getOutput() != null && !pingResponse.getOutput().isEmpty())
						isOnline = true;
				} catch (StatusRuntimeException sre) {
					//Nothing needs to execute
				}

				responseBuilder.addServerPath(path);
				String status = isOnline ? "Up" : "Down";
				responseBuilder.addServerStatus(status);
			}

			SystemStatusResponse response = responseBuilder.build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (ZKNamingException zke) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription(zke.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void userLogin(UserLoginRequest request, StreamObserver<UserLoginResponse> responseObserver) {
		String userID = request.getUserID();

		if (userID == null || userID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O ID não pode ser vazio.").asRuntimeException());
			return;
		}

		User user = userList.stream().filter(x -> x.GetID().equals(userID)).findFirst().orElse(null);
		if(user == null) {
			responseObserver.onError(NOT_FOUND
					.withDescription("O ID não foi encontrado.").asRuntimeException());
			return;
		}

		UserLoginResponse response = UserLoginResponse.newBuilder()
				.setUserName(user.GetName())
				.setUserPhoneNumber(user.GetPhoneNumber()).build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
	public void infoStation(StationInfoRequest request, StreamObserver<StationInfoResponse> responseObserver) {
		String input = request.getStationID();

		if(input == null || input.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O ID não pode ser vazio.").asRuntimeException());
			return;
		}
		if(input.length() != 4) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O ID é de 4 caracteres alfanuméricos.").asRuntimeException());
			return;
		}

		Station station = stationList.stream()
				.filter(x -> x.GetStationID().equals(input))
				.findAny()
				.orElse(null);

		Integer bicicleAmount;
		Integer requestAmount;
		Integer returnAmount;

		try{
			bicicleAmount = quorum.readStationNBikes(station.GetStationID());
			requestAmount = quorum.readStationNRequests(station.GetStationID());
			returnAmount = quorum.readStationNReturns(station.GetStationID());
		}
		catch (InvalidProtocolBufferException | NullPointerException e) {
			responseObserver.onError(NOT_FOUND
					.withDescription("A estação indicada não foi encontrada.").asRuntimeException());
			return;
		}
		catch (IndexOutOfBoundsException oobe) {
			responseObserver.onError(UNAVAILABLE
				.withDescription("Demasiadas réplicas impossíveis de se contactar.").asRuntimeException());
			return;
		}

		StationInfoResponse	response = StationInfoResponse.newBuilder().
					setStationName(station.GetStationName()).
					setStationLatitude(station.GetLatitureCoord()).
					setStationLongitude(station.GetLongitudeCoord()).
					setStationDockCapacity(station.GetDockTotal()).
					setStationPrize(station.GetReturnPrize()).
					setStationAvailableBikes(bicicleAmount).
					setRequestedStatistic(requestAmount).
					setReturnedStatistic(returnAmount).build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
	public void locateStation(LocateStationRequest request, StreamObserver<LocateStationResponse> responseObserver) {
		double inputLatitude = request.getUserLatitude();
		double inputLongitude = request.getUserLongitude();
		int amountToDisplay = request.getAmountToDisplay();

		if (inputLatitude < -90 || inputLatitude > 90 ||
			inputLongitude < -180 || inputLongitude > 180) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("Valores inválidos para as coordenadas.").asRuntimeException());
			return;
		}
		if(amountToDisplay <= 0) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("Valor inválido para a quantia de estações a se representar.").asRuntimeException());
			return;
		}

		Builder responseBuilder = LocateStationResponse.newBuilder();

		List<Station> closestStations = stationList.stream()
				.sorted((x, y) -> Double.compare(HaversineDistance(inputLatitude, inputLongitude, x.GetLatitureCoord(), x.GetLongitudeCoord()),
						HaversineDistance(inputLatitude, inputLongitude, y.GetLatitureCoord(), y.GetLongitudeCoord())))
				.limit(amountToDisplay)
				.collect(Collectors.toList());

		for (Station station : closestStations) {
			responseBuilder = responseBuilder.addStationID(station.GetStationID());
		}

		LocateStationResponse response = responseBuilder.build();

		responseObserver.onNext(response);
		responseObserver.onCompleted();
	}

	@Override
	public void balance(BalanceRequest request, StreamObserver<BalanceResponse> responseObserver) {
		String userID = request.getUserID();
		if(userID == null || userID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O Nome não pode ser vazio.").asRuntimeException());
			return;
		}

		try{
			User user = userList.stream()
					.filter(x -> x.GetID().equals(userID))
					.findFirst().orElse(null);

			int balanceAmount = quorum.readUserNBicloins(user.GetID());
			BalanceResponse response = BalanceResponse.newBuilder().setUserBicloinBalance(balanceAmount).build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}
		catch (InvalidProtocolBufferException | NullPointerException e) {
			responseObserver.onError(NOT_FOUND
					.withDescription("O utilizador indicado não foi encontrado.").asRuntimeException());
		}
		catch (IndexOutOfBoundsException oobe) {
			responseObserver.onError(UNAVAILABLE
					.withDescription("Demasiadas réplicas impossíveis de se contactar.").asRuntimeException());
		}
	}

	@Override
	public void topUp(TopUpRequest request, StreamObserver<TopUpResponse> responseObserver) {
		String userID = request.getUserID();
		String userPhone = request.getUserPhoneNumber();
		long money = request.getUserChargeAmount().getUnits();

		if(userID == null || userID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O Nome não pode ser vazio.").asRuntimeException());
			return;
		}
		else if(userPhone == null || userPhone.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O número de telemóvel não pode ser vazio.").asRuntimeException());
			return;
		}
		else if (money < 1 || money > 20 || !request.getUserChargeAmount().getCurrencyCode().equals("EUR")) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("A quantia deve ser um número inteiro entre 1 e 20 EUR.").asRuntimeException());
			return;
		}


		try{
			User user = userList.stream()
					.filter(x -> x.GetID().equals(userID) && x.GetPhoneNumber().equals(userPhone))
					.findFirst().orElse(null);

			int balanceAmount = quorum.readUserNBicloins(user.GetID());
			balanceAmount += (int) money * 10;

			quorum.writeUserNBicloins(user.GetID(), balanceAmount);

			TopUpResponse response = TopUpResponse.newBuilder().setUserBicloinBalance(balanceAmount).build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}
		catch (InvalidProtocolBufferException | NullPointerException e) {
			responseObserver.onError(NOT_FOUND
					.withDescription("O utilizador indicado não foi encontrado.").asRuntimeException());
		}
		catch (IndexOutOfBoundsException oobe) {
			responseObserver.onError(UNAVAILABLE
					.withDescription("Demasiadas réplicas impossíveis de se contactar.").asRuntimeException());
		}
	}

	@Override
	public void bikeUp(BikeUpRequest request, StreamObserver<BikeUpResponse> responseObserver) {
		String userID = request.getUserID();
		String stationID = request.getStationID();
		double latitude = request.getUserLatitude();
		double longitude = request.getUserLongitude();

		if(userID == null || userID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O Nome não pode ser vazio.").asRuntimeException());
			return;
		}
		else if(stationID == null || stationID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O ID da estação não pode ser vazio.").asRuntimeException());
			return;
		}
		else if (latitude < -90 || latitude > 90 ||
				longitude < -180 || longitude > 180) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("Valores inválidos para as coordenadas.").asRuntimeException());
			return;
		}

		try {
			User user = userList.stream()
					.filter(x -> x.GetID().equals(userID))
					.findFirst().orElse(null);
			Station station = stationList.stream()
					.filter(x -> x.GetStationID().equals(stationID))
					.findFirst().orElse(null);

			int balanceAmount = quorum.readUserNBicloins(userID);
			int bikeAmount = quorum.readStationNBikes(stationID);
			int requestAmount = quorum.readStationNRequests(stationID);

			if(quorum.readUserHasBike(userID)){
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("O utilizador já está a utilizar uma bicicleta.").asRuntimeException());
				return;
			}
			else if(balanceAmount - 10 < 0){
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("O utilizador não tem bicloins suficientes (necessário 10 BIC).").asRuntimeException());
				return;
			}
			else if(HaversineDistance(latitude, longitude, station.GetLatitureCoord(), station.GetLongitudeCoord()) > 200) {
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("O utilizador está demasiado afastado da estação indicada.").asRuntimeException());
				return;
			}
			else if(bikeAmount <= 0) {
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("A estação não possui bicicletas disponíveis de momento.").asRuntimeException());
				return;
			}

			quorum.writeUserNBicloins(userID, balanceAmount - 10);
			quorum.writeUserHasBike(userID, true);
			quorum.writeStationNBikes(stationID, bikeAmount - 1);
			quorum.writeStationNRequests(stationID, requestAmount + 1);

			BikeUpResponse response = BikeUpResponse.newBuilder().setRequestAccepted(true).build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (InvalidProtocolBufferException | NullPointerException e) {
			responseObserver.onError(NOT_FOUND
					.withDescription("O utilizador/estação indicado não foi encontrado.").asRuntimeException());
		}
		catch (IndexOutOfBoundsException oobe) {
			responseObserver.onError(UNAVAILABLE
					.withDescription("Demasiadas réplicas impossíveis de se contactar.").asRuntimeException());
		}

	}

	@Override
	public void bikeDown(BikeDownRequest request, StreamObserver<BikeDownResponse> responseObserver) {
		String userID = request.getUserID();
		String stationID = request.getStationID();
		double latitude = request.getUserLatitude();
		double longitude = request.getUserLongitude();

		if(userID == null || userID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O Nome não pode ser vazio.").asRuntimeException());
			return;
		}
		else if(stationID == null || stationID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O ID da estação não pode ser vazio.").asRuntimeException());
			return;
		}
		else if (latitude < -90 || latitude > 90 ||
				longitude < -180 || longitude > 180) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("Valores inválidos para as coordenadas.").asRuntimeException());
			return;
		}

		try {
			User user = userList.stream()
					.filter(x -> x.GetID().equals(userID))
					.findFirst().orElse(null);
			Station station = stationList.stream()
					.filter(x -> x.GetStationID().equals(stationID))
					.findFirst().orElse(null);

			int balanceAmount = quorum.readUserNBicloins(userID);
			int bikeAmount = quorum.readStationNBikes(stationID);
			int returnAmount = quorum.readStationNReturns(stationID);

			if(!quorum.readUserHasBike(userID)){
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("O utilizador não está a utilizar uma bicicleta.").asRuntimeException());
				return;
			}
			else if(HaversineDistance(latitude, longitude, station.GetLatitureCoord(), station.GetLongitudeCoord()) > 200) {
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("O utilizador está demasiado afastado da estação indicada.").asRuntimeException());
				return;
			}
			else if(bikeAmount + 1 > station.GetDockTotal()) {
				responseObserver.onError(PERMISSION_DENIED
						.withDescription("A estação encontra-se cheia de momento.").asRuntimeException());
				return;
			}

			quorum.writeUserNBicloins(userID, balanceAmount + station.GetReturnPrize());
			quorum.writeUserHasBike(userID, false);
			quorum.writeStationNBikes(stationID, bikeAmount + 1);
			quorum.writeStationNReturns(stationID, returnAmount + 1);

			BikeDownResponse response = BikeDownResponse.newBuilder().setRequestAccepted(true).build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (InvalidProtocolBufferException | NullPointerException e) {
			responseObserver.onError(NOT_FOUND
					.withDescription("O utilizador/estação indicado não foi encontrado.").asRuntimeException());
		}
		catch (IndexOutOfBoundsException oobe) {
			responseObserver.onError(UNAVAILABLE
					.withDescription("Demasiadas réplicas impossíveis de se contactar.").asRuntimeException());
		}

	}

	@Override
	public void clearUserData(ClearUserDataRequest request, StreamObserver<ClearUserDataResponse> responseObserver) {
		String userID = request.getUserID();

		if(userID == null || userID.isBlank()) {
			responseObserver.onError(INVALID_ARGUMENT
					.withDescription("O Nome não pode ser vazio.").asRuntimeException());
			return;
		}

		try{
			User user = userList.stream()
					.filter(x -> x.GetID().equals(userID))
					.findFirst().orElse(null);

			quorum.writeUserNBicloins(user.GetID(), 0);
			quorum.writeUserHasBike(user.GetID(), false);

			ClearUserDataResponse response = ClearUserDataResponse.newBuilder().setRequestAccepted(true).build();

			responseObserver.onNext(response);
			responseObserver.onCompleted();
		} catch (InvalidProtocolBufferException | NullPointerException e) {
			responseObserver.onError(NOT_FOUND
					.withDescription("O utilizador/estação indicado não foi encontrado.").asRuntimeException());
		}
	}

	public void setZKHostPort(String host, String port) {
		zkHost = host;
		zkPort = port;
	}

	public void ShutdownRecConnection() {
		rec.shutdown();
	}

	public void ShutdownQuorumConnections() {
		quorum.shutdown();
	}

	private RecordFrontend ConnectToRec() throws ZKNamingException{
		ZKNaming zkNaming = new ZKNaming(zkHost, zkPort);
		Collection<ZKRecord> recServers = GetListResults(zkNaming, zkRecServerPaths);
		ZKRecord record = recServers.stream().findFirst().orElse(null);

		if(record == null)
			return null;
		else
			//Currently there'll be only one Rec server active
			return new RecordFrontend(zkHost, zkPort, record.getPath());
	}

	private QuorumFrontend ConnectToQuorums() throws ZKNamingException{
		ZKNaming zkNaming = new ZKNaming(zkHost, zkPort);
		Collection<ZKRecord> recServers = GetListResults(zkNaming, zkRecServerPaths);
		ZKRecord record = recServers.stream().findFirst().orElse(null);

		if(record == null)
			return null;
		else
			return new QuorumFrontend(zkHost, zkPort);
	}

	private boolean InitializeData(List<String> filePaths, boolean initRec) {
		userList = new ArrayList<>();
		stationList = new ArrayList<>();

		boolean returnVal = false;
		for (String path : filePaths) {
			debug("Initializing File: " + path);
			if(path.endsWith("users.csv"))
				returnVal = InitializeUsers(path, initRec);
			else if(path.endsWith("stations.csv"))
				returnVal = InitializeStations(path, initRec);
		}
		return returnVal;
	}

	private boolean InitializeUsers(String path, boolean initRec){
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");

				debug("Attempting to create User with: " +
						values[0] +  "|" + values[1] +  "|" + values[2]);

				if(!ValidateUserValues(values)) {
					return false;
				}

				User user = new User(values[0], values[1], values[2]);
				userList.add(user);

				if(initRec){
					quorum.writeUserNBicloins(values[0], 0);
					quorum.writeUserHasBike(values[0], false);
				}
				debug("User successfully created");
			}
		} catch (Exception e) {	//Any error in the initialization should stop the execution
			return false;
		}
		return true;
	}

	private boolean InitializeStations(String path, boolean initRec){
		try (BufferedReader br = new BufferedReader(new FileReader(path))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");

				if(!ValidateStationValues(values))
					return false;

				Station station = new Station(values[1],
						values[0],
						Double.parseDouble(values[2]),
						Double.parseDouble(values[3]),
						Integer.parseInt(values[4]),
						Integer.parseInt(values[6]));

				stationList.add(station);

				if(initRec){
					quorum.writeStationNBikes(values[1], Integer.parseInt(values[5]));
					quorum.writeStationNRequests(values[1], 0);
					quorum.writeStationNReturns(values[1], 0);
				}
			}
		} catch (Exception e) {	//Any error in the initialization should stop the execution
			return false;
		}
		return true;
	}

	private boolean ValidateUserValues(String[] values) {
		return (values.length == 3 &&
				values[0].length() > 3 && values[0].length() < 10 &&
				values[1].length() > 3 && values[1].length() < 30 &&
				values[2].startsWith("+"));
	}

	private boolean ValidateStationValues(String[] values) {
		if(values.length != 7 || values[1].length() != 4)
			return false;

		try{
			double latitude = Double.parseDouble(values[2]);
			double longitude = Double.parseDouble(values[3]);
			int capacity = Integer.parseInt(values[4]);
			int bikeAmount = Integer.parseInt(values[6]);

			if(latitude < -90 || latitude > 90 ||
			longitude < -180 || longitude > 180 ||
			capacity <= 0 || bikeAmount < 0)
				return false;
		}
		catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

	/* Source for this code: https://github.com/jasonwinn/haversine/blob/master/Haversine.java */
	private static double HaversineDistance(double startLatitude, double startLongitude, double endLatitude, double endLongitude) {
		double earthRadius = 6371e3;

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

	private Collection<ZKRecord> GetListResults(ZKNaming zkNameServer, String serverPath) {
		try {
			return zkNameServer.listRecords(serverPath);
		}
		catch (ZKNamingException e) {
			return new ArrayList<>();
		}
	}
}
