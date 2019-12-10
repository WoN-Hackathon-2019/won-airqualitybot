package won.bot.airquality.event;

import lombok.Getter;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.framework.eventbot.event.BaseEvent;

public class CreateAtomFromMeasurements extends BaseEvent {
    @Getter
    private final LocationMeasurements locationMeasurements;

    public CreateAtomFromMeasurements(LocationMeasurements locationMeasurements) {
        this.locationMeasurements = locationMeasurements;
    }
}
