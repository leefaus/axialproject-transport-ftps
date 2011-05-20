package org.axialproject.transport.ftps;

import org.mule.api.MuleContext;
import org.mule.api.lifecycle.InitialisationException;

/**
 * Created by IntelliJ IDEA.
 * User: lfaus
 * Date: 5/19/11
 * Time: 2:54 PM
 * To change this template use File | Settings | File Templates.
 */
public class FTPSConnector {
    public final static String FTPS = "ftps";

    public FTPSConnector(MuleContext context)
    {
    }

    protected void doInitialise() throws InitialisationException
    {
    }

    public String getProtocol()
    {
        return FTPS;
    }

}
