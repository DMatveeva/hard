public interface InstantTrackGenerator {
    void generateTrackInstantly(Vehicle vehicle, double[] start, double[] finish, int maxSpeedKmH, LocalDateTime startDate);
    void generateTracksInCity(Integer enterpriseId, String cityName, int num);
}

public interface RealTimeTrackGenerator {
    void generateTrackInRealTime(Vehicle vehicle, double[] start, double[] finish, int maxSpeedKmH, LocalDateTime startDate, int delaySec);
}

@Slf4j
@Service
public class InstantTrackGeneratorService implements InstantTrackGenerator {

    @Autowired RestTemplate restTemplate;
    @Autowired TrackRepository trackRepository;
    @Autowired VehicleCoordinateRepository vehicleCoordinateRepository;
    @Autowired VehicleRepository vehicleRepository;

    @Override
    @Transactional
    public void generateTrackInstantly(Vehicle vehicle, double[] start, double[] finish, int maxSpeedKmH, LocalDateTime startDate) {
        List<LatLng> coordinates = RouteUtil.getRouteFromOpenRoute(restTemplate, start, finish, maxSpeedKmH);

        Track track = new Track();
        track.setVehicle(vehicle);
        track.setStarted(startDate);

        List<VehicleCoordinate> vehicleCoordinates = coordinates.stream()
                .map(latlng -> VehicleCoordinateUtil.createVehicleCoordinate(latlng, vehicle, track))
                .toList();
        IntStream.rangeClosed(0, vehicleCoordinates.size() - 1)
                .forEach(i -> vehicleCoordinates.get(i).setVisited(getVisitedDateForCoordinate(startDate, i)));

        vehicleCoordinateRepository.saveAll(vehicleCoordinates);

        LocalDateTime finishDateOfTrack = vehicleCoordinates.get(vehicleCoordinates.size()-1).getVisited();
        track.setFinished(finishDateOfTrack);
        Track saved = trackRepository.save(track);
        log.info("Track {} is created", saved.getVehicle().getId());
    }

    @Override
    public void generateTracksInCity(Integer enterpriseId, String cityName, int num) {
        City city = City.fromName(cityName);
        List<Vehicle> vehicles = vehicleRepository.findAllByEnterpriseId(enterpriseId);
        vehicles.forEach(v -> IntStream.rangeClosed(1, num).forEach(i -> generateTrackInCity(v, city)));
    }

    private void generateTrackInCity(Vehicle vehicle, City city){
        double[] start = new double[]{city.getRandomLon(), city.getRandomLat()};
        double[] finish = new double[]{city.getRandomLon(), city.getRandomLat()};
        int maxSpeedKmH = new Random().nextInt(40, 120);
        var startDate = DateUtil.getRandomStartOfTrack();
        generateTrackInstantly(vehicle, start, finish, maxSpeedKmH, startDate);
    }

    private LocalDateTime getVisitedDateForCoordinate(LocalDateTime startDate, int i) {
        long sec = startDate.toEpochSecond(ZoneOffset.UTC) + 10L * i;
        return LocalDateTime.ofEpochSecond(sec, 0, ZoneOffset.UTC);
    }
}

@Slf4j
@Service
public class RealTimeTrackGeneratorService implements RealTimeTrackGenerator {

    @Autowired VehicleRepository vehicleRepository;
    @Autowired VehicleCoordinateRepository vehicleCoordinateRepository;
    @Autowired TrackRepository trackRepository;
    @Autowired RestTemplate restTemplate;

    @Override
    public void generateTrackInRealTime(Vehicle vehicle, double[] start, double[] finish, int maxSpeedKmH, LocalDateTime startDate, int delaySec) {
        List<LatLng> coordinates = RouteUtil.getRouteFromOpenRoute(restTemplate, start, finish, maxSpeedKmH);

        Track track = new Track();
        track.setVehicle(vehicle);
        track.setStarted(startDate);

        Flux.fromStream(coordinates.stream())
                .delayUntil(d -> Mono.delay(Duration.ofSeconds(delaySec)))
                .map(latLon -> VehicleCoordinateUtil.createVehicleCoordinate(latLon, vehicle, track))
                .map(vehicleCoordinateRepository::save)
                .log()
                .subscribe(c -> log.debug(c.toString()));

        long sec = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) + 10L;
        track.setFinished(LocalDateTime.ofEpochSecond(sec, 0, ZoneOffset.UTC));
        Track saved = trackRepository.save(track);
        log.info("Track {} is created", saved.getVehicle().getId());
    }
}

