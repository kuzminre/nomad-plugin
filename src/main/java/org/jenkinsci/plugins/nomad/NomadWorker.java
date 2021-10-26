package org.jenkinsci.plugins.nomad;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.EphemeralNode;
import hudson.slaves.JNLPLauncher;
import jenkins.model.Jenkins;

public class NomadWorker extends AbstractCloudSlave implements EphemeralNode {

    private static final Logger LOGGER = Logger.getLogger(NomadWorker.class.getName());
    private final boolean reusable;
    private final String cloudName;
    private final int idleTerminationInMinutes;

    @DataBoundConstructor
    public NomadWorker(String name, String cloudName, String labelString, int numExecutors, int idleTerminationInMinutes,
            boolean reusable) throws FormException, IOException {
        super(name, "", new JNLPLauncher(false));

        setLabelString(labelString);
        setMode(labelString.isEmpty() ? Mode.NORMAL : Mode.EXCLUSIVE);
        setNumExecutors(numExecutors);
        setRetentionStrategy(new NomadRetentionStrategy(idleTerminationInMinutes));

        this.cloudName = cloudName;
        this.reusable = reusable;
        this.idleTerminationInMinutes = idleTerminationInMinutes;
    }

    @Override
    public Node asNode() {
        return this;
    }

    @Override
    public AbstractCloudComputer<NomadWorker> createComputer() {
        return new NomadComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) {
        LOGGER.log(Level.INFO, "Asking Nomad to deregister worker '" + getNodeName() + "'");
        getCloud().nomad().stopWorker(getNodeName(), getCloud().getNomadACL());
    }

    public NomadCloud getCloud() {
        return (NomadCloud) Jenkins.get().getCloud(cloudName);
    }

    public String getCloudName() {
        return cloudName;
    }

    public boolean isReusable() {
        return reusable;
    }

    public int getIdleTerminationInMinutes() {
        return this.idleTerminationInMinutes;
    }

    @Extension
    public static class DescriptorImpl extends SlaveDescriptor {
        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Nomad Worker";
        }

        /**
         * We only create these kinds of nodes programatically.
         */
        @Override
        public boolean isInstantiable() {
            return false;
        }
    }
}
