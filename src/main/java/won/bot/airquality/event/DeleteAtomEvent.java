package won.bot.airquality.event;

import lombok.Getter;
import won.bot.framework.eventbot.event.BaseEvent;

import java.net.URI;

public class DeleteAtomEvent extends BaseEvent {
    @Getter
    private final URI atomUri;

    public DeleteAtomEvent(URI atomUri) {
        this.atomUri = atomUri;
    }
}
