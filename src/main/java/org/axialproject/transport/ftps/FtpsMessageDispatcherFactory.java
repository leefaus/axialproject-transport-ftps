package org.axialproject.transport.ftps;

import org.mule.api.MuleException;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.transport.MessageDispatcher;
import org.mule.transport.AbstractMessageDispatcherFactory;

/**
 * @author: lfaus
 * Create Date: 5/20/11
 * @version: @TODO Add Version Information
 * @TODO Add Java Doc Information
 */
public class FtpsMessageDispatcherFactory extends AbstractMessageDispatcherFactory {
    private FtpsMessageDispatcher dispatcher;

    public MessageDispatcher create(OutboundEndpoint endpoint) throws MuleException {
        dispatcher = new FtpsMessageDispatcher(endpoint);
        return dispatcher;
    }

    public boolean isCreateDispatcherPerRequest() {
        return false;
    }

}
