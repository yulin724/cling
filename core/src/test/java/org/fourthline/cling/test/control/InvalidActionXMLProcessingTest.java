/*
 * Copyright (C) 2012 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.fourthline.cling.test.control;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.mock.MockUpnpService;
import org.fourthline.cling.mock.MockUpnpServiceConfiguration;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.StreamRequestMessage;
import org.fourthline.cling.model.message.UpnpRequest;
import org.fourthline.cling.model.message.control.IncomingActionRequestMessage;
import org.fourthline.cling.model.message.header.ContentTypeHeader;
import org.fourthline.cling.model.message.header.SoapActionHeader;
import org.fourthline.cling.model.message.header.UpnpHeader;
import org.fourthline.cling.model.meta.Action;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.types.SoapActionType;
import org.fourthline.cling.transport.impl.PullSOAPActionProcessorImpl;
import org.fourthline.cling.transport.impl.RecoveringSOAPActionProcessorImpl;
import org.fourthline.cling.transport.spi.SOAPActionProcessor;
import org.fourthline.cling.transport.spi.UnsupportedDataException;
import org.seamless.util.io.IO;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.URI;

import static org.testng.Assert.assertEquals;

/**
 * @author Christian Bauer
 */
public class InvalidActionXMLProcessingTest {

    @DataProvider(name = "invalidXMLFile")
    public String[][] getInvalidXMLFile() throws Exception {
        return new String[][]{
            {"/invalidxml/control/request_missing_envelope.xml"},
            {"/invalidxml/control/request_missing_action_namespace.xml"},
            {"/invalidxml/control/request_invalid_action_namespace.xml"},
        };
    }

    @DataProvider(name = "invalidRecoverableXMLFile")
    public String[][] getInvalidRecoverableXMLFile() throws Exception {
        return new String[][]{
            {"/invalidxml/control/request_no_entityencoding.xml"},
            {"/invalidxml/control/request_wrong_termination.xml"},
        };
    }

    @Test(dataProvider = "invalidXMLFile")
    public void readRequest(String invalidXMLFile) throws Exception {
        readRequest(
            invalidXMLFile,
            new MockUpnpService(new MockUpnpServiceConfiguration() {
                @Override
                public SOAPActionProcessor getSoapActionProcessor() {
                    // Tests should fail with this:
                    //return new SOAPActionProcessorImpl();

                    // But work with this:
                    return new PullSOAPActionProcessorImpl();
                }
            })
        );
    }

    @Test(dataProvider = "invalidRecoverableXMLFile")
    public void readRequestRecoverable(String invalidXMLFile) throws Exception {
        readRequest(
            invalidXMLFile,
            new MockUpnpService(new MockUpnpServiceConfiguration() {
                @Override
                public SOAPActionProcessor getSoapActionProcessor() {
                    // Tests should fail with this:
                    //return new PullSOAPActionProcessorImpl();

                    // But work with this:
                    return new RecoveringSOAPActionProcessorImpl();
                }
            })
        );
    }

    protected void readRequest(String invalidXMLFile, UpnpService upnpService) throws Exception {
        LocalDevice ld = ActionSampleData.createTestDevice(ActionSampleData.LocalTestServiceExtended.class);
        LocalService svc = ld.getServices()[0];

        Action action = svc.getAction("SetSomeValue");
        ActionInvocation actionInvocation = new ActionInvocation(action);

        StreamRequestMessage message = createRequestMessage(action, invalidXMLFile);
        IncomingActionRequestMessage request = new IncomingActionRequestMessage(message, svc);

        upnpService.getConfiguration().getSoapActionProcessor().readBody(request, actionInvocation);

        assertEquals(actionInvocation.getInput()[0].toString(), "foo&bar");
    }

    public StreamRequestMessage createRequestMessage(Action action, String xmlFile) throws Exception {
        StreamRequestMessage message =
            new StreamRequestMessage(UpnpRequest.Method.POST, URI.create("http://some.uri"));

        message.getHeaders().add(
            UpnpHeader.Type.CONTENT_TYPE,
            new ContentTypeHeader(ContentTypeHeader.DEFAULT_CONTENT_TYPE_UTF8)
        );
        message.getHeaders().add(
            UpnpHeader.Type.SOAPACTION,
            new SoapActionHeader(
                new SoapActionType(
                    action.getService().getServiceType(),
                    action.getName()
                )
            )
        );
        message.setBody(IO.readLines(getClass().getResourceAsStream(xmlFile)));
        return message;
    }
}
