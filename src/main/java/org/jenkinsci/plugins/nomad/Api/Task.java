package org.jenkinsci.plugins.nomad.Api;

import java.util.Arrays;
import java.util.Map;
import java.util.List;

/**
 * Only required for backward compatibility
 */
@Deprecated
public class Task {
    private String Name;
    private String Driver;
    private String User;
    private Map<String, Object> Config;
    private Resource Resources;
    private LogConfig LogConfig;
    private Artifact[] Artifacts;
    private Vault Vault;

    public Task(
            String name,
            String driver,
            String user,
            Map<String, Object> config,
            Resource resources,
            LogConfig logConfig,
            Artifact[] artifacts,
            Vault vault
    ) {
        Name = name;
        Driver = driver;
        User = user;
        Config = config;
        Resources = resources;
        LogConfig = logConfig;
        Artifacts = Arrays.copyOf(artifacts, artifacts.length);
        if (Boolean.TRUE.equals(vault.isEmpty())) {
            Vault = null;    
        } else {
            Vault = vault;
        }
    }

    public String getName() {
        return Name;
    }

    public void setName(String name) {
        Name = name;
    }

    public String getDriver() {
        return Driver;
    }

    public void setDriver(String driver) {
        Driver = driver;
    }

    public String getUser() {
        return User;
    }

    public void setUser(String user) {
        User = user;
    }

    public Map<String, Object> getConfig() {
        return Config;
    }

    public void setConfig(Map<String, Object> config) {
        Config = config;
    }

    public Resource getResources() {
        return Resources;
    }

    public void setResources(Resource resources) {
        Resources = resources;
    }

    public LogConfig getLogConfig() {
        return LogConfig;
    }

    public void setLogConfig(LogConfig logConfig) {
        LogConfig = logConfig;
    }

    public Artifact[] getArtifacts() {
        return Arrays.copyOf(Artifacts, Artifacts.length);
    }

    public void setArtifacts(Artifact[] artifacts) {
        Artifacts = Arrays.copyOf(artifacts, artifacts.length);
    }

    public Vault getVault() {
        return Vault;
    }

    public void setVault(Vault vault) {
        Vault = vault;
    }

}
