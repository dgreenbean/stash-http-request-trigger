package com.zerosumtech.wparad.stash;

import java.io.DataOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.*;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.stash.hook.repository.RepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.user.Permission;
import com.atlassian.stash.user.PermissionValidationService;
import com.atlassian.stash.user.SecurityService;
import com.atlassian.stash.util.Operation;
import com.google.common.collect.Maps;

public class RepositoryInformationService 
{
	private static final Logger logger = LoggerFactory.getLogger(RepositoryInformationService.class);
	
	private static final String PLUGIN_KEY = "com.zerosumtech.wparad.stash.stash-http-request-trigger:postReceiveHook";
	private final PermissionValidationService permissionValidationService;
	private final RepositoryHookService repositoryHookService;
	private final SecurityService securityService;
	  
	public RepositoryInformationService(
			PermissionValidationService permissionValidationService, 
			RepositoryHookService repositoryHookService,
			SecurityService securityService) 
	{
		this.permissionValidationService = permissionValidationService;
	  	this.repositoryHookService = repositoryHookService;
		this.securityService = securityService;
	}
	  
	public boolean IsPluginEnabled(final Repository repository)
	{
		permissionValidationService.validateForRepository(repository, Permission.REPO_READ);
		try 
		{
			return securityService.doWithPermission("Retrieving repository hook", Permission.REPO_ADMIN, new Operation<Boolean, Exception>()
			{
				@Override
				public Boolean perform() throws Exception 
				{
					RepositoryHook repositoryHook = repositoryHookService.getByKey(repository, PLUGIN_KEY); 
					return repositoryHook != null && repositoryHook.isEnabled() && repositoryHookService.getSettings(repository, PLUGIN_KEY) != null;
				}
			}).booleanValue();
		}
		catch (Exception e)
		{
			logger.error("Failed: IsPluginEnabled({})", repository.getName(), e);
			return false;
		}
	}

	private Settings GetSettings(final Repository repository)
	{
		permissionValidationService.validateForRepository(repository, Permission.REPO_READ);
		try 
		{
			return securityService.doWithPermission("Retrieving settings", Permission.REPO_ADMIN, new Operation<Settings, Exception>()
			{
				@Override
				public Settings perform() throws Exception { return repositoryHookService.getSettings(repository, PLUGIN_KEY); } 
			});
		}
		catch(Exception e)
		{
			logger.error("Failed: GetSettings({})", repository.getName(), e);
			return null;
		}
	}
	
	private void UpdatePullRequestUrl(final Repository repository)
	{
		permissionValidationService.validateForRepository(repository, Permission.REPO_READ);
		try
		{
			securityService.doWithPermission("Updating settings", Permission.REPO_ADMIN, new Operation<Boolean, Exception>()
			{
				@Override
				public Boolean perform() throws Exception
				{
					Map<String, Object> newMap = Maps.newHashMap(repositoryHookService.getSettings(repository, PLUGIN_KEY).asMap());
					newMap.put("prurl", newMap.get("url"));
					repositoryHookService.setSettings(repository, PLUGIN_KEY, repositoryHookService.createSettingsBuilder().addAll(newMap).build());
					return true;
				}
			});
		}
		catch(Exception e) { logger.error("Failed: UpdateSettings({})", repository.getName(), e); }
	}
  
	public boolean CheckFromRefChanged(final Repository repository)
	{
		Settings settings = GetSettings(repository);
		return settings != null && settings.getBoolean("checkFromRefChanged", false);
	}

	public void PostChange(Repository repository, String ref, String sha, String toRef, String pullRequestNbr)
	{
		if(!IsPluginEnabled(repository)) { return; }
		Post(GetUrl(repository, ref, sha, toRef, pullRequestNbr));
	}
  
	//TODO: split pull request generation from ref change, these are actually different things.
	public String GetUrl(final Repository repository, String ref, String sha, String toRef, String pullRequestNbr)
	{
		Settings settings = GetSettings(repository);
		String baseUrl = settings.getString("url");
		String pullRequestUrl = settings.getString("prurl");
		StringBuilder urlParams = new StringBuilder();
		try 
		{
			urlParams.append("STASH_REF=" + URLEncoder.encode(ref, "UTF-8"));
			urlParams.append("&STASH_SHA=" + URLEncoder.encode(sha, "UTF-8"));
			if(pullRequestNbr != null)
			{
				if(pullRequestUrl == null || pullRequestUrl.isEmpty()) { UpdatePullRequestUrl(repository); }
				else{ baseUrl = pullRequestUrl; }
				
				urlParams.append("&STASH_TO_REF=" + URLEncoder.encode(toRef, "UTF-8"));
				urlParams.append("&STASH_PULL_REQUEST=" + pullRequestNbr);
			}
		} 
		catch (UnsupportedEncodingException e) 
		{
			logger.error("Failed to get URL ({}, {}, {}, {})", new Object[]{repository.getName(), ref, sha, pullRequestNbr});
			throw new RuntimeException(e);
		}

		//If the URL already includes query parameters then append them
		int index = baseUrl.indexOf("?");
		return baseUrl.concat( (index == -1 ? "?" : "&") + urlParams.toString());
	}
  
	public void Post(String url) 
	{
		try 
		{
			logger.info("Begin Posting to URL: {}", url);
			int index = url.indexOf("?");
			String baseUrl = url.substring(0, index);
			String urlParams = url.substring(index + 1);
			HttpsURLConnection conn = (HttpsURLConnection)(new URL(baseUrl).openConnection());
			
			//Use the unsecure trustmanager by default
			SSLContext sc = SSLContext.getInstance("TLS");
			sc.init(null, new TrustManager[] { new UnsecureX509TrustManager() }, new SecureRandom());
			conn.setSSLSocketFactory(sc.getSocketFactory());
			conn.setHostnameVerifier(new HostnameVerifier() { public boolean verify(String string, SSLSession ssls) { return true; } });
			conn.setDoOutput(true);  // Triggers POST
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

			DataOutputStream wr = new DataOutputStream(conn.getOutputStream ());
			wr.writeBytes(urlParams);
			wr.flush();
			wr.close();

			conn.getInputStream().close();
			logger.info("Success Posting to URL: {}", url);
		} 
		catch (Exception e)  { logger.error("Failed Posting to URL: {}", url, e); }
	}
}
