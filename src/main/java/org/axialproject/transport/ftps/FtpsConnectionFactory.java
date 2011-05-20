package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import org.apache.commons.pool.PoolableObjectFactory;
import org.mule.api.endpoint.EndpointURI;

import java.io.IOException;

public class FtpsConnectionFactory implements PoolableObjectFactory {
    private EndpointURI uri;

    public FtpsConnectionFactory(EndpointURI uri) {
        this.uri = uri;
    }

    public Object makeObject() throws Exception {
        FTPClient client = new FTPClient();
        client.setSecurity(FTPClient.SECURITY_FTPS);
        if (uri.getPort() > 0) {
            client.connect(uri.getHost(), uri.getPort());
        } else {
            client.connect(uri.getHost());
        }
        try {
            client.login(uri.getUser(), uri.getPassword());
        } catch (Exception e) {
            throw new Exception("Ftps login failed!");
        }
        return client;
    }

    public void destroyObject(Object obj) throws Exception {
        FTPClient client = (FTPClient) obj;
        client.logout();
        client.disconnect(true);
    }

    public boolean validateObject(Object obj) {
        FTPClient client = (FTPClient) obj;
        try {
            client.noop();
        } catch (IOException e) {
            return false;
        } catch (FTPException e) {
            e.printStackTrace();
            return false;
        } catch (FTPIllegalReplyException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public void activateObject(Object obj) throws Exception {
        // nothing to do
    }

    public void passivateObject(Object obj) throws Exception {
        // nothing to do
    }
}
