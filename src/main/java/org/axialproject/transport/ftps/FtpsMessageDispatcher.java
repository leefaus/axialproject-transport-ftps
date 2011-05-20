package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import org.mule.DefaultMuleMessage;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.transport.AbstractMessageDispatcher;
import org.mule.transport.NullPayload;

import java.io.OutputStream;

public class FtpsMessageDispatcher extends AbstractMessageDispatcher {
    protected final FtpsConnector connector;

    public FtpsMessageDispatcher(OutboundEndpoint endpoint) {
        super(endpoint);
        this.connector = (FtpsConnector) endpoint.getConnector();
    }

    @Override
    protected void doDispatch(MuleEvent muleEvent) throws Exception {
        Object data = muleEvent.getMessage().getPayload();
        OutputStream out = connector.getOutputStream((OutboundEndpoint) endpoint, muleEvent);

//            if ((data instanceof InputStream) && (out != null)) {
//                InputStream is = ((InputStream) data);
//                IOUtils.copy(is, out);
//                is.close();
//            } else {
//                byte[] dataBytes;
//                if (data instanceof byte[]) {
//                    dataBytes = (byte[]) data;
//                } else {
//                    dataBytes = data.toString().getBytes(muleEvent.getEncoding());
//                }
//                IOUtils.write(dataBytes, out);
//            }
    }

    protected void doConnect() throws Exception {
        // template method
    }

    protected void doDisconnect() throws Exception {
        try {
            EndpointURI uri = endpoint.getEndpointURI();
            FTPClient client = connector.getFtp(uri);
            connector.destroyFtp(uri, client);
        } catch (Exception e) {
            // pool may be closed
        }
    }

    @Override
    protected MuleMessage doSend(MuleEvent muleEvent) throws Exception {
        doDispatch(muleEvent);
        return new DefaultMuleMessage(NullPayload.getInstance(), connector.getMuleContext());
    }
}
