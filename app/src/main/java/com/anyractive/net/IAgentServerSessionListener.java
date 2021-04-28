package com.anyractive.net;

public interface IAgentServerSessionListener {
    public enum CONNECTION_STATUS
    {
        CONNECTING, CONNECTED, DISCONNECTED
    }
    public void onConnectionStatus(AgentServerSessionThread session,
                                   CONNECTION_STATUS connectionStatus);
    public void onPacketReceived(String msg);
}
