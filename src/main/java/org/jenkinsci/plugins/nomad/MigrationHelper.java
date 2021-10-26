package org.jenkinsci.plugins.nomad;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.nomad.Api.Artifact;
import org.jenkinsci.plugins.nomad.Api.Constraint;
import org.jenkinsci.plugins.nomad.Api.ConstraintGroup;
import org.jenkinsci.plugins.nomad.Api.Device;
import org.jenkinsci.plugins.nomad.Api.DevicePluginGroup;
import org.jenkinsci.plugins.nomad.Api.EphemeralDisk;
import org.jenkinsci.plugins.nomad.Api.Job;
import org.jenkinsci.plugins.nomad.Api.LogConfig;
import org.jenkinsci.plugins.nomad.Api.Network;
import org.jenkinsci.plugins.nomad.Api.PortGroup;
import org.jenkinsci.plugins.nomad.Api.Resource;
import org.jenkinsci.plugins.nomad.Api.RestartPolicy;
import org.jenkinsci.plugins.nomad.Api.Task;
import org.jenkinsci.plugins.nomad.Api.TaskGroup;
import org.jenkinsci.plugins.nomad.Api.Vault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import hudson.Util;
import hudson.util.Secret;
import jenkins.model.Jenkins;

/**
 * Helps with migrating plugin configurations from an older version to the current one.
 */
public class MigrationHelper {

    private static final Logger LOGGER = Logger.getLogger(MigrationHelper.class.getName());

    /**
     * Migrates a given {@link NomadCloud} (created with plugin version &lt; 0.8.0) to the current version. If no migration is necessary
     * then the given {@link NomadCloud} remains unchanged.
     * @since 0.8.0
     */
    public static void migrate(NomadCloud cloud) {
        LOGGER.info(String.format("migrate '%s'", cloud.getName()));
        migrateJenkinsUrl(cloud);
        migrateWorkerUrl(cloud);
        migrateTemplates(cloud);
    }

    private static void migrateTemplates(NomadCloud cloud) {
        String jenkinsUrl = getFieldValue(cloud, "jenkinsUrl");
        String jenkinsTunnel = getFieldValue(cloud, "jenkinsTunnel");
        String workerUrl = getFieldValue(cloud, "workerUrl");

        cloud.getTemplates().forEach(template -> {
            migrateDriver(template);
            migrateJobTemplate(template, jenkinsUrl, jenkinsTunnel, workerUrl);
        });
    }

    private static void migrateWorkerUrl(NomadCloud cloud) {
        String workerUrl = getFieldValue(cloud, "workerUrl");

        if (StringUtils.isEmpty(workerUrl)) {
            String jenkinsUrl = getFieldValue(cloud, "jenkinsUrl");
            migrateField(cloud, "workerUrl", Util.ensureEndsWith(jenkinsUrl, "/") + "jnlpJars/slave.jar");
        }
    }

    private static void migrateJenkinsUrl(NomadCloud cloud) {
        String jenkinsUrl = getFieldValue(cloud, "jenkinsUrl");

        if (StringUtils.isEmpty(jenkinsUrl)) {
            migrateField(cloud, "jenkinsUrl", Jenkins.get().getRootUrl());
        }
    }

    private static void migrateDriver(NomadWorkerTemplate template) {
        String driver = getFieldValue(template, "driver");

        if (StringUtils.isEmpty(driver)) {
            Boolean useRawExec = getFieldValue(template, "useRawExec");

            if (Boolean.TRUE.equals(useRawExec)) {
                driver  = "raw_exec";
            } else {
                String image = getFieldValue(template, "image");
                driver = StringUtils.isEmpty(image) ? "java" : "docker";
            }

            migrateField(template, "driver", driver);
        }
    }

