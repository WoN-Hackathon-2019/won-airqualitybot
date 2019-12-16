package won.bot.airquality.action;

import org.apache.jena.query.Dataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.atom.AtomFactory;
import won.bot.airquality.context.AirQualityBotContextWrapper;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.bot.airquality.event.DeleteAtomEvent;
import won.bot.airquality.external.OpenAqApi;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractCreateAtomAction;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandFailureEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandResultEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.filter.impl.CommandResultFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnFirstEventListener;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UpdateAirQualityAction extends AbstractCreateAtomAction {

    public static final String URI_LIST_NAME = "air_quality_uri_list_name";
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final OpenAqApi openAqApi;

    public UpdateAirQualityAction(EventListenerContext eventListenerContext, OpenAqApi openAqApi) {
        super(eventListenerContext);
        this.openAqApi = openAqApi;
    }

    @Override
    protected void doRun(Event event, EventListener eventListener) {
        EventListenerContext ctx = getEventListenerContext();

        if (!(event instanceof ActEvent) || !(ctx.getBotContextWrapper() instanceof AirQualityBotContextWrapper)) {
            return;
        }
        List<LocationMeasurements> locations = this.openAqApi.fetchLatestMeasurements();
        List<Parameter> parameters = this.openAqApi.fetchParameters();
        ArrayList<URI> toDelete = new ArrayList<>(getEventListenerContext().getBotContext().getNamedAtomUriList(URI_LIST_NAME));

        logger.info("Creating atoms...");
        for (LocationMeasurements locationMeasurements : locations.subList(0, 3)) { // TODO use entire list if in productive use
            createAtomForLocationMeasurements(locationMeasurements, parameters);
        }

        toDelete.forEach(uri -> ctx.getEventBus().publish(new DeleteAtomEvent(uri)));
    }

    private void createAtomForLocationMeasurements(LocationMeasurements locationMeasurements, List<Parameter> parameters) {
        // Create a new atom URI
        EventListenerContext ctx = getEventListenerContext();
        URI wonNodeURI = ctx.getNodeURISource().getNodeURI();
        URI atomURI = ctx.getWonNodeInformationService().generateAtomURI(wonNodeURI);

        Map<String, Parameter> paramIdToParam = parameters.stream().collect(Collectors.toMap(Parameter::getId, Function.identity()));
        Dataset atomDataset = AtomFactory.generateLocationMeasurementsAtomStructure(atomURI, locationMeasurements, paramIdToParam);

        CreateAtomCommandEvent createCommand = new CreateAtomCommandEvent(atomDataset, URI_LIST_NAME);

        // create listeners to react to events fired by the published command
        ctx.getEventBus().subscribe(
                CreateAtomCommandResultEvent.class,
                new ActionOnFirstEventListener( // the listener is destroyed after being invoked once.
                        ctx,
                        new CommandResultFilter(createCommand),  // only listen for success to the command we just published
                        new BaseEventBotAction(ctx) {
                            @Override
                            protected void doRun(Event event, EventListener executingListener) {
                                if (event instanceof CreateAtomCommandResultEvent) {
                                    if (event instanceof CreateAtomCommandSuccessEvent) {
                                        logger.info("Created Atom: {}", ((CreateAtomCommandSuccessEvent) event).getAtomURI());
                                        return;
                                    } else if (event instanceof CreateAtomCommandFailureEvent) {
                                        logger.error("Failed to create atom with original URI: {}, event={}", ((CreateAtomCommandFailureEvent) event).getAtomUriBeforeCreation(), event);
                                        EventBotActionUtils.removeFromList(ctx, atomURI, uriListName);
                                        return; // TODO throw exception?
                                    }
                                }
                                throw new IllegalStateException("Could not handle CreateAtomCommandResultEvent");
                            }
                        }));

        logger.info("publishing atom create command with atomURI {} to wonNode {}", atomURI, wonNodeURI);
        ctx.getEventBus().publish(createCommand);
    }
}
