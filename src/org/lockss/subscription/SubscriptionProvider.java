package org.lockss.subscription;

public class SubscriptionProvider {
    private Long providerNumber;
    private String providerId;
    private String providerName;
    
    public String getProviderId() {
        return providerId;
    }
    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
    public String getProviderName() {
        return providerName;
    }
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }
    public Long getProviderNumber() {
        return providerNumber;
    }
    public void setProviderNumber(Long providerNumber) {
        this.providerNumber = providerNumber;
    }

    @Override
    public String toString() {
      return "[Provider providerId=" + providerId
      	+ ", providerName=" + providerName + "]";
    }
}
