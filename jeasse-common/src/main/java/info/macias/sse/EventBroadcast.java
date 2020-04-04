/*
Copyright 2016 - Mario Macias Lloret

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package info.macias.sse;

import info.macias.sse.events.MessageEvent;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * This class implements a one-to-many connection for broadcasting messages across multiple subscribers.
 *
 * @author <a href="http://github.com/mariomac">Mario Macías</a>
 */
public class EventBroadcast extends Broadcast
{
    protected Queue<EventTarget> targets = new ConcurrentLinkedQueue<>();
    private static final int MAX_HISTORY_SIZE = 10;
    protected SortedMap<String, MessageEvent> history = new ConcurrentSkipListMap(); // messages per id

	/**
	 * <p>Adds a subscriber from a <code>connectionRequest</code> that contains the information to allow sending back
	 * information to the subsbriber (e.g. an <code>HttpServletRequest</code> for servlets or <code>HttpServerRequest</code>
	 * for VertX)</p>
	 *
	 * @param eventTarget an event target to be subscribed to the broadcast messages
	 *
	 * @throws IOException if there was an error during the acknowledge process between broadcaster and subscriber
	 */
	public final void addSubscriber(EventTarget eventTarget) throws IOException {
		addSubscriber(eventTarget, null, null);
    }
    
	/**
	 * <p>Adds a subscriber to the broadcaster from a <code>connectionRequest</code> reference that contains the information to allow sending back
	 * information to the subsbriber (e.g. an <code>HttpServletRequest</code> for servlets or <code>HttpServerRequest</code>
	 * for VertX).</p>
	 *
	 *
	 *
	 * @param eventTarget an event target to be subscribed to the broadcast messages
	 * @param welcomeMessage The welcome message
	 * @throws IOException if there was an error during the acknowledge process between broadcaster and subscriber, or
	 *         if the subscriber immediately closed the connection before receiving the welcome message
	 */
	public final void addSubscriber(EventTarget eventTarget, MessageEvent welcomeMessage) throws IOException {
		addSubscriber(eventTarget, welcomeMessage, null);
    }

	/**
	 * <p>Adds a subscriber from a <code>connectionRequest</code> that contains the information to allow sending back
	 * information to the subsbriber (e.g. an <code>HttpServletRequest</code> for servlets or <code>HttpServerRequest</code>
     * plus a last-event_id field
	 * for VertX)</p>
	 *
	 * @param eventTarget an event target to be subscribed to the broadcast messages
     * @param lastEventId last received event id
	 * @throws IOException if there was an error during the acknowledge process between broadcaster and subscriber
	 */
	public final void addSubscriber(EventTarget eventTarget, String lastEventId) throws IOException {
		addSubscriber(eventTarget, null, lastEventId);
    }

	public void addSubscriber(EventTarget eventTarget, MessageEvent welcomeMessage, String lastEventId) throws IOException {
		targets.add(eventTarget);
		eventTarget.ok().open();
		if (welcomeMessage != null) eventTarget.send(welcomeMessage);
		if (lastEventId != null)
		{
			lastEventId = padLastEventId(lastEventId);
			if (lastEventId != null && lastEventId.length() > 0) {
				synchronized (history) { // to avoid sending twice a new event
					SortedMap<String, MessageEvent> missedEvents = history.tailMap(lastEventId);
					Iterator<Map.Entry<String, MessageEvent>> it = missedEvents.entrySet().iterator();
					if (it.hasNext()) {
						// skip first event if it's the same
						Map.Entry<String, MessageEvent> entry = it.next();
						if (entry.getKey().equals(lastEventId)) entry = it.next();
						while (it.hasNext()) eventTarget.send(it.next().getValue());
					}
				}
			}
		}
		subscriberJoined(eventTarget);
	}

    /**
     * Utility method to left-pad last-event-id with zeros when it's a numeric id
     */
    private static String padLastEventId(String lastEventId) {
        boolean isNumericId = false;
        int numericId = 0;
        if (lastEventId != null && lastEventId.length() > 0) {
            try {
                numericId = Integer.parseInt(lastEventId);
                isNumericId = true;
            }
            catch (NumberFormatException nfe) {}
        }
        if (isNumericId && numericId >= 0) {
            // left-pad with zeros to achieve numerical ordering
            lastEventId = String.format("%010d", numericId);
        }
        return lastEventId;
    }
        
	/**
	 * Get total count of subscribers. Actual number of active subscribers may be less that this.
	 * @return the size of the Set holding the subscribers
	 */
	public int getSubscriberCount() {
		return targets.size();
	}

	/**
	 * Get total count of subscribers. Actual number of active subscribers may be less that this.
	 * @return the size of the Set holding the subscribers
	 */
	public int size() {
		return targets.size();
	}

	/**
	 * Returns true if subscriber count is greater than zero
	 * @return true if subscriber count is greater than zero
	 */
	public boolean hasSubscribers() {
		return getSubscriberCount() > 0;
	}

	/**
	 * <p>Broadcasts a {@link MessageEvent} to the subscribers.</p>
	 *
	 * <p>This method relies on the {@link EventTarget#send(MessageEvent)} method. If this method throws an
	 * {@link IOException}, the broadcaster assumes the subscriber went offline and silently detaches it
	 * from the collection of subscribers.</p>
	 *
	 * @param messageEvent The instance that encapsulates all the desired fields for the {@link MessageEvent}
	 */
	public boolean broadcast(MessageEvent messageEvent)
	{
		boolean hasLiveDispatchers = false;
        for (Iterator<EventTarget> it = targets.iterator(); it.hasNext(); ) {
            EventTarget dispatcher = it.next();
            try
			{
                dispatcher.send(messageEvent);
                hasLiveDispatchers = true;
            }
            catch (IOException|IllegalStateException e)
			{
                // Client disconnected. Removing from targets
                it.remove();
                subscriberLeft(dispatcher);
            }
        }
        String id = messageEvent.getId();
        if (id != null && !history.containsKey(id)) {
			history.put(id, messageEvent);
		while (history.size() > MAX_HISTORY_SIZE) history.remove(history.firstKey());
	}
	return hasLiveDispatchers;
    }

	/**
	 * Closes all the connections between the broadcaster and the subscribers, and detaches all of them from the
	 * collection of subscribers.
	 */
	public void close() {
        for (EventTarget d : targets) {
            try {
                d.close();
            } catch (Exception e) {
                // Uncontrolled exception when closing a dispatcher. Removing anyway and ignoring.
            }
        }
        targets.clear();
        history.clear();
    }

	public void remove(String targetId)
	{
		for (Iterator<EventTarget> it = targets.iterator(); it.hasNext(); )
		{
			EventTarget dispatcher = it.next();
			if (targetId.equals(dispatcher.getID()))
			{
				targets.remove(targetId);
				break;
			}
		}
	}

	/**
	 * Debugging: the toString() returns the list of event targets ids
	 * @return
	 */
	@Override
	public String toString()
	{
		return targets.stream().map(eventTarget -> eventTarget.getID()).collect(Collectors.joining(":"));
	}

}
