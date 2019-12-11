package won.bot.airquality.impl;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.action.MatcherExtensionAtomCreatedAction;
import won.bot.airquality.action.UpdateAirQualityAction;
import won.bot.airquality.context.AirQualityBotContextWrapper;
import won.bot.airquality.dto.LocationMeasurements;
import won.bot.airquality.dto.Parameter;
import won.bot.airquality.external.OpenAqApi;
import won.bot.framework.bot.base.EventBot;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.BaseEventBotAction;
import won.bot.framework.eventbot.behaviour.ExecuteWonMessageCommandBehaviour;
import won.bot.framework.eventbot.bus.EventBus;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandEvent;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandResultEvent;
import won.bot.framework.eventbot.event.impl.command.connect.ConnectCommandSuccessEvent;
import won.bot.framework.eventbot.event.impl.lifecycle.ActEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.CloseFromOtherAtomEvent;
import won.bot.framework.eventbot.event.impl.wonmessage.ConnectFromOtherAtomEvent;
import won.bot.framework.eventbot.filter.impl.AtomUriInNamedListFilter;
import won.bot.framework.eventbot.filter.impl.CommandResultFilter;
import won.bot.framework.eventbot.filter.impl.NotFilter;
import won.bot.framework.eventbot.listener.EventListener;
import won.bot.framework.eventbot.listener.impl.ActionOnFirstEventListener;
import won.bot.framework.extensions.matcher.MatcherBehaviour;
import won.bot.framework.extensions.matcher.MatcherExtension;
import won.bot.framework.extensions.matcher.MatcherExtensionAtomCreatedEvent;
import won.bot.framework.extensions.serviceatom.ServiceAtomBehaviour;
import won.bot.framework.extensions.serviceatom.ServiceAtomExtension;

import java.lang.invoke.MethodHandles;
import java.net.URI;
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

        // register listeners for event.impl.command events used to tell the bot to send
        // messages
        ExecuteWonMessageCommandBehaviour wonMessageCommandBehaviour = new ExecuteWonMessageCommandBehaviour(ctx);
        wonMessageCommandBehaviour.activate();

        // activate ServiceAtomBehaviour
        serviceAtomBehaviour = new ServiceAtomBehaviour(ctx);
        serviceAtomBehaviour.activate();

        // set up matching extension
        // as this is an extension, it can be activated and deactivated as needed
        // if activated, a MatcherExtensionAtomCreatedEvent is sent every time a new
        // atom is created on a monitored node
        matcherBehaviour = new MatcherBehaviour(ctx, "BotSkeletonMatchingExtension", registrationMatcherRetryInterval);
        matcherBehaviour.activate();

        // create filters to determine which atoms the bot should react to
        NotFilter noOwnAtoms = new NotFilter(
                new AtomUriInNamedListFilter(ctx, ctx.getBotContextWrapper().getAtomCreateListName()));
        // filter to prevent reacting to serviceAtom<->ownedAtom events;
        NotFilter noInternalServiceAtomEventFilter = getNoInternalServiceAtomEventFilter();
        bus.subscribe(ConnectFromOtherAtomEvent.class, noInternalServiceAtomEventFilter, new BaseEventBotAction(ctx) {
            @Override
            protected void doRun(Event event, EventListener executingListener) {
                EventListenerContext ctx = getEventListenerContext();
                ConnectFromOtherAtomEvent connectFromOtherAtomEvent = (ConnectFromOtherAtomEvent) event;
                try {
                    String message = "Hello i am the BotSkeletor i will send you a message everytime an atom is created...";
                    final ConnectCommandEvent connectCommandEvent = new ConnectCommandEvent(
                            connectFromOtherAtomEvent.getRecipientSocket(),
                            connectFromOtherAtomEvent.getSenderSocket(), message);
                    ctx.getEventBus().subscribe(ConnectCommandSuccessEvent.class, new ActionOnFirstEventListener(ctx,
                            new CommandResultFilter(connectCommandEvent), new BaseEventBotAction(ctx) {
                        @Override
                        protected void doRun(Event event, EventListener executingListener) {
                            ConnectCommandResultEvent connectionMessageCommandResultEvent = (ConnectCommandResultEvent) event;
                            if (!connectionMessageCommandResultEvent.isSuccess()) {
                                logger.error("Failure when trying to open a received Request: "
                                        + connectionMessageCommandResultEvent.getMessage());
                            } else {
                                logger.info(
                                        "Add an established connection " +
                                                connectCommandEvent.getLocalSocket()
                                                + " -> "
                                                + connectCommandEvent.getTargetSocket()
                                                +
                                                " to the botcontext ");
                                botContextWrapper.addConnectedSocket(
                                        connectCommandEvent.getLocalSocket(),
                                        connectCommandEvent.getTargetSocket());
                            }
                        }
                    }));
                    ctx.getEventBus().publish(connectCommandEvent);
                } catch (Exception te) {
                    logger.error(te.getMessage(), te);
                }
            }
        });

        // listen for the MatcherExtensionAtomCreatedEvent
        bus.subscribe(MatcherExtensionAtomCreatedEvent.class, new MatcherExtensionAtomCreatedAction(ctx));
        bus.subscribe(CloseFromOtherAtomEvent.class, new BaseEventBotAction(ctx) {
            @Override
            protected void doRun(Event event, EventListener executingListener) {
                EventListenerContext ctx = getEventListenerContext();
                CloseFromOtherAtomEvent closeFromOtherAtomEvent = (CloseFromOtherAtomEvent) event;
                URI targetSocketUri = closeFromOtherAtomEvent.getSocketURI();
                URI senderSocketUri = closeFromOtherAtomEvent.getTargetSocketURI();
                logger.info("Remove a closed connection " + senderSocketUri + " -> " + targetSocketUri
                        + " from the botcontext ");
                botContextWrapper.removeConnectedSocket(senderSocketUri, targetSocketUri);
            }
        });
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
