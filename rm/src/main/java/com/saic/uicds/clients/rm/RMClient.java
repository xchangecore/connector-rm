package com.saic.uicds.clients.rm;

import org.apache.xmlbeans.XmlObject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.uicds.broadcastService.BroadcastMessageRequestDocument;
import org.uicds.incident.IncidentDocument;
import org.uicds.incident.UICDSIncidentType;
import org.uicds.resourceManagementService.EdxlDeRequestDocument;
import org.uicds.resourceManagementService.EdxlDeResponseDocument;
import org.uicds.resourceProfileService.CreateProfileRequestDocument;
import org.uicds.resourceProfileService.ResourceProfile;
import org.w3c.dom.Document;

import x0.oasisNamesTcEmergencyEDXLDE1.EDXLDistributionDocument.EDXLDistribution;
import x0Msg.oasisNamesTcEmergencyEDXLRM1.RequestResourceDocument;

import com.saic.precis.x2009.x06.base.IdentifierType;
import com.saic.precis.x2009.x06.base.ProcessingStateType;
import com.saic.precis.x2009.x06.base.ProcessingStatusType;
import com.saic.uicds.clients.em.async.UicdsIncident;
import com.saic.uicds.clients.em.async.UicdsResourceProfile;
import com.saic.uicds.clients.util.Common;
import com.saic.uicds.clients.util.SpringClient;
import com.saic.uicds.clients.util.WebServiceClient;

