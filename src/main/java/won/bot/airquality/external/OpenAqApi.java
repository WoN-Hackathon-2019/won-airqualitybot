package won.bot.airquality.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import won.bot.airquality.dto.LocationMeasurements;

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
    private static final String COUNTRY_GET_PARAM = "country";
    private static final String LIMIT_GET_PARAM = "limit";

    private static final String AT_COUNTRY_CODE = "AT";
    private static final String DEFAULT_LIMIT = "500";

    private String openaqApiUrl;

    public OpenAqApi(String openaqApiUrl) {
        this.openaqApiUrl = openaqApiUrl;
    }

    public List<LocationMeasurements> fetchLatestMeasurements() {
        String responseString = fetchLatestMeasurementsAsString();
        return parseResponseString(responseString);
    }

    private String fetchLatestMeasurementsAsString() {
        CloseableHttpResponse response = null;
        StringBuilder resultBuilder = new StringBuilder();
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet getRequest = buildGetRequest(LATEST_ENDPOINT,
                    Arrays.asList(new GetParam(COUNTRY_GET_PARAM, AT_COUNTRY_CODE), new GetParam(LIMIT_GET_PARAM, DEFAULT_LIMIT)));
            response = httpClient.execute(getRequest);
            if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));
            String line;
            while ((line = br.readLine()) != null) {
                resultBuilder.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return resultBuilder.toString();
    }

    private HttpGet buildGetRequest(String resourcePath, List<GetParam> getParams) {
        URI url = buildUrl(resourcePath, getParams);
        System.out.println("URI: " + url.toString());
        HttpGet request = new HttpGet(url);
        request.addHeader("accept", "application/json");
        return request;
    }

    private URI buildUrl(String resourcePath, List<GetParam> getParams) {
        try {
            URIBuilder builder = new URIBuilder(this.openaqApiUrl + "/" + resourcePath);
            for (GetParam param : getParams) {
                builder.setParameter(param.getKey(), param.getValue());
            }
            return builder.build();
        } catch (URISyntaxException e) {
            e.printStackTrace();
            throw new RuntimeException("Could not build Url for the given parameters."); // TODO use dedicated exception
        }
    }

    private List<LocationMeasurements> parseResponseString(String responseString) {
        List<LocationMeasurements> locationMeasurements = new ArrayList<>();

        try {
            JSONObject jsonResponse = new JSONObject(responseString);
            JSONArray jsonLocMeasurements = jsonResponse.getJSONArray("results");
            ObjectMapper objectMapper = new ObjectMapper();
            for (int count = 0; count < jsonLocMeasurements.length(); count++) {
                String locMeasurementString = jsonLocMeasurements.getJSONObject(count).toString();
                LocationMeasurements tmpJob = objectMapper.readValue(locMeasurementString, LocationMeasurements.class);
                locationMeasurements.add(tmpJob);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return locationMeasurements;
    }
}
