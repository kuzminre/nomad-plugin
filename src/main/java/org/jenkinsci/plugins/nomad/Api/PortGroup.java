package org.jenkinsci.plugins.nomad.Api;

import org.jenkinsci.plugins.nomad.NomadPortTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Only required for backward compatibility
 */
@Deprecated
public class PortGroup {

    private final List<Port> ports = new ArrayList<>();

    public PortGroup(List<? extends NomadPortTemplate> portTemplate) {
        for (NomadPortTemplate template : portTemplate) {
            ports.add(new Port(template));
        }
    }

    public List<Port> getPorts() {
        return ports;
    }
}
