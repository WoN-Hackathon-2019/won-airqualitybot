package won.bot.airquality.external;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetParam {

    private String key;
    private String value;

    public GetParam(String key, String value) {
        this.key = key;
        this.value = value;
    }
}
