package org.jenkinsci.plugins.nomad;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.Util;
import hudson.XmlFile;

/**
 * Checks that different cloud configurations created with different versions of the plugin are working as expected. Below you can find a
 * list of the versions and there specifics:<br>
 * <ul>
 *     <li>0.x.y - introduced jenkinsUrl, workerUrl and driver</li>
 *     <li>0.7.4 - contains jenkinsUrl, workerUrl and driver</li>
 *     <li>0.8.0 - introduced jobTemplate and removed jenkinsUrl, workerUrl, jenkinsTunnel and almost all template fields</li>
 * </ul>
 */
@RunWith(Parameterized.class)
public class NomadCloudConfigurationTest {

    @Parameterized.Parameters(name = "path: {0}")
    public static Iterable<String> data() {
        List<String> data = new LinkedList<>();
        for (String version : Arrays.asList("0.x.y", "0.7.4", "0.9.0")) {
            for (String type : Arrays.asList("java", "docker", "raw_exec")) {
                data.add("/config/"+version+"/"+type+".xml");
            }
        }
        return data;
    }

    @Parameterized.Parameter
    public String path;

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void test() throws URISyntaxException, IOException, IllegalAccessException, NoSuchFieldException {
        // GIVEN
        NomadCloud nomadCloud = createNomadCloud(path);
        NomadWorkerTemplate template = nomadCloud.getTemplates().get(0);
        String jenkinsUrl = createJenkinsUrl(nomadCloud, path);
        NomadApi nomadApi = new NomadApi(nomadCloud);
        String workerName = template.createWorkerName();
        String secret = UUID.randomUUID().toString();

        // WHEN
        String job = nomadApi.buildWorkerJob(workerName, secret, template);

        // THEN
        assertThat(job, hasJsonPath("$.Job.ID", is(workerName)));
        assertThat(job, hasJsonPath("$.Job.Name", is(workerName)));
        assertThat(job, hasJsonPath("$.Job.Region", is("global")));
        assertThat(job, hasJsonPath("$.Job.Type", is("batch")));
        assertThat(job, hasJsonPath("$.Job.Priority", is(50)));
        assertThat(job, hasJsonPath("$.Job.Datacenters.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.Datacenters[0]", is("dc1")));
        assertThat(job, hasJsonPath("$.Job.Constraints.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.Constraints[0].LTarget", is("${attr.vault.version}")));
        assertThat(job, hasJsonPath("$.Job.Constraints[0].Operand", is("semver")));
        assertThat(job, hasJsonPath("$.Job.Constraints[0].RTarget", is(">= 0.6.1")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Name", is("jenkins-worker-taskgroup")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Count", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Name", is("jenkins-worker")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].User", is("alice")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.CPU", is(6000)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.MemoryMB", is(1024)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Networks.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Networks[0].MBits", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Networks[0].ReservedPorts.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Networks[0].ReservedPorts[0].Label", is("web")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Networks[0].ReservedPorts[0].Value", is(8080)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Devices.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Devices[0].Name", is("nvidia/gpu")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Resources.Devices[0].Count", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].LogConfig.MaxFiles", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].LogConfig.MaxFileSizeMB", is(10)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Artifacts.length()", is(1)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Artifacts[0].GetterSource", is(Util.ensureEndsWith(jenkinsUrl, "/")+"jnlpJars/slave.jar")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Artifacts[0].RelativeDest", is("/local/")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Vault.Policies.length()", is(2)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Vault.Policies[0]", is("policy1")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Vault.Policies[1]", is("policy2")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].RestartPolicy.Interval", is(10000000000L)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].RestartPolicy.Mode", is("fail")));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].RestartPolicy.Delay", is(1000000000)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].RestartPolicy.Attempts", is(0)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].EphemeralDisk.SizeMB", is(300)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].EphemeralDisk.Migrate", is(false)));
        assertThat(job, hasJsonPath("$.Job.TaskGroups[0].EphemeralDisk.Sticky", is(false)));

        if (path.endsWith("java.xml")) {
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Driver", is("java")));
            assertThat(job, hasNoJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.command"));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args.length()", is(4)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[0]", is("-jnlpUrl")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[1]", is(Util.ensureEndsWith(jenkinsUrl, "/")+"computer/"+workerName+"/worker-agent.jnlp")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[2]", is("-secret")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[3]", is(secret)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.jar_path", is("/local/slave.jar")));
        }

        else if (path.endsWith("docker.xml")) {
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Driver", is("docker")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.auth.length()", is(1)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.image", is("registry/image:version")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.auth.length()", is(1)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.auth[0].username", is("bla")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.auth[0].password", is("foo")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.security_opt.length()", is(2)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.security_opt[0]", is("apparmor=unconfined")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.security_opt[1]", is("seccomp=unconfined")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.cap_add.length()", is(2)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.cap_add[0]", is("net_raw")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.cap_add[1]", is("sys_time")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.cap_drop.length()", is(1)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.cap_drop[0]", is("mknod")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.network_mode", is("bridge")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.volumes.length()", is(1)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.volumes[0]", is("/hostDir:/localDir:ro")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.extra_hosts.length()", is(1)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.extra_hosts[0]", is("my_host:192.168.1.1")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.command", is("/bin/bash")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.dns_servers.length()", is(2)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.dns_servers[0]", is("8.8.8.8")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.dns_servers[1]", is("1.1.1.1")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args.length()", is(2)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[0]", is("-c")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[1]", is("sleep 5; java -cp /local/slave.jar hudson.remoting.jnlp.Main -headless -headless -url "+jenkinsUrl+ " -tunnel jenkins-tunnel:50000 -workDir /workspace/ " + secret + " " + workerName)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.privileged", is(false)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.force_pull", is(true)));
        }

        else if (path.endsWith("raw_exec.xml")) {
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Driver", is("raw_exec")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.command", is("java")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args.length()", is(6)));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[0]", is("-jar")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[1]", is("./local/slave.jar")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[2]", is("-jnlpUrl")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[3]", is(Util.ensureEndsWith(jenkinsUrl, "/")+"computer/"+workerName+"/worker-agent.jnlp")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[4]", is("-secret")));
            assertThat(job, hasJsonPath("$.Job.TaskGroups[0].Tasks[0].Config.args[5]", is(secret)));
        }
    }

    private static String createJenkinsUrl(NomadCloud nomadCloud, String path) throws IllegalAccessException, NoSuchFieldException {
        if (path.startsWith("/config/0.9.0/")) {
            return "http://jenkins:8080";
        }

        Field field = NomadCloud.class.getDeclaredField("jenkinsUrl");
        field.setAccessible(true);
        return (String) field.get(nomadCloud);
    }

    private static NomadCloud createNomadCloud(String configurationPath) throws URISyntaxException, IOException {
        File configFile = Paths.get(NomadCloudConfigurationTest.class.getResource(configurationPath).toURI()).toFile();
        return (NomadCloud) new XmlFile(configFile).read();
    }

}
