package com.saic.uicds.clients.rm;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.oasisOpen.docs.wsn.b2.NotificationMessageHolderType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.XmlMappingException;
import org.springframework.ws.client.WebServiceClientException;
import org.uicds.broadcastService.BroadcastMessageRequestDocument;
import org.uicds.broadcastService.BroadcastMessageResponseDocument;
import org.uicds.notificationService.GetMessagesRequestDocument;
import org.uicds.notificationService.GetMessagesResponseDocument;
import org.uicds.resourceManagementService.EdxlDeRequestDocument;
import org.uicds.resourceManagementService.EdxlDeRequestDocument.EdxlDeRequest;
import org.uicds.resourceManagementService.EdxlDeResponseDocument;
import org.uicds.workProductService.GetProductRequestDocument;
import org.uicds.workProductService.GetProductResponseDocument;
import org.w3.x2005.x08.addressing.EndpointReferenceType;

import x0.oasisNamesTcEmergencyEDXLDE1.AnyXMLType;
import x0.oasisNamesTcEmergencyEDXLDE1.ContentObjectType;
import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument;
import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;
import x0.oasisNamesTcEmergencyEDXLDE1.StatusValues;
import x0.oasisNamesTcEmergencyEDXLDE1.TypeValues;
import x0.oasisNamesTcEmergencyEDXLDE1.ValueSchemeType;
import x0Msg.oasisNamesTcEmergencyEDXLRM1.CommitResourceDocument;

import com.saic.precis.x2009.x06.base.IdentificationType;
import com.saic.precis.x2009.x06.base.IdentifierType;
import com.saic.precis.x2009.x06.base.ProcessingStateType;
import com.saic.precis.x2009.x06.base.ProcessingStatusType;
import com.saic.precis.x2009.x06.structures.WorkProductDocument.WorkProduct;
import com.saic.uicds.clients.em.async.UicdsApplicationInstance;
import com.saic.uicds.clients.em.async.UicdsCore;
import com.saic.uicds.clients.em.async.WorkProductListener;
import com.saic.uicds.clients.util.WebServiceClient;

/**
 * Implemenation of the UicdsCore {@link UicdsCore}
 * 
 * @author roger
 */
