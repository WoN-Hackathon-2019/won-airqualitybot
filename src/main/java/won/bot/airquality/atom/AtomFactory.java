package won.bot.airquality.atom;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
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

    public static Dataset generateLocationMeasurementsAtomStructure(URI atomURI, LocationMeasurements locationMeasurements, Map<String, Parameter> paramIdToParam) {
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle(String.format("Leaze %s; %s; %s; %s", AQ_DATA_TAG, locationMeasurements.getLocation(),
                locationMeasurements.getCity(), locationMeasurements.getCountry())); // TODO remove "Leaze " from String
        atomWrapper.setDescription(locationMeasurements.toString());
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
                .forEach(measurementResource -> locationNode.addProperty(AirQualitySchema.PLACE_MEASUREMENT, measurementResource));

        return atomWrapper.getDataset();
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
