package won.bot.airquality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Parameter {

    private static final String UNKNOWN_NAME = "unknown";
    private static final String UNKNOWN_DESCRIPTION = "unknown";
    private static final String UNKNOWN_PREFERRED_UNIT = "unknown";

    private String id;

    private String name;

    private String description;

    private String preferredUnit;

    @Override
    public String toString() {
        return "Parameter{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", preferredUnit='" + preferredUnit + '\'' +
                '}';
    }

    public static Parameter unknown(String id) {
        Parameter newParam = new Parameter();
        newParam.setId(id);
        newParam.setName(UNKNOWN_NAME);
        newParam.setName(UNKNOWN_DESCRIPTION);
        newParam.setName(UNKNOWN_PREFERRED_UNIT);
        return newParam;
    }
}
