package won.bot.airquality;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.PropertySource;
import won.bot.framework.bot.utils.BotUtils;

@SpringBootConfiguration
@PropertySource("classpath:application.properties")
@ImportResource("classpath:/spring/app/botApp.xml")
public class AirQualityBotApp {

    public static void main(String[] args) {
        if (!BotUtils.isValidRunConfig()) {
            System.exit(1);
        }
        SpringApplication app = new SpringApplication(AirQualityBotApp.class);
        app.setWebEnvironment(false);
        app.run(args);
        // ConfigurableApplicationContext applicationContext = app.run(args);
        // Thread.sleep(5*60*1000);
        // app.exit(applicationContext);
    }
}
