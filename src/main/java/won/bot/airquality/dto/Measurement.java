package won.bot.airquality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import won.bot.airquality.external.AqApiDateDeserializer;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Measurement {

    private String parameter;

    private Double value;

    private String unit;

    @JsonDeserialize(using = AqApiDateDeserializer.class)
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
