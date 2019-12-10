package won.bot.airquality.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.HttpStatus;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OpenAqApi {

    private static final String LATEST_ENDPOINT = "latest";
    private static final String PARAMETERS_ENDPOINT = "parameters";

    private static final String COUNTRY_GET_PARAM = "country";
    private static final String LIMIT_GET_PARAM = "limit";

    private static final String LIST_RESOURCE_DATA_KEY = "results";

    private static final String AT_COUNTRY_CODE = "AT";
    private static final String DEFAULT_LIMIT = "500";

    private final String apiUrl;

    public OpenAqApi(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public List<LocationMeasurements> fetchLatestMeasurements() {
        List<GetParam> params = Arrays.asList(
                new GetParam(COUNTRY_GET_PARAM, AT_COUNTRY_CODE),
                new GetParam(LIMIT_GET_PARAM, DEFAULT_LIMIT));
        HttpGet request = createGetRequest(LATEST_ENDPOINT, params);
        String responseString = dispatchGetRequest(request);
        return Arrays.asList(parseResponseString(responseString, LocationMeasurements[].class, LIST_RESOURCE_DATA_KEY));
    }

    public List<Parameter> fetchParameters() {
        HttpGet request = createGetRequest(PARAMETERS_ENDPOINT, new ArrayList<>());
        String responseString = dispatchGetRequest(request);
        return Arrays.asList(parseResponseString(responseString, Parameter[].class, LIST_RESOURCE_DATA_KEY));
    }

    private String dispatchGetRequest(HttpGet getRequest) {
        StringBuilder resultBuilder = new StringBuilder();
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(getRequest)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.OK.value()) {
                throw new ExtCommunicationException(
                        String.format("invalid status code %d received for request to %s", statusCode, getRequest.getURI()));
            }

            try (BufferedReader responseReader = new BufferedReader(new InputStreamReader((response.getEntity().getContent())))) {
                String line;
                while ((line = responseReader.readLine()) != null) {
                    resultBuilder.append(line);
                }
            }
        } catch (IOException e) {
            throw new ExtCommunicationException("error when reading data from HttpClient", e);
        }

        return resultBuilder.toString();
    }

    private <T> T parseResponseString(String responseString, Class<T> parsedClass, String jsonPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode root = objectMapper.readTree(responseString);
            JsonNode node = root.get(jsonPath);
            return objectMapper.treeToValue(node, parsedClass);
        } catch (IOException e) {
            throw new JsonParseException("json response could not be parsed.", e);
        }
    }

    private HttpGet createGetRequest(String resourcePath, List<GetParam> getParams) {
        URI url = buildURI(resourcePath, getParams);
        HttpGet request = new HttpGet(url);
        request.addHeader("accept", "application/json");
        return request;
    }

    private URI buildURI(String resourcePath, List<GetParam> getParams) {
        try {
            URIBuilder builder = new URIBuilder(this.apiUrl + "/" + resourcePath);
            for (GetParam param : getParams) {
                builder.setParameter(param.getKey(), param.getValue());
            }
            return builder.build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Could not build URI with the given parameters.");
        }
    }
}
