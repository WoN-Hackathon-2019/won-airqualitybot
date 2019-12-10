package won.bot.airquality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationMeasurements {

    private String location;

    private String city;

    private String country;

    private List<Measurement> measurements;

    @Override
    public String toString() {
        return "LocationMeasurements{" +
                "location='" + location + '\'' +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                ", measurements=" + measurements +
                '}';
    }
}
