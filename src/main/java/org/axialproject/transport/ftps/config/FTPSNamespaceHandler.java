package org.axialproject.transport.ftps.config;

import org.axialproject.transport.ftps.FtpsConnector;
import org.mule.config.spring.handlers.AbstractMuleNamespaceHandler;
import org.mule.config.spring.parsers.generic.ChildDefinitionParser;
import org.mule.endpoint.URIBuilder;
import org.mule.transport.file.ExpressionFilenameParser;
import org.mule.transport.file.FilenameParser;


public class FtpsNamespaceHandler extends AbstractMuleNamespaceHandler {
    public void init() {
        registerStandardTransportEndpoints(FtpsConnector.FTPS, URIBuilder.SOCKET_ATTRIBUTES);
        registerConnectorDefinitionParser(FtpsConnector.class);

        registerBeanDefinitionParser("custom-filename-parser", new ChildDefinitionParser("filenameParser", null, FilenameParser.class));
        registerBeanDefinitionParser("expression-filename-parser", new ChildDefinitionParser("filenameParser", ExpressionFilenameParser.class));
    }
}
