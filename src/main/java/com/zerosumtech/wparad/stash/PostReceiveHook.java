package com.zerosumtech.wparad.stash;

import com.atlassian.event.api.EventListener;
import com.atlassian.stash.event.pull.PullRequestOpenedEvent;
import com.atlassian.stash.event.pull.PullRequestReopenedEvent;
import com.atlassian.stash.event.pull.PullRequestRescopedEvent;
import com.atlassian.stash.event.RepositoryRefsChangedEvent;
import com.atlassian.stash.hook.repository.AsyncPostReceiveRepositoryHook;
import com.atlassian.stash.hook.repository.RepositoryHookContext;
import com.atlassian.stash.pull.PullRequest;
import com.atlassian.stash.repository.RefChange;
import com.atlassian.stash.repository.RefChangeType;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.RepositorySettingsValidator;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PostReceiveHook implements AsyncPostReceiveRepositoryHook, RepositorySettingsValidator 
{
    private final RepositoryInformationService repositoryInformationService;
    
    public PostReceiveHook(RepositoryInformationService repositoryInformationService) 
    {
    	this.repositoryInformationService = repositoryInformationService;
    }

    @Override
    public void postReceive(RepositoryHookContext context, Collection<RefChange> refChanges) 
    {
		//Don't need to do this because the event triggers will handle the receive.
    }

    @EventListener
    public void onRefsChangedEvent(RepositoryRefsChangedEvent event) 
    {
    	Repository repository = event.getRepository();
    	for(RefChange refChange : event.getRefChanges())
    	{
    		if(refChange.getType() == RefChangeType.DELETE) {continue;}
    		repositoryInformationService.PostChange(repository, refChange.getRefId(), refChange.getToHash(), null, null);
    	}
    }

    @EventListener
    public void onPullRequestOpened(PullRequestOpenedEvent event) { HandlePullRequestEvent(event.getPullRequest()); }
    
    @EventListener
    public void onPullRequestReopened(PullRequestReopenedEvent event) { HandlePullRequestEvent(event.getPullRequest()); }

    @EventListener
    public void onPullRequestRescoped(PullRequestRescopedEvent event) 
	{
    	boolean checkFromRefChanged = repositoryInformationService.CheckFromRefChanged(event.getPullRequest().getToRef().getRepository());
    	String previousHash = event.getPreviousFromHash();
    	String newHash = event.getPullRequest().getFromRef().getLatestChangeset();
		if(!checkFromRefChanged || previousHash == null || newHash == null || !previousHash.equals(newHash))
		{
			HandlePullRequestEvent(event.getPullRequest());
		}
	}

    private void HandlePullRequestEvent(PullRequest pullRequest)
    {
    	Repository repository = pullRequest.getToRef().getRepository();
    	String ref = "refs/pull-requests/" + Long.toString(pullRequest.getId());
		
		repositoryInformationService.PostChange(repository,
				ref,
				pullRequest.getFromRef().getLatestChangeset(),
				pullRequest.getToRef().getId(),
				Long.toString(pullRequest.getId()));
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository)
    {
        String url = settings.getString(Constants.CONFIG_KEY_URL);
        if (url == null || url.trim().isEmpty())  { errors.addFieldError("url", "URL field is blank, please supply one"); }
        String regex = settings.getString(Constants.CONFIG_KEY_REFREGEX);
        if (regex != null && !regex.trim().isEmpty())
        {
            try { Pattern.compile(regex); }
            catch(PatternSyntaxException e) { errors.addFieldError(Constants.CONFIG_KEY_REFREGEX, "The Ref Regex is invalid, please supply a valid one: " + e.getMessage()); }
        }
    }
}
