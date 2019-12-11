package won.bot.airquality.impl;

import lombok.Setter;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.tdb.TDB;
import org.apache.jena.vocabulary.DC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.action.UpdateAirQualityAction;
import won.bot.airquality.context.AirQualityBotContextWrapper;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.bot.airquality.external.OpenAqApi;
import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.action.impl.wonmessage.OpenConnectionAction;
import won.bot.framework.eventbot.behaviour.ExecuteWonMessageCommandBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandEvent;
import won.bot.framework.eventbot.event.impl.command.create.CreateAtomCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.ConnectFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.HintFromMatcherEvent;
import won.bot.framework.eventbot.filter.impl.CommandResultFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnEventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnFirstEventListener;
import won.bot.framework.extensions.matcher.MatcherBehaviour;
import won.bot.framework.extensions.matcher.MatcherExtension;
import won.bot.framework.extensions.matcher.MatcherExtensionAtomCreatedEvent;
import won.bot.framework.extensions.serviceatom.ServiceAtomBehaviour;
import won.bot.framework.extensions.serviceatom.ServiceAtomExtension;
import won.protocol.message.WonMessage;
import won.protocol.model.Coordinate;
import won.protocol.util.DefaultAtomModelWrapper;
import won.protocol.util.WonRdfUtils;
import won.protocol.util.linkeddata.LinkedDataSource;
import won.protocol.util.linkeddata.WonLinkedDataUtils;
import won.protocol.vocabulary.SCHEMA;
import won.protocol.vocabulary.WXCHAT;

import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

public class AirQualityBot extends EventBot implements MatcherExtension, ServiceAtomExtension {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private int registrationMatcherRetryInterval;
    private MatcherBehaviour matcherBehaviour;
    private ServiceAtomBehaviour serviceAtomBehaviour;

    @Setter
    private String openaqApiUrl;
    @Setter
    private int updateTime;
    @Setter
    private int publishTime;
    @Setter
    private boolean createAllInOne;

    // bean setter, used by spring
    public void setRegistrationMatcherRetryInterval(final int registrationMatcherRetryInterval) {
        this.registrationMatcherRetryInterval = registrationMatcherRetryInterval;
    }

    @Override
    public ServiceAtomBehaviour getServiceAtomBehaviour() {
        return serviceAtomBehaviour;
    }

    @Override
    public MatcherBehaviour getMatcherBehaviour() {
        return matcherBehaviour;
    }

    private void printRdfDatasetAndModel(Dataset dataset, Model model) {
        RDFDataMgr.write(System.out, dataset, Lang.TRIG);
        RDFDataMgr.write(System.out, model, Lang.TTL);
    }

