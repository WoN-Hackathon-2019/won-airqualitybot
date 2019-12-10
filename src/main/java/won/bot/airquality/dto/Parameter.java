package won.bot.airquality.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Parameter {

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
}
