package won.bot.airquality.context;

import won.bot.framework.bot.context.BotContext;
import won.bot.framework.extensions.serviceatom.ServiceAtomEnabledBotContextWrapper;

public class AirQualityBotContextWrapper extends ServiceAtomEnabledBotContextWrapper {

    public AirQualityBotContextWrapper(BotContext botContext, String botName) {
        super(botContext, botName);
    }
}
