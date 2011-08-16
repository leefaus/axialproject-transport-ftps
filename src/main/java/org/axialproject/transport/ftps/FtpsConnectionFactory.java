package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.log4j.Logger;
import org.mule.api.endpoint.EndpointURI;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class FtpsConnectionFactory implements PoolableObjectFactory {
    private Logger logger = Logger.getLogger(FtpsConnectionFactory.class);
    private EndpointURI uri;

    public FtpsConnectionFactory(EndpointURI uri) {
        this.uri = uri;
    }

    public Object makeObject() throws Exception {
        logger.info("== makeObject ==");
        return this.createFtpClient(uri);
    }

    public void destroyObject(Object obj) throws Exception {
        logger.info("== destroyObject ==");
        FTPClient client = (FTPClient) obj;
        //client.logout();
        client.disconnect(true);
    }

    public boolean validateObject(Object obj) {
        logger.info("== validateObject ==");
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

    /**
     * Creates a new FTPClient that logs in and changes the working directory using the data
     * provided in <code>endpoint</code>.
     *
     * @param endpoint The endpoint of the XML configuration file
     * @return a new FTP client that supports FTPS
     * @throws Exception is generic to catch all potential exceptions
     */
    protected FTPClient createFtpClient(EndpointURI endpoint) throws Exception {
        TrustManager[] trustManager = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustManager, new SecureRandom());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyManagementException e) {
            e.printStackTrace();
        }
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        FTPClient client = new FTPClient();
        client.setSSLSocketFactory(sslSocketFactory);
        client.setSecurity(FTPClient.SECURITY_FTPS);
        client.setPassive(true);
        client.connect(endpoint.getHost());
        client.login(endpoint.getUser(), endpoint.getPassword());
        if (endpoint.getPath().equals("") || (endpoint.getPath() != null)) {
            client.changeDirectory(endpoint.getPath());
        }
        return client;
    }
}
