package info.macias.sse;

import com.republicate.json.Json;
import info.macias.sse.events.MessageEvent;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class Broadcast implements Serializable
{
    private static final long serialVersionUID = -1569302669699160751L;

    /**
     * <p>Broadcasts a {@link MessageEvent} to all the subscribers, containing only 'event' and 'data' fields.</p>
     *
     * <p>This method relies on the {@link EventTarget#send(MessageEvent)} method. If this method throws an
     * {@link IOException}, the broadcaster assumes the subscriber went offline and silently detaches it
     * from the collection of subscribers.</p>
     *
     * @param event The descriptor of the 'event' field.
     * @param data The content of the 'data' field.
     */
    public boolean broadcast(String event, String data)
    {
        return broadcast(new MessageEvent.Builder()
            .setEvent(event)
            .setData(data)
            .build());
    }

    public abstract boolean broadcast(MessageEvent event);

    public abstract void keepAlive();

    public abstract int size();

    public boolean isEmpty()
    {
        return size() == 0;
    }

    public void subscriberJoined(EventTarget target) {}

    public void subscriberLeft(EventTarget target) {}
}
