package org.lockss.subscription;

public class SubscriptionPlugin {
    private Long pluginNumber;
    private String pluginId;
    private String providerName;
    
    public String getPluginId() {
        return pluginId;
    }
    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }
    public String getProviderName() {
        return providerName;
    }
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
    public Long getPluginNumber() {
        return pluginNumber;
    }
    public void setPluginNumber(Long pluginNumber) {
        this.pluginNumber = pluginNumber;
    }

    @Override
    public String toString() {
      return "[Plugin pluginId=" + pluginId
      	+ ", providerName=" + providerName + "]";
    }
}
