package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.mule.api.*;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.DispatchException;
import org.mule.config.i18n.CoreMessages;
import org.mule.config.i18n.MessageFactory;
import org.mule.model.streaming.CallbackOutputStream;
import org.mule.transport.AbstractConnector;
import org.mule.transport.ConnectException;
import org.mule.transport.file.ExpressionFilenameParser;
import org.mule.transport.file.FilenameParser;
import org.mule.util.ClassUtils;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

public class FtpsConnector extends AbstractConnector {
    public final static String FTPS = "ftps";
    public static final String PROPERTY_OUTPUT_PATTERN = "outputPattern"; // outbound only
    // message properties
    public static final String PROPERTY_FILENAME = "filename";
    public static final String DEFAULT_FTP_CONNECTION_FACTORY_CLASS = "org.axialproject.transport.ftps.FtpsConnectionFactory";
    private String connectionFactoryClass = DEFAULT_FTP_CONNECTION_FACTORY_CLASS;
    private FilenameParser filenameParser = new ExpressionFilenameParser();
    private Map<String, ObjectPool> pools;
    private String outputPattern;

    public FtpsConnector(MuleContext context) {
        super(context);
    }

    /**
     * Getter for property 'connectionFactoryClass'.
     *
     * @return Value for property 'connectionFactoryClass'.
     */
    public String getConnectionFactoryClass() {
        return connectionFactoryClass;
    }

    /**
     * Setter for property 'connectionFactoryClass'. Should be an instance of
     * {@link FtpsConnectionFactory}.
     *
     * @param connectionFactoryClass Value to set for property 'connectionFactoryClass'.
     */
    public void setConnectionFactoryClass(final String connectionFactoryClass) {
        this.connectionFactoryClass = connectionFactoryClass;
    }

