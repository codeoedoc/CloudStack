/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package com.cloud.api.response;

import java.util.Date;

import com.cloud.api.ApiConstants;
import com.cloud.serializer.Param;
import com.google.gson.annotations.SerializedName;

public class IPAddressResponse extends BaseResponse {
    @SerializedName("id") @Param(description="public IP address id")
    private Long id;
    
    @SerializedName("ipaddress") @Param(description="public IP address")
    private String ipAddress;

    @SerializedName("allocated") @Param(description="date the public IP address was acquired")
    private Date allocated;

    @SerializedName("zoneid") @Param(description="the ID of the zone the public IP address belongs to")
    private Long zoneId;

    @SerializedName("zonename") @Param(description="the name of the zone the public IP address belongs to")
    private String zoneName;

    @SerializedName("issourcenat") @Param(description="true if the IP address is a source nat address, false otherwise")
    private Boolean sourceNat;

    @SerializedName("account") @Param(description="the account the public IP address is associated with")
    private String accountName;

    @SerializedName("domainid") @Param(description="the domain ID the public IP address is associated with")
    private Long domainId;

    @SerializedName("domain") @Param(description="the domain the public IP address is associated with")
    private String domainName;

    @SerializedName("forvirtualnetwork") @Param(description="the virtual network for the IP address")
    private Boolean forVirtualNetwork;

    @SerializedName("vlanid") @Param(description="the ID of the VLAN associated with the IP address")
    private Long vlanId;

    @SerializedName("vlanname") @Param(description="the VLAN associated with the IP address")
    private String vlanName;

    @SerializedName("isstaticnat") @Param(description="true if this ip is for static nat, false otherwise")
    private Boolean staticNat;
    
    @SerializedName("virtualmachineid") @Param(description="virutal machine id the ip address is assigned to (not null only for static nat Ip)")
    private Long virtualMachineId;
    
    @SerializedName("virtualmachinename") @Param(description="virutal machine name the ip address is assigned to (not null only for static nat Ip)")
    private String virtualMachineName;
    
    @SerializedName("virtualmachinedisplayname") @Param(description="virutal machine display name the ip address is assigned to (not null only for static nat Ip)")
    private String virtualMachineDisplayName;
    
    @SerializedName("associatednetworkid") @Param(description="the ID of the Network associated with the IP address")
    private Long associatedNetworkId;
    
    @SerializedName("networkid") @Param(description="the ID of the Network where ip belongs to")
    private Long networkId;
    
    @SerializedName(ApiConstants.STATE) @Param(description="State of the ip address. Can be: Allocatin, Allocated and Releasing")
    private String state;
    
    @SerializedName(ApiConstants.JOB_ID) @Param(description="shows the current pending asynchronous job ID. This tag is not returned if no current pending jobs are acting on the volume")
    private Long jobId;

    @SerializedName("jobstatus") @Param(description="shows the current pending asynchronous job status")
    private Integer jobStatus;
    
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Date getAllocated() {
        return allocated;
    }

    public void setAllocated(Date allocated) {
        this.allocated = allocated;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public Boolean getSourceNat() {
        return sourceNat;
    }

    public void setSourceNat(Boolean sourceNat) {
        this.sourceNat = sourceNat;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    public void setDomainId(Long domainId) {
        this.domainId = domainId;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public Boolean getForVirtualNetwork() {
        return forVirtualNetwork;
    }

    public void setForVirtualNetwork(Boolean forVirtualNetwork) {
        this.forVirtualNetwork = forVirtualNetwork;
    }

    public Long getVlanId() {
        return vlanId;
    }

    public void setVlanId(Long vlanId) {
        this.vlanId = vlanId;
    }

    public String getVlanName() {
        return vlanName;
    }

    public void setVlanName(String vlanName) {
        this.vlanName = vlanName;
    }

	public Boolean getStaticNat() {
		return staticNat;
	}

	public void setStaticNat(Boolean staticNat) {
		this.staticNat = staticNat;
	}

    public Long getAssociatedNetworkId() {
        return associatedNetworkId;
    }

    public void setAssociatedNetworkId(Long networkId) {
        this.associatedNetworkId = networkId;
    }

    public Long getNetworkId() {
        return networkId;
    }

    public void setNetworkId(Long networkId) {
        this.networkId = networkId;
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    public void setVirtualMachineId(Long virtualMachineId) {
        this.virtualMachineId = virtualMachineId;
    }

    public String getVirtualMachineName() {
        return virtualMachineName;
    }

    public void setVirtualMachineName(String virtualMachineName) {
        this.virtualMachineName = virtualMachineName;
    }

    public String getVirtualMachineDisplayName() {
        return virtualMachineDisplayName;
    }

    public void setVirtualMachineDisplayName(String virtualMachineDisplayName) {
        this.virtualMachineDisplayName = virtualMachineDisplayName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public Long getObjectId() {
        return getId();
    }
    
    @Override
    public Long getJobId() {
        return jobId;
    }

    @Override
    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }
    
    @Override
    public Integer getJobStatus() {
        return jobStatus;
    }

    @Override
    public void setJobStatus(Integer jobStatus) {
        this.jobStatus = jobStatus;
    }
}
