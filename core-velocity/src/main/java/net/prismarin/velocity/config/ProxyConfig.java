package net.prismarin.velocity.config;

public record ProxyConfig(String motdLine1, String motdLine2, String maintenanceMotdLine1,
                          String maintenanceMotdLine2, String maintenanceVersionName,
                          boolean maintenance, int maxPlayers, String lobbyServer) {

    public ProxyConfig withMaintenance(boolean active) {
        return new ProxyConfig(motdLine1, motdLine2, maintenanceMotdLine1, maintenanceMotdLine2,
                maintenanceVersionName, active, maxPlayers, lobbyServer);
    }
}