    public FTPClient getFtp(EndpointURI uri) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(">>> retrieving client for " + uri);
        }
        return (FTPClient) getFtpPool(uri).borrowObject();
    }

    public void releaseFtp(EndpointURI uri, FTPClient client) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("<<< releasing client for " + uri);
        }
        if (dispatcherFactory.isCreateDispatcherPerRequest()) {
            destroyFtp(uri, client);
        } else {
            getFtpPool(uri).returnObject(client);
        }
    }

    public void destroyFtp(EndpointURI uri, FTPClient client) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("<<< destroying client for " + uri);
        }
        try {
            getFtpPool(uri).invalidateObject(client);
        } catch (Exception e) {
            // no way to test if pool is closed except try to access it
            logger.debug(e.getMessage());
        }
    }

    protected synchronized ObjectPool getFtpPool(EndpointURI uri) {
        if (logger.isDebugEnabled()) {
            logger.debug("=== get pool for " + uri);
        }
        String key = uri.getUser() + ":" + uri.getPassword() + "@" + uri.getHost() + ":" + uri.getPort();
        ObjectPool pool = pools.get(key);
        if (pool == null) {
            try {
                FtpsConnectionFactory connectionFactory =
                        (FtpsConnectionFactory) ClassUtils.instanciateClass(getConnectionFactoryClass(),
                                new Object[]{uri}, getClass());
                GenericObjectPool genericPool = createPool(connectionFactory);
                pools.put(key, genericPool);
                pool = genericPool;
            } catch (Exception ex) {
                throw new MuleRuntimeException(
                        MessageFactory.createStaticMessage("Hmm, couldn't instanciate FTP connection factory."), ex);
            }
        }
        return pool;
    }

    protected GenericObjectPool createPool(FtpsConnectionFactory connectionFactory) {
        GenericObjectPool genericPool = new GenericObjectPool(connectionFactory);
        byte poolExhaustedAction = ThreadingProfile.DEFAULT_POOL_EXHAUST_ACTION;

        ThreadingProfile receiverThreadingProfile = this.getReceiverThreadingProfile();
        if (receiverThreadingProfile != null) {
            int threadingProfilePoolExhaustedAction = receiverThreadingProfile.getPoolExhaustedAction();
            if (threadingProfilePoolExhaustedAction == ThreadingProfile.WHEN_EXHAUSTED_WAIT) {
                poolExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_BLOCK;
            } else if (threadingProfilePoolExhaustedAction == ThreadingProfile.WHEN_EXHAUSTED_ABORT) {
                poolExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_FAIL;
            } else if (threadingProfilePoolExhaustedAction == ThreadingProfile.WHEN_EXHAUSTED_RUN) {
                poolExhaustedAction = GenericObjectPool.WHEN_EXHAUSTED_GROW;
            }
        }

        genericPool.setWhenExhaustedAction(poolExhaustedAction);
        genericPool.setTestOnBorrow(isValidateConnections());
        return genericPool;
    }

    @Override
    protected void doInitialise() throws InitialisationException {
        if (filenameParser != null) {
            filenameParser.setMuleContext(muleContext);
        }

        try {
            Class objectFactoryClass = ClassUtils.loadClass(this.connectionFactoryClass, getClass());
            if (!FtpsConnectionFactory.class.isAssignableFrom(objectFactoryClass)) {
                throw new InitialisationException(MessageFactory.createStaticMessage(
                        "FTPS connectionFactoryClass is not an instance of org.axialproject.transport.ftps.FtpsConnectionFactory"),
                        this);
            }
        } catch (ClassNotFoundException e) {
            throw new InitialisationException(e, this);
        }

        pools = new HashMap<String, ObjectPool>();

    }

    @Override
    protected void doDispose() {
        // template method
    }

    @Override
    protected void doConnect() throws Exception {
        // template method
    }

    @Override
    protected void doDisconnect() throws Exception {
        // template method
    }

    @Override
    protected void doStart() throws MuleException {
        // template method
    }

    @Override
    protected void doStop() throws MuleException {
        // implement this
    }

    public String getProtocol() {
        return FTPS;
    }

    /**
     * @return Returns the outputPattern.
     */
    public String getOutputPattern() {
        return outputPattern;
    }

    /**
     * @param outputPattern The outputPattern to set.
     */
    public void setOutputPattern(String outputPattern) {
        this.outputPattern = outputPattern;
    }

    /**
     * @return Returns the filenameParser.
     */
    public FilenameParser getFilenameParser() {
        return filenameParser;
    }

    /**
     * @param filenameParser The filenameParser to set.
     */
    public void setFilenameParser(FilenameParser filenameParser) {
        this.filenameParser = filenameParser;
        if (filenameParser != null) {
            filenameParser.setMuleContext(muleContext);
        }
    }

    private String getFilename(ImmutableEndpoint endpoint, MuleMessage message) throws IOException {
        String filename = message.getOutboundProperty(FtpsConnector.PROPERTY_FILENAME);
        String outPattern = (String) endpoint.getProperty(FtpsConnector.PROPERTY_OUTPUT_PATTERN);
        if (outPattern == null) {
            outPattern = message.getOutboundProperty(FtpsConnector.PROPERTY_OUTPUT_PATTERN, getOutputPattern());
        }
        if (outPattern != null || filename == null) {
            filename = generateFilename(message, outPattern);
        }
        if (filename == null) {
            throw new IOException("Filename is null");
        }
        return filename;
    }

    private String generateFilename(MuleMessage message, String pattern) {
        if (pattern == null) {
            pattern = getOutputPattern();
        }
        return getFilenameParser().getFilename(message, pattern);
    }

    /**
     * Creates a new FTPClient that logs in and changes the working directory using the data
     * provided in <code>endpoint</code>.
     *
     * @param endpoint The endpoint of the XML configuration file
     * @return a new FTP client that supports FTPS
     * @throws Exception is generic to catch all potential exceptions
     */
    protected FTPClient createFtpClient(ImmutableEndpoint endpoint) throws Exception {
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
        EndpointURI uri = endpoint.getEndpointURI();
        FTPClient client = new FTPClient();
        logger.warn(">>>> endpoint = " + endpoint.getEndpointURI().getHost());
        client.setSSLSocketFactory(sslSocketFactory);
        client.setSecurity(FTPClient.SECURITY_FTPS);
        client.connect(endpoint.getEndpointURI().getHost());
        client.login(endpoint.getEndpointURI().getUser(), endpoint.getEndpointURI().getPassword());
        if (endpoint.getEndpointURI().getPath().equals("") || (endpoint.getEndpointURI().getPath() != null)) {
            client.changeDirectory(endpoint.getEndpointURI().getPath());
        }
        return client;
    }

    public OutputStream getOutputStream(OutboundEndpoint endpoint, MuleEvent event) throws MuleException {

        final EndpointURI uri = endpoint.getEndpointURI();
        String filename = null;
        OutputStream out = null;
        try {
            filename = getFilename(endpoint, event.getMessage());


            final FTPClient client;
            try {
                client = this.createFtpClient(endpoint);


                try {
                    client.upload(filename, new ByteArrayInputStream(event.getMessageAsBytes()), 0, 0, new FTPDataTransferListener() {
                        public void started() {
                            //To change body of implemented methods use File | Settings | File Templates.
                        }

                        public void transferred(int i) {
                            //To change body of implemented methods use File | Settings | File Templates.
                        }

                        public void completed() {
                            //To change body of implemented methods use File | Settings | File Templates.
                        }

                        public void aborted() {
                            //To change body of implemented methods use File | Settings | File Templates.
                        }

                        public void failed() {
                            //To change body of implemented methods use File | Settings | File Templates.
                        }
                    });

                    return new CallbackOutputStream(out,
                            new CallbackOutputStream.Callback() {
                                public void onClose() throws Exception {
                                    try {
                                        client.logout();
                                        client.disconnect(true);
                                        throw new IOException("FTP Stream failed to complete pending request");
                                    } finally {
                                        client.disconnect(true);
                                    }
                                }
                            });
                } catch (Exception e) {
                    logger.debug("Error getting output stream: ", e);
                    client.disconnect(true);
                    throw e;
                }
            } catch (ConnectException ce) {
                // Don't wrap a ConnectException, otherwise the retry policy will not go into effect.
                throw ce;
            } catch (Exception e) {
                throw new DispatchException(CoreMessages.streamingFailedNoStream(), event, endpoint, e);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return out;
    }

}
