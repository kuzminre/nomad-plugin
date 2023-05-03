package org.jenkinsci.plugins.nomad;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.remoting.Channel;
import hudson.slaves.AbstractCloudComputer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NomadComputer extends AbstractCloudComputer<NomadWorker> {

    private static final Logger LOGGER = Logger.getLogger(NomadComputer.class.getName());
    private transient long connectedSince = 0;

    public NomadComputer(NomadWorker worker) {
        super(worker);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        super.taskAccepted(executor, task);
        if (!isReusable()) {
            setAcceptingTasks(false);
        }
        LOGGER.log(Level.INFO, " Computer " + this + ": task accepted");
    }

    private boolean isReusable() {
        NomadWorker node = getNode();
        return node == null ? false : node.isReusable();
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        super.taskCompleted(executor, task, durationMS);
        LOGGER.log(Level.INFO, " Computer " + this + ": task completed");
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        super.taskCompletedWithProblems(executor, task, durationMS, problems);
        LOGGER.log(Level.WARNING, " Computer " + this + " task completed with problems");
    }

    @Override
    public String toString() {
        return String.format("%s (worker: %s)", getName(), getNode());
    }

    public long getConnectedSince() {
        return connectedSince;
    }

    public void setChannel(Channel channel, OutputStream launchLog, Channel.Listener listener) throws IOException, InterruptedException {
        super.setChannel(channel, launchLog, listener);
        connectedSince = System.currentTimeMillis();
    }
}
