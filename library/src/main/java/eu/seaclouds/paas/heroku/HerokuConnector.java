package eu.seaclouds.paas.heroku;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.heroku.api.Heroku;
import com.heroku.api.Addon;
import com.heroku.api.AddonChange;
import com.heroku.api.App;
import com.heroku.api.HerokuAPI;
import com.heroku.api.exception.RequestFailedException;
import com.heroku.sdk.deploy.DeployWar;


/**
 * 
 * @author
 *
 */
public class HerokuConnector
{
	
	
	// logger
	private static final Logger logger = LoggerFactory.getLogger(HerokuConnector.class);
		
	/**
	 * API client
	 */
	private HerokuAPI _hApiClient;
	private String apiKey;
	
	/**
	 * HEROKU KEY - FROM ENVIRONMENT VALUES
	 */
	public static String ENV_HEROKU_API_KEY = "HEROKU_API_KEY";
	
	// DEFAULT / GLOBAL PROPERTIES
	public static String DEFAULT_WEBAPP_RUNNER_VERSION = "8.0.23.0";
	public static String WEBAPP_RUNNER_URL_FORMAT = "http://central.maven.org/maven2/com/github/jsimone/webapp-runner/%s/webapp-runner-%s.jar";
	public static String DEFAULT_STACK = Heroku.Stack.Cedar.name();
	public static String DEFAULT_SLUG_FILE_NAME = "slug.tgz";
	public static String PROPERTY_DEFAULT_JDK_VERSION = System.getProperty("heroku.jdkVersion", null);
	public static String PROPERTY_DEFAULT_JDK_URL = System.getProperty("heroku.jdkUrl", null);
	public static String PROPERTY_DEFAULT_INCLUDES_VALUE = System.getProperty("heroku.includes", "");
	
	
	/**
	 * 
	 */
	public HerokuConnector()
	{
		logger.info(">> Connecting to Heroku ...");
		apiKey = System.getenv(ENV_HEROKU_API_KEY);
		connect();
	}
	
	
	/**
	 * 
	 * @param apiKey
	 */
	public HerokuConnector(String apiKey) {
		logger.info(">> Connecting to Heroku ...");
	    this.apiKey = apiKey;
	    connect();
	}
	
	
	/**
	 * 
	 * @param login
	 * @param passwd
	 */
	public HerokuConnector(String login, String passwd)
	{
		logger.info(">> Connecting to Heroku ...");
		apiKey = HerokuAPI.obtainApiKey(login, passwd);
		connect();
	}

	
	/**
	 * 
	 */
	private void connect() {
        if ((apiKey != null) && (!apiKey.isEmpty()))
        {
            try 
            {
                _hApiClient = new HerokuAPI(apiKey);
                logger.info(">> Connection established: " + _hApiClient.getUserInfo().getId());
                return;
            } 
            catch (RequestFailedException e) 
            {
            	logger.warn(">> Not connected to Heroku: " + e.getMessage());
                throw new RuntimeException("Not connected to Heroku: " + e.getMessage(), e);
            }
        }
        else
        {
        	logger.warn(">> Not connected to Heroku: API key is null or empty");
            throw new RuntimeException("Not connected to Heroku: API key is null or empty");
        }
	}
	
	
	/**
	 * 
	 * @return
	 */
	public HerokuAPI getHerokuAPIClient()
	{
		return _hApiClient;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @return
	 */
	public App createJavaWebApp(String applicationName) 
	{
		logger.info(">> Creating a new java web application ['" + applicationName + "'] ...");
		App app = null;
		
		if (_hApiClient != null)
		{
			// create application
			try 
			{
				app = _hApiClient.createApp(new App().on(Heroku.Stack.Cedar).named(applicationName));
			} 
			catch (Exception ex) 
			{
				logger.error(">> Error when creating the application: " + ex.getMessage());
			} 
			if (app != null)
			{
				logger.info(">> Application '" + applicationName + "' was created");
				return app;
			}
			
			logger.warn(">> Couldn't create application '" + applicationName + "'");
		}
		
		return app;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 */
	public void deleteApp(String applicationName) 
	{
		logger.info(">> Deleting application '" + applicationName + "' ...");
		
		if ( (_hApiClient != null) && (appExists(applicationName)) )
		{
			_hApiClient.destroyApp(applicationName);
			logger.info(">> Application '" + applicationName + "' deleted");
		}
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @param warFile
	 * @return
	 */
	public boolean deployJavaWebApp(String applicationName, String warFile) 
	{
		try 
		{
			logger.info(">> Deploying app '" + applicationName + "' ...");
			
			if (checkAppName(applicationName))
			{
				String jdkVersion = PROPERTY_DEFAULT_JDK_VERSION;
				String jdkUrl = PROPERTY_DEFAULT_JDK_URL;
				String stack = DEFAULT_STACK;
				List<File> includes = includesToList(PROPERTY_DEFAULT_INCLUDES_VALUE);
				String slugFileName = DEFAULT_SLUG_FILE_NAME;
				String webappRunnerVersion = DEFAULT_WEBAPP_RUNNER_VERSION;
				String webappRunnerUrl = String.format(WEBAPP_RUNNER_URL_FORMAT, webappRunnerVersion, webappRunnerVersion);
				
				DeployWar deployWarApp = new DeployWar(applicationName, new File(warFile), new URL(webappRunnerUrl), apiKey);
				deployWarApp.deploy(includes, new HashMap<String, String>(), jdkUrl == null ? jdkVersion : jdkUrl, stack, slugFileName);
				
				logger.info(">> Application deployed");
				return true;
			}
			
			logger.warn(">> Application not deployed");
		} 
		catch (Exception ex) 
		{
			logger.error(">> Error when deploying the java war application: " + ex.getMessage());
		}
		
		return false;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @param warFile
	 * @param addon_plan
	 * @return
	 */
	public boolean deployJavaWebAppWithDataBase(String applicationName, String warFile, String addon_plan) 
	{
		if (_hApiClient != null)
		{
			if ((deployJavaWebApp(applicationName, warFile)) && (addAddonToApp(applicationName, addon_plan)))
				return true;
		}
		return false;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @return
	 */
	public boolean appExists(String applicationName)
	{
		logger.info(">> Checking if application '" + applicationName + "' exists ...");
		if (_hApiClient != null)
		{
			for (App ap : _hApiClient.listApps())
			{
				if (ap.getName().equals(applicationName))
				{
					logger.info(">> Application exists");
					return true;
				}
			}
			logger.info(">> Application doesn't exist");
		}
		return false;
	}
	
	
	/**
	 * 
	 * @param addon_plan
	 * @return
	 */
	public Addon getAddonByName(String addon_plan)
	{
		logger.info(">> Looking for addon '" + addon_plan + "' ... ");
		if (_hApiClient != null)
		{
			List<Addon> lAddons = _hApiClient.listAllAddons();
			if (lAddons != null)
			{
				for (Addon ad : lAddons)
				{
					if (ad.getName().equalsIgnoreCase(addon_plan))
					{
						logger.debug(">> '" + addon_plan + "': ");
						logger.debug(">> 		beta: " + ad.getBeta());
						logger.debug(">> 		desc: " + ad.getDescription());
						logger.debug(">> 		id:   " + ad.getId());
						logger.debug(">> 		conf: " + ad.getConfigured());
						logger.debug(">> 		url:  " + ad.getUrl());
						logger.debug(">> 		str:  " + ad.toString());
						return ad;
					}
				}
			}
		}
		
		return null;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @param String addon_plan
	 * @return
	 */
	public boolean addAddonToApp(String applicationName, String addon_plan) 
	{
		if (_hApiClient != null)
		{
			logger.info(">> Checking if addon is already installed ... ");
			
			if (containsAddon(_hApiClient.listAppAddons(applicationName), addon_plan))
			{
				logger.info(">> Addon already installed ... ");
				return true;
			}
			
			logger.info(">> Installing addon ... ");
			
			Addon addon = getAddonByName(addon_plan);
			if (addon != null)
			{
				AddonChange addChange = _hApiClient.addAddon(applicationName, addon_plan);
				logger.info(">> Addon installed: " + addChange.getStatus());
				return true;
			}
			else
			{
				logger.warn(">> Addon '" + addon_plan + "' not found");
			}
		}
		
		return false;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @param env
	 * @return
	 */
	public String getAppEnvironmentValue(String applicationName, String env) 
	{
		if (_hApiClient != null)
		{
			logger.info(">> looking for the environment value of key '" + env + "' ...");
			Map<String, String> envValues = _hApiClient.listConfig(applicationName);
			
			for (Map.Entry<String, String> entry : envValues.entrySet())
			{
				 if (env.equalsIgnoreCase(entry.getKey()))
				 {
					 logger.info(" -> " + entry.getKey() + "/" + entry.getValue());
					 return entry.getValue();
				 }
			}
			
			logger.warn(">> key '" + env + "' not found");
		}
		
		return null;
	}
	
	
	/**
	 * 
	 * @param lAddons
	 * @param addon_plan
	 * @return
	 */
	private boolean containsAddon(List<Addon> lAddons, String addon_plan)
	{
		if (lAddons != null)
		{
			for (Addon ad : lAddons)
			{
				if (ad.getName().equalsIgnoreCase(addon_plan))
				{
					return true;
				}
			}
		}
		return false;
	}
	
	
	/**
	 * 
	 * @param applicationName
	 * @return
	 */
	private boolean checkAppName(String applicationName)
	{
		if ( (!appExists(applicationName)) && (createJavaWebApp(applicationName) == null))
		{
			return false;
		}
		return true;
	}
	
	
	/**
	 * 
	 * @param includes
	 * @return
	 */
	private List<File> includesToList(String includes)
	{
		List<String> includeStrings = Arrays.asList(includes.split(File.pathSeparator));

		List<File> includeFiles = new ArrayList<>(includeStrings.size());
		for (String includeString : includeStrings) {
			if (!includeString.isEmpty()) {
				includeFiles.add(new File(includeString));
			}
		}

		return includeFiles;
	}


	/**
	 * 
	 * @return
	 */
	public static String getDEFAULT_WEBAPP_RUNNER_VERSION()
	{
		return DEFAULT_WEBAPP_RUNNER_VERSION;
	}

	
	/**
	 * 
	 * @param dEFAULT_WEBAPP_RUNNER_VERSION
	 */
	public static void setDEFAULT_WEBAPP_RUNNER_VERSION(String dEFAULT_WEBAPP_RUNNER_VERSION)
	{
		DEFAULT_WEBAPP_RUNNER_VERSION = dEFAULT_WEBAPP_RUNNER_VERSION;
	}


	/**
	 * 
	 * @return
	 */
	public static String getWEBAPP_RUNNER_URL_FORMAT()
	{
		return WEBAPP_RUNNER_URL_FORMAT;
	}

	
	/**
	 * 
	 * @param wEBAPP_RUNNER_URL_FORMAT
	 */
	public static void setWEBAPP_RUNNER_URL_FORMAT(String wEBAPP_RUNNER_URL_FORMAT)
	{
		WEBAPP_RUNNER_URL_FORMAT = wEBAPP_RUNNER_URL_FORMAT;
	}


	/**
	 * 
	 * @return
	 */
	public static String getDEFAULT_STACK()
	{
		return DEFAULT_STACK;
	}

	
	/**
	 * 
	 * @param dEFAULT_STACK
	 */
	public static void setDEFAULT_STACK(String dEFAULT_STACK)
	{
		DEFAULT_STACK = dEFAULT_STACK;
	}


	/**
	 * 
	 * @return
	 */
	public static String getDEFAULT_SLUG_FILE_NAME()
	{
		return DEFAULT_SLUG_FILE_NAME;
	}


	/**
	 * 
	 * @param dEFAULT_SLUG_FILE_NAME
	 */
	public static void setDEFAULT_SLUG_FILE_NAME(String dEFAULT_SLUG_FILE_NAME)
	{
		DEFAULT_SLUG_FILE_NAME = dEFAULT_SLUG_FILE_NAME;
	}


	/**
	 * 
	 * @return
	 */
	public static String getPROPERTY_DEFAULT_JDK_VERSION()
	{
		return PROPERTY_DEFAULT_JDK_VERSION;
	}


	/**
	 * 
	 * @param pROPERTY_DEFAULT_JDK_VERSION
	 */
	public static void setPROPERTY_DEFAULT_JDK_VERSION(String pROPERTY_DEFAULT_JDK_VERSION)
	{
		PROPERTY_DEFAULT_JDK_VERSION = pROPERTY_DEFAULT_JDK_VERSION;
	}

	
	/**
	 * 
	 * @return
	 */
	public static String getPROPERTY_DEFAULT_JDK_URL()
	{
		return PROPERTY_DEFAULT_JDK_URL;
	}


	/**
	 * 
	 * @param pROPERTY_DEFAULT_JDK_URL
	 */
	public static void setPROPERTY_DEFAULT_JDK_URL(String pROPERTY_DEFAULT_JDK_URL)
	{
		PROPERTY_DEFAULT_JDK_URL = pROPERTY_DEFAULT_JDK_URL;
	}
	
	
	/**
	 * 
	 * @return
	 */
	public static String getPROPERTY_DEFAULT_INCLUDES_VALUE()
	{
		return PROPERTY_DEFAULT_INCLUDES_VALUE;
	}


	/**
	 * 
	 * @param pROPERTY_DEFAULT_INCLUDES_VALUE
	 */
	public static void setPROPERTY_DEFAULT_INCLUDES_VALUE(String pROPERTY_DEFAULT_INCLUDES_VALUE)
	{
		PROPERTY_DEFAULT_INCLUDES_VALUE = pROPERTY_DEFAULT_INCLUDES_VALUE;
	}
	

}
