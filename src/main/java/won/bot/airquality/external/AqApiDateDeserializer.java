package won.bot.airquality.external;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;

public class AqApiDateDeserializer extends JsonDeserializer<DateTime> {

    private static final String FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    @Override
    public DateTime deserialize(JsonParser jsonParser,
                                DeserializationContext deserializationContext) throws IOException {

        DateTimeFormatter formatter = DateTimeFormat.forPattern(FORMAT);
        return formatter.parseDateTime(jsonParser.getText());
    }
}