    private static void migrateJobTemplate(NomadWorkerTemplate template, String jenkinsUrl, String jenkinsTunnel, String workerUrl) {
        String jobTemplate = getFieldValue(template, "jobTemplate");

        if (StringUtils.isEmpty(jobTemplate)) {
            jobTemplate = buildWorkerJob("%WORKER_NAME%", "%WORKER_SECRET%", jenkinsUrl, jenkinsTunnel, workerUrl, template);

            migrateField(template, "jobTemplate", jobTemplate);
        }
    }

    private static void migrateField(Object object, String fieldName, Object newValue) {
        String oldValue = getFieldValue(object, fieldName);

        setFieldValue(object, fieldName, newValue);

        LOGGER.info(String.format("parameter migrated [param=%s, old=%s, new=%s]",
                fieldName,
                oldValue,
                newValue));
    }

    /**
     * This is basically a copy of the old NomadApi#buildWorkerJob method (plugin version &lt; 0.8.0).
     */
    private static String buildWorkerJob(
            String name,
            String secret,
            String jenkinsUrl,
            String jenkinsTunnel,
            String workerUrl,
            NomadWorkerTemplate template
    ) {
        PortGroup portGroup = new PortGroup(getFieldValue(template, "ports"));
        Network network = new Network(1, portGroup.getPorts());
        DevicePluginGroup devicePluginGroup = new DevicePluginGroup(getFieldValue(template, "devicePlugins"));
        List<Device> devices = devicePluginGroup.getDevicePlugins();

        ArrayList<Network> networks = new ArrayList<>(1);
        networks.add(network);

        Task task = new Task(
                "jenkins-worker",
                getFieldValue(template, "driver"),
                getFieldValue(template, "switchUser"),
                buildDriverConfig(name, secret, jenkinsUrl, jenkinsTunnel, template),
                new Resource(
                        getFieldValue(template, "cpu"),
                        getFieldValue(template, "memory"),
                        networks,
                        devices
                ),
                new LogConfig(1, 10),
                new Artifact[]{
                        new Artifact(workerUrl, null, "/local/")
                },
                new Vault(getFieldValue(template, "vaultPolicies"))
        );

        TaskGroup taskGroup = new TaskGroup(
                "jenkins-worker-taskgroup",
                1,
                new Task[]{task},
                new RestartPolicy(0, 10000000000L, 1000000000L, "fail"),
                new EphemeralDisk(getFieldValue(template, "disk"), false, false)
        );

        ConstraintGroup constraintGroup = new ConstraintGroup(getFieldValue(template, "constraints"));
        List<Constraint> Constraints = constraintGroup.getConstraints();

        Job job = new Job(
                name,
                name,
                getFieldValue(template, "region"),
                "batch",
                getFieldValue(template, "priority"),
                ((String) getFieldValue(template, "datacenters")).split(","),
                Constraints,
                new TaskGroup[]{taskGroup}
        );

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        JsonObject jobJson = new JsonObject();

        jobJson.add("Job", gson.toJsonTree(job));

        return gson.toJson(jobJson);
    }

