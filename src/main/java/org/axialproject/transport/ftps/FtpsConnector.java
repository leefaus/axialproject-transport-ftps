package org.axialproject.transport.ftps;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.mule.api.MuleContext;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.ImmutableEndpoint;
import org.mule.api.endpoint.OutboundEndpoint;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.api.transport.DispatchException;
import org.mule.config.i18n.CoreMessages;
import org.mule.module.client.MuleClient;
import org.mule.transport.AbstractConnector;
import org.mule.transport.ConnectException;
import org.mule.transport.file.ExpressionFilenameParser;
import org.mule.transport.file.FilenameParser;

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
import java.util.Map;

public class FtpsConnector extends AbstractConnector {
    public final static String FTPS = "ftps";
    public static final String PROPERTY_OUTPUT_PATTERN = "outputPattern"; // outbound only
    // message properties
    public static final String PROPERTY_FILENAME = "filename";
    private FilenameParser filenameParser = new ExpressionFilenameParser();
    private Map<String, ObjectPool> pools;
    private String outputPattern;
    private Logger logger = Logger.getLogger(FtpsConnector.class);


    public FtpsConnector(MuleContext context) {
        super(context);
    }

    @Override
    protected void doInitialise() throws InitialisationException {
        if (filenameParser != null) {
            filenameParser.setMuleContext(muleContext);
        }
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

    public OutputStream getOutputStream(OutboundEndpoint endpoint, MuleEvent event, FTPClient client) throws MuleException {
        logger.info("=== getOutputStream ===");
        String filename = null;
        OutputStream out = null;
        try {
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
