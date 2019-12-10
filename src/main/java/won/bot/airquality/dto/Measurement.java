package won.bot.airquality.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Measurement {

    private String parameter;

    private Double value;

    private String unit;
}
