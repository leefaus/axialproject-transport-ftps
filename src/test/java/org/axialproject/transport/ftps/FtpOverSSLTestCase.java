package org.axialproject.transport.ftps;

import org.mule.api.MuleEvent;
import org.mule.construct.SimpleFlowConstruct;
import org.mule.tck.FunctionalTestCase;

/**
 * @author: lfaus
 * Create Date: 5/20/11
 * @version: @TODO Add Version Information
 * @TODO Add Java Doc Information
 */
public class FtpOverSSLTestCase extends FunctionalTestCase {

    public void testFileUpload() throws Exception {
        SimpleFlowConstruct flow = (SimpleFlowConstruct) muleContext.getRegistry().lookupFlowConstruct("ftpsFlow.out");
        String text = "Hello World, By Lee Faus";
        MuleEvent event = getTestEvent(text);
        MuleEvent responseEvent = flow.process(event);
        logger.warn(responseEvent.getMessage().getPayloadForLogging());
        assertNotNull(responseEvent);
    }

    @Override
    protected String getConfigResources() {
        return "ftps-mule-config.xml";
    }
}