public class UicdsCoreRMImpl
    implements UicdsCore {

    private Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String WORKPRODUCTSERVICE_NS = "http://uicds.org/WorkProductService";
    private static final String PRECISS_NS = "http://www.saic.com/precis/2009/06/structures";
    private static final String WORK_PRODUCT_PUBLICATION_RESPONSE = "WorkProductPublicationResponse";
    private static final String WORK_PRODUCT_PROCESSING_STATUS = "WorkProductProcessingStatus";

    final String UICDS_EXPLICIT_ADDRESS_SCHEME = "uicds:user";

    private StringBuffer processingStatusXPath;

    private WebServiceClient webServiceClient;

    private HashMap<String, ProcessingStatusType> pendingRequestMap = new HashMap<String, ProcessingStatusType>();

    private ArrayList<WorkProductListener> listenerList = new ArrayList<WorkProductListener>();

    private Collection<String> applicationProfileInterests = new HashSet<String>();

    private String applicationID;
    private String applicationProfileID;
    private String localID;
    private EndpointReferenceType applicationEndpoint;
    private UicdsApplicationInstance applicationInstance;

    public EndpointReferenceType getApplicationEndpoint() {

        return applicationEndpoint;
    }

    @Override
    public void setApplicationID(String ID) {

        applicationID = ID;
    }

    @Override
    public void setLocalID(String ID) {

        localID = ID;
    }

    private CommitResourceDocument commitResourceDocument;

    public void setCommitResourceDocument(CommitResourceDocument commitResourceDocument) {

        this.commitResourceDocument = commitResourceDocument;
    }

    public CommitResourceDocument getCommitResourceDocument() {

        return this.commitResourceDocument;
    }

    /*
     * (non-Javadoc)
     * @see com.saic.uicds.clients.async.UicdsCore#setApplicationProfileID(java.lang.String)
     */
    @Override
    public void setApplicationProfileID(String id) {

        applicationProfileID = id;
    }

    /*
     * (non-Javadoc)
     * @seecom.saic.uicds.clients.async.UicdsCore#setWebServiceClient(com.saic.uicds.clients.util.
     * WebServiceClient)
     */
    @Override
    public void setWebServiceClient(WebServiceClient client) {

        webServiceClient = client;
    }

    /*
     * (non-Javadoc) Initialize the UicdsProfile and get the endpoint.
     * @see com.saic.uicds.clients.async.UicdsCore#initialize()
     */
    @Override
    public Boolean initialize() {

        // Register the application with the UICDS Core
        applicationInstance = new UicdsApplicationInstance();
        applicationInstance.setWebServiceClient(webServiceClient);
        if (!applicationInstance.registerApplication(applicationID, localID, applicationProfileID,
            applicationProfileInterests)) {
            return false;
        }

        // Get the endpoint for polling for notification messages
        applicationEndpoint = applicationInstance.getEndpoint();

        // Create an XPath expression to get to the processing status in a notification
        processingStatusXPath = new StringBuffer();
        processingStatusXPath.append("declare namespace ws='");
        processingStatusXPath.append(WORKPRODUCTSERVICE_NS);
        processingStatusXPath.append("' declare namespace s='");
        processingStatusXPath.append(PRECISS_NS);
        processingStatusXPath.append("'  /*/ws:");
        processingStatusXPath.append(WORK_PRODUCT_PUBLICATION_RESPONSE);
        processingStatusXPath.append("/s:");
        processingStatusXPath.append(WORK_PRODUCT_PROCESSING_STATUS);
        return true;
    }
    
    @Override
    public Boolean shutdown() {
    	return new Boolean(true);
    };

    /*
     * (non-Javadoc)
     * @see
     * com.saic.uicds.clients.async.UicdsCore#marshalSendAndReceive(org.apache.xmlbeans.XmlObject)
     */
    @Override
    public XmlObject marshalSendAndReceive(XmlObject request) {

        try {
            XmlObject response = webServiceClient.sendRequest(request);
            XmlObject[] statusArray = response.selectPath(processingStatusXPath.toString());
            if (statusArray != null && statusArray.length > 0) {
                if (statusArray[0] instanceof ProcessingStatusType) {
                    if (((ProcessingStatusType) statusArray[0]).getStatus() == ProcessingStateType.PENDING) {
                        // Make a copy of the status to store
                        ProcessingStatusType status = (ProcessingStatusType) ((ProcessingStatusType) statusArray[0]).copy();
                        pendingRequestMap.put(
                            ((ProcessingStatusType) statusArray[0]).getACT().getStringValue(),
                            status);
                    }
                }
            }

            return response;
        } catch (XmlMappingException e) {
            log.error("Exception processing a sendEdxlDeRequest: " + e.getMessage());
        } catch (WebServiceClientException e) {
            log.error("Exception processing a sendEdxlDeRequest: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception caught while processing a sendEdxlDeRequest: " + e.getMessage());
        }

        return null;
    }

    public EdxlDeResponseDocument sendEdxlDeRequest(EdxlDeRequestDocument request) {

        try {
            EdxlDeResponseDocument response = (EdxlDeResponseDocument) webServiceClient.sendRequest(request);
            return response;
        } catch (XmlMappingException e) {
            log.error("Exception processing a sendEdxlDeRequest: " + e.getMessage());
        } catch (WebServiceClientException e) {
            log.error("Exception processing a sendEdxlDeRequest: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception caught while processing a sendEdxlDeRequest: " + e.getMessage());
        }
        return null;
    }

    public BroadcastMessageResponseDocument sendBroadcastMessage(
        BroadcastMessageRequestDocument request) {

        BroadcastMessageResponseDocument response;
        try {
            response = (BroadcastMessageResponseDocument) webServiceClient.sendRequest(request);
            return response;
        } catch (XmlMappingException e) {
            log.error("Exception processing a sendEdxlDeRequest: " + e.getMessage());
        } catch (WebServiceClientException e) {
            log.error("Exception processing a sendEdxlDeRequest: " + e.getMessage());
        } catch (Exception e) {
            log.error("Exception caught while processing a sendEdxlDeRequest: " + e.getMessage());
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * @seecom.saic.uicds.clients.async.UicdsCore#getRequestStatus(com.saic.precis.x2009.x06.base.
     * IdentifierType)
     */
    @Override
    public ProcessingStatusType getRequestStatus(IdentifierType incidentUpdateACT) {

        if (pendingRequestMap.containsKey(incidentUpdateACT.getStringValue())) {
            return pendingRequestMap.get(incidentUpdateACT.getStringValue());
        } else {
            return null;
        }
    }

    public void processNotifications() {

        // Create the request to get messages for the application
        GetMessagesRequestDocument request = GetMessagesRequestDocument.Factory.newInstance();
        BigInteger max = BigInteger.ONE;
        request.addNewGetMessagesRequest().setMaximumNumber(max);
        XmlCursor xc = request.getGetMessagesRequest().newCursor();
        xc.toLastChild();
        xc.toEndToken();
        xc.toNextToken();
        QName to = new QName("http://www.w3.org/2005/08/addressing", "To");
        xc.insertElementWithText(to, applicationEndpoint.getAddress().getStringValue());
        xc.dispose();

        // Send the request
        XmlObject response = XmlObject.Factory.newInstance();
        try {
            response = webServiceClient.sendRequest(request);
        } catch (XmlMappingException e) {
            log.error("Exception processing a request to the core while processing notifications: " +
                e.getMessage());
        } catch (WebServiceClientException e) {
            log.error("Exception processing a request to the core while processing notifications: " +
                e.getMessage());
        }
        if (response instanceof GetMessagesResponseDocument) {
            processResourceNotificationMessages((GetMessagesResponseDocument) response);
        }
    }

    /**
     * process the GetMessagesResponseDocument
     * 
     * @param response
     */
    private void processResourceNotificationMessages(GetMessagesResponseDocument response) {

        System.out.println("processResourceNotificationMessages...");

        // If there are any notifications
        if (response.getGetMessagesResponse().sizeOfNotificationMessageArray() > 0) {
            System.out.println("Received notification");
            // Process each one
            for (NotificationMessageHolderType message : response.getGetMessagesResponse().getNotificationMessageArray()) {
                System.out.println("Processing notification message");

                XmlObject[] xmlObjectArray = message.getMessage().selectChildren(
                    "urn:oasis:names:tc:emergency:EDXL:DE:1.0", "EDXLDistribution");

                for (XmlObject xmlObject : xmlObjectArray) {
                    EDXLDistributionDocument doc = EDXLDistributionDocument.Factory.newInstance();
                    doc.addNewEDXLDistribution().set(xmlObject);

                    if (doc.getEDXLDistribution().sizeOfContentObjectArray() > 0 &&
                        doc.getEDXLDistribution().getContentObjectArray(0).getXmlContent() != null &&
                        doc.getEDXLDistribution().getContentObjectArray(0).getXmlContent().sizeOfEmbeddedXMLContentArray() > 0) {
                        AnyXMLType xmlContent = doc.getEDXLDistribution().getContentObjectArray(0).getXmlContent().getEmbeddedXMLContentArray(
                            0);
                        XmlObject[] contentObjectArray = xmlContent.selectChildren(
                            "urn:oasis:names:tc:emergency:EDXL:RM:1.0:msg", "RequestResource");

                        if (contentObjectArray.length > 0) {
                            System.out.println("\nRECEIVED EDXL Distribution=\n" + doc.toString() +
                                "\n");
                            processRequestResource(doc);
                            continue;
                        }

                        contentObjectArray = xmlContent.selectChildren(
                            "urn:oasis:names:tc:emergency:EDXL:HAVE:1.0", "HospitalStatus");
                        if (contentObjectArray.length > 0) {
                            System.out.println("\nRECEIVED EDXL-HAVE=\n" + doc.xmlText() + "\n");
                            continue;
                        }

                        System.err.println("\nUNKNOWN CONTENT in EDXL-DE\n");
                    } else {
                        System.err.println("\nNO CONTENT in EDXL-DE message");
                    }

                }
            }
        }
    }

    private void processRequestResource(EDXLDistributionDocument doc) {

        String originalSenderId = doc.getEDXLDistribution().getSenderID();
        String originalReceiverId = null;
        ValueSchemeType[] explicitAddressArray = doc.getEDXLDistribution().getExplicitAddressArray();
        for (ValueSchemeType explicitAddress : explicitAddressArray) {
            if (!(explicitAddress.getExplicitAddressValueArray(0).equals(originalSenderId))) {
                originalReceiverId = explicitAddress.getExplicitAddressValueArray(0);
            }
        }

        // swap sender and receiver
        String receiverId = originalSenderId;
        String senderId = originalReceiverId;
        String incidentId = doc.getEDXLDistribution().getContentObjectArray(0).getIncidentID();

        CommitResourceDocument commitDoc = getCommitResourceDocument();
        if (commitDoc.getCommitResource().sizeOfIncidentInformationArray() > 0) {
            commitDoc.getCommitResource().getIncidentInformationArray(0).setIncidentID(incidentId);
        } else {
            commitDoc.getCommitResource().addNewIncidentInformation().setIncidentID(incidentId);
        }

        EdxlDeRequestDocument edxlDeRequestDocument = getEdxlDeRequest(senderId, receiverId,
            incidentId, commitDoc);

        System.out.println("\nSENDING edxlDeRequestDocument=\n" + edxlDeRequestDocument.toString() +
            "\n");
        EdxlDeResponseDocument edxlDeResponseDocument = sendEdxlDeRequest(edxlDeRequestDocument);
        if (edxlDeResponseDocument != null) {
            System.out.println("\nRESPONSE edxlDeResponseDocument=\n" +
                edxlDeResponseDocument.toString() + "\n");
        } else {
            System.err.println("\nNULL RESPONSE from sending Commit Resource");
        }

    }

    public EdxlDeRequestDocument getEdxlDeRequest(String senderId, String receiverId,
        String incidentId, XmlObject xmlObject) {

        EDXLDistribution edxl = getEdxlDistribution(senderId, receiverId, incidentId, xmlObject);

        EdxlDeRequestDocument edxlDeRequestDocument = EdxlDeRequestDocument.Factory.newInstance();
        EdxlDeRequest edxlDeRequest = edxlDeRequestDocument.addNewEdxlDeRequest();
        edxlDeRequest.setEDXLDistribution(edxl);

        return edxlDeRequestDocument;
    }

    public EDXLDistribution getEdxlDistribution(String senderId, String receiverId,
        String incidentId, XmlObject xmlObject) {

        EDXLDistribution edxl = EDXLDistribution.Factory.newInstance();
        edxl.setDistributionID(UUID.randomUUID().toString());
        edxl.setSenderID(senderId);
        edxl.setDateTimeSent(Calendar.getInstance());
        edxl.setDistributionStatus(StatusValues.TEST);
        edxl.setDistributionType(TypeValues.UPDATE);
        edxl.setCombinedConfidentiality("UNCLASSIFIED AND NOT SENSITIVE");
        ContentObjectType content = ContentObjectType.Factory.newInstance();
        content.setIncidentID(incidentId);
        try {
            content.addNewXmlContent().addNewEmbeddedXMLContent().set(xmlObject);
        } catch (Exception e) {
            System.out.println("the xml product content is not a valid xml document: " +
                e.getMessage());
        }
        if (content != null) {
            edxl.addNewContentObject().set(content);
        }

        // indicate the uicdsId of the receiving application
        ValueSchemeType explictAddressUicdsId2 = edxl.addNewExplicitAddress();
        explictAddressUicdsId2.setExplicitAddressScheme(UICDS_EXPLICIT_ADDRESS_SCHEME);
        explictAddressUicdsId2.addExplicitAddressValue(receiverId);

        return edxl;
    }

    /*
     * (non-Javadoc)
     * @seecom.saic.uicds.clients.async.UicdsCore#requestCompleted(com.saic.precis.x2009.x06.base.
     * IdentifierType)
     */
    @Override
    public boolean requestCompleted(IdentifierType act) {

        // TODO Auto-generated method stub
        return false;
    }

    /*
     * (non-Javadoc)
     * @see
     * com.saic.uicds.clients.async.WorkProductProducer#registerListener(com.saic.uicds.clients.
     * async.WorkProductListener)
     */
    @Override
    public void registerListener(WorkProductListener listener) {

        listenerList.add(listener);
    }

    /*
     * (non-Javadoc)
     * @see
     * com.saic.uicds.clients.async.WorkProductProducer#unregisterListener(com.saic.uicds.clients
     * .async.WorkProductListener)
     */
    @Override
    public void unregisterListener(WorkProductListener listener) {

        listenerList.remove(listener);
    }

    @Override
    public WorkProduct getWorkProductFromCore(IdentificationType workProductID) {

        GetProductRequestDocument request = GetProductRequestDocument.Factory.newInstance();
        request.addNewGetProductRequest().setWorkProductIdentification(workProductID);
        try {
            GetProductResponseDocument response = (GetProductResponseDocument) marshalSendAndReceive(request);
            return response.getGetProductResponse().getWorkProduct();
        } catch (ClassCastException e) {
            log.error("Error casting response to GetProductResponseDocument");
            return null;
        }
    }

    @Override
    public void setApplicationProfileInterests(Collection<String> interests) {

        applicationProfileInterests.addAll(interests);
    }

    @Override
    public String getApplicationID() {

        return applicationID;
    }

    @Override
    public String getFullResourceInstanceID() {

        return applicationInstance.getFullResourceInstanceID();
    }

	@Override
	public WebServiceClient getWebServiceClient() {
		return webServiceClient;
	}

}
