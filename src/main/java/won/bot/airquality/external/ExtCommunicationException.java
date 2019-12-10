package won.bot.airquality.external;

public class ExtCommunicationException extends RuntimeException {

    public ExtCommunicationException() {
    }

    public ExtCommunicationException(String message) {
        super(message);
    }

    public ExtCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
