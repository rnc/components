/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */

package org.switchyard.component.soap;

import java.io.ByteArrayOutputStream;
import java.net.MalformedURLException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPMessage;

import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.switchyard.ServiceDomain;
import org.switchyard.common.net.SocketAddr;
import org.switchyard.component.soap.config.model.SOAPBindingModel;
import org.switchyard.component.soap.util.StreamUtil;
import org.switchyard.config.model.ModelPuller;
import org.switchyard.config.model.switchyard.SwitchYardModel;
import org.switchyard.config.model.transform.TransformModel;
import org.switchyard.extensions.wsdl.WSDLService;
import org.switchyard.test.SwitchYardRunner;
import org.switchyard.test.SwitchYardTestCaseConfig;
import org.switchyard.test.SwitchYardTestKit;
import org.switchyard.test.mixins.CDIMixIn;

@RunWith(SwitchYardRunner.class)
@SwitchYardTestCaseConfig(mixins = CDIMixIn.class)
public class JAXBGreetingServiceTest {

    private static final QName GREETING_SERVICE_NAME = new QName("JAXBGreetingService");

    private ServiceDomain _domain;

    private SOAPBindingModel config;
    
    private SwitchYardTestKit _testKit;

    @Before
    public void setUp() throws Exception {
        String host = System.getProperty("org.switchyard.test.soap.host", "localhost");
        String port = System.getProperty("org.switchyard.test.soap.port", "48080");
        
        config = new SOAPBindingModel();
        config.setPublishAsWS(true);
        config.setWsdl("src/test/resources/GreetingServiceImplService.wsdl");
        config.setServiceName(GREETING_SERVICE_NAME);
        config.setSocketAddr(new SocketAddr(host, Integer.parseInt(port)));
        
        registerTransformers();
    }

    @Test
    public void invokeRequestResponse() throws Exception {
        String soapRequest = "<gre:greet xmlns:gre=\"urn:switchyard-component-soap:test-greeting:1.0\">\n" +
                " <arg0>\n" +
                "    <person>\n" +
                "       <firstname>Mal</firstname>\n" +
                "       <lastname>Beck</lastname>\n" +
                "    </person>\n" +
                "    <time>2011-01-22T21:32:52</time>\n" +
                " </arg0>\n" +
                "</gre:greet>";
        String expectedResponse = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><gre:greetResponse xmlns:gre=\"urn:switchyard-component-soap:test-greeting:1.0\">\n" +
                "    <return>\n" +
                "        <greetingid>987789</greetingid>\n" +
                "        <person>\n" +
                "            <firstname>Mal</firstname>\n" +
                "            <lastname>Beck</lastname>\n" +
                "        </person>\n" +
                "    </return>\n" +
                "</gre:greetResponse></SOAP-ENV:Body></SOAP-ENV:Envelope>";

        test(soapRequest, expectedResponse, false);
    }

    @Test
    public void invokeRequestResponse_bad_soap_01() throws Exception {
        String soapRequest = "<gre:greet xmlns:gre=\"http://broken/unknown/namespace\" />";
        String expectedResponse = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode><faultstring>Invalid input SOAP payload namespace for service operation 'greet' (service 'JAXBGreetingService').  Port defines operation namespace as 'urn:switchyard-component-soap:test-greeting:1.0'.  Actual namespace on input SOAP message 'http://broken/unknown/namespace'.</faultstring></SOAP-ENV:Fault></SOAP-ENV:Body></SOAP-ENV:Envelope>";

        test(soapRequest, expectedResponse, false);
    }

    @Test
    public void invokeRequestResponse_bad_soap_02() throws Exception {
        String soapRequest = "<gre:xxxxx xmlns:gre=\"urn:switchyard-component-soap:test-greeting:1.0\" />";
        String expectedResponse = "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/><SOAP-ENV:Body><SOAP-ENV:Fault><faultcode>SOAP-ENV:Server</faultcode><faultstring>Operation for 'xxxxx' not available on target Service 'JAXBGreetingService'.</faultstring></SOAP-ENV:Fault></SOAP-ENV:Body></SOAP-ENV:Envelope>";

        test(soapRequest, expectedResponse, false);
    }

    public void test(String request, String expectedResponse, boolean dumpResponse) throws Exception {

        // Launch the SOAP Handler...
        _domain.registerServiceReference(GREETING_SERVICE_NAME, WSDLService.fromWSDL(
                "GreetingServiceImplService.wsdl#wsdl.porttype(GreetingServiceImpl)"));
        InboundHandler inboundHandler = new InboundHandler(config, _domain);
        inboundHandler.start();

        try {
            SOAPMessage soapRequest = StreamUtil.readSOAP(request);
            SOAPMessage soapResponse = inboundHandler.invoke(soapRequest);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            soapResponse.writeTo(baos);
            String actualResponse = new String(baos.toByteArray());

            if(dumpResponse) {
                System.out.println(actualResponse);
            }

//            Assert.assertEquals(expectedResponse, actualResponse);

            XMLUnit.setIgnoreWhitespace(true);
            XMLAssert.assertXMLEqual(expectedResponse, actualResponse);
        } finally {
            inboundHandler.stop();
        }
    }
    
    private void registerTransformers() throws Exception {
        SwitchYardModel config = new ModelPuller<SwitchYardModel>().pull("jaxb-transformers.xml");
        for (TransformModel tm : config.getTransforms().getTransforms()) {
            _testKit.registerTransformer(tm);
        }
    }
}
