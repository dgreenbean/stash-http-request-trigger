# Stash Http Request Trigger

After making commits to Stash, this plugin will make a Http POST request to a specified URL, adding in query parameters for the Source Ref and Source Sha.  This plugin listens to both the main repository and all pull-requests.

##Background
While attempting to migrate source control to stash and and build to jenkins I noticed that there wasn't a good way of having Stash be the source of truth and Jenkins build on time for every commit.  There were a bunch of Stash plugins some which could listen for Forked-repository pull requests and some that would send commit information.  The Jenkins Post-Receive plugin for Stash did everything correct except attempt to start Jenkins polling.  Jenkins polling is useless, and I am not sure what problem it solves.  I want to build every commit and every pull-request, and I thought it wasn't a problem that would be hard to solve.  Fortunately if you pass a sha as the branch (the Jenkins GIT Plugin GIT_BRANCH parameter), the plugin confusingly enough will build that sha in a detached mode.  The only explanation I have for this is that git supports that functionality innately, so the plugin does too.

But that still left me with one small problem of how to send the commit information to Jenkins, for which no current Stash plugin was smart enough to handle that task.  There lies the origin of this plugin.  Although this could be used with any repository, an example for Jenkins would be having a url as:
http://jenkins/job/JENKINS_JOB_NAME/buildwithparameters?token=TOKEN

## Setup

Once installed, follow these steps:
- Navigate to a repository in Stash.
- Hit the *Settings* link
- In the left-navigation, hit the *Hooks* link
- For the **Stash Http Request Trigger**, click the *Enable* button.
- Enter a URL

### The following query parameters will be inserted into the http request
#### For Ref Changes
* STASH_REF - ref of change refs/heads/master
* STASH_SHA - Resulting sha of the ref change

#### For Pull Requests
* STASH_REF - refs/pull-requests/1
* STASH_SHA - pull request sha
* STASH_PULL_REQUEST - pull request id 1
* STASH_TO_REF - refs/heads/master (or whereever the pull request is to)

##Development
Run `rake -T` to see the list of possible commands.

