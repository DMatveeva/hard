@Service
@Slf4j
public class TrackGeneratorService {

    TrackRepository trackRepository;
    VehicleCoordinateRepository coordinateRepository;

    VehicleRepository vehicleRepository;

    public TrackGeneratorService(TrackRepository trackRepository, VehicleCoordinateRepository coordinateRepository, VehicleRepository vehicleRepository) {
        this.trackRepository = trackRepository;
        this.coordinateRepository = coordinateRepository;
        this.vehicleRepository = vehicleRepository;
    }

    String coordinatesToStr(double[] arr) {
        return "[" + arr[0] + "," +arr[1] + "]";
    }

    public void generateTrackInRealTime(Integer vehicleId, double[] start, double[] finish, int maxSpeedKmH) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId).orElseThrow();
        generateTrack(vehicle, start, finish, maxSpeedKmH, 10000, LocalDateTime.now());
    }

    public void generateTrackInstantly(Vehicle vehicle, double[] start, double[] finish, int maxSpeedKmH, LocalDateTime startDate) {
        generateTrack(vehicle, start, finish, maxSpeedKmH, 0, startDate);
    }

    @Transactional
    public void generateTrack(Vehicle vehicle, double[] start, double[] finish,
            int maxSpeedKmH, int delay, LocalDateTime startDate) {

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "5b3ce3597851110001cf6248a454b30fc2664363888082aa092ef980");
        headers.add("Accept", "application/json, application/geo+json, application/gpx+xml, img/png; charset=utf-8");
        headers.add("Content-Type", "application/json; charset=utf-8");

        String jsonContent = "{\"coordinates\":["+ coordinatesToStr(start) +"," + coordinatesToStr(finish)+"], \"maximum_speed\":"+ maxSpeedKmH +"}";

        HttpEntity<String> request = new HttpEntity<>(jsonContent, headers);

        String uri = "https://api.openrouteservice.org/v2/directions/driving-car";

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, request, String.class);

        System.out.println("status: " + response.getStatusCode());
        System.out.println("headers: " + response.getHeaders());

        String responseBody = response.getBody();
        System.out.println("body:" + responseBody);

        JSONObject json = new JSONObject(responseBody);
        String geometryEncoded;
        try {
            geometryEncoded = json.getJSONArray("routes").getJSONObject(0).getString("geometry");
        } catch (JSONException e) {
            return;
        }
        JSONArray geometryArray = GeometryDecoder.decodeGeometry(geometryEncoded, false);

        ZoneId zoneId = ZoneId.of(vehicle.getEnterprise().getLocalTimeZone());
        Track track = new Track();
        track.setVehicle(vehicle);
        track.setStarted(startDate);
        Track saved = trackRepository.save(track);
        log.info("Track {} is created", saved.getVehicle().getId());

        LocalDateTime coordinateDate = startDate;
        for (int i = 0; i < geometryArray.length(); i++) {
            JSONArray latLon = geometryArray.getJSONArray(i);
            createAndSaveCoordinate(vehicle, latLon, track, coordinateDate);

            long sec = coordinateDate.toEpochSecond(ZoneOffset.UTC) + 10L;
            coordinateDate = LocalDateTime.ofEpochSecond(sec, 0, ZoneOffset.UTC);
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        track.setFinished(coordinateDate);
    }


    private void createAndSaveCoordinate(Vehicle vehicle, JSONArray latLon, Track track, LocalDateTime coordinateDate) {
        double lat = latLon.getDouble(0);
        double lon = latLon.getDouble(1);
        VehicleCoordinate vehicleCoordinate = new VehicleCoordinate();
        vehicleCoordinate.setVehicle(vehicle);
        vehicleCoordinate.setLat(lat);
        vehicleCoordinate.setLon(lon);
        vehicleCoordinate.setTrack(track);
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate coordinate = new Coordinate(lon, lat);
        Point point = geometryFactory.createPoint(coordinate);

        vehicleCoordinate.setPosition(point);
        vehicleCoordinate.setVisited(coordinateDate);
        coordinateRepository.save(vehicleCoordinate);
    }

    private static final LocalDateTime MIN_DATE_START = LocalDateTime.of(2018, 1, 1, 0,0);
    private static final LocalDateTime MAX_DATE_START = LocalDateTime.of(2021, 1, 1, 0,0);

    private static final double MIN_LAT_LOS_ANGELES = 33.830504;
    private static final double MIN_LON_LOS_ANGELES = -118.385967;
    private static final double MAX_LAT_LOS_ANGELES = 34.007444;
    private static final double MAX_LON_LOS_ANGELES = -118.070118;

    private static final double MIN_LAT_LAS_VEGAS = 36.147515;
    private static final double MIN_LON_LAS_VEGAS = -115.247185;
    private static final double MAX_LAT_LAS_VEGAS = 36.253734;
    private static final double MAX_LON_LAS_VEGAS = -115.113607;


    // lon, lat
    public void generateTracksForVehicles(List<Vehicle> vehicles, int numTracks, String city) {
        final double minLat;
        final double minLon;
        final double maxLat;
        final double maxLon;
        if (city.equals("Los Angeles")) {
            minLat = MIN_LAT_LOS_ANGELES;
            minLon = MIN_LON_LOS_ANGELES;
            maxLat = MAX_LAT_LOS_ANGELES;
            maxLon = MAX_LON_LOS_ANGELES;
        } else if(city.equals("Las Vegas")) {
            minLat = MIN_LAT_LAS_VEGAS;
            minLon = MIN_LON_LAS_VEGAS;
            maxLat = MAX_LAT_LAS_VEGAS;
            maxLon = MAX_LON_LAS_VEGAS;
        } else {
            throw new RuntimeException();
        }
        for (int j = 0; j < vehicles.size(); j++) {
            Vehicle vehicle = vehicles.get(j);
            for(int i = 0; i <= numTracks; i++) {
                double latStart = ThreadLocalRandom.current().nextDouble(minLat, maxLat);
                double lonStart = ThreadLocalRandom.current().nextDouble(minLon, maxLon);
                double[] start = {lonStart, latStart};
                double latFinish = ThreadLocalRandom.current().nextDouble(minLat, maxLat);
                double lonFinish = ThreadLocalRandom.current().nextDouble(minLon, maxLon);
                double[] finish = {lonFinish, latFinish};
                LocalDateTime startDate = generateDate(MIN_DATE_START, MAX_DATE_START);

                generateTrackInstantly(vehicle, start, finish, 90, startDate);
                System.out.println("vehicle " + j + ", track " + i );
            }
        }
    }

    public LocalDateTime generateDate(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        long minDay = startInclusive.toEpochSecond(ZoneOffset.UTC);
        long maxDay = endExclusive.toEpochSecond(ZoneOffset.UTC);
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return LocalDateTime.ofEpochSecond(randomDay, 0, ZoneOffset.UTC);
    }
}




public class GeometryDecoder {

    public static JSONArray decodeGeometry(String encodedGeometry, boolean inclElevation) {
        JSONArray geometry = new JSONArray();
        int len = encodedGeometry.length();
        int index = 0;
        int lat = 0;
        int lng = 0;
        int ele = 0;

        while (index < len) {
            int result = 1;
            int shift = 0;
            int b;
            do {
                b = encodedGeometry.charAt(index++) - 63 - 1;
                result += b << shift;
                shift += 5;
            } while (b >= 0x1f);
            lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);

            result = 1;
            shift = 0;
            do {
                b = encodedGeometry.charAt(index++) - 63 - 1;
                result += b << shift;
                shift += 5;
            } while (b >= 0x1f);
            lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);if(inclElevation){
                result = 1;
                shift = 0;
                do {
                    b = encodedGeometry.charAt(index++) - 63 - 1;
                    result += b << shift;
                    shift += 5;
                } while (b >= 0x1f);
                ele += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
            }

            JSONArray location = new JSONArray();
            try {
                location.put(lat / 1E5);
                location.put(lng / 1E5);
                if(inclElevation){
                    location.put((float) (ele / 100));
                }
                geometry.put(location);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        return geometry;
    }
}