package eu.seaclouds.paas.cloudfoundry;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;
import static org.testng.AssertJUnit.fail;
import org.cloudfoundry.client.lib.CloudFoundryException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import eu.seaclouds.paas.Credentials;
import eu.seaclouds.paas.PaasClient;
import eu.seaclouds.paas.PaasClientFactory;
import eu.seaclouds.paas.PaasException;
import eu.seaclouds.paas.PaasSession;
import eu.seaclouds.paas.PaasSession.ScaleUpDownCommand;
import eu.seaclouds.paas.PaasSession.StartStopCommand;
import eu.seaclouds.paas.ServiceApp;


/**
 * Integration tests:
 * 		http://maven.apache.org/surefire/maven-failsafe-plugin/examples/testng.html
 * 		http://stackoverflow.com/questions/2669576/order-of-execution-of-tests-in-testng
 * 		http://zeroturnaround.com/rebellabs/the-correct-way-to-use-integration-tests-in-your-build-process/
 * @author rsucasas
 *
 */
public class CloudFoundryIT
{
	
	
	// Cloud Foundry PaaS providers 
	// 		PIVOTAL = "https://api.run.pivotal.io"
	// 		BLUEMIX = "https://api.eu-gb.bluemix.net"
	// 		APPFOG = ""
	// 		PRIVATE_CF = "https://api.95.211.172.243.xip.io"
	private static final String API_URL = "https://api.run.pivotal.io";
	private static final String ORG = "ATOS-ModaClouds";
	private static final String SPACE = "development";
	private static final boolean TRUST_SELF_SIGNED_CERTS = true;
	
	// Application
	private static final String APP_NAME = "unified-paas-cloudfoundry-test2";
	private static final String SERV_NAME = "mycleardb2";
	
	// session
	private PaasSession session;
	
	
	@BeforeTest
    public void initialize()
    {
        // login / connect to PaaS
        PaasClient client = new PaasClientFactory().getClient("cloudfoundry");
        session = client.getSession(new Credentials.ApiUserPasswordOrgSpaceCredentials(API_URL, 
        																			   System.getenv("cf_user"), 
        																			   System.getenv("cf_password"), 
        																			   ORG, 
        																			   SPACE, 
        																			   TRUST_SELF_SIGNED_CERTS));
    }
    

	/**
	 * 
	 * @param m
	 * @param exeFunc
	 * @param operation
	 * @param expectedValue
	 * @param seconds
	 * @return
	 */
	private boolean checkResult(eu.seaclouds.paas.Module m, String exeFunc, String operation, int expectedValue, int seconds)
	{
		for (int i = 0; i < 10; i++)
		{
			try
			{
				System.out.println("         > " + exeFunc + " > " + operation + " == " + expectedValue + " ? ...");
				Thread.sleep(seconds*1000);
				m = session.getModule(APP_NAME);

				if (("instances".equalsIgnoreCase(operation)) && (m.getRunningInstances() == expectedValue))
				{
					System.out.println("         > " + operation + " = " + expectedValue + " !!");
					return true;
				}
			}
			catch (Exception e)
			{
				fail(e.getMessage());
				break;
			}
		}

		return false;
	}
	
	
    @Test
    public void deploy() {
    	System.out.println("### TEST > CloudFoundry > deploy()");

        String path = this.getClass().getResource("/SampleApp1.war").getFile();
        eu.seaclouds.paas.Module m = session.deploy(APP_NAME, new DeployParameters(path));
        assertNotNull(m);
        System.out.println("         > " + String.format("name='%s',  url='%s'", m.getName(), m.getUrl()));
        assertEquals(APP_NAME, m.getName());
        
        if (!checkResult(m, "deploying / starting application", "instances", 1, 10))
        	fail(APP_NAME + " not started");
        else
        	assertTrue(true);
    }
    
    
    @Test (dependsOnMethods={"deploy"})
    public void stop() 
    {
    	System.out.println("### TEST > CloudFoundry > stop()");

        eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
        session.startStop(m, StartStopCommand.STOP);
        
        if (!checkResult(m, "stopping application", "instances", 0, 2))
        	fail(APP_NAME + " not stopped");
        else
        	assertTrue(true);
    }
    
    
    @Test (dependsOnMethods={"stop"})
    public void start() {
    	System.out.println("### TEST > CloudFoundry > start()");

        eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
        session.startStop(m, StartStopCommand.START);
        
        if (!checkResult(m, "starting application", "instances", 1, 5))
        	fail(APP_NAME + " not started");
        else
        	assertTrue(true);
    }
    
    
    @Test (dependsOnMethods={"start"})
    public void scaleUp() {
    	System.out.println("### TEST > CloudFoundry > scaleUp()");

        eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
        session.scaleUpDown(m, ScaleUpDownCommand.SCALE_UP_INSTANCES);
        
        if (!checkResult(m, "scaling application", "instances", 2, 5))
        	fail(APP_NAME + " not scaled up");
        else
        	assertTrue(true);
    }
    
    
    @Test (dependsOnMethods={"scaleUp"})
    public void scaleDown() {
    	System.out.println("### TEST > CloudFoundry > scaleDown()");

        eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
        session.scaleUpDown(m, ScaleUpDownCommand.SCALE_DOWN_INSTANCES);
        
        if (!checkResult(m, "scaling application", "instances", 1, 5))
        	fail(APP_NAME + " not scaled down");
        else
        	assertTrue(true);
    }
    
    
    @Test (dependsOnMethods={"scaleDown"})
    public void bindToService() {
    	System.out.println("### TEST > CloudFoundry > bindToService()");

    	eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
    	ServiceApp service = new ServiceApp("cleardb");
    	service.setServiceInstanceName(SERV_NAME);
    	service.setServicePlan("spark");
    	
        session.bindToService(m, service);
        
        m = session.getModule(APP_NAME);
        assertEquals(1, m.getServices().size());
    }

    
    @Test (dependsOnMethods={"bindToService"})
    public void unbindFromService() {
    	System.out.println("### TEST > CloudFoundry > unbindFromService()");

    	eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
    	ServiceApp service = new ServiceApp("cleardb");
    	service.setServiceInstanceName(SERV_NAME);
    	service.setServicePlan("spark");
    	
        session.unbindFromService(m, service);
        
        m = session.getModule(APP_NAME);
        assertEquals(0, m.getServices().size());
    }
    
    
    @Test (dependsOnMethods={"unbindFromService"})
    public void undeploy() {
    	System.out.println("### TEST > CloudFoundry > undeploy()");

        session.undeploy(APP_NAME);
        
        try {
        	eu.seaclouds.paas.Module m = session.getModule(APP_NAME);
        	System.out.println("### TEST > CloudFoundry > undeploy() > " + m.getName());
        	fail(APP_NAME + " still exists");
        }
        catch (CloudFoundryException | PaasException ex)
        {
        	assertTrue(true);
        }
    }

}
