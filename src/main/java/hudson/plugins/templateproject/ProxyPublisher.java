package hudson.plugins.templateproject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.DependecyDeclarer;
import hudson.model.DependencyGraph;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Messages;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ProxyPublisher extends Recorder implements DependecyDeclarer {

	private final String projectName;

	@DataBoundConstructor
	public ProxyPublisher(String projectName) {
		this.projectName = projectName;
	}

	public String getProjectName() {
		return projectName;
	}

	public String getExpandedProjectName(AbstractBuild<?, ?> build) {
		return TemplateUtils.getExpandedProjectName(projectName, build);
	}

	public AbstractProject<?, ?> getProject() {
		return TemplateUtils.getProject(projectName, null);
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean needsToRunAfterFinalized() {
		return false;
	}

	public List<Publisher> getProjectPublishersList(AbstractBuild<?, ?> build) {
		// @TODO: exception handling
		return TemplateUtils.getProject(projectName, build).getPublishersList().toList();
	}

	@Override
	public boolean prebuild(AbstractBuild<?, ?> build, BuildListener listener) {
		for (Publisher publisher : getProjectPublishersList(build)) {
			if (!publisher.prebuild(build, listener)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		for (Publisher publisher : getProjectPublishersList(build)) {
			listener.getLogger().println("[TemplateProject] Starting publishers from: '" + getExpandedProjectName(build) + "'");
			if (!publisher.perform(build, launcher, listener)) {
				listener.getLogger().println("[TemplateProject] FAILED performing publishers from: '" + getExpandedProjectName(build) + "'");
				return false;
			}
			listener.getLogger().println("[TemplateProject] Successfully performed publishers from: '" + getExpandedProjectName(build) + "'");
		}
		return true;
	}

	@Override
	public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
		List<Action> actions = new ArrayList<Action>();
		// project might not defined when loading the first time
		AbstractProject<?, ?> templateProject = getProject();
		if (templateProject != null) {
			for (Publisher publisher : templateProject.getPublishersList().toList()) {
				actions.addAll(publisher.getProjectActions(project));
			}
		}
		return actions;
	}

	/**
	 * Any of the publisher could support the DependecyDeclarer interface,
	 *  so proxy will handle it as well.
	 *  {@inheritDoc} 
	 */
	public void buildDependencyGraph(AbstractProject project, DependencyGraph graph) {
		AbstractProject<?, ?> templateProject = getProject();
		if (templateProject != null) {
			for (Publisher publisher : templateProject.getPublishersList().toList()) {
				if (publisher instanceof DependecyDeclarer) {
					((DependecyDeclarer)publisher).buildDependencyGraph(project, graph);
				}
			}
		}
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public String getDisplayName() {
			return "Use publishers from another project";
		}

		@Override
		public boolean isApplicable(Class<? extends hudson.model.AbstractProject> jobType) {
			return true;
		}

		/**
		 * Form validation method.
		 */
		public FormValidation doCheckProjectName(@AncestorInPath AccessControlled anc, @QueryParameter String value) {
			// Require CONFIGURE permission on this project
			if (!anc.hasPermission(Item.CONFIGURE)) return FormValidation.ok();
			Item item = Hudson.getInstance().getItemByFullName(
					value, Item.class);
			if (item == null) {
				return FormValidation.error(Messages.BuildTrigger_NoSuchProject(value,
						AbstractProject.findNearest(value)
								.getName()));
			}
			if (!(item instanceof AbstractProject)) {
				return FormValidation.error(Messages.BuildTrigger_NotBuildable(value));
			}
			return FormValidation.ok();
		}
	}

}
