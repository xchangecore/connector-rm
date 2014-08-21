connector-rm
============

Example code for an XchangeCore Resource Management Client.

Dependencies:
connector-base-util
connector-base-async

To Build:
1. Use maven and run "mvn clean install" to build the dependencies.
2. Run "mvn clean install" to build Resource Management Client.

To Run:
1. Copy the rm/src/main/resources/contexts/rm-context to the same directory of the RMClient.jar file.
2. Use an editor to open the rm-context file.
3. Look for the webServiceTemplate bean, replace the "defaultUri" to the XchangeCore you are using to run this adapter to create the incidents.
   If not localhost, change http to https, example "https://test4.xchangecore.leidos.com/uicds/core/ws/services"
4. Change the "credentials" to a valid username and password that can access your XchangeCore.
5. Open a cygwin or windows, change directory to where the RMClient.jar file is located, run "java -jar RMclient.jar"

