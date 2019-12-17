package won.bot.airquality.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.bot.airquality.event.DeleteAtomEvent;
import won.bot.framework.eventbot.EventListenerContext;
import won.bot.framework.eventbot.action.EventBotActionUtils;
import won.bot.framework.eventbot.action.impl.atomlifecycle.AbstractDeleteAtomAction;
import won.bot.framework.eventbot.event.Event;
import won.bot.framework.eventbot.event.impl.wonmessage.FailureResponseEvent;
import won.bot.framework.eventbot.listener.EventListener;
import won.protocol.message.WonMessage;
import won.protocol.util.WonRdfUtils;

import java.lang.invoke.MethodHandles;
import java.net.URI;

public class DeleteAction extends AbstractDeleteAtomAction {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public DeleteAction(EventListenerContext eventListenerContext) {
        super(eventListenerContext);
    }

    public DeleteAction(EventListenerContext eventListenerContext, String uriListName) {
        super(eventListenerContext, uriListName);
    }

    @Override
    protected void doRun(Event event, EventListener eventListener) {
        DeleteAtomEvent deleteEvent = (DeleteAtomEvent) event;
        URI atomUri = deleteEvent.getAtomUri();

        EventListenerContext ctx = getEventListenerContext();
        logger.info("deleting atom with uri {} ", atomUri);

        WonMessage deleteAtomMessage = ctx.getWonMessageSender().prepareMessage(buildWonMessage(atomUri));

        // for some reason this callback is never called
        EventListener successCallback = e -> {
            logger.info("atom deletion successful, URI was {}", atomUri);
            throw new IllegalStateException("yeah we did it!!!");
        };
        EventBotActionUtils.removeFromList(ctx, atomUri, uriListName); // this should be inside the success callback

        EventListener failureCallback = e -> {
            FailureResponseEvent failureEvent = (FailureResponseEvent) e;
            String textMessage = WonRdfUtils.MessageUtils.getTextMessage(failureEvent.getFailureMessage());
            logger.error("atom deletion failed for atom URI {}, original message URI {}: {}", atomUri, failureEvent.getOriginalMessageURI(), textMessage);
        };

//        EventBotActionUtils.makeAndSubscribeResponseListener(deleteAtomMessage, successCallback, failureCallback, ctx);
        EventBotActionUtils.makeAndSubscribeResponseListener(deleteAtomMessage, successCallback, failureCallback, ctx);
        logger.debug("registered listeners for response to message URI {}", deleteAtomMessage.getMessageURI());

        ctx.getWonMessageSender().sendMessage(deleteAtomMessage);
        logger.debug("atom deletion message sent with message URI {}", deleteAtomMessage.getMessageURI());
    }
}
