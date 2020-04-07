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

package info.macias.sse.servlet3;

import info.macias.sse.EventBroadcast;
import info.macias.sse.EventTarget;
import info.macias.sse.events.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE dispatcher for one-to-one connections from Server to client-side subscriber
 *
 * @author <a href="http://github.com/mariomac">Mario Macías</a>
 */
public class ServletEventTarget implements EventTarget
{
    private static final long serialVersionUID = -7023267185323329595L;

    protected static Logger logger = LoggerFactory.getLogger("sse");

	private final AsyncContext asyncContext;
    private String id = null;
    private static AtomicInteger nextID = new AtomicInteger();

    private String lastMessageId = "";

    /**
     * Builds a new dispatcher from an {@link HttpServletRequest} object.
     * @param request The {@link HttpServletRequest} reference, as sent by the subscriber.
     */
    public ServletEventTarget(HttpServletRequest request) {
        asyncContext = request.startAsync();
        asyncContext.setTimeout(0);
        asyncContext.addListener(new AsyncListenerImpl());
        id = String.valueOf(nextID.incrementAndGet());
    }

    /**
     * If the connection is accepted, the server sends the 200 (OK) status message, plus the next HTTP headers:
     * <pre>
     *     Content-type: text/event-stream;charset=utf-8
     *     Cache-Control: no-cache
     *     Connection: keep-alive
     * </pre>
     * @return The same {@link ServletEventTarget} object that received the method call
     */
	@Override
    public ServletEventTarget ok() {
        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        response.setStatus(200);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control","no-cache");
        response.setHeader("Connection","keep-alive");
        return this;
    }

    /**
     * Responds to the client-side subscriber that the connection has been open
     *
     * @return The same {@link ServletEventTarget} object that received the method call
     * @throws IOException if there was an error writing into the response's {@link java.io.OutputStream}. This may be
     * a common exception: e.g. it will be thrown when the SSE subscriber closes the connection
     */
	@Override
    public ServletEventTarget open() throws IOException {
        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        response.getOutputStream().print("event: open\n\n");
        response.getOutputStream().flush();

        return this;
    }

    /**
     * Sends a {@link MessageEvent} to the subscriber, containing only 'event' and 'data' fields.
     * @param event The descriptor of the 'event' field.
     * @param data The content of the 'data' field.
     * @return The same {@link ServletEventTarget} object that received the method call
     * @throws IOException if there was an error writing into the response's {@link java.io.OutputStream}. This may be
     * a common exception: e.g. it will be thrown when the SSE subscriber closes the connection
     */
	@Override
    public ServletEventTarget send(String event, String data) throws IOException {
        logger.warn("#{} message without id: {}: {}", id, event, data);
        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
        response.getOutputStream().write(
                new MessageEvent.Builder()
                    .setData(data)
                    .setEvent(event)
                    .build()
                    .toString()
                    .getBytes("UTF-8")
        );
        response.getOutputStream().flush();
        return this;
    }

    /**
     * Sends a {@link MessageEvent} to the subscriber
     * @param messageEvent The instance that encapsulates all the desired fields for the {@link MessageEvent}
     * @return The same {@link ServletEventTarget} object that received the method call
     * @throws IOException if there was an error writing into the response's {@link java.io.OutputStream}. This may be
     * a common exception: e.g. it will be thrown when the SSE subscriber closes the connection
     */
	@Override
    public ServletEventTarget send(MessageEvent messageEvent) throws IOException {
		HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();
		String messageId = messageEvent.getId();
		if (messageId == null)
        {
            logger.warn("#{} message without id: {}: {}", id, messageEvent.getEvent(), messageEvent.getData());
        }
		else
        {
            this.lastMessageId = messageId;
        }
        response.getOutputStream().write(messageEvent.toString().getBytes("UTF-8"));
        response.getOutputStream().flush();
        return this;
    }

    @Override
    public void keepAlive() throws IOException
    {
        HttpServletResponse response = (HttpServletResponse)asyncContext.getResponse();

        /* keep-alive using comments */
        response.getOutputStream().write(":\n\n".getBytes("UTF-8"));
        response.getOutputStream().flush();

        /* keep-alive using message */
//        response.getOutputStream().write(
//            new MessageEvent.Builder()
//                .setData("")
//                .setEvent("noop")
//                .setId(lastMessageId)
//                .build()
//                .toString()
//                .getBytes("UTF-8")
//        );
//        response.getOutputStream().flush();

    }

    private boolean completed = false;

    /**
     * Closes the connection between the server and the client.
     */
	@Override
    public void close() {
        if(!completed) {
            completed = true;
            asyncContext.complete();
        }
    }

    @Override
    public String getID()
    {
        return id;
    }

    public AsyncContext getAsyncContext() {
        return asyncContext;
    }
    private class AsyncListenerImpl implements AsyncListener {

        @Override
        public void onComplete(AsyncEvent event) throws IOException {
            completed = true;
            // logger.trace("#{} event complete", id);
        }

        @Override
        public void onTimeout(AsyncEvent event) throws IOException {
            // logger.trace("[{}] event timeout", id);
        }

        @Override
        public void onError(AsyncEvent event) throws IOException {
            // logger.trace("[{}] event error: {}", id, event.getThrowable().getMessage());
        }
        @Override
        public void onStartAsync(AsyncEvent event) throws IOException {
            // logger.trace("[{}] event start", id);
        }

    }
}
