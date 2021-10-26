package org.jenkinsci.plugins.nomad.Api;

/**
 * Only required for backward compatibility
 */
@Deprecated
public class EphemeralDisk {

    private Integer SizeMB;
    private Boolean Migrate;
    private Boolean Sticky;

    public EphemeralDisk(Integer sizeMB, Boolean migrate, Boolean sticky) {
        SizeMB = sizeMB;
        Migrate = migrate;
        Sticky = sticky;
    }

    public Integer getSizeMB() {
        return SizeMB;
    }

    public void setSizeMB(Integer sizeMB) {
        SizeMB = sizeMB;
    }

    public Boolean getMigrate() {
        return Migrate;
    }

    public void setMigrate(Boolean migrate) {
        Migrate = migrate;
    }

    public Boolean getSticky() {
        return Sticky;
    }

    public void setSticky(Boolean sticky) {
        Sticky = sticky;
    }
}
