package won.bot.airquality.impl;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.action.DeleteAction;
import won.bot.airquality.action.UpdateAirQualityAction;
import won.bot.airquality.context.AirQualityBotContextWrapper;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.bot.airquality.event.DeleteAtomEvent;
import won.bot.airquality.external.OpenAqApi;
import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.behaviour.ExecuteWonMessageCommandBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.extensions.serviceatom.ServiceAtomBehaviour;
import won.bot.framework.extensions.serviceatom.ServiceAtomExtension;

import java.lang.invoke.MethodHandles;
import java.util.List;

public class AirQualityBot extends EventBot implements ServiceAtomExtension {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private ServiceAtomBehaviour serviceAtomBehaviour;

    @Setter
    private OpenAqApi openAqApi;

    @Override
    public ServiceAtomBehaviour getServiceAtomBehaviour() {
        return serviceAtomBehaviour;
    }

    @Override
    protected void initializeEventListeners() {
        fetchAndPrintData(openAqApi);

        EventListenerContext ctx = getEventListenerContext();
        if (!(getBotContextWrapper() instanceof AirQualityBotContextWrapper)) {
            String botName = getBotContextWrapper().getBotName();
            logger.error("{} does not work without a AirQualityBotContextWrapper", botName);
            throw new IllegalStateException(botName + " does not work without a AirQualityBotContextWrapper");
        }

        // required for sending messages, like those for creating atoms
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();

        // TODO what is this for? do we need this?
        // activate ServiceAtomBehaviour
        serviceAtomBehaviour = new ServiceAtomBehaviour(ctx);
        serviceAtomBehaviour.activate();

        EventBus bus = getEventBus();
        bus.subscribe(DeleteAtomEvent.class, new DeleteAction(ctx, UpdateAirQualityAction.URI_LIST_NAME));
        bus.subscribe(ActEvent.class, new UpdateAirQualityAction(ctx, openAqApi));
    }

    // TODO for testing purposes only, remove at some point
    private void fetchAndPrintData(OpenAqApi openAqApi) {
        int printCount = 10;
        String separator = "------------------------------------------------------------------------------------------------------------------------";

        System.out.println(separator + "\n" + separator);
        List<LocationMeasurements> latestMeasurements = openAqApi.fetchLatestMeasurements();
        System.out.println("Fetched Measurements (first " + printCount + " Elements printed):");
        for (int i = 0; i < printCount; i++) {
            System.out.println(latestMeasurements.get(i).toString());
        }
        System.out.println(separator + "\n" + separator);
        List<Parameter> parameters = openAqApi.fetchParameters();
        System.out.println("Fetched Parameters:");
        for (Parameter param : parameters) {
            System.out.println(param.toString());
        }
        System.out.println(separator + "\n" + separator);
    }
}