    private void executeSparqlQuery(Dataset dataset) {
        String queryString = "select * where {?a ?b ?c}";
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, dataset)) {
            qexec.getContext().set(TDB.symUnionDefaultGraph, true); // use this unless you know why not to - it will execute the query over the union of all graphs. Makes things easier.
            ResultSet rs = qexec.execSelect();
            if (rs.hasNext()) {
                QuerySolution qs = rs.nextSolution();
                System.out.println("?a:" + qs.get("a") + ", ?b:" + qs.get("b") + ", ?c: " + qs.get("c"));
            }
        }
    }

    private void sendMessageAndProcessResult(EventListenerContext ctx, Dataset atomContent) {
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();

        CreateAtomCommandEvent createCommand = new CreateAtomCommandEvent(atomContent);
        //
        // ...(a) register result listener here, if needed (see (b) below)
        //
        getEventListenerContext().getEventBus().publish(createCommand);

        // (b) this registers a listener that is activated when the message has been successful
        // insert at position (a) above, if needed (because you want to register the listener before you publish the command)
        ctx.getEventBus().subscribe(
                CreateAtomCommandSuccessEvent.class,
                new ActionOnFirstEventListener( //note the 'onFIRSTevent' in the name: the listener is destroyed after being invoked once.
                        ctx,
                        new CommandResultFilter(createCommand),  // only listen for success to the command we just made
                        new BaseEventBotAction(ctx) {
                            @Override
                            protected void doRun(Event event, EventListener executingListener) {
                                //your action here
                            }
                        }));
    }

    private void connectToOtherAtom(URI senderSocketURI, URI recipientSocketURI) {
        String message = "Hello, let's connect!"; // optional welcome message
        ConnectCommandEvent connectCommandEvent = new ConnectCommandEvent(senderSocketURI, recipientSocketURI, message);
        getEventBus().publish(connectCommandEvent);
    }

    private void acceptConnectionFromOtherAtom(EventListenerContext ctx) {
        // this code accepts any incoming connection request
        getEventListenerContext().getEventBus().subscribe(
                ConnectFromOtherAtomEvent.class,
                new ActionOnEventListener(ctx, "open-reactor",
                        new OpenConnectionAction(ctx, "Accepting Connection")));
    }

    private void reactToHint(EventListenerContext ctx) {
        // this reacts with connect to any hint
        getEventListenerContext().getEventBus().subscribe(
                HintFromMatcherEvent.class,
                new ActionOnEventListener(ctx, "hint-reactor",
                        new OpenConnectionAction(ctx, "Connecting because of a Hint I got")));
    }

    private void obtainAtomSocket() {
        try {
            URI atomURI = new URI("");
            URI socketURI = new URI(WXCHAT.ChatSocket.getURI()); // or any other socket type you want
            LinkedDataSource linkedDataSource = getEventListenerContext().getLinkedDataSource();
            Collection<URI> sockets = WonLinkedDataUtils.getSocketsOfType(atomURI, socketURI, linkedDataSource);
            //sockets should have 0 or 1 items
            if (sockets.isEmpty()) {
                //did not find a socket of that type
            }
            for (URI socket : sockets) {
                // do sth
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    private void createAtom() {
        // Create a new atom URI
        EventListenerContext ctx = getEventListenerContext();
        URI wonNodeUri = ctx.getNodeURISource().getNodeURI();
        URI atomURI = ctx.getWonNodeInformationService().generateAtomURI(wonNodeUri);

        // Set atom data - here only shown for commonly used (hence 'default') properties
        DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        atomWrapper.setTitle("Interested in H.P. Lovecraft");
        atomWrapper.setDescription("Contact me for all things Cthulhu, Yogge-Sothothe and R'lyeh");
        atomWrapper.addTag("Fantasy");
        atomWrapper.addTag("Fiction");
        atomWrapper.addTag("Lovecraft");

        // publish command
        CreateAtomCommandEvent createCommand = new CreateAtomCommandEvent(atomWrapper.getDataset());
        ctx.getEventBus().publish(createCommand);
    }

    private void setNonStandardAtomData(URI atomURI) {
        // we assume you have created a new atom URI as 'atomURI'
        // DefaultAtomModelWrapper atomWrapper = new DefaultAtomModelWrapper(atomURI);
        // atomWrapper.createSeeksNodeIfNonExist(); // (sorry, this is ugly.)
        // Resource book = getSeeksNodes().get(0);  // get the blank node that represents a book
        // Resource author = book.getModel().createResource(); // create the second blank node (which represents an author)
        // //set the properties
        // book.addProperty(SCHEMA.ISBN, "1234-1234-1234");
        // book.addProperty(SCHEMA.AUTHOR, author);
        // author.addProperty(SCHEMA.NAME, "H.P. Lovecraft");
        // author.addProperty(RDF.type, SCHEMA.PERSON);
    }

    private void getAtomData(MatcherExtensionAtomCreatedEvent atomCreatedEvent) {
        DefaultAtomModelWrapper defaultAtomModelWrapper = new DefaultAtomModelWrapper(atomCreatedEvent.getAtomData());
        System.out.println(defaultAtomModelWrapper.getAllTags());
        defaultAtomModelWrapper.getSeeksNodes().forEach(node -> {
            System.out.println(defaultAtomModelWrapper.getContentPropertyStringValue(node, DC.description));
            Coordinate locationCoordinate = defaultAtomModelWrapper.getLocationCoordinate(node);
            System.out.println(locationCoordinate.getLatitude() + ", " + locationCoordinate.getLongitude());
            defaultAtomModelWrapper.getContentPropertyObjects(node, SCHEMA.LOCATION);
        });
    }

    private Dataset getAtomByURI(URI atomURI) {
        return getEventListenerContext().getLinkedDataSource().getDataForResource(atomURI);
    }

    private Dataset getMessageContent(WonMessage msg) {
        WonRdfUtils.MessageUtils.getTextMessage(msg);
        return msg.getMessageContent();
    }

    @Override
    protected void initializeEventListeners() {
        OpenAqApi openAqApi = new OpenAqApi(this.openaqApiUrl);
        fetchAndPrintData(openAqApi);

        EventListenerContext ctx = getEventListenerContext();
        if (!(getBotContextWrapper() instanceof AirQualityBotContextWrapper)) {
            logger.error(getBotContextWrapper().getBotName() + " does not work without a AirQualityBotContextWrapper");
            throw new IllegalStateException(
                    getBotContextWrapper().getBotName() + " does not work without a AirQualityBotContextWrapper");
        }

        EventBus bus = getEventBus();
        AirQualityBotContextWrapper botContextWrapper = (AirQualityBotContextWrapper) getBotContextWrapper();
        // register listeners for event.impl.command events used to tell the bot to send messages
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();
        // activate ServiceAtomBehaviour
        serviceAtomBehaviour = new ServiceAtomBehaviour(ctx);
        serviceAtomBehaviour.activate();

        // List<LocationMeasurements> measurements = openAqApi.fetchLatestMeasurements();
        // if (measurements == null || measurements.isEmpty()) {
        //     throw new IllegalStateException("No measurements found.");
        // }
        // new UpdateAirQualityAction(ctx, openAqApi).createAtomForLocationMeasurement(measurements.get(0));

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
