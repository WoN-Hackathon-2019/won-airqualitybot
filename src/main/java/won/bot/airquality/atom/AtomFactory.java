package won.bot.airquality.atom;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Resource;
import won.bot.airquality.dto.LocationMeasurements;
import won.protocol.util.DefaultAtomModelWrapper;

import java.net.URI;

public class AtomFactory {

    private AtomFactory() {
    }

    private static final String AQ_DATA_TAG = "AirQualityData";
    private static final String AQ_BOT_TAG = "AirQualityBot";

    public static Dataset generateLocationMeasurementsAtomStructure(URI atomURI, LocationMeasurements locationMeasurements) {
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle(String.format("%s; %s; %s; %s", AQ_DATA_TAG, locationMeasurements.getLocation(),
                locationMeasurements.getCity(), locationMeasurements.getCountry()));
        atomWrapper.setDescription(locationMeasurements.toString());
        atomWrapper.addTag(AQ_DATA_TAG);
        atomWrapper.addTag(AQ_BOT_TAG);

        Resource locationNode = atomWrapper.getAtomContentNode();  // create a blank node that represents a locationNode
        locationNode.addProperty(AirQualitySchema.LOCATION, locationMeasurements.getLocation());
        locationNode.addProperty(AirQualitySchema.CITY, locationMeasurements.getCity());
        locationNode.addProperty(AirQualitySchema.COUNTRY, locationMeasurements.getCountry());

        locationMeasurements.getMeasurements().stream()
                .map(measurement -> {
                    Resource measurementNode = locationNode.getModel().createResource(); // create the second blank node (which represents a measurementNode)
                    measurementNode.addProperty(AirQualitySchema.MEASURE_PARAM, measurement.getParameter());
                    measurementNode.addProperty(AirQualitySchema.MEASURE_VALUE, String.valueOf(measurement.getValue()));
                    measurementNode.addProperty(AirQualitySchema.MEASURE_UNIT, String.valueOf(measurement.getUnit()));
                    // TODO add timestamp of measurement
                    //-----------------------


                    // in atom/AirQualitySchema https://schema.org/datePublished oder https://schema.org/DateTime ??

                    //-----------------------
                    // TODO maybe add name of parameter (not just ID)
                    return measurementNode;
                })
                .forEach(measurementResource -> locationNode.addProperty(AirQualitySchema.MEASUREMENT, measurementResource));

        return atomWrapper.getDataset();
    }
}