public class RMClient
    extends SpringClient {

    static final int SLEEP_TIMER = 5000; // check for new messages every 10 seconds
    static final String ACTION_REQUEST = "request"; // parameter to indicate that this client will
                                                    // act as requester
    static final String ACTION_RESPONSE = "response"; // parameter to indicate that this client will
                                                      // act as responder
    final String UICDS_EXPLICIT_ADDRESS_SCHEME = "uicds:user";

    private UicdsResourceProfile uicdsResourceProfile;

    public void setUicdsResourceProfile(UicdsResourceProfile resourceProfile) {

        uicdsResourceProfile = resourceProfile;
    }

    private UicdsCoreRMImpl uicdsCore;

    public void setUicdsCore(UicdsCoreRMImpl core) {

        uicdsCore = core;
    }

    private WebServiceClient webServiceClient;

    public void setWebServiceClient(WebServiceClient client) {

        webServiceClient = client;
    }

    public WebServiceClient getWebServiceClient() {

        return webServiceClient;
    }

    private String createProfileRequestFileName;

    public void setCreateProfileRequestFileName(String createProfileRequestFileName) {

        this.createProfileRequestFileName = createProfileRequestFileName;
    }

    public String getCreateProfileRequestFileName() {

        return this.createProfileRequestFileName;
    }

    private String requestResourceFileName;

    public void setRequestResourceFileName(String requestResourceFileName) {

        this.requestResourceFileName = requestResourceFileName;
    }

    public String getRequestResourceFileName() {

        return this.requestResourceFileName;
    }

    private String commitResourceFileName;

    public void setCommitResourceFileName(String commitResourceFileName) {

        this.commitResourceFileName = commitResourceFileName;
    }

    public String getCommitResourceFileName() {

        return this.commitResourceFileName;
    }

    private String incidentFileName;

    public void setIncidentFileName(String incidentFileName) {

        this.incidentFileName = incidentFileName;
    }

    public String getIncidentFileName() {

        return this.incidentFileName;
    }

    private String uicdsId;

    public void setUicdsId(String uicdsId) {

        this.uicdsId = uicdsId;
    }

    public String getUicdsId() {

        return this.uicdsId;
    }

    private String uicdsId2;

    public void setUicdsId2(String uicdsId2) {

        this.uicdsId2 = uicdsId2;
    }

    public String getUicdsId2() {

        return this.uicdsId2;
    }

    private String action;

    public void setAction(String action) {

        this.action = action;
    }

    public String getAction() {

        return this.action;
    }

    /**
     * @param args
     */

    public static void main(String[] args) {

        // default the protocol if not specified
        if (args.length == 0) {
            usage();
            return;
        }

        // Get the spring context and then the RMClient object that was configured in it
        ApplicationContext context = new ClassPathXmlApplicationContext(
            new String[] { "contexts/rm-context.xml" });
        RMClient rmClient = (RMClient) context.getBean("rmClient");
        if (rmClient == null) {
            System.err.println("Could not instantiate rmClient");
        }

        if (rmClient.processArgs(args)) {

            if (!rmClient.initializeUicdsCore()) {
                return;
            }

            String action = rmClient.getAction();
            if (action.equals(ACTION_REQUEST)) {
                rmClient.processRequest();
            } else if (action.equals(ACTION_RESPONSE)) {
                rmClient.processResponse();
            } else {
                System.out.println("Nothing to do for action=" + action);
            }
        }
    }

    private Boolean processArgs(String[] args) {

        if (uicdsCore == null) {
            System.err.println("Could not instantiate uicdsCore");
            return false;
        }

        if (webServiceClient == null) {
            System.err.println("Could not instantiate webServiceClient");
            return false;
        }

        String uRI = Common.getArgURI(args);
        if (uRI != null) {
            webServiceClient.setURI(uRI);
        }
        System.out.println("URI=" + webServiceClient.getURI());

        String parameterFileName = Common.getArgParameterFileName(args);
        if (parameterFileName == null) {
            System.out.println("parameterFileName is null or file does not exist");
            return false;
        }
        System.out.println("using parameterFile " + parameterFileName);

        // read the parameter file
        Document parametersDocument = Common.getXmlDocByFile(parameterFileName);

        // get the createResourceProfileRequestFileName parameter from the parameter file
        String createProfileRequestFileName = Common.getDocumentParameter(parametersDocument,
            "createProfileRequestFileName");
        if (createProfileRequestFileName != null) {
            setCreateProfileRequestFileName(createProfileRequestFileName);
            System.out.println("createProfileRequestFileName=" + createProfileRequestFileName);
        }

        // get the requestResourceFileName parameter from the parameter file
        String requestResourceFileName = Common.getDocumentParameter(parametersDocument,
            "requestResourceFileName");
        if (requestResourceFileName != null) {
            setRequestResourceFileName(requestResourceFileName);
            System.out.println("requestResourceFileName=" + requestResourceFileName);
        }

        // get the commitResourceFileName parameter from the parameter file
        String commitResourceFileName = Common.getDocumentParameter(parametersDocument,
            "commitResourceFileName");
        if (commitResourceFileName != null) {
            setCommitResourceFileName(commitResourceFileName);
            System.out.println("commitResourceFileName=" + commitResourceFileName);
        }

        // get the incidentFileName parameter from the parameter file
        String incidentFileName = Common.getDocumentParameter(parametersDocument,
            "incidentFileName");
        if (incidentFileName != null) {
            setIncidentFileName(incidentFileName);
            System.out.println("incidentFileName=" + incidentFileName);
        }

        // get the uicdsId parameter from the parameter file
        String uicdsId = Common.getDocumentParameter(parametersDocument, "uicdsId");
        if (uicdsId != null) {
            setUicdsId(uicdsId);
            System.out.println("uicdsId=" + uicdsId);
        }

        // get the uicdsId parameter from the parameter file
        // this is the uicdsId of the rm application that is listening for notifications
        String uicdsId2 = Common.getDocumentParameter(parametersDocument, "uicdsId2");
        if (uicdsId2 != null) {
            setUicdsId2(uicdsId2);
            System.out.println("uicdsId2=" + uicdsId2);
        }

        // get the action parameter from the parameter file
        String action = Common.getDocumentParameter(parametersDocument, "action");
        if (action != null) {
            setAction(action);
            System.out.println("action=" + action);
        }
        return true;
    }

    private Boolean initializeUicdsCore() {

        // get the create profile request from file
        CreateProfileRequestDocument resourceProfileRequestDocument = Common.getCreateProfileRequestDocumentFromFile(getCreateProfileRequestFileName());

        // pluck the profile id from the create profile request document
        String profileId = resourceProfileRequestDocument.getCreateProfileRequest().getProfile().getID().getStringValue();

        // get the resource profile
        ResourceProfile resourceProfile = uicdsResourceProfile.getResourceProfile(profileId);
        if (resourceProfile.getID() == null) {
            // since the response processor starts up first, it is responsible to create the profile
            // if it does not exist already
            if (getAction().equals(ACTION_RESPONSE)) {
                uicdsResourceProfile.createResourceProfile(resourceProfileRequestDocument);
            } else {
                System.out.println(profileId
                    + " resourceProfile needs to be created by an instance of this client running in response mode");
            }
        }

        // The UicdsCore object is created by the Spring Framework as defined in rm-context.xml
        // Now set the application specific data for the UicdsCore object

        // Set the UICDS identifier for the application
        uicdsCore.setApplicationID(getUicdsId());
        System.out.println("applicationId=" + getUicdsId());

        // Set the site local identifier for this application
        uicdsCore.setLocalID(this.getClass().getName());
        System.out.println("localId=" + this.getClass().getName());

        // Set the application profile to use for the connection to the core
        uicdsCore.setApplicationProfileID(profileId);
        System.out.println("applicationProfileId=" + profileId);

        // Set the Web Service Client that will handle web service invocations
        uicdsCore.setWebServiceClient(webServiceClient);

        if (!uicdsCore.initialize()) {
            return false;
        }

        // Show the string to use for the explict address of this application
        System.out.println("Explict Address for this resource instance: "
            + uicdsCore.getFullResourceInstanceID());

        return true;
    }

    private void processResponse() {

        // get from file the pre-prepared commit resource xml doc command
        // that will be executed when a request resource notification is received
        uicdsCore.setCommitResourceDocument(Common.getCommitResourceDocumentFromFile(getCommitResourceFileName()));

        // loop continuously for notifications
        while (true) {
            uicdsCore.processNotifications();
            try {
                Thread.sleep(SLEEP_TIMER);
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private void processRequest() {

        // Create an incident
        System.out.println("Creating Incident");
        UicdsIncident uicdsIncident = new UicdsIncident();
        uicdsIncident.setUicdsCore(uicdsCore);

        // Create an IncidentDocument to describe the incident
        IncidentDocument incidentDoc = Common.getIncidentDocumentFromFile(getIncidentFileName());

        // Create the incident on the core
        uicdsIncident.createOnCore(incidentDoc.getIncident());
        System.out.println("incidentId=" + uicdsIncident.getIncidentID());

        // Update the incident
        System.out.println("Updating the incident");
        updateIncident(uicdsIncident);

        RequestResourceDocument requestResourceDocument = Common.getRequestResourceDocumentFromFile(getRequestResourceFileName());
        requestResourceDocument.getRequestResource().getIncidentInformationArray(0).setIncidentID(
            uicdsIncident.getIncidentID());

        EdxlDeRequestDocument edxlDeRequestDocument = uicdsCore.getEdxlDeRequest(
            uicdsCore.getFullResourceInstanceID(), getUicdsId2(), uicdsIncident.getIncidentID(),
            requestResourceDocument);
        // String senderId, String receiverId,
        // String incidentId, XmlObject xmlObject) {

        System.out.println("\nSENDING edxlDeRequestDocument=\n" + edxlDeRequestDocument.toString()
            + "\n");
        EdxlDeResponseDocument edxlDeResponseDocument = uicdsCore.sendEdxlDeRequest(edxlDeRequestDocument);
        System.out.println("\nRESPONSE edxlDeResponseDocument=\n"
            + edxlDeResponseDocument.toString() + "\n");

    }

    public BroadcastMessageRequestDocument createBroadcastMessage(String senderId,
        String receiverId, String incidentId, XmlObject haveDoc) {

        BroadcastMessageRequestDocument message = BroadcastMessageRequestDocument.Factory.newInstance();
        EDXLDistribution edxl = uicdsCore.getEdxlDistribution(senderId, receiverId, incidentId,
            haveDoc);
        message.addNewBroadcastMessageRequest().addNewEDXLDistribution().set(edxl);
        return message;
    }

    private void updateIncident(UicdsIncident uicdsIncident) {

        // Get the current incident document
        UICDSIncidentType incidentType = uicdsIncident.getIncidentDocument();

        // Change the type of incident
        if (incidentType.sizeOfActivityCategoryTextArray() < 1) {
            incidentType.addNewActivityCategoryText();
        }
        incidentType.getActivityCategoryTextArray(0).setStringValue("CHANGED");
        // Update the incident on the core
        ProcessingStatusType status = uicdsIncident.updateIncident(incidentType);
        // If the request is pending then process requests until the request is accepted or rejected
        // Get the asynchronous completion token
        IdentifierType incidentUpdateACT = status.getACT();
        if (status.getStatus() == ProcessingStateType.PENDING) {
            System.out.println("Incident update is PENDING");

            // Process notifications from the core until the update request is completed
            // This loop should also process other incoming notification messages such
            // as updates for other work products
            while (!uicdsCore.requestCompleted(incidentUpdateACT)) {
                // Process messages from the core
                uicdsCore.processNotifications();

                // Get the status of the request we are waiting on
                status = uicdsCore.getRequestStatus(incidentUpdateACT);
            }

            // Check the final status of the request
            status = uicdsCore.getRequestStatus(incidentUpdateACT);
            if (status.getStatus() == ProcessingStateType.REJECTED) {
                System.err.println("UpdateIncident request was rejected: " + status.getMessage());
            }
        } else {
            System.out.println("Incident update was ACCEPTED");
        }
    }

    private static void usage() {

        System.out.println("");
        System.out.println("This is the UICDS Resource Management Example Client.");
        System.out.println("Execution of this client depends on a functioning UICDS server. The default is http://localhost/uicds/core/ws/services");
        System.out.println("To verify that a UICDS server is accessible, use a browser to navigate to http://localhost/uicds/core/ws/services/ProfileService.wsdl\"");
        System.out.println("");
        System.out.println("Usage: java -jar RMClient.jar [-u <Server URI>] -i parameterFileName");
        System.out.println("");
        System.out.println("Example request parameter file contents:");
        System.out.println("<parameters>");
        System.out.println("<createProfileRequestFileName>..\\src\\main\\resources\\data\\CreateResourceProfile-RMApplication.xml</createProfileRequestFileName>");
        System.out.println("<requestResourceFileName>..\\src\\main\\resources\\data\\RequestResource.xml</requestResourceFileName>");
        System.out.println("<incidentFileName>..\\src\\main\\resources\\data\\IncidentSample.xml</incidentFileName>");
        System.out.println("<uicdsId>RMapplication1@constellation2</uicdsId>");
        System.out.println("<uicdsId2>RMapplication2@constellation2</uicdsId2>");
        System.out.println("<action>request</action>");
        System.out.println("</parameters>");
        System.out.println("");
        System.out.println("Example response parameter file contents:");
        System.out.println("<parameters>");
        System.out.println("<createProfileRequestFileName>..\\src\\main\\resources\\data\\CreateResourceProfile-RMApplication.xml</createProfileRequestFileName>");
        System.out.println("<commitResourceFileName>..\\src\\main\\resources\\data\\CommitResource.xml</commitResourceFileName>");
        System.out.println("<uicdsId>RMapplication2@constellation2</uicdsId>");
        System.out.println("<action>response</action>");
        System.out.println("</parameters>");
        System.out.println("");
    }
}
