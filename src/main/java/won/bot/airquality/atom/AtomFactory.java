package won.bot.airquality.atom;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.protocol.util.DefaultAtomModelWrapper;

import java.net.URI;
import java.util.Map;

public class AtomFactory {

    private AtomFactory() {
    }

    private static final String AQ_DATA_TAG = "TEST1205_AirQualityData";
    private static final String AQ_BOT_TAG = "TEST1205_AirQualityBot";

    public static Dataset generateLocationMeasurementsAtomStructure(URI atomURI, LocationMeasurements locationMeasurements, Map<String, Parameter> paramIdToParam) {
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle(String.format("%s; %s; %s; %s", AQ_DATA_TAG, locationMeasurements.getLocation(),
                locationMeasurements.getCity(), locationMeasurements.getCountry()));
        atomWrapper.setDescription(locationMeasurements.toString());
        atomWrapper.addTag(AQ_DATA_TAG);
        atomWrapper.addTag(AQ_BOT_TAG);

        Model model = atomWrapper.getAtomModel();
        Resource locationNode = model.createResource(AirQualitySchema.LOCATION);

        Resource addressNode = atomWrapper.getAtomModel().createResource(AirQualitySchema.ADDRESS);
        addressNode.addProperty(AirQualitySchema.ADDR_COUNTRY, locationMeasurements.getCountry());
        addressNode.addProperty(AirQualitySchema.ADDR_CITY, locationMeasurements.getCity());
        addressNode.addProperty(AirQualitySchema.ADDR_LOCALITY, locationMeasurements.getLocation());
        locationNode.addProperty(AirQualitySchema.LOC_ADDRESS, addressNode);

        locationMeasurements.getMeasurements().stream()
                .map(measurement -> {
                    Resource measurementNode = locationNode.getModel().createResource(AirQualitySchema.MEASUREMENT);
                    String paramId = measurement.getParameter();
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM, paramId);
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM_NAME,
                            paramIdToParam.getOrDefault(paramId, Parameter.unknown(paramId)).getDescription());
                    measurementNode.addProperty(AirQualitySchema.MEASURE_VALUE, String.valueOf(measurement.getValue()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_UNIT, String.valueOf(measurement.getUnit()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_DATETIME, String.valueOf(measurement.getLastUpdated()));
                    // TODO add timestamp of measurement
                    //-----------------------


                    // in atom/AirQualitySchema https://schema.org/datePublished oder https://schema.org/DateTime ??

                    //-----------------------
                    // TODO maybe add name of parameter (not just ID)
                    return measurementNode;
                })
                .forEach(measurementResource -> locationNode.addProperty(AirQualitySchema.LOC_MEASUREMENT, measurementResource));

        return atomWrapper.getDataset();
    }


}