    /**
     * This is basically a copy of the old NomadApi#buildDriverConfig method (plugin version &lt; 0.8.0).
     */
    private static Map<String, Object> buildDriverConfig(String name, String secret, String jenkinsUrl, String jenkinsTunnel,
            NomadWorkerTemplate template) {
        Map<String, Object> driverConfig = new HashMap<>();

        String username = getFieldValue(template, "username");
        Secret password = getFieldValue(template, "password");
        if (username != null && !username.isEmpty()) {
            Map<String, String> authConfig = new HashMap<>();
            authConfig.put("username", username);
            authConfig.put("password", password.getPlainText());

            ArrayList<Map> credentials = new ArrayList<>();
            credentials.add(authConfig);

            driverConfig.put("auth", credentials);
        }

        ArrayList<String> args = new ArrayList<>();

        if ("java".equals(getFieldValue(template, "driver"))) {
            args.add("-jnlpUrl");

            args.add(Util.ensureEndsWith(jenkinsUrl, "/") + "computer/" + name + "/worker-agent.jnlp");

            // java -cp /local/slave.jar [options...] <secret key> <agent name>
            if (!secret.isEmpty()) {
                args.add("-secret");
                args.add(secret);
            }

            driverConfig.put("jar_path", "/local/slave.jar");
            driverConfig.put("args", args);
        } else if ("raw_exec".equals(getFieldValue(template, "driver"))) {
            args.add("-jar");
            args.add("./local/slave.jar");

            args.add("-jnlpUrl");
            args.add(Util.ensureEndsWith(jenkinsUrl, "/") + "computer/" + name + "/worker-agent.jnlp");

            // java -cp /local/slave.jar [options...] <secret key> <agent name>
            if (!secret.isEmpty()) {
                args.add("-secret");
                args.add(secret);
            }

            driverConfig.put("command", "java");
            driverConfig.put("args", args);
        } else if ("docker".equals(getFieldValue(template, "driver"))) {
            args.add("-headless");

            if (!jenkinsUrl.isEmpty()) {
                args.add("-url");
                args.add(jenkinsUrl);
            }

            if (!jenkinsTunnel.isEmpty()) {
                args.add("-tunnel");
                args.add(jenkinsTunnel);
            }

            String remoteFs = getFieldValue(template, "remoteFs");
            if (!remoteFs.isEmpty()) {
                args.add("-workDir");
                args.add(Util.ensureEndsWith(remoteFs, "/"));
            }

            // java -cp /local/slave.jar [options...] <secret key> <agent name>
            if (!secret.isEmpty()) {
                args.add(secret);
            }
            args.add(name);

            String prefixCmd = getFieldValue(template, "prefixCmd");
            // If an addtional command is defined - prepend it to jenkins worker invocation
            if (!prefixCmd.isEmpty()) {
                driverConfig.put("command", "/bin/bash");
                String argString =
                        prefixCmd + "; java -cp /local/slave.jar hudson.remoting.jnlp.Main -headless ";
                argString += StringUtils.join(args, " ");
                args.clear();
                args.add("-c");
                args.add(argString);
            } else {
                driverConfig.put("command", "java");
                args.add(0, "-cp");
                args.add(1, "/local/slave.jar");
                args.add(2, "hudson.remoting.jnlp.Main");
            }
            driverConfig.put("image", getFieldValue(template, "image"));

            String hostVolumes = getFieldValue(template, "hostVolumes");
            if (hostVolumes != null && !hostVolumes.isEmpty()) {
                driverConfig.put("volumes", StringUtils.split(hostVolumes, ","));
            }

            driverConfig.put("args", args);
            driverConfig.put("force_pull", getFieldValue(template, "forcePull"));
            driverConfig.put("privileged", getFieldValue(template, "privileged"));
            driverConfig.put("network_mode", getFieldValue(template, "network"));

            String extraHosts = getFieldValue(template, "extraHosts");
            if (extraHosts != null && !extraHosts.isEmpty()) {
                driverConfig.put("extra_hosts", StringUtils.split(extraHosts, ", "));
            }

            String dnsServers = getFieldValue(template, "dnsServers");
            if (dnsServers != null && !dnsServers.isEmpty()) {
                driverConfig.put("dns_servers", StringUtils.split(dnsServers, ", "));
            }

            String securityOpt = getFieldValue(template, "securityOpt");
            if (securityOpt != null && !securityOpt.isEmpty()) {
                driverConfig.put("security_opt", StringUtils.split(securityOpt, ", "));
            }

            String capAdd = getFieldValue(template, "capAdd");
            if (capAdd != null && !capAdd.isEmpty()) {
                driverConfig.put("cap_add", StringUtils.split(capAdd, ", "));
            }

            String capDrop = getFieldValue(template, "capDrop");
            if (capDrop != null && !capDrop.isEmpty()) {
                driverConfig.put("cap_drop", StringUtils.split(capDrop, ", "));
            }
        }

        return driverConfig;
    }

    private static void setFieldValue(Object object, String fieldName, Object fieldValue) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, fieldValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T getFieldValue(Object object, String fieldName) {
        try {
            Field field = object.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(object);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

}
