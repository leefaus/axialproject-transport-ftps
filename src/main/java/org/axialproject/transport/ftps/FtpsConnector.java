package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;
import org.apache.log4j.Logger;
import org.mule.api.*;
import org.mule.api.config.ThreadingProfile;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.DispatchException;
import org.mule.config.i18n.CoreMessages;
import org.mule.config.i18n.MessageFactory;
import org.mule.module.client.MuleClient;
import org.mule.transport.AbstractConnector;
import org.mule.transport.ConnectException;
import org.mule.transport.file.ExpressionFilenameParser;
import org.mule.transport.file.FilenameParser;
import org.mule.util.ClassUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    private Logger logger = Logger.getLogger(FtpsConnector.class);


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
        logger.info("** DISPOSE **");
    }

    @Override
    protected void doConnect() throws Exception {
        logger.info("** CONNECT **");

    }

    @Override
    protected void doDisconnect() throws Exception {
        logger.info("** DISCONNECT **");
    }

    @Override
    protected void doStart() throws MuleException {
        logger.info("** START **");
    }

    @Override
    protected void doStop() throws MuleException {
        logger.info("** STOP **");
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

    public OutputStream getOutputStream(OutboundEndpoint endpoint, MuleEvent event) throws MuleException {
        logger.info("=== getOutputStream ===");
        String filename = null;
        OutputStream out = null;

        try {
            FTPClient client = this.getFtp(endpoint.getEndpointURI());
            filename = getFilename(endpoint, event.getMessage());
            if (event.getMessage().getPayloadAsString().length() > 0) {
                final String messagePayload = event.getMessage().getPayloadForLogging();
                final ByteArrayInputStream stream = new ByteArrayInputStream(event.getMessage().getPayloadAsBytes());
                final MuleClient muleClient = new MuleClient(muleContext);

                try {
                    try {
                        client.upload(filename, stream, 0, 0, new FTPDataTransferListener() {
                            public void started() {
                                logger.info("== UPLOAD STARTED ==");
                            }

                            public void transferred(int i) {
                                logger.info("== UPLOAD TRANSFERRED ==");
                            }

                            public void completed() {
                                logger.info("== UPLOAD COMPLETED ==");
                            }

                            public void aborted() {
                                logger.warn("== UPLOAD ABORTED ==");
                                logger.warn("MESSAGE STRING = \n" + messagePayload);
                                try {
                                    muleClient.dispatch("ccc.exception.in", messagePayload, null);
                                } catch (MuleException e) {
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                }
                            }

                            public void failed() {
                                logger.warn("== UPLOAD FAILED ==");
                                logger.warn("MESSAGE STRING = \n" + messagePayload);
                                try {
                                    muleClient.dispatch("ccc.exception.in", messagePayload, null);
                                } catch (MuleException e) {
                                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                                }
                            }
                        });
                        stream.close();
                    } catch (Exception e) {
                        logger.debug("Error getting output stream: ", e);
                        muleClient.dispatch("ccc.exception.in", messagePayload, null);
                        throw e;
                    }
                    stream.close();
                } catch (ConnectException ce) {
                    // Don't wrap a ConnectException, otherwise the retry policy will not go into effect.
                    throw ce;
                } catch (Exception e) {
                    throw new DispatchException(CoreMessages.streamingFailedNoStream(), event, endpoint, e);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }
}
