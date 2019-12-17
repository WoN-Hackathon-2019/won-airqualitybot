package won.bot.airquality.atom;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.vocabulary.WXHOLD;

import java.net.URI;
import java.util.Map;

public class AtomFactory {

    private AtomFactory() {
    }

    private static final String AQ_DATA_TAG = "AirQualityData";
    private static final String AQ_BOT_TAG = "AirQualityBot";
    private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    public static Dataset generateLocationMeasurementsAtomStructure(URI atomURI,
                                                                    LocationMeasurements location,
                                                                    Map<String, Parameter> paramIdToParam,
                                                                    String dataSourceUrl) {
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle(String.format("%s for %s, %s (%s)", AQ_DATA_TAG, location.getLocation(), location.getCity(), location.getCountry()));
        atomWrapper.setDescription(buildAtomDescription(location, dataSourceUrl));
        atomWrapper.addTag(AQ_DATA_TAG);
        atomWrapper.addTag(AQ_BOT_TAG);
        atomWrapper.addSocket("#HoldableSocket", WXHOLD.HoldableSocketString); // to connect the created atoms with the bot

        Model model = atomWrapper.getAtomModel();
        Resource locationNode = model.createResource(AirQualitySchema.PLACE);
        atomWrapper.getAtomContentNode().addProperty(AirQualitySchema.LOCATION, locationNode);

        Resource addressNode = model.createResource(AirQualitySchema.ADDRESS);
        addressNode.addProperty(AirQualitySchema.ADDR_COUNTRY, location.getCountry());
        addressNode.addProperty(AirQualitySchema.ADDR_CITY, location.getCity());
        addressNode.addProperty(AirQualitySchema.ADDR_LOCALITY, location.getLocation());
        locationNode.addProperty(AirQualitySchema.PLACE_ADDRESS, addressNode);

        if (location.getCoordinates() != null) {
            Resource geoCoordinatesNode = model.createResource(AirQualitySchema.GEOCOORDINATES);
            geoCoordinatesNode.addProperty(AirQualitySchema.GEO_LATITUDE, String.valueOf(location.getCoordinates().getLatitude()));
            geoCoordinatesNode.addProperty(AirQualitySchema.GEO_LONGITUDE, String.valueOf(location.getCoordinates().getLongitude()));
            locationNode.addProperty(AirQualitySchema.PLACE_COORDINATES, geoCoordinatesNode);
        }

        location.getMeasurements().stream()
                .map(measurement -> {
                    Resource measurementNode = locationNode.getModel().createResource(AirQualitySchema.MEASUREMENT);
                    String paramId = measurement.getParameter();
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM, paramId);
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM_NAME,
                            paramIdToParam.getOrDefault(paramId, Parameter.unknown(paramId)).getDescription());
                    measurementNode.addProperty(AirQualitySchema.MEASURE_VALUE, String.valueOf(measurement.getValue()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_UNIT, String.valueOf(measurement.getUnit()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_DATETIME, dateTimeToIso8601(measurement.getLastUpdated()));
                    return measurementNode;
                })
                .forEach(measurementResource -> locationNode.addProperty(AirQualitySchema.PLACE_MEASUREMENT, measurementResource));

        return atomWrapper.getDataset();
    }

    private static String buildAtomDescription(LocationMeasurements location, String dataSourceUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Automatically generated air quality data, retrieved from ");
        sb.append(dataSourceUrl);
        sb.append('\n');
        sb.append("Location: ");
        sb.append(location.getLocation());
        sb.append('\n');
        sb.append("City: ");
        sb.append(location.getCity());
        sb.append('\n');
        sb.append("Country: ");
        sb.append(location.getCountry());
        return sb.toString();
    }

    private static String dateTimeToIso8601(DateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern(DATE_FORMAT);
        return formatter.print(dateTime);
    }
}
