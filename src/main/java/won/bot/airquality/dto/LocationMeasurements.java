package won.bot.airquality.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LocationMeasurements {

    private String location;

    private String city;

    private String country;

    private List<Measurement> measurements;
}
