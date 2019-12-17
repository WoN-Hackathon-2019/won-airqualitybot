# AirQualityBot
A [WebOfNeeds](https://github.com/researchstudio-sat/webofneeds)-Bot to regularly fetch air quality data from https://docs.openaq.org/ and publish them as WoN-atoms to a WoN-node.
Those atoms can then be used by others in order to get up-to-date air quality data for various locations in Austria.

The AirQualityBot is a [Spring Boot Application](https://docs.spring.io/spring-boot/docs/current/reference/html/using-boot-running-your-application.html).
It is based on the WoN [bot-skeleton](https://github.com/researchstudio-sat/bot-skeleton).


## Running the bot

### Prerequisites

- [Openjdk 8](https://adoptopenjdk.net/index.html) - (it is not guaranteed that the following instructions will also work with other JDKs)
- Maven framework set up

### On the command line

```
cd won-airqualitybot
export WON_NODE_URI="https://hackathonnode.matchat.org/won"
mvn clean package
java -jar target/bot.jar
```

### In Intellij Idea
1. Create a run configuration for the class `won.bot.airquality.AirQualityBotApp`
2. Add the environment variables

  * `WON_NODE_URI` pointing to your node uri (e.g. `https://hackathonnode.matchat.org/won` without quotes)
  
  to your run configuration.
  
3. Run your configuration

If you get a message indicating your keysize is restricted on startup (`JCE unlimited strength encryption policy is not enabled, WoN applications will not work. Please consult the setup guide.`), refer to [Enabling Unlimited Strength Jurisdiction Policy](https://github.com/open-eid/cdoc4j/wiki/Enabling-Unlimited-Strength-Jurisdiction-Policy) to increase the allowed key size.

##### Optional Parameters for both Run Configurations:
- `WON_KEYSTORE_DIR` path to folder where `bot-keys.jks` and `owner-trusted-certs.jks` are stored (needs write access and folder must exist) 


## Configuration
The configuration of this bot can be done in the `application.properties` file.

### Bot Name
The name under which the bot is registered at the WoN-Node can be altered with `bot.name`

### Source API
The parameters for the API from which the air-quality data is fetched is configured with `openaq.*`

### AQ-Bot Specifics
Specific properties to configure the behaviour of this bot can be altered with `aqbot.*` (in resources/application.properties):
With `aqbot.initialDelayMs`, you can specify after how many milliseconds the bot starts to generate atoms.
With `aqbot.updateIntervalMs`, you can specify after how many milliseconds the bot starts to fetch new data, generates new atoms and deletes the old ones.
With `aqbot.atom.uri.storage.directory`, you can set the path to the directory which holds the uris of the generated atoms.


## Team
[airqualitybot](https://github.com/orgs/WoN-Hackathon-2019/teams/airqualitybot)

Members:
* https://github.com/RobertObkircher
* https://github.com/LeazeDev
* https://github.com/maho5


## See also
[PollutionWarningBot](https://github.com/WoN-Hackathon-2019/won-pollutionwarningbot): a bot using atoms created by the AirQualityBot in order to warn subscribers whenever newly available data exceeds some limits.
