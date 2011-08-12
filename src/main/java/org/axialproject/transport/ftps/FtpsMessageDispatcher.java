package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import org.apache.log4j.Logger;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.transport.AbstractMessageDispatcher;
import org.mule.transport.NullPayload;

import java.io.OutputStream;

public class FtpsMessageDispatcher extends AbstractMessageDispatcher {
    Logger logger = Logger.getLogger(FtpsMessageDispatcher.class);
    protected final FtpsConnector connector;
    private FTPClient client;

    public FtpsMessageDispatcher(OutboundEndpoint endpoint) {
        super(endpoint);
        this.connector = (FtpsConnector) endpoint.getConnector();
        this.connector.setMaxDispatchersActive(1);
    }

    @Override
    protected void doDispatch(MuleEvent muleEvent) throws Exception {
        logger.info("=== doDispatch ===");
        Object data = muleEvent.getMessage().getPayload();
        OutputStream out = connector.getOutputStream((OutboundEndpoint) endpoint, muleEvent, client);
    }

    protected void doConnect() throws Exception {
        logger.info("=== doConnect ===");
        client = connector.createFtpClient(endpoint.getEndpointURI());
    }

    protected void doDisconnect() throws Exception {
        logger.info("=== doDisconnect ===");
        //client.logout();
        client.disconnect(true);
    }

    @Override
    protected MuleMessage doSend(MuleEvent muleEvent) throws Exception {
        logger.info("=== doSend ===");
        doDispatch(muleEvent);
        return new DefaultMuleMessage(NullPayload.getInstance(), connector.getMuleContext());
    }
}
