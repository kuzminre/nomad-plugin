package org.jenkinsci.plugins.nomad;

import hudson.model.Descriptor;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.RetentionStrategy;
import net.jcip.annotations.GuardedBy;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.logging.Level.WARNING;

public class NomadRetentionStrategy extends CloudRetentionStrategy {

    private static final Logger LOGGER = Logger.getLogger(NomadRetentionStrategy.class.getName());
    public NomadRetentionStrategy(int idleMinutes) {
        super(idleMinutes);
    }

    public NomadRetentionStrategy(String idleMinutes) {
        super(Integer.parseInt(idleMinutes));
    }

    public static class DescriptorImpl extends Descriptor<RetentionStrategy<?>> {
        @Override
        public String getDisplayName() {
            return "Nomad Retention Strategy";
        }
    }

    public long check(final AbstractCloudComputer c) {
        NomadComputer nc = (NomadComputer) c;
        if (nc.getNode() == null) {
            return 1;
        }
        LOGGER.log(Level.FINEST, "Checking nomad agent {0} for retention.", nc.getName());
        if (nc.getConnectedSince() == 0 ) {
            LOGGER.log(Level.FINEST, "Agent {0} has never been connected to Jenkins, skipped.", nc.getName());
            return 1;
        }
        return super.check(c);
    }
}
