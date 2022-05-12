package org.jenkinsci.plugins.nomad.Api;

public final class JobSummary {
    private String JobID;
    private String Namespace;

    public JobSummary(String JobID, String Namespace) {
        this.JobID = JobID;
        this.Namespace = Namespace;
    }

    public String getNamespace() {
        return Namespace;
    }

    public void setNamespace(String namespace) {
        Namespace = namespace;
    }

    public String getJobID() {
        return JobID;
    }

    public void setJobID(String jobID) {
        JobID = jobID;
    }

}
