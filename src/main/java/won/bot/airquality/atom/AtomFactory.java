package won.bot.airquality.atom;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.bot.airquality.external.OpenAqApi;
import won.protocol.util.DefaultAtomModelWrapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AtomFactory {

    private AtomFactory() {
    }

    private static final String AQ_DATA_TAG = "AirQualityData";
    private static final String AQ_BOT_TAG = "AirQualityBot";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static Dataset generateLocationMeasurementsAtomStructure(URI atomURI, LocationMeasurements locationMeasurements, Map<String, Parameter> paramIdToParam) {
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle(String.format("%s for %s, %s (%s); %s", AQ_DATA_TAG, locationMeasurements.getLocation(),
                locationMeasurements.getCity(), locationMeasurements.getCountry(), locationMeasurements.getCoordinates())); // TODO remove "Leaze " from String
        atomWrapper.setDescription("Automatically generated air quality data; data fetched from https://docs.openaq.org"); // atom description
        atomWrapper.addTag(AQ_DATA_TAG);
        atomWrapper.addTag(AQ_BOT_TAG);

        Model model = atomWrapper.getAtomModel();
        Resource locationNode = model.createResource(AirQualitySchema.PLACE);
        atomWrapper.getAtomContentNode().addProperty(AirQualitySchema.LOCATION, locationNode);

        Resource addressNode = model.createResource(AirQualitySchema.ADDRESS);
        addressNode.addProperty(AirQualitySchema.ADDR_COUNTRY, locationMeasurements.getCountry());
        addressNode.addProperty(AirQualitySchema.ADDR_CITY, locationMeasurements.getCity());
        addressNode.addProperty(AirQualitySchema.ADDR_LOCALITY, locationMeasurements.getLocation());
        locationNode.addProperty(AirQualitySchema.PLACE_ADDRESS, addressNode);

        Resource geoCoordinatesNode = model.createResource(AirQualitySchema.GEOCOORDINATES);
        geoCoordinatesNode.addProperty(AirQualitySchema.GEO_LATITUDE, String.valueOf(locationMeasurements.getCoordinates().getLatitude()));
        geoCoordinatesNode.addProperty(AirQualitySchema.GEO_LONGITUDE, String.valueOf(locationMeasurements.getCoordinates().getLongitude()));
        locationNode.addProperty(AirQualitySchema.PLACE_COORDINATES, geoCoordinatesNode);

        locationMeasurements.getMeasurements().stream()
                .map(measurement -> {
                    Resource measurementNode = locationNode.getModel().createResource(AirQualitySchema.MEASUREMENT);
                    String paramId = measurement.getParameter();
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM, paramId);
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM_NAME,
                            paramIdToParam.getOrDefault(paramId, Parameter.unknown(paramId)).getDescription());
                    measurementNode.addProperty(AirQualitySchema.MEASURE_VALUE, String.valueOf(measurement.getValue()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_UNIT, String.valueOf(measurement.getUnit()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_DATETIME, dateTimeToISO8601(measurement.getLastUpdated()));
                    return measurementNode;
                })
                .forEach(measurementResource -> locationNode.addProperty(AirQualitySchema.PLACE_MEASUREMENT, measurementResource));

        return atomWrapper.getDataset();
    }

    private static String dateTimeToISO8601(DateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(DATE_FORMAT);
        return formatter.print(dateTime);
    }

    // TODO finish or delete
    public static void main(String[] args) {
        OpenAqApi openAqApi = new OpenAqApi("https://api.openaq.org/v1");
        List<LocationMeasurements> locations = openAqApi.fetchLatestMeasurements();
        List<Parameter> parameters = openAqApi.fetchParameters();
        Map<String, Parameter> paramIdToParam = parameters.stream().collect(Collectors.toMap(Parameter::getId, Function.identity()));
        locations.forEach(location -> {
            Dataset atomDataset = null;
            try {
                atomDataset = AtomFactory.generateLocationMeasurementsAtomStructure(new URI("http://schema.org/NONEXISTENT"), location, paramIdToParam);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            atomDataset.asDatasetGraph().listGraphNodes().forEachRemaining(node -> {
                // TODO create string-representation of node
            });
            System.out.println(atomDataset.asDatasetGraph().toString());
        });
    }
}
