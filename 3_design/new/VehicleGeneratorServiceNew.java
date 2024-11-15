public interface VehicleGenerator {
    void generateVehiclesForEnterprises(List<Integer> enterpriseIds, int numVehicles);
}

public class VehicleGeneratorServiceNew implements VehicleGenerator {

    @Autowired VehicleModelRepository vehicleModelRepository;
    @Autowired EnterpriseRepository enterpriseRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired List<VehicleModel> vehicleModels;

    @PostConstruct
    private void initModels() {
        vehicleModels = vehicleModelRepository.findAll();
    }

    @Override
    public void generateVehiclesForEnterprises(List<Integer> enterpriseIds, int numVehicles) {
        List<Vehicle> vehicles = enterpriseIds.stream()
                .map(e -> enterpriseRepository.findById(e).orElseThrow())
                .map(e -> generateVehiclesForEnterprise(e, numVehicles))
                .flatMap(Collection::stream).toList();
        vehicleRepository.saveAll(vehicles);
    }

    private List<Vehicle> generateVehiclesForEnterprise(Enterprise enterprise, int numVehicles) {
        return IntStream.rangeClosed(0, numVehicles-1)
                .mapToObj(i -> generateVehicle(enterprise))
                .toList();
    }
    private Vehicle generateVehicle(Enterprise enterprise) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleModel(RandomVehicleUtil.getVehicleModel(vehicleModels));
        vehicle.setVin(RandomVehicleUtil.generateVin());
        vehicle.setCostUsd(RandomVehicleUtil.generateCost());
        vehicle.setColor(Color.getRandomColor().getName());
        vehicle.setMileage(RandomVehicleUtil.getMileage());
        vehicle.setProductionYear(DateUtil.getRandomProductionYear());
        vehicle.setEnterprise(enterprise);
        vehicle.setPurchaseDate(DateUtil.getRandomPurchaseDate());
        List<Driver> drivers = RandomDriverUtil.getDrivers(enterprise, vehicle, RandomDriverUtil.getIsDriverActive());
        vehicle.setDrivers(drivers);
        drivers.forEach(d -> d.setVehicle(vehicle));
        return vehicle;
    }
}
class RandomDriverUtil {
    private static final int MAX_NUM_DRIVERS_FOR_VEHICLE = 2;

    public static List<Driver> getDrivers(Enterprise enterprise, Vehicle vehicle, boolean isActive) {
        int numDrivers = new Random().nextInt(0, MAX_NUM_DRIVERS_FOR_VEHICLE);
        return IntStream.rangeClosed(0, numDrivers-1)
                .mapToObj(i -> getDriver(enterprise, vehicle, isActive))
                .toList();
    }
    private static Driver getDriver(Enterprise enterprise, Vehicle vehicle, boolean isActive) {

        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('a', 'z')
                .build();
        String firstName = "FN " + generator.generate(5);
        String secondName = "SN " + generator.generate(5);
        return new Driver(
                firstName,
                secondName,
                generateSalary(),
                getExperienceYears(),
                enterprise,
                vehicle,
                isActive);
    }

    private static BigDecimal generateSalary() {
        return new BigDecimal(BigInteger.valueOf(new Random().nextInt(1000, 40001)), 2);
    }

    private static int getExperienceYears() {
        return new Random().nextInt(2, 41);
    }

    public static boolean getIsDriverActive() {
        int randomIndex = new Random().nextInt(1, 11);
        return randomIndex == 10;
    }
}

class RandomVehicleUtil {
    private final static int VIN_LENGTH = 17;
    private final static int MAX_MILEAGE = 500_000;

    public static String generateVin() {
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(LETTERS, DIGITS)
                .build();
        return generator.generate(VIN_LENGTH);
    }

    public static VehicleModel getVehicleModel(List<VehicleModel> vehicleModels) {
        int randomIndex = new Random().nextInt(0, vehicleModels.size());
        return vehicleModels.get(randomIndex);
    }

    public static BigDecimal generateCost() {
        return new BigDecimal(BigInteger.valueOf(new Random().nextInt(1000, 100001)), 2);
    }

    public static int getMileage() {
        return new Random().nextInt(MAX_MILEAGE);
    }
}

class DateUtil {
    private static final LocalDateTime MIN_DATE_OF_PURCHASE_2000 = LocalDateTime.of(2000, 1, 1, 0,0);
    private static final LocalDateTime MAX_DATE_OF_PURCHASE_2020 = LocalDateTime.of(2020, 1, 1, 0,0);
    private static final int OLDEST_YEAR_OF_PRODUCTION = 1980;
    private static final int NEWEST_YEAR_OF_PRODUCTION = 2020;

    public static LocalDateTime getRandomPurchaseDate() {
        return getRandomDate(MIN_DATE_OF_PURCHASE_2000, MAX_DATE_OF_PURCHASE_2020);
    }
    private static LocalDateTime getRandomDate(LocalDateTime startInclusive, LocalDateTime endExclusive) {
        long minDay = startInclusive.toEpochSecond(ZoneOffset.UTC);
        long maxDay = endExclusive.toEpochSecond(ZoneOffset.UTC);
        long randomDay = ThreadLocalRandom.current().nextLong(minDay, maxDay);
        return LocalDateTime.ofEpochSecond(randomDay, 0, ZoneOffset.UTC);
    }
    public static int getRandomProductionYear() {
        return new Random().nextInt(OLDEST_YEAR_OF_PRODUCTION, NEWEST_YEAR_OF_PRODUCTION);
    }

}

@Getter
enum Color {
    BLACK("Black"),
    BLUE("Blue"),
    WHITE("White"),
    GREEN("Green"),
    PURPLE("Purple"),
    SILVER("Silver"),
    RED("Red"),
    ORANGE("Orange"),
    PINK("Pink"),
    YELLOW("Yellow"),
    BLOWN("Brown");

    private final String name;
    Color(String name) {
        this.name = name;
    }

    public static Color getRandomColor() {
        int size = Color.values().length;
        int randomIndex = new Random().nextInt(size-1);
        return Color.values()[randomIndex];
    }
}