public class DateUtil {
    private static final LocalDateTime MIN_DATE_OF_TRACK_2000 = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime MAX_DATE_OF_TRACK_2020 = LocalDateTime.of(2024, 1, 1, 0, 0);

    public static LocalDateTime getRandomStartOfTrack() {
        return generateDate(MIN_DATE_OF_TRACK_2000, MAX_DATE_OF_TRACK_2020);
    }

    private static LocalDateTime generateDate(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        long minDay = startInclusive.toEpochSecond(ZoneOffset.UTC);
        long maxDay = endExclusive.toEpochSecond(ZoneOffset.UTC);
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return LocalDateTime.ofEpochSecond(randomDay, 0, ZoneOffset.UTC);
    }
}

@Slf4j
public class RouteUtil {
    public static final String OPENROUTE_URI = "https://api.openrouteservice.org/v2/directions/driving-car";

    public static List<LatLng> getRouteFromOpenRoute(RestTemplate restTemplate, double[] start, double[] finish, int maxSpeedKmH){
        HttpEntity<String> request = new HttpEntity<>(getContent(start, finish, maxSpeedKmH), getHeaders());
        ResponseEntity<String> response = restTemplate.exchange(OPENROUTE_URI, HttpMethod.POST, request, String.class);

        JsonNode jsonNode;
        try {
            jsonNode = new ObjectMapper().readTree(response.getBody());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String geometryDecodedStr = jsonNode.get("routes").get(0).get("geometry").toString();
        EncodedPolyline encodedPolyline = new EncodedPolyline(geometryDecodedStr);
        return encodedPolyline.decodePath();
    }

    private static HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "5b3ce3597851110001cf6248a454b30fc2664363888082aa092ef980");
        headers.add("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8");
        headers.add("Content-Type", "application/json; charset=utf-8");
        return headers;
    }

    private static String getContent(double[] start, double[] finish, int maxSpeedKmH) {
        return String.format("{\"coordinates\":[%s,%s], \"maximum_speed\":%d}",
                coordinatesToStr(start), coordinatesToStr(finish), maxSpeedKmH);
    }
    private static String coordinatesToStr(double[] arr) {
        return String.format("[%f,%f]", arr[0], arr[1]);
    }
}

public class VehicleCoordinateUtil {

    public static VehicleCoordinate createVehicleCoordinate(LatLng latLng, Vehicle vehicle, Track track) {
        VehicleCoordinate vehicleCoordinate = new VehicleCoordinate();
        vehicleCoordinate.setVehicle(vehicle);
        vehicleCoordinate.setLat(latLng.lat);
        vehicleCoordinate.setLon(latLng.lng);
        vehicleCoordinate.setTrack(track);

        GeometryFactory geometryFactory = new GeometryFactory();
        Point point = geometryFactory.createPoint(new Coordinate(latLng.lng, latLng.lat));
        vehicleCoordinate.setPosition(point);
        return vehicleCoordinate;
    }
}

@Getter
public
enum City {

    LOS_ANGELES("Los Angeles", 33.830504, -118.385967, 34.007444, -118.070118),
    LAS_VEGAS("Las Vegas", 36.147515, -115.247185, 36.253734, -115.113607);

    City(String name, double minLat, double minLon, double maxLat, double maxLon) {
        this.name = name;
        this.minLat = minLat;
        this.minLon = minLon;
        this.maxLat = maxLat;
        this.maxLon = maxLon;
    }

    private final String name;
    private final double minLat;
    private final double minLon;
    private final double maxLat;
    private final double maxLon;

    public double getRandomLat() {
        return ThreadLocalRandom.current().nextDouble(minLat, maxLat);
    }

    public double getRandomLon() {
        return ThreadLocalRandom.current().nextDouble(minLon, maxLon);
    }

    public static City fromName(String name) {
        return Arrays.stream(City.values())
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Город не найден"));
    }
}