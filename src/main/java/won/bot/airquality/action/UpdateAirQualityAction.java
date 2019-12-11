package won.bot.airquality.action;


import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.context.AirQualityBotContextWrapper;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Measurement;
import won.bot.airquality.external.OpenAqApi;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractCreateAtomAction;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.atomlifecycle.AtomCreatedEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandFailureEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandResultEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.FailureResponseEvent;
import won.bot.framework.eventbot.filter.impl.CommandResultFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnFirstEventListener;
import won.protocol.message.WonMessage;
import won.protocol.service.WonNodeInformationService;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.util.RdfUtils;
import won.protocol.util.WonRdfUtils;
import won.protocol.vocabulary.SCHEMA;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.List;

public class UpdateAirQualityAction extends AbstractCreateAtomAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final OpenAqApi openAqApi;

    public UpdateAirQualityAction(EventListenerContext eventListenerContext, OpenAqApi openAqApi) {
        super(eventListenerContext);
        this.openAqApi = openAqApi;
    }

    @Override
    protected void doRun(Event event, EventListener eventListener) throws Exception {
        EventListenerContext ctx = getEventListenerContext();

        if (!(event instanceof ActEvent) || !(ctx.getBotContextWrapper() instanceof AirQualityBotContextWrapper)) {
            return;
        }

        List<LocationMeasurements> locations = this.openAqApi.fetchLatestMeasurements();

        logger.info("Creating atoms...");
        for (LocationMeasurements locationMeasurements : locations.subList(0, 3)) { // TODO use entire list if in productive use
            createAtomForLocationMeasurements(locationMeasurements);
        }
    }

    private void createAtomForLocationMeasurements(LocationMeasurements locationMeasurements) {
        // Create a new atom URI
        EventListenerContext ctx = getEventListenerContext();
        URI wonNodeURI = ctx.getNodeURISource().getNodeURI();
        URI atomURI = ctx.getWonNodeInformationService().generateAtomURI(wonNodeURI);

        Dataset atomDataset = generateLocationMeasurementsAtomStructure(atomURI, locationMeasurements);

        // publish command
        CreateAtomCommandEvent createCommand = new CreateAtomCommandEvent(atomDataset, "air_quality_uri_list_name");
        // System.out.println("-----------------------------------------------------------------");
        // System.out.println("-----------------------------------------------------------------");
        // System.out.println("uriListName with explicit set: " + createCommand.getUriListName());
        // createCommand = new CreateAtomCommandEvent(atomDataset);
        // System.out.println("uriListName with default uriListName: " + createCommand.getUriListName());
        // System.out.println("-----------------------------------------------------------------");
        // System.out.println("-----------------------------------------------------------------");

        // TODO actually use listeners to determine if the creation was successful or not
        // create listeners for events fired when the atom was created
        ctx.getEventBus().subscribe(
                CreateAtomCommandResultEvent.class,
                new ActionOnFirstEventListener( // the listener is destroyed after being invoked once.
                        ctx,
                        new CommandResultFilter(createCommand),  // only listen for success to the command we just published
                        new BaseEventBotAction(ctx) {
                            @Override
                            protected void doRun(Event event, EventListener executingListener) {
                                if(event instanceof CreateAtomCommandResultEvent) {
                                    if (event instanceof CreateAtomCommandSuccessEvent) {
                                        logger.info("Created Atom: {}", ((CreateAtomCommandSuccessEvent) event).getAtomURI());
                                        return;
                                    } else if (event instanceof CreateAtomCommandFailureEvent) {
                                        logger.error("Failed to create atom with original URI: {}", ((CreateAtomCommandFailureEvent) event).getAtomUriBeforeCreation());
                                        return; // TODO throw exception?
                                    }
                                }
                                throw new IllegalStateException("Could not handle CreateAtomCommandResultEvent");
                            }
                        }));

        logger.info("publishing atom create command with atomURI {} to wonNode {}", atomURI, wonNodeURI);
        ctx.getEventBus().publish(createCommand);
    }

    private Dataset generateLocationMeasurementsAtomStructure(URI atomURI, LocationMeasurements locationMeasurements) {
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle("Air Quality Data of Location " + locationMeasurements.getLocation());
        atomWrapper.setDescription(locationMeasurements.toString());
        atomWrapper.addTag("AirQualityData");
        atomWrapper.addTag("AirQualityBot");

        Resource locationNode = atomWrapper.createSeeksNode(null);  // create a blank node that represents a locationNode
        //set the properties
        locationNode.addProperty(SCHEMA.NAME, "location");
        locationNode.addProperty(SCHEMA.LOCATION, locationMeasurements.getLocation());

        for (Measurement m : locationMeasurements.getMeasurements()) {
            Resource measurementNode = locationNode.getModel().createResource(); // create the second blank node (which represents an measurementNode)
            measurementNode.addProperty(SCHEMA.NAME, "measurement");
            measurementNode.addProperty(SCHEMA.ABOUT, m.getParameter());
            measurementNode.addProperty(SCHEMA.VALUE, String.valueOf(m.getValue()));
        }

        return atomWrapper.getDataset();
    }

    // TODO reuse or remove
    private boolean createAtomFromLocation(EventListenerContext ctx, AirQualityBotContextWrapper botContextWrapper,
                                           LocationMeasurements locationMeasurements) {
        WonNodeInformationService wonNodeInformationService = ctx.getWonNodeInformationService();

        final URI wonNodeUri = ctx.getNodeURISource().getNodeURI();
        final URI atomURI = wonNodeInformationService.generateAtomURI(wonNodeUri);

        Dataset dataset = this.generateLocationMeasurementsAtomStructure(atomURI, locationMeasurements);
        logger.debug("creating atom on won node {} with content {} ", wonNodeUri,
                StringUtils.abbreviate(RdfUtils.toString(dataset), 150));

        WonMessage createAtomMessage = ctx.getWonMessageSender().prepareMessage(createWonMessage(atomURI, dataset));
        EventBotActionUtils.rememberInList(ctx, atomURI, uriListName);
//        botContextWrapper.addURIJobURLRelation(hokifyJob.getUrl(), atomURI);

        EventBus bus = ctx.getEventBus();
        EventListener successCallback = event -> {
            logger.debug("atom creation successful, new atom URI is {}", atomURI);
            bus.publish(new AtomCreatedEvent(atomURI, wonNodeUri, dataset, null));
        };
        EventListener failureCallback = event -> {
            String textMessage = WonRdfUtils.MessageUtils
                    .getTextMessage(((FailureResponseEvent) event).getFailureMessage());
            logger.error("atom creation failed for atom URI {}, original message URI {}: {}", new Object[]{
                    atomURI, ((FailureResponseEvent) event).getOriginalMessageURI(), textMessage});
            EventBotActionUtils.removeFromList(ctx, atomURI, uriListName);
//            botContextWrapper.removeURIJobURLRelation(atomURI);
        };
        EventBotActionUtils.makeAndSubscribeResponseListener(createAtomMessage, successCallback, failureCallback,
                ctx);
        logger.debug("registered listeners for response to message URI {}", createAtomMessage.getMessageURI());
        ctx.getWonMessageSender().sendMessage(createAtomMessage);
        logger.debug("atom creation message sent with message URI {}", createAtomMessage.getMessageURI());

        return true;
    }
}
