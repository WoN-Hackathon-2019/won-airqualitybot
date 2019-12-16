package won.bot.airquality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Measurement {

    private String parameter;

    private Double value;

    private String unit;

    private DateTime lastUpdated;

    @Override
    public String toString() {
        return "Measurement{" +
                "parameter='" + parameter + '\'' +
                ", value=" + value +
                ", unit='" + unit + '\'' +
                ", lastUpdated='" + lastUpdated + '\'' +
                '}';
    }
}
