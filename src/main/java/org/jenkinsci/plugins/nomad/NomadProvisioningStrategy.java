package org.jenkinsci.plugins.nomad;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.LoadStatistics.LoadStatisticsSnapshot;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import hudson.model.queue.QueueListener;
import hudson.slaves.CloudProvisioningListener;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;

/**
 * Only required for backward compatibility
 */
@Deprecated
/**
 * Idea picked from yet-another-docker-pluign @kostyasha
 *
 * @author antweiss
 */
@Extension
public class NomadProvisioningStrategy extends NodeProvisioner.Strategy {
    private static final Logger LOGGER = getLogger(NomadProvisioningStrategy.class.getName());

    /**
     * Do asap provisioning
     */
    @Nonnull
    @Override
    public NodeProvisioner.StrategyDecision apply(@Nonnull NodeProvisioner.StrategyState strategyState) {
        final Label label = strategyState.getLabel();
        LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
        for (Cloud nomadCloud : Jenkins.get().clouds) {
            if (nomadCloud instanceof NomadCloud) {

                LOGGER.log(Level.FINE,
                        "Available executors={0} connecting executors={1} AdditionalPlannedCapacity={2} pending ={3}",
                        new Object[] { snapshot.getAvailableExecutors(), snapshot.getConnectingExecutors(),
                                strategyState.getAdditionalPlannedCapacity(), ((NomadCloud) nomadCloud).getPending() });
                int availableCapacity = snapshot.getAvailableExecutors() +
                        snapshot.getConnectingExecutors() +
                        strategyState.getAdditionalPlannedCapacity() +
                        strategyState.getPlannedCapacitySnapshot();
                int previousCapacity = availableCapacity;
                int currentDemand = snapshot.getQueueLength();

                LOGGER.log(Level.FINE, "Available capacity=" + availableCapacity + " currentDemand=" + currentDemand);

                if (availableCapacity < currentDemand) {
                    Collection<PlannedNode> plannedNodes = nomadCloud.provision(label,
                            currentDemand - availableCapacity);
                    LOGGER.log(Level.FINE, "Planned " + plannedNodes.size() + " new nodes");
                    fireOnStarted(nomadCloud, strategyState.getLabel(), plannedNodes);
                    strategyState.recordPendingLaunches(plannedNodes);
                    availableCapacity += plannedNodes.size();
                    LOGGER.log(Level.FINE, "After provisioning, available capacity=" + availableCapacity
                            + " currentDemand=" + currentDemand);
                }
                if (availableCapacity > previousCapacity && label != null) {
                    LOGGER.log(Level.FINE, "Suggesting NodeProvisioner review");
                    Timer.get().schedule(label.nodeProvisioner::suggestReviewNow, 1L, TimeUnit.SECONDS);
                }
                if (availableCapacity >= currentDemand) {
                    LOGGER.log(Level.FINE, "Provisioning completed");
                    return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
                } else {
                    LOGGER.log(Level.FINE, "Provisioning not complete, consulting remaining strategies");
                    return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
                }
            }
        }
        LOGGER.log(Level.FINE, "Provisioning not complete, consulting remaining strategies");
        return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
    }

    /**
     * Force the onStarted event on the CloudProvisioningListener even if the nodes are not ready
     * to notify the state as early as possible
     */
    private static void fireOnStarted(final Cloud cloud, final Label label,
            final Collection<NodeProvisioner.PlannedNode> plannedNodes) {
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
            try {
                cl.onStarted(cloud, label, plannedNodes);
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE, "Unexpected uncaught exception encountered while "
                        + "processing onStarted() listener call in " + cl + " for label "
                        + label.toString(), e);
            }
        }
    }

    /**
     * Ping the nodeProvisioner as a new task enters the queue.
     */
    @Extension
    public static class FastProvisioning extends QueueListener {

        @Override
        public void onEnterBuildable(Queue.BuildableItem item) {
            final Jenkins jenkins = Jenkins.get();
            final Label label = item.getAssignedLabel();
            for (Cloud cloud : jenkins.clouds) {
                if (cloud instanceof NomadCloud && cloud.canProvision(new Cloud.CloudState(label, 0))) {
                    final NodeProvisioner provisioner = (label == null
                            ? jenkins.unlabeledNodeProvisioner
                            : label.nodeProvisioner);
                    provisioner.suggestReviewNow();
                }
            }
        }
    }
}
