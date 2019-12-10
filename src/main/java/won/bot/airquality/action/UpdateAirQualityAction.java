package won.bot.airquality.action;


import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.context.AirQualityBotContextWrapper;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.external.OpenAqApi;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractCreateAtomAction;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.atomlifecycle.AtomCreatedEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.FailureResponseEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.message.WonMessage;
import won.protocol.service.WonNodeInformationService;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.util.RdfUtils;
import won.protocol.util.WonRdfUtils;
import won.protocol.vocabulary.SCHEMA;
import won.protocol.vocabulary.WONCON;

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

        logger.info("Fetching new data");
        List<LocationMeasurements> locations = openAqApi.fetchLatestMeasurements();
        logger.info("Fetched Measurements");

        AirQualityBotContextWrapper botContextWrapper = (AirQualityBotContextWrapper) ctx.getBotContextWrapper();
        try {
            logger.info("Create all job atoms");
            for (LocationMeasurements locationMeasurements : locations) {
                createAtomFromLocation(ctx, botContextWrapper, locationMeasurements);
            }
        } catch (Exception me) {
            // TODO copied from another bot; should we remove this?
            logger.error("messaging exception occurred:", me);
        }

        logger.info("Done creating atoms");
    }

    protected boolean createAtomFromLocation(EventListenerContext ctx, AirQualityBotContextWrapper botContextWrapper,
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

    private Dataset generateLocationMeasurementsAtomStructure(URI atomURI, LocationMeasurements locationMeasurements) {
        DefaultAtomModelWrapper atomModelWrapper = new DefaultAtomModelWrapper(atomURI);
        Resource atom = atomModelWrapper.getAtomModel().createResource(atomURI.toString());
        Resource seeksPart = atom.getModel().createResource();

        // s:url
        atom.addProperty(SCHEMA.URL, "");

        String[] tags = {"AirData"};
        for (String tag : tags) {
            atom.addProperty(WONCON.tag, tag);
        }

        return atomModelWrapper.copyDataset();
    }
}